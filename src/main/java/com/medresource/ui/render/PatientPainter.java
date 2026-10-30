package com.medresource.ui.render;

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
        graphics.setStroke(new BasicStroke(1f));
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

    private void paintPatient(Graphics2D graphics, PatientMarker marker) {
        double scale = marker.minor() ? MINOR_SCALE : 1.0;
        double cx = marker.x() + OFFSET_X;
        double cy = marker.y();
        Color body = marker.deceased() ? Palette.DECEASED
                : marker.triage() == null ? UNKNOWN_TRIAGE : Palette.triage(marker.triage());
        Color head = marker.deceased() ? Palette.DECEASED : Palette.PATIENT_HEAD;
        fillAndOutline(graphics, bodyShape(marker.sex(), cx, cy, scale), body);
        double radius = HEAD_RADIUS * scale;
        fillAndOutline(graphics, new Ellipse2D.Double(cx - radius, cy - 9 * scale - radius, 2 * radius, 2 * radius),
                head);
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