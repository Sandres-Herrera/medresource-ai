package com.medresource.ui.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Iconos de línea de la pantalla de inicio, dibujados con trazos para que se vean nítidos a cualquier tamaño.
 *
 * <p>Cada icono ocupa un cuadro de {@code size} × {@code size} píxeles y se traza con un grosor proporcional a
 * su tamaño, con puntas y uniones redondeadas, en el estilo de los iconos de línea modernos.</p>
 *
 * <p>Los usan la pantalla de inicio y el formulario de una simulación nueva.</p>
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
    CHEVRON,
    /** Edificio con una cruz: el hospital. */
    HOSPITAL,
    /** Corazón: el escenario normal. */
    HEART,
    /** Dos personas: el escenario de alta demanda. */
    USERS,
    /** Ramas que se separan: la estrategia que decide a quién atender. */
    BRANCH,
    /** Estetoscopio: la estrategia de prioridad clínica. */
    STETHOSCOPE,
    /** Lista numerada: la estrategia de orden de llegada. */
    LIST,
    /** Dado: sortear una semilla o la estrategia aleatoria. */
    DICE,
    /** Equis: cerrar. */
    CLOSE,
    /** Círculo con una «i»: información. */
    INFO,
    /** Triángulo hacia la derecha: empezar. */
    PLAY,
    /** Signo menos: bajar una cantidad. */
    MINUS,
    /** Reloj: la velocidad de la simulación. */
    CLOCK;

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
            case HOSPITAL -> new Shape[]{polyline(3, 21, 3, 7, 12, 3, 21, 7, 21, 21), new Line2D.Double(2, 21, 22, 21),
                new Line2D.Double(12, 9, 12, 15), new Line2D.Double(9, 12, 15, 12),
                polyline(10, 21, 10, 18, 14, 18, 14, 21)};
            case HEART -> new Shape[]{heart()};
            case USERS -> new Shape[]{new Ellipse2D.Double(5.5, 4, 7, 7), new Arc2D.Double(3, 13, 12, 14, 0, 180,
                Arc2D.OPEN), new Arc2D.Double(15, 4, 6, 7, -90, 180, Arc2D.OPEN),
                new Arc2D.Double(15, 13, 6, 14, 0, 90, Arc2D.OPEN)};
            case BRANCH -> new Shape[]{new Line2D.Double(6, 3, 6, 15), new Ellipse2D.Double(3, 15, 6, 6),
                new Ellipse2D.Double(15, 3, 6, 6), polyline(18, 9, 18, 11, 6, 15)};
            case STETHOSCOPE -> new Shape[]{polyline(5, 3, 5, 9), polyline(13, 3, 13, 9),
                new Arc2D.Double(5, 5, 8, 8, 180, 180, Arc2D.OPEN), polyline(9, 13, 9, 16),
                new Arc2D.Double(9, 10, 9, 10, 180, 180, Arc2D.OPEN), new Ellipse2D.Double(16, 9, 4, 4)};
            case LIST -> new Shape[]{new Line2D.Double(10, 6, 21, 6), new Line2D.Double(10, 12, 21, 12),
                new Line2D.Double(10, 18, 21, 18), polyline(4, 5, 5, 4, 5, 8), polyline(4, 16, 6, 16, 4, 20, 6, 20)};
            case DICE -> new Shape[]{new RoundRectangle2D.Double(3, 3, 18, 18, 5, 5), dot(8, 8), dot(16, 16),
                dot(12, 12)};
            case CLOSE -> new Shape[]{new Line2D.Double(6, 6, 18, 18), new Line2D.Double(18, 6, 6, 18)};
            case INFO -> new Shape[]{new Ellipse2D.Double(3, 3, 18, 18), new Line2D.Double(12, 11, 12, 16),
                new Line2D.Double(12, 8, 12, 8.01)};
            case PLAY -> new Shape[]{polyline(7, 4, 19, 12, 7, 20, 7, 4)};
            case MINUS -> new Shape[]{new Line2D.Double(6, 12, 18, 12)};
            case CLOCK -> new Shape[]{new Ellipse2D.Double(3, 3, 18, 18), polyline(12, 7, 12, 12, 15.5, 14)};
        };
    }

    /** Corazón dibujado con dos curvas. */
    private static Path2D heart() {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(12, 20);
        path.curveTo(5, 15, 2.5, 11, 3.5, 7.5);
        path.curveTo(4.5, 4, 9.5, 3, 12, 7);
        path.curveTo(14.5, 3, 19.5, 4, 20.5, 7.5);
        path.curveTo(21.5, 11, 19, 15, 12, 20);
        return path;
    }

    /** Punto de un dado: un trazo de largo casi cero, que con las puntas redondas queda como un punto. */
    private static Line2D dot(double x, double y) {
        return new Line2D.Double(x, y, x, y + 0.01);
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
