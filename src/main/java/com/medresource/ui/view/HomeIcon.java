package com.medresource.ui.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

/**
 * Iconos de línea de la pantalla de inicio, dibujados con trazos para que se vean nítidos a cualquier tamaño.
 *
 * <p>Cada icono ocupa un cuadro de {@code size} × {@code size} píxeles y se traza con un grosor proporcional a
 * su tamaño, con puntas y uniones redondeadas, en el estilo de los iconos de línea modernos.</p>
 */
public enum HomeIcon {

    /** Línea de un electrocardiograma: el logo de la aplicación. */
    PULSE,
    /** Rayo: la simulación rápida. */
    BOLT,
    /** Triángulo de advertencia con un signo de exclamación: la demostración de catástrofe. */
    WARNING,
    /** Signo más: crear una simulación nueva. */
    PLUS,
    /** Flecha hacia la derecha: abrir una opción. */
    CHEVRON;

    /**
     * Dibuja el icono.
     *
     * @param graphics superficie de dibujo
     * @param x        borde izquierdo del cuadro del icono
     * @param y        borde superior del cuadro del icono
     * @param size     lado del cuadro, en píxeles; mayor que cero
     * @param color    color del trazo
     * @throws IllegalArgumentException si falta la superficie o el color, o si el tamaño no es positivo
     */
    public void paint(Graphics2D graphics, double x, double y, double size, Color color) {
        if (graphics == null || color == null) {
            throw new IllegalArgumentException("La superficie de dibujo y el color son obligatorios.");
        }
        if (!(size > 0)) {
            throw new IllegalArgumentException("El tamaño del icono debe ser positivo.");
        }
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        surface.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        surface.setColor(color);
        surface.translate(x, y);
        surface.scale(size / 24.0, size / 24.0);
        surface.setStroke(new BasicStroke((float) (Math.max(1.5, size / 11) * 24.0 / size), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        for (Shape stroke : strokes()) {
            surface.draw(stroke);
        }
        surface.dispose();
    }

    /** Trazos del icono en un cuadro de 24 × 24. */
    private Shape[] strokes() {
        return switch (this) {
            case PULSE -> new Shape[]{polyline(2, 12, 7, 12, 9.5, 7, 13, 17, 15.5, 10, 17, 12, 22, 12)};
            case BOLT -> new Shape[]{polyline(13, 2, 4, 14, 12, 14, 11, 22, 20, 10, 12, 10, 13, 2)};
            case WARNING -> new Shape[]{polyline(12, 3, 22, 20, 2, 20, 12, 3), new Line2D.Double(12, 9, 12, 13.5),
                new Line2D.Double(12, 17, 12, 17.01)};
            case PLUS -> new Shape[]{new Line2D.Double(12, 5, 12, 19), new Line2D.Double(5, 12, 19, 12)};
            case CHEVRON -> new Shape[]{polyline(9, 6, 15, 12, 9, 18)};
        };
    }

    private static Path2D polyline(double... points) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int index = 2; index < points.length; index += 2) {
            path.lineTo(points[index], points[index + 1]);
        }
        return path;
    }
}
