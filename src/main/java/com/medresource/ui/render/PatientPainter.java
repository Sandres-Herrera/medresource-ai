package com.medresource.ui.render;

import com.medresource.model.Patient;
import com.medresource.model.Sex;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Dibuja a los pacientes sobre el plano.
 *
 * <p>Cada paciente es una figura pequeña: cabeza y cuerpo. El cuerpo tiene el color de su nivel de triage
 * (rojo para el I, azul para el V), es un vestido para las mujeres y un tronco para los hombres, se dibuja más
 * pequeño si es menor y en gris si falleció. La figura se coloca a la derecha del centro de la casilla para
 * no tapar el código de la cama.</p>
 *
 * <p>Debajo de los pies lleva una barra con su salud, del color que le da {@link Palette#health(double)}: llena
 * si está sano y vacía si se apaga. Mientras está en tratamiento lleva, además, una barra azul sobre la cabeza
 * que se llena a medida que el tratamiento avanza. Un fallecido no lleva barras.</p>
 *
 * <p>Quien va en camilla (un fallecido o alguien que no puede caminar) se dibuja acostado sobre ella, con la
 * cabeza a la izquierda; mientras la camilla se mueve, un camillero de uniforme gris azulado la empuja desde la
 * derecha.</p>
 *
 * <p>Quien está de pie lleva una sombra ovalada suave bajo los pies ({@link #paintShadow}), dibujada antes que
 * la figura, para que se vea parado en el piso; la sombra no sube ni baja con el movimiento de reposo. Quien va
 * acostado en camilla no lleva sombra, pero el camillero sí.</p>
 *
 * <p>La figura (y su camilla) sube o baja lo que diga el {@linkplain IdleMotion movimiento de reposo} del
 * marcador; las barras se quedan en su sitio para que se lean bien.</p>
 *
 * <p>Solo usa puntos del mundo, como los que da {@link SceneRenderer#centerOf}, así sirve igual para la vista
 * 2D y para la 2.5D.</p>
 */
public final class PatientPainter {

    /** Cuánto se corre la figura a la derecha del centro de la casilla, en píxeles. */
    public static final int OFFSET_X = 18;

    private static final double MINOR_SCALE = 0.75;
    private static final double HEAD_RADIUS = 5.0;
    private static final int BADGE_SIZE = 11;
    private static final Color UNKNOWN_TRIAGE = new Color(0x9E, 0x9E, 0x9E);
    /** Ancho de la barra de salud, en píxeles. */
    private static final int HEALTH_BAR_WIDTH = 18;
    /** Ancho de la barra de avance del tratamiento, en píxeles. */
    private static final int PROGRESS_BAR_WIDTH = 22;
    /** Alto de las dos barras, en píxeles. */
    private static final int BAR_HEIGHT = 3;
    /** Radio del anillo del paciente seleccionado, en píxeles del mundo. */
    public static final double SELECTION_RADIUS = 15.0;
    private static final float SELECTION_STROKE = 2.5f;
    /** Dónde empieza la barra de salud, debajo de los pies del paciente. */
    private static final int HEALTH_BAR_TOP = 14;
    /** Ancho de la sombra bajo los pies de un adulto, en píxeles. */
    private static final double SHADOW_WIDTH = 18.0;
    /** Alto de la sombra bajo los pies de un adulto, en píxeles. */
    private static final double SHADOW_HEIGHT = 3.0;
    /** Dónde queda el centro de la sombra, bajo el centro de la figura: a la altura de los pies. */
    private static final double SHADOW_CENTER = 10.5;
    /** Dónde empieza la barra de avance, sobre la cabeza del paciente. */
    private static final int PROGRESS_BAR_TOP = -21;

    /**
     * Dibuja los pacientes, en el orden de la lista (los últimos quedan encima).
     *
     * @param graphics superficie de dibujo, ya con la cámara aplicada
     * @param markers  pacientes que se dibujan
     * @throws IllegalArgumentException si algún argumento es nulo
     */
    public void paint(Graphics2D graphics, List<PatientMarker> markers) {
        if (graphics == null || markers == null) {
            throw new IllegalArgumentException("La superficie de dibujo y los pacientes son obligatorios.");
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setStroke(new BasicStroke(Palette.OUTLINE_WIDTH));
        for (PatientMarker marker : markers) {
            paintPatient(graphics, marker);
        }
    }

    /**
     * Dibuja la insignia «+N» con los pacientes que esperan y no caben en las sillas.
     *
     * @param graphics   superficie de dibujo, ya con la cámara aplicada
     * @param lastSeat   centro de la última silla de la sala de espera
     * @param count      cantidad de pacientes que no caben; si es 0 o menos no se dibuja nada
     * @throws IllegalArgumentException si la superficie o la silla son nulas
     */
    public void paintOverflow(Graphics2D graphics, Point2D lastSeat, int count) {
        if (graphics == null || lastSeat == null) {
            throw new IllegalArgumentException("La superficie de dibujo y la silla son obligatorias.");
        }
        if (count <= 0) {
            return;
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, BADGE_SIZE));
        String text = "+" + count;
        FontMetrics metrics = graphics.getFontMetrics();
        double width = metrics.stringWidth(text) + 10.0;
        double height = BADGE_SIZE + 6.0;
        double x = lastSeat.getX() + 14;
        double y = lastSeat.getY() - 20;
        graphics.setColor(Palette.BADGE);
        graphics.fill(new RoundRectangle2D.Double(x, y, width, height, height, height));
        graphics.setColor(Palette.CARD);
        graphics.drawString(text, (float) (x + 5), (float) (y + BADGE_SIZE + 1));
    }

    /**
     * Dibuja el anillo que marca al paciente seleccionado, alrededor de su figura.
     *
     * @param graphics superficie de dibujo, ya con la cámara aplicada
     * @param marker   paciente seleccionado, tal como se dibuja
     * @throws IllegalArgumentException si algún argumento es nulo
     */
    public void paintSelection(Graphics2D graphics, PatientMarker marker) {
        if (graphics == null || marker == null) {
            throw new IllegalArgumentException("La superficie de dibujo y el paciente son obligatorios.");
        }
        double cx = marker.x() + OFFSET_X;
        double cy = marker.y() + marker.bob();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setStroke(new BasicStroke(SELECTION_STROKE));
        graphics.setColor(Palette.SELECTION);
        graphics.draw(new Ellipse2D.Double(cx - SELECTION_RADIUS, cy - SELECTION_RADIUS, 2 * SELECTION_RADIUS,
                2 * SELECTION_RADIUS));
        graphics.setStroke(new BasicStroke(Palette.OUTLINE_WIDTH));
    }

    private void paintPatient(Graphics2D graphics, PatientMarker marker) {
        double scale = marker.minor() ? MINOR_SCALE : 1.0;
        double cx = marker.x() + OFFSET_X;
        double cy = marker.y() + marker.bob();
        Color body = marker.deceased() ? Palette.DECEASED
                : marker.triage() == null ? UNKNOWN_TRIAGE : Palette.triage(marker.triage());
        Color head = marker.deceased() ? Palette.DECEASED : Palette.PATIENT_HEAD;
        double radius = HEAD_RADIUS * scale;
        if (marker.onStretcher()) {
            paintStretcher(graphics, cx, cy);
            fillAndOutline(graphics, new RoundRectangle2D.Double(cx - 6, cy - 4, 18 * scale, 7, 4, 4), body);
            fillAndOutline(graphics, new Ellipse2D.Double(cx - 11 - radius, cy - 1 - radius, 2 * radius,
                    2 * radius), head);
            if (marker.isCarried()) {
                paintShadow(graphics, cx + 21, marker.y() - 1, 1.0);
                paintPorter(graphics, cx + 21, cy);
            }
        } else {
            paintShadow(graphics, cx, marker.y(), scale);
            fillAndOutline(graphics, bodyShape(marker.sex(), cx, cy, scale), body);
            fillAndOutline(graphics, new Ellipse2D.Double(cx - radius, cy - 9 * scale - radius, 2 * radius,
                    2 * radius), head);
        }
        if (marker.deceased()) {
            return;
        }
        paintBar(graphics, cx, marker.y() + HEALTH_BAR_TOP, HEALTH_BAR_WIDTH, marker.health() / Patient.MAX_HEALTH,
                Palette.health(marker.health()));
        if (marker.inTreatment()) {
            paintBar(graphics, cx, marker.y() + PROGRESS_BAR_TOP, PROGRESS_BAR_WIDTH, marker.progress(),
                    Palette.TREATMENT_PROGRESS);
        }
    }

    /**
     * Dibuja una barra horizontal centrada en {@code cx}: el fondo oscuro completo y, encima, la parte llena desde
     * la izquierda.
     */
    private void paintBar(Graphics2D graphics, double cx, double top, int width, double share, Color fill) {
        int left = (int) Math.round(cx - width / 2.0);
        int y = (int) Math.round(top);
        graphics.setColor(Palette.BAR_TRACK);
        graphics.fillRect(left, y, width, BAR_HEIGHT);
        int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, share)));
        if (filled > 0) {
            graphics.setColor(fill);
            graphics.fillRect(left, y, filled, BAR_HEIGHT);
        }
    }

    /**
     * Dibuja la sombra ovalada bajo los pies de una figura de pie, en el piso.
     *
     * @param graphics superficie de dibujo
     * @param cx       centro horizontal de la figura
     * @param floorY   altura del centro de la figura sin el movimiento de reposo
     * @param scale    tamaño de la figura (1 para un adulto)
     */
    static void paintShadow(Graphics2D graphics, double cx, double floorY, double scale) {
        double width = SHADOW_WIDTH * scale;
        double height = SHADOW_HEIGHT * scale;
        graphics.setColor(Palette.FIGURE_SHADOW);
        graphics.fill(new Ellipse2D.Double(cx - width / 2, floorY + SHADOW_CENTER * scale - height / 2, width,
                height));
    }

    /** Camilla vista desde un lado: el colchón y, debajo, dos ruedas. */
    private void paintStretcher(Graphics2D graphics, double cx, double cy) {
        fillAndOutline(graphics, new RoundRectangle2D.Double(cx - 15, cy + 2, 30, 6, 3, 3), Palette.STRETCHER);
        graphics.setColor(Palette.STRETCHER_FRAME);
        graphics.fill(new Ellipse2D.Double(cx - 13, cy + 8, 4, 4));
        graphics.fill(new Ellipse2D.Double(cx + 9, cy + 8, 4, 4));
    }

    /** Camillero que empuja la camilla desde la derecha: cabeza y uniforme gris azulado. */
    private void paintPorter(Graphics2D graphics, double cx, double cy) {
        fillAndOutline(graphics, new RoundRectangle2D.Double(cx - 4, cy - 4, 8, 14, 4, 4), Palette.PORTER_UNIFORM);
        fillAndOutline(graphics, new Ellipse2D.Double(cx - 4, cy - 14, 8, 8), Palette.PATIENT_HEAD);
    }

    /** Vestido (triángulo) para las mujeres y tronco (rectángulo redondeado) para los hombres. */
    private Shape bodyShape(Sex sex, double cx, double cy, double scale) {
        if (sex == Sex.FEMALE) {
            Path2D.Double dress = new Path2D.Double();
            dress.moveTo(cx, cy - 3 * scale);
            dress.lineTo(cx - 8 * scale, cy + 11 * scale);
            dress.lineTo(cx + 8 * scale, cy + 11 * scale);
            dress.closePath();
            return dress;
        }
        return new RoundRectangle2D.Double(cx - 5 * scale, cy - 3 * scale, 10 * scale, 14 * scale, 4, 4);
    }

    private void fillAndOutline(Graphics2D graphics, Shape shape, Color color) {
        graphics.setColor(color);
        graphics.fill(shape);
        graphics.setColor(Palette.PATIENT_OUTLINE);
        graphics.draw(shape);
    }
}