package com.medresource.ui.render;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Patient;
import com.medresource.model.Position;
import com.medresource.model.ZoneType;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Elementos puramente visuales de la vista plana (2D). Un paciente que YA llegó a su recurso
 * de hospitalización, UCI o quirófano se ve acostado en el mueble, no como una figura de pie
 * encima de él. No cambia el marcador original ni el estado, los recorridos o los tiempos del
 * motor. No participa en absoluto en el dibujo de la vista isométrica (2.5D).
 *
 * <p>La ubicación se determina por el centro exacto de la casilla del recurso: no basta con que
 * el paciente esté cerca de una cama mientras camina. Esto también funciona con los primeros
 * auxilios en cama y con pacientes transportados en camilla.</p>
 */
public final class RestingPatient2DPainter {

    private static final long LIE_DOWN_NANOS = 440_000_000L;
    private static final double ARRIVAL_TOLERANCE = 1.25;

    /** La geometría coincide con la cama y la mesa de cirugía de {@link Renderer2D}; no altera sus posiciones. */
    private static final int BED_X = 29;
    private static final int BED_Y = 3;
    private static final int BED_WIDTH = 45;
    private static final int SURGERY_X = 44;
    private static final int SURGERY_Y = 11;

    /** Una cama real del layout y el paciente que ya ha llegado. */
    public record Resting(PatientMarker patient, Position slot, ZoneType zone) {
        public Resting {
            if (patient == null || slot == null || zone == null) {
                throw new IllegalArgumentException("Faltan datos para la figura acostada.");
            }
        }
    }

    private record Arrival(Position slot, long since) {
    }

    private final Map<String, Arrival> arrivals = new HashMap<>();
    private final PatientPainter walkingPainter = new PatientPainter();
    private final LongSupplier clock;
    private List<Resting> resting = List.of();

    /** Crea el pintor con el reloj del sistema. */
    public RestingPatient2DPainter() {
        this(System::nanoTime);
    }

    /**
     * Crea el pintor con un reloj propio, en nanosegundos; sirve para probar la transición sin esperar.
     *
     * @param clock reloj que da el instante actual en nanosegundos
     * @throws IllegalArgumentException si el reloj es nulo
     */
    RestingPatient2DPainter(LongSupplier clock) {
        if (clock == null) {
            throw new IllegalArgumentException("El reloj es obligatorio.");
        }
        this.clock = clock;
    }

    /**
     * Actualiza únicamente las transiciones visuales. El marcador recibido sigue siendo el mismo
     * que utiliza la 2.5D, y no es modificado ni sustituido.
     */
    public void observe(HospitalLayout layout, List<PatientMarker> markers) {
        if (layout == null || markers == null) {
            throw new IllegalArgumentException("El plano y los pacientes son obligatorios.");
        }
        resting = locate(layout, markers);
        long now = clock.getAsLong();
        Set<String> present = new HashSet<>();
        for (Resting entry : resting) {
            String code = entry.patient().code();
            present.add(code);
            Arrival previous = arrivals.get(code);
            if (previous == null || !previous.slot().equals(entry.slot())) {
                arrivals.put(code, new Arrival(entry.slot(), now));
            }
        }
        arrivals.keySet().removeIf(code -> !present.contains(code));
    }

    /** En un hospital nuevo no se reutiliza la animación del anterior. */
    public void clear() {
        arrivals.clear();
        resting = List.of();
    }

    /** Indica si hace falta repintar unos cuadros para completar la transición 2D. */
    public boolean transitioning() {
        long now = clock.getAsLong();
        return arrivals.values().stream().anyMatch(a -> now - a.since() < LIE_DOWN_NANOS);
    }

    /** Identifica a quienes realmente ocupan un mueble, sin cambiar la lista recibida. */
    public Set<String> restingCodes() {
        Set<String> codes = new HashSet<>();
        for (Resting entry : resting) {
            codes.add(entry.patient().code());
        }
        return codes;
    }

    /** Útil para la selección: un paciente acostado debe poder pulsarse sobre la cama, no sobre un sprite fantasma. */
    public Optional<String> pick(double worldX, double worldY) {
        for (int i = resting.size() - 1; i >= 0; i--) {
            Resting entry = resting.get(i);
            double x = entry.slot().col() * Renderer2D.CELL_W;
            double y = entry.slot().row() * Renderer2D.CELL_H;
            if (entry.zone() == ZoneType.OPERATING_ROOM) {
                if (worldX >= x + SURGERY_X - 8 && worldX <= x + SURGERY_X + 73
                        && worldY >= y + SURGERY_Y + 5 && worldY <= y + SURGERY_Y + 30) {
                    return Optional.of(entry.patient().code());
                }
            } else if (worldX >= x + BED_X - 4 && worldX <= x + BED_X + BED_WIDTH + 5
                    && worldY >= y + BED_Y - 3 && worldY <= y + BED_Y + 43) {
                return Optional.of(entry.patient().code());
            }
        }
        return Optional.empty();
    }

    /** Anillo de selección que sigue la figura acostada, no el antiguo centro del paciente de pie. */
    public boolean paintSelection(Graphics2D graphics, String code) {
        if (code == null) {
            return false;
        }
        for (Resting entry : resting) {
            if (!code.equals(entry.patient().code())) {
                continue;
            }
            double x = entry.slot().col() * Renderer2D.CELL_W;
            double y = entry.slot().row() * Renderer2D.CELL_H;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setStroke(new BasicStroke(2.2f));
                g.setColor(Palette.SELECTION);
                if (entry.zone() == ZoneType.OPERATING_ROOM) {
                    g.draw(new RoundRectangle2D.Double(x + SURGERY_X - 8, y + SURGERY_Y + 2,
                            80, 30, 15, 15));
                } else {
                    g.draw(new RoundRectangle2D.Double(x + BED_X - 7, y + BED_Y - 4,
                            BED_WIDTH + 16, 48, 11, 11));
                }
            } finally {
                g.dispose();
            }
            return true;
        }
        return false;
    }

    /**
     * Primero dibuja la figura sobre la superficie del colchón y luego su sábana (no al revés).
     * Durante 0,44 segundos la figura anterior se funde con la nueva sin añadir tiempo simulado.
     */
    public void paint(Graphics2D surface) {
        long now = clock.getAsLong();
        for (Resting entry : resting) {
            Arrival arrival = arrivals.get(entry.patient().code());
            double t = arrival == null ? 1.0 : Math.min(1.0,
                    Math.max(0.0, (now - arrival.since()) / (double) LIE_DOWN_NANOS));
            double eased = t * t * (3.0 - 2.0 * t);
            Graphics2D g = (Graphics2D) surface.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if (eased > 0) {
                    Graphics2D lying = (Graphics2D) g.create();
                    try {
                        lying.setComposite(AlphaComposite.SrcOver.derive((float) eased));
                        if (entry.zone() == ZoneType.OPERATING_ROOM) {
                            paintSurgeryPatient(lying, entry);
                        } else {
                            paintBedPatient(lying, entry);
                        }
                    } finally {
                        lying.dispose();
                    }
                }
                if (eased < 1) {
                    Graphics2D arriving = (Graphics2D) g.create();
                    try {
                        arriving.setComposite(AlphaComposite.SrcOver.derive((float) (1.0 - eased)));
                        walkingPainter.paint(arriving, List.of(entry.patient()));
                    } finally {
                        arriving.dispose();
                    }
                }
                paintVitals(g, entry);
            } finally {
                g.dispose();
            }
        }
    }

    /** Detecta únicamente la coincidencia exacta de marcador quieto y centro del recurso. */
    public static List<Resting> locate(HospitalLayout layout, List<PatientMarker> markers) {
        if (layout == null || markers == null) {
            throw new IllegalArgumentException("El plano y los pacientes son obligatorios.");
        }
        Map<Position, ZoneType> beds = new HashMap<>();
        for (String code : layout.getResourceCodes()) {
            Position slot = layout.positionOf(code).orElseThrow();
            ZoneType zone = layout.areaAt(slot).orElseThrow().zone();
            if (zone == ZoneType.HOSPITALIZATION || zone == ZoneType.ICU
                    || zone == ZoneType.OPERATING_ROOM) {
                beds.put(slot, zone);
            }
        }
        List<Resting> result = new ArrayList<>();
        Set<Position> claimed = new HashSet<>();
        for (PatientMarker marker : markers) {
            if (marker.moving()) {
                continue;
            }
            int col = (int) Math.floor(marker.x() / Renderer2D.CELL_W);
            int row = (int) Math.floor(marker.y() / Renderer2D.CELL_H);
            if (col < 0 || row < 0) {
                continue;
            }
            Position slot = new Position(col, row);
            ZoneType zone = beds.get(slot);
            if (zone == null || !claimed.add(slot)) {
                continue;
            }
            double centerX = slot.col() * Renderer2D.CELL_W + Renderer2D.CELL_W / 2.0;
            double centerY = slot.row() * Renderer2D.CELL_H + Renderer2D.CELL_H / 2.0;
            if (Math.abs(marker.x() - centerX) <= ARRIVAL_TOLERANCE
                    && Math.abs(marker.y() - centerY) <= ARRIVAL_TOLERANCE) {
                result.add(new Resting(marker, slot, zone));
            } else {
                claimed.remove(slot);
            }
        }
        return List.copyOf(result);
    }

    private static Color mixed(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) Math.round(a.getRed() * (1 - t) + b.getRed() * t),
                (int) Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                (int) Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    /** Cuerpo visto desde arriba, con la cabeza cerca de la almohada y ropa de cama tridimensional. */
    private static void paintBedPatient(Graphics2D g, Resting entry) {
        double x = entry.slot().col() * Renderer2D.CELL_W + BED_X;
        double y = entry.slot().row() * Renderer2D.CELL_H + BED_Y;
        PatientMarker p = entry.patient();
        Color sheet = p.triage() == null ? Palette.UNCLASSIFIED : Palette.triage(p.triage());
        if (p.deceased()) {
            sheet = mixed(sheet, new Color(0xAAB9C7), 0.65);
        }
        double center = x + BED_WIDTH / 2.0;
        double scale = p.minor() ? 0.82 : 1.0;

        // Sombra muy leve sobre la almohada: el paciente está recostado, no flotando.
        g.setColor(new Color(14, 44, 66, 36));
        g.fill(new Ellipse2D.Double(center - 8 * scale, y + 9, 16 * scale, 12 * scale));
        g.setColor(p.deceased() ? Palette.DECEASED : mixed(Palette.PATIENT_HEAD, Color.WHITE, 0.04));
        g.fill(new Ellipse2D.Double(center - 6.5 * scale, y + 8.2, 13 * scale, 12.8 * scale));
        g.setColor(new Color(0x8D6053));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Ellipse2D.Double(center - 6.5 * scale, y + 8.2, 13 * scale, 12.8 * scale));
        g.setColor(p.deceased() ? new Color(0x82878B) : new Color(0x3B3944));
        g.fillArc((int) (center - 6.5 * scale), (int) (y + 8), (int) (13 * scale),
                (int) (10 * scale), 0, 180);
        if (!p.deceased()) {
            g.setColor(new Color(0x664535));
            g.fillOval((int)(center - 3.5 * scale), (int)(y + 15), 2, 1);
            g.fillOval((int)(center + 2.5 * scale), (int)(y + 15), 2, 1);
        }
        // Pecho y brazos: solo la cabeza/hombros quedan por fuera de la sábana.
        g.setColor(p.deceased() ? new Color(0x9EADB4) : new Color(0xF5FBFC));
        g.fillRoundRect((int)(center - 10 * scale), (int)(y + 20), (int)(20 * scale), 12, 7, 7);
        g.setColor(p.deceased() ? Palette.DECEASED : Palette.PATIENT_HEAD);
        g.fillRoundRect((int)(center - 12 * scale), (int)(y + 21), 4, 9, 3, 3);
        g.fillRoundRect((int)(center + 8 * scale), (int)(y + 21), 4, 9, 3, 3);

        // Cobertor color triage: borde bajo, pliegue superior blanco y dos luces longitudinales.
        g.setColor(new Color(10, 45, 70, 52));
        g.fillRoundRect((int)x + 6, (int)y + 25, BED_WIDTH - 11, 12, 5, 5);
        g.setPaint(new GradientPaint((float)x + 6, (float)y + 24,
                mixed(sheet, Color.WHITE, 0.44), (float)x + BED_WIDTH - 6, (float)y + 37,
                mixed(sheet, Color.BLACK, 0.21)));
        g.fillRoundRect((int)x + 5, (int)y + 23, BED_WIDTH - 10, 12, 5, 5);
        g.setColor(mixed(sheet, Color.WHITE, 0.70));
        g.fillRoundRect((int)x + 5, (int)y + 22, BED_WIDTH - 10, 4, 3, 3);
        g.setColor(mixed(sheet, Color.BLACK, 0.28));
        g.drawLine((int)x + 8, (int)y + 31, (int)x + BED_WIDTH - 8, (int)y + 31);
        g.setColor(new Color(255, 255, 255, 98));
        g.drawLine((int)x + 11, (int)y + 26, (int)x + 11, (int)y + 29);
        g.drawLine((int)x + BED_WIDTH - 12, (int)y + 26, (int)x + BED_WIDTH - 12, (int)y + 29);
    }

    /** La figura de cirugía respeta el eje horizontal de la mesa y deja libre el espacio de la lámpara. */
    private static void paintSurgeryPatient(Graphics2D g, Resting entry) {
        double sx = entry.slot().col() * Renderer2D.CELL_W + SURGERY_X;
        double sy = entry.slot().row() * Renderer2D.CELL_H + SURGERY_Y;
        PatientMarker p = entry.patient();
        double scale = p.minor() ? .85 : 1.0;
        Color skin = p.deceased() ? Palette.DECEASED : Palette.PATIENT_HEAD;
        double midY = sy + 12.3;

        // Relleno discreto bajo el cuerpo: ninguna camilla ni sombra de persona de pie.
        g.setColor(new Color(12, 69, 76, 39));
        g.fill(new RoundRectangle2D.Double(sx + 4, midY - 3, 57, 9, 7, 7));
        g.setColor(new Color(0xF3FFFF));
        g.fill(new RoundRectangle2D.Double(sx + 7, midY - 5, 15, 10, 6, 6));
        g.setColor(skin);
        g.fill(new Ellipse2D.Double(sx + 1, midY - 4.7 * scale, 12.5 * scale, 9.4 * scale));
        g.setColor(p.deceased() ? new Color(0x747C84) : new Color(0x433D47));
        g.fillArc((int)(sx + 1), (int)(midY - 4.7 * scale), (int)(12.5 * scale),
                (int)(9 * scale), 90, 180);
        g.setColor(new Color(0xE7FCF7));
        g.fillRoundRect((int)sx + 16, (int)midY - 5, 24, 10, 6, 6);
        g.setColor(new Color(0x75BDB7));
        g.drawRoundRect((int)sx + 16, (int)midY - 5, 24, 10, 6, 6);
        g.setColor(skin);
        g.fillRoundRect((int)sx + 22, (int)midY - 7, 17, 3, 2, 2);
        g.fillRoundRect((int)sx + 22, (int)midY + 4, 17, 3, 2, 2);
        // La sábana quirúrgica usa el MISMO color de triage que las camas de
        // hospitalización y UCI. Ocupa una parte visible de torso y piernas, sin
        // cubrir la cabeza ni los brazos: así el paciente sigue viéndose acostado.
        Color drape = p.triage() == null ? Palette.UNCLASSIFIED : Palette.triage(p.triage());
        if (p.deceased()) {
            drape = mixed(drape, new Color(0xAAB9C7), 0.65);
        }
        g.setColor(new Color(12, 47, 58, 46));
        g.fill(new RoundRectangle2D.Double(sx + 28, midY - 4.9, 37, 11.5, 4, 4));
        g.setPaint(new GradientPaint((float) sx + 28, (float) midY - 5.8f,
                mixed(drape, Color.WHITE, 0.22), (float) sx + 65, (float) midY + 5.8f,
                mixed(drape, Color.BLACK, 0.15)));
        g.fill(new RoundRectangle2D.Double(sx + 28, midY - 5.8, 37, 11.6, 4, 4));
        g.setColor(mixed(drape, Color.WHITE, 0.55));
        g.drawLine((int) sx + 30, (int) midY - 4, (int) sx + 61, (int) midY - 4);
        g.setColor(mixed(drape, Color.BLACK, 0.28));
        g.drawLine((int) sx + 33, (int) midY + 4, (int) sx + 61, (int) midY + 4);
    }

    /** Conserva las lecturas existentes de salud y progreso, reubicadas junto a cada cama. */
    private static void paintVitals(Graphics2D g, Resting entry) {
        PatientMarker p = entry.patient();
        if (p.deceased()) {
            return;
        }
        int x = entry.slot().col() * Renderer2D.CELL_W;
        int y = entry.slot().row() * Renderer2D.CELL_H;
        int barX = x + (entry.zone() == ZoneType.OPERATING_ROOM ? 2 : 5);
        int healthY = y + 40;
        int barW = 19;
        paintTinyBar(g, barX, healthY, barW, p.health() / Patient.MAX_HEALTH,
                Palette.health(p.health()));
        if (p.inTreatment()) {
            paintTinyBar(g, barX, healthY - 6, barW, p.progress(), Palette.TREATMENT_PROGRESS);
        }
    }

    private static void paintTinyBar(Graphics2D g, int x, int y, int w, double fill, Color color) {
        g.setColor(new Color(28, 54, 68, 178));
        g.fillRoundRect(x - 1, y - 1, w + 2, 5, 2, 2);
        g.setColor(new Color(0xD5E3E9));
        g.fillRect(x, y, w, 3);
        g.setColor(color);
        g.fillRect(x, y, (int)Math.round(w * Math.max(0, Math.min(1, fill))), 3);
    }
}
