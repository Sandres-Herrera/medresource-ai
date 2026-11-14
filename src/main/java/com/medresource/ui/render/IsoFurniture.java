package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.BloodType;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.Sex;
import com.medresource.model.StaffSeat;
import com.medresource.model.ZoneType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Los muebles de la vista 2.5D, uno por cada lugar del plano: camas, camas de UCI con su monitor, mesas de
 * operaciones con su lámpara, sillas de la sala de espera, puestos de triage con su enfermera, neveras del banco de
 * sangre, mesas de la morgue, escritorios de los consultorios y mesas de la sala del personal.
 *
 * <p>Cada mueble cabe dentro de su casilla de un metro y deja libre el lado izquierdo, donde se para el personal que
 * atiende al paciente (a la izquierda del centro de la casilla, como en la vista 2D). Las medidas de las camas, de la
 * mesa de operaciones y de la mesa de la morgue son públicas porque {@link IsoFigures} acuesta a los pacientes
 * encima.</p>
 */
public final class IsoFurniture {

    /** Borde izquierdo de una cama dentro de su casilla, en metros. */
    public static final double BED_LEFT = 0.5;
    /** Borde derecho de una cama dentro de su casilla. */
    public static final double BED_RIGHT = 0.94;
    /** Borde de la cabecera de una cama (al fondo) dentro de su casilla. */
    public static final double BED_TOP = 0.1;
    /** Borde de los pies de una cama dentro de su casilla. */
    public static final double BED_BOTTOM = 0.9;
    /** Altura del colchón de una cama, en metros. */
    public static final double MATTRESS_TOP = 0.38;
    /** Altura de la superficie de la mesa de operaciones. */
    public static final double OPERATING_TOP = 0.49;
    /** Altura de la superficie de la mesa de la morgue. */
    public static final double SLAB_TOP = 0.42;
    /** Altura del asiento de las sillas. */
    public static final double SEAT_TOP = 0.24;
    /** Lado de las sillas, en metros. */
    public static final double CHAIR_SIZE = 0.4;

    private final HospitalLayout layout;
    private final IsoProjection projection;
    private final SceneRenderer flat;

    /**
     * Prepara los muebles de un plano.
     *
     * @param layout     plano del hospital
     * @param projection proyección de la vista
     * @param flat       renderizador 2D, que da el punto de cada puesto de la sala del personal
     * @throws IllegalArgumentException si falta alguno
     */
    public IsoFurniture(HospitalLayout layout, IsoProjection projection, SceneRenderer flat) {
        if (layout == null || projection == null || flat == null) {
            throw new IllegalArgumentException("El plano, la proyección y el renderizador 2D son obligatorios.");
        }
        this.layout = layout;
        this.projection = projection;
        this.flat = flat;
    }

    /**
     * Arma un elemento por mueble, para dibujarlos en su turno junto con los muros y las personas.
     *
     * @return los muebles del plano
     */
    public List<IsoItem> items() {
        List<IsoItem> items = new ArrayList<>();
        for (String code : new TreeSet<>(layout.getResourceCodes())) {
            Position spot = layout.positionOf(code).orElseThrow();
            ZoneType zone = layout.areaAt(spot).map(Area::zone).orElse(ZoneType.HOSPITALIZATION);
            switch (zone) {
                case ICU -> {
                    add(items, spot, graphics -> bed(graphics, spot));
                    add(items, spot.col() + 0.25, spot.row() + 0.22, graphics -> monitor(graphics, spot));
                }
                case OPERATING_ROOM -> {
                    add(items, spot, graphics -> operatingTable(graphics, spot));
                    add(items, spot.col() + 0.2, spot.row() + 0.2, graphics -> anesthesia(graphics, spot));
                    items.add(new IsoItem(spot.col() + spot.row() + 1.2, IsoItem.PERSON + 1,
                            graphics -> lamp(graphics, spot)));
                }
                default -> add(items, spot, graphics -> bed(graphics, spot));
            }
        }
        for (Position seat : layout.getWaitingSpots()) {
            add(items, seat, graphics -> waitingChair(graphics, seat));
        }
        for (Position station : layout.getTriageSpots()) {
            add(items, station.col() + 0.5, station.row() + 0.06, graphics -> triageNurse(graphics, station));
            add(items, station.col() + 0.5, station.row() + 0.21, graphics -> triageDesk(graphics, station));
        }
        List<Position> blood = layout.getBloodSpots();
        BloodType[] types = BloodType.values();
        for (int index = 0; index < blood.size(); index++) {
            Position spot = blood.get(index);
            String label = index < types.length ? types[index].getLabel() : "";
            add(items, spot, graphics -> fridge(graphics, spot, label));
        }
        for (Position spot : layout.getMorgueSpots()) {
            add(items, spot, graphics -> slab(graphics, spot));
        }
        TreeMap<String, Position> desks = new TreeMap<>();
        for (String code : layout.getStaffCodes()) {
            layout.consultDeskOf(code).ifPresent(desk -> desks.put(code, desk));
        }
        desks.values().forEach(desk -> add(items, desk, graphics -> consultDesk(graphics, desk)));
        addStaffTables(items);
        return items;
    }

    private void add(List<IsoItem> items, Position spot, Consumer<Graphics2D> painter) {
        add(items, spot.col() + 0.5, spot.row() + 0.5, painter);
    }

    private void add(List<IsoItem> items, double gx, double gy, Consumer<Graphics2D> painter) {
        items.add(new IsoItem(gx + gy, IsoItem.FURNITURE, painter));
    }

    /** Mesas de la sala del personal con una silla en cada puesto que tiene dueño. */
    private void addStaffTables(List<IsoItem> items) {
        Set<Position> tables = new TreeSet<>(Comparator.comparingInt(Position::row).thenComparingInt(Position::col));
        List<StaffSeat> seats = new ArrayList<>();
        for (String code : layout.getStaffCodes()) {
            StaffSeat seat = layout.staffSeatOf(code).orElseThrow();
            tables.add(seat.table());
            seats.add(seat);
        }
        for (Position table : tables) {
            add(items, table, graphics -> IsoBrush.box(graphics, projection, table.col() + 0.36, table.row() + 0.28,
                    0, 0.28, 0.44, 0.4, IsoPalette.DESK_TOP, IsoPalette.DESK,
                    IsoPalette.darker(IsoPalette.DESK, 0.76)));
        }
        for (StaffSeat seat : seats) {
            Point2D point = flat.seatOf(seat);
            double gx = projection.gridX(point.getX());
            double gy = projection.gridY(point.getY());
            boolean backOnLeft = seat.side() == 0;
            add(items, gx, gy, graphics -> staffChair(graphics, gx, gy, backOnLeft));
        }
    }

    // ---------- muebles ----------

    private void bed(Graphics2D graphics, Position spot) {
        double x = spot.col() + BED_LEFT;
        double y = spot.row() + BED_TOP;
        double w = BED_RIGHT - BED_LEFT;
        double d = BED_BOTTOM - BED_TOP;
        IsoBrush.box(graphics, projection, x, y, 0, w, 0.06, 0.6, IsoPalette.darker(IsoPalette.BED_FRAME, 0.8));
        IsoBrush.box(graphics, projection, x, y + 0.06, 0, w, d - 0.06, 0.3, IsoPalette.BED_FRAME);
        IsoBrush.box(graphics, projection, x + 0.03, y + 0.08, 0.3, w - 0.06, d - 0.12, 0.08, IsoPalette.MATTRESS);
        IsoBrush.box(graphics, projection, x + 0.07, y + 0.11, MATTRESS_TOP, w - 0.14, 0.2, 0.06, IsoPalette.PILLOW);
        IsoBrush.box(graphics, projection, x + 0.01, y + d - 0.05, 0, w - 0.02, 0.05, 0.42,
                IsoPalette.darker(IsoPalette.BED_FRAME, 0.85));
    }

    private void monitor(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.25;
        double y = spot.row() + 0.22;
        IsoBrush.box(graphics, projection, x - 0.09, y - 0.09, 0, 0.18, 0.18, 0.03, IsoPalette.METAL);
        IsoBrush.box(graphics, projection, x - 0.02, y - 0.02, 0.03, 0.04, 0.04, 0.62, IsoPalette.METAL);
        screen(graphics, x, y, 0.66);
    }

    /** Pantalla con el trazo verde de los latidos, mirando hacia quien mira. */
    private void screen(Graphics2D graphics, double x, double y, double z) {
        IsoBrush.box(graphics, projection, x - 0.15, y - 0.05, z, 0.3, 0.1, 0.22, IsoPalette.DEVICE);
        double face = y + 0.051;
        graphics.setColor(new Color(0x06, 0x23, 0x1C));
        graphics.fill(IsoBrush.polygon(projection, new double[][] {{x - 0.13, face, z + 0.03},
                {x + 0.13, face, z + 0.03}, {x + 0.13, face, z + 0.19}, {x - 0.13, face, z + 0.19}}));
        Path2D.Double beat = new Path2D.Double();
        double[][] points = {{-0.11, 0.11}, {-0.04, 0.11}, {-0.02, 0.17}, {0.01, 0.05}, {0.04, 0.11}, {0.11, 0.11}};
        for (int index = 0; index < points.length; index++) {
            Point2D at = projection.at(x + points[index][0], face, z + points[index][1]);
            if (index == 0) {
                beat.moveTo(at.getX(), at.getY());
            } else {
                beat.lineTo(at.getX(), at.getY());
            }
        }
        graphics.setStroke(new BasicStroke(1f));
        graphics.setColor(IsoPalette.MONITOR_LINE);
        graphics.draw(beat);
    }

    private void operatingTable(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.52;
        double y = spot.row() + 0.12;
        IsoBrush.box(graphics, projection, x + 0.12, y + 0.3, 0, 0.16, 0.16, 0.41, IsoPalette.METAL);
        IsoBrush.box(graphics, projection, x, y, 0.41, 0.4, 0.76, 0.05, IsoPalette.STEEL);
        IsoBrush.box(graphics, projection, x + 0.02, y + 0.02, 0.46, 0.36, 0.72, 0.03, IsoPalette.SURGICAL_PAD);
    }

    private void anesthesia(Graphics2D graphics, Position spot) {
        IsoBrush.box(graphics, projection, spot.col() + 0.08, spot.row() + 0.08, 0, 0.24, 0.2, 0.55, IsoPalette.DEVICE);
        screen(graphics, spot.col() + 0.2, spot.row() + 0.18, 0.55);
    }

    /** Lámpara colgada sobre la mesa, con un halo de luz en el piso. */
    private void lamp(Graphics2D graphics, Position spot) {
        double gx = spot.col() + 0.72;
        double gy = spot.row() + 0.5;
        Point2D floor = projection.at(gx, gy, 0.5);
        Point2D hang = projection.at(gx, gy, 1.45);
        Point2D center = projection.at(gx, gy, 1.15);
        double rx = IsoBrush.screenRadius(0.3);
        double ry = rx / 2;
        graphics.setColor(new Color(0xFF, 0xF7, 0xD6, 26));
        graphics.fill(new Ellipse2D.Double(floor.getX() - rx * 1.3, floor.getY() - ry * 1.3, rx * 2.6, ry * 2.6));
        graphics.setStroke(new BasicStroke(1.8f));
        graphics.setColor(IsoPalette.METAL);
        graphics.draw(new Line2D.Double(hang, center));
        graphics.setColor(IsoPalette.darker(IsoPalette.METAL, 0.6));
        graphics.fill(new Ellipse2D.Double(center.getX() - rx, center.getY() - ry + 2.5, 2 * rx, 2 * ry));
        graphics.setColor(IsoPalette.METAL);
        graphics.fill(new Ellipse2D.Double(center.getX() - rx, center.getY() - ry, 2 * rx, 2 * ry));
        graphics.setColor(IsoPalette.LAMP_LIGHT);
        graphics.fill(new Ellipse2D.Double(center.getX() - rx * 0.6, center.getY() - ry * 0.6 + 1.5, rx * 1.2,
                ry * 1.2));
    }

    private void waitingChair(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.5 - CHAIR_SIZE / 2;
        double y = spot.row() + 0.5 - CHAIR_SIZE / 2;
        IsoBrush.box(graphics, projection, x, y, 0, CHAIR_SIZE, CHAIR_SIZE, SEAT_TOP, IsoPalette.CHAIR_TOP,
                IsoPalette.CHAIR, IsoPalette.darker(IsoPalette.CHAIR, 0.76));
        IsoBrush.box(graphics, projection, x, y, SEAT_TOP, CHAIR_SIZE, 0.07, 0.26, IsoPalette.CHAIR_TOP,
                IsoPalette.CHAIR, IsoPalette.darker(IsoPalette.CHAIR, 0.76));
    }

    private void staffChair(Graphics2D graphics, double gx, double gy, boolean backOnLeft) {
        double size = 0.3;
        double x = gx - size / 2;
        double y = gy - size / 2;
        IsoBrush.box(graphics, projection, x, y, 0, size, size, SEAT_TOP, IsoPalette.CHAIR_TOP, IsoPalette.CHAIR,
                IsoPalette.darker(IsoPalette.CHAIR, 0.76));
        double backX = backOnLeft ? x : x + size - 0.06;
        IsoBrush.box(graphics, projection, backX, y, SEAT_TOP, 0.06, size, 0.24, IsoPalette.CHAIR_TOP,
                IsoPalette.CHAIR, IsoPalette.darker(IsoPalette.CHAIR, 0.76));
    }

    private void triageDesk(Graphics2D graphics, Position spot) {
        IsoBrush.box(graphics, projection, spot.col() + 0.18, spot.row() + 0.14, 0, 0.64, 0.16, 0.42,
                IsoPalette.lighter(IsoPalette.accent(ZoneType.TRIAGE), 0.15), IsoPalette.DESK,
                IsoPalette.darker(IsoPalette.DESK, 0.76));
        IsoBrush.box(graphics, projection, spot.col() + 0.4, spot.row() + 0.16, 0.42, 0.2, 0.04, 0.14,
                IsoPalette.DEVICE);
    }

    /** Enfermera de triage de pie detrás de su puesto: no es personal del motor, siempre está ahí. */
    private void triageNurse(Graphics2D graphics, Position spot) {
        IsoBrush.shadow(graphics, projection, spot.col() + 0.5, spot.row() + 0.06, 0.18);
        IsoBrush.person(graphics, projection, spot.col() + 0.5, spot.row() + 0.06, 0, 0,
                new IsoBrush.Person(Palette.TRIAGE_NURSE, IsoPalette.cap(Palette.TRIAGE_NURSE), Sex.FEMALE, 1.0,
                        false));
    }

    /** Nevera con puerta de vidrio y bolsas de sangre a la vista, y su tipo de sangre encima. */
    private void fridge(Graphics2D graphics, Position spot, String label) {
        double x = spot.col() + 0.2;
        double y = spot.row() + 0.22;
        double w = 0.6;
        double d = 0.56;
        double h = 0.9;
        IsoBrush.box(graphics, projection, x, y, 0, w, d, h, IsoPalette.FRIDGE);
        double face = y + d + 0.004;
        graphics.setColor(IsoPalette.FRIDGE_GLASS);
        graphics.fill(IsoBrush.polygon(projection, new double[][] {{x + 0.05, face, 0.06}, {x + w - 0.05, face, 0.06},
                {x + w - 0.05, face, h - 0.06}, {x + 0.05, face, h - 0.06}}));
        graphics.setColor(IsoPalette.BLOOD_BAG);
        for (int shelf = 0; shelf < 3; shelf++) {
            double z = 0.12 + shelf * 0.24;
            for (int bag = 0; bag < 3; bag++) {
                double left = x + 0.09 + bag * 0.155;
                graphics.fill(IsoBrush.polygon(projection, new double[][] {{left, face, z}, {left + 0.12, face, z},
                        {left + 0.12, face, z + 0.15}, {left, face, z + 0.15}}));
            }
        }
        Point2D top = projection.at(x + w / 2, y + d / 2, h);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(IsoPalette.BLOOD_BAG);
        graphics.drawString(label, (float) (top.getX() - metrics.stringWidth(label) / 2.0),
                (float) (top.getY() + metrics.getAscent() / 2.0 - 1));
    }

    private void slab(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.22;
        double y = spot.row() + 0.14;
        IsoBrush.box(graphics, projection, x + 0.18, y + 0.25, 0, 0.2, 0.22, 0.36, IsoPalette.METAL);
        IsoBrush.box(graphics, projection, x, y, 0.36, 0.56, 0.72, 0.06, IsoPalette.STEEL);
    }

    private void consultDesk(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.6;
        double y = spot.row() + 0.2;
        IsoBrush.box(graphics, projection, x, y, 0, 0.34, 0.56, 0.4, IsoPalette.DESK_TOP, IsoPalette.DESK,
                IsoPalette.darker(IsoPalette.DESK, 0.76));
        IsoBrush.box(graphics, projection, x + 0.15, y + 0.12, 0.4, 0.04, 0.3, 0.2, IsoPalette.DEVICE);
    }

    /**
     * Dice qué mueble hay en una casilla, para saber si un paciente que está quieto en su centro va acostado o
     * sentado.
     *
     * @param spot casilla
     * @return el tipo de mueble, o vacío si no hay ninguno donde acostarse o sentarse
     */
    public Optional<Kind> kindAt(Position spot) {
        Optional<String> resource = layout.resourceAt(spot);
        if (resource.isPresent() && layout.positionOf(resource.get()).filter(spot::equals).isPresent()) {
            ZoneType zone = layout.areaAt(spot).map(Area::zone).orElse(ZoneType.HOSPITALIZATION);
            if (zone == ZoneType.OPERATING_ROOM) {
                return Optional.of(Kind.OPERATING_TABLE);
            }
            if (zone == ZoneType.HOSPITALIZATION || zone == ZoneType.ICU) {
                return Optional.of(Kind.BED);
            }
        }
        if (layout.getMorgueSpots().contains(spot)) {
            return Optional.of(Kind.SLAB);
        }
        if (layout.getWaitingSpots().contains(spot)) {
            return Optional.of(Kind.CHAIR);
        }
        return Optional.empty();
    }

    /** Muebles donde un paciente se acuesta o se sienta. */
    public enum Kind {
        /** Cama de hospitalización o de UCI. */
        BED,
        /** Mesa de operaciones. */
        OPERATING_TABLE,
        /** Mesa de la morgue. */
        SLAB,
        /** Silla de la sala de espera. */
        CHAIR
    }
}
