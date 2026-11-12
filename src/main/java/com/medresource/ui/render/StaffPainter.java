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
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Dibuja a los médicos y enfermeros sobre el plano.
 *
 * <p>Cada persona es una figura del mismo tamaño que la de un paciente, centrada en su punto. Mientras está
 * sentada en su puesto lleva su código debajo; junto a un paciente no, para no tapar el código de la cama. El
 * médico lleva bata blanca con una franja del color de su especialidad a la altura del pecho; el
 * enfermero, uniforme verde agua y gorro blanco. Igual que los pacientes, el cuerpo es un vestido para las mujeres
 * y un tronco para los hombres.</p>
 *
 * <p>La figura sube o baja lo que diga el {@linkplain IdleMotion movimiento de reposo} del marcador; el código
 * se queda en su sitio para que se lea bien.</p>
 *
 * <p>Solo usa puntos del mundo, así sirve igual para la vista 2D y para la 2.5D.</p>
 */
public final class StaffPainter {

    private static final double HEAD_RADIUS = 5.0;
    private static final int CODE_SIZE = 9;

    /**
     * Dibuja al personal, en el orden de la lista (los últimos quedan encima).
     *
     * @param graphics superficie de dibujo, ya con la cámara aplicada
     * @param markers  médicos y enfermeros que se dibujan
     * @throws IllegalArgumentException si algún argumento es nulo
     */
    public void paint(Graphics2D graphics, List<StaffMarker> markers) {
        if (graphics == null || markers == null) {
            throw new IllegalArgumentException("La superficie de dibujo y el personal son obligatorios.");
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setStroke(new BasicStroke(Palette.OUTLINE_WIDTH));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, CODE_SIZE));
        for (StaffMarker marker : markers) {
            paintMember(graphics, marker);
        }
    }

    private void paintMember(Graphics2D graphics, StaffMarker marker) {
        double cx = marker.x();
        double cy = marker.y() + marker.bob();
        Color body = marker.isDoctor() ? Palette.DOCTOR_COAT : Palette.NURSE_UNIFORM;
        if (!marker.seated()) {
            PatientPainter.paintShadow(graphics, cx, marker.y(), 1.0);
        }
        fillAndOutline(graphics, bodyShape(marker.sex(), cx, cy), body);
        if (marker.isDoctor()) {
            graphics.setColor(Palette.specialty(marker.specialty()));
            graphics.fill(new Rectangle2D.Double(cx - 4, cy, 8, 3));
        }
        fillAndOutline(graphics, new Ellipse2D.Double(cx - HEAD_RADIUS, cy - 9 - HEAD_RADIUS,
                2 * HEAD_RADIUS, 2 * HEAD_RADIUS), Palette.PATIENT_HEAD);
        if (!marker.isDoctor()) {
            fillAndOutline(graphics, new RoundRectangle2D.Double(cx - 4, cy - 16, 8, 3, 2, 2), Palette.CARD);
        }
        if (marker.seated()) {
            paintCode(graphics, marker.code(), cx, marker.y());
        }
    }

    /** Escribe el código debajo de la figura, centrado. */
    private void paintCode(Graphics2D graphics, String code, double cx, double cy) {
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(Palette.LABEL);
        graphics.drawString(code, (float) (cx - metrics.stringWidth(code) / 2.0), (float) (cy + 11 + CODE_SIZE));
    }

    /** Vestido (triángulo) para las mujeres y tronco (rectángulo redondeado) para los hombres. */
    private Shape bodyShape(Sex sex, double cx, double cy) {
        if (sex == Sex.FEMALE) {
            Path2D.Double dress = new Path2D.Double();
            dress.moveTo(cx, cy - 3);
            dress.lineTo(cx - 8, cy + 11);
            dress.lineTo(cx + 8, cy + 11);
            dress.closePath();
            return dress;
        }
        return new RoundRectangle2D.Double(cx - 5, cy - 3, 10, 14, 4, 4);
    }

    private void fillAndOutline(Graphics2D graphics, Shape shape, Color color) {
        graphics.setColor(color);
        graphics.fill(shape);
        graphics.setColor(Palette.PATIENT_OUTLINE);
        graphics.draw(shape);
    }
}
