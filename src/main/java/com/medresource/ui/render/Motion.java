package com.medresource.ui.render;

import java.awt.geom.Point2D;

/**
 * Movimiento suave de un paciente entre dos puntos del mundo.
 *
 * <p>Cuando el paciente recibe un destino nuevo, el recorrido arranca desde donde está en ese momento, dura
 * {@value #TRAVEL_SECONDS} segundos simulados y avanza con una curva suave (empieza y termina despacio). Como
 * la duración está en segundos simulados, a ×1 un recorrido dura {@value #TRAVEL_SECONDS} segundos reales y a
 * ×10, la décima parte. Es solo estado de la vista: el modelo no lo conoce y no cambia el resultado.</p>
 */
public final class Motion {

    /** Duración de un recorrido, en segundos simulados (supuesto visual, ajustable). */
    public static final double TRAVEL_SECONDS = 12.0;

    private Point2D from;
    private Point2D target;
    private Point2D current;
    private double startTime;

    /**
     * Crea un movimiento quieto en un punto.
     *
     * @param start punto donde está
     * @throws IllegalArgumentException si el punto es nulo
     */
    public Motion(Point2D start) {
        requirePoint(start);
        this.from = copy(start);
        this.target = copy(start);
        this.current = copy(start);
    }

    /**
     * Da un destino nuevo. Si es el mismo que ya tiene, no cambia nada; si no, el recorrido vuelve a empezar
     * desde donde está ahora, en el momento indicado.
     *
     * @param newTarget destino
     * @param time      momento en que empieza el recorrido, en segundos simulados desde el inicio
     * @throws IllegalArgumentException si el destino es nulo o el momento es negativo
     */
    public void moveTo(Point2D newTarget, double time) {
        requirePoint(newTarget);
        if (time < 0) {
            throw new IllegalArgumentException("El momento no puede ser negativo.");
        }
        if (newTarget.equals(target)) {
            return;
        }
        from = copy(current);
        target = copy(newTarget);
        startTime = time;
    }

    /**
     * Coloca el paciente donde le toca estar en un momento dado: antes de empezar sigue en el punto de
     * partida y después de {@value #TRAVEL_SECONDS} segundos ya llegó.
     *
     * @param time momento actual, en segundos simulados desde el inicio
     */
    public void update(double time) {
        double progress = Math.max(0.0, Math.min(1.0, (time - startTime) / TRAVEL_SECONDS));
        if (progress >= 1.0) {
            current = copy(target);
            return;
        }
        double eased = progress * progress * (3.0 - 2.0 * progress);
        current = new Point2D.Double(from.getX() + (target.getX() - from.getX()) * eased,
                from.getY() + (target.getY() - from.getY()) * eased);
    }

    /** Punto donde está ahora. */
    public Point2D getCurrent() {
        return copy(current);
    }

    /** Punto al que va. */
    public Point2D getTarget() {
        return copy(target);
    }

    /** Indica si todavía no llegó a su destino. */
    public boolean isMoving() {
        return !current.equals(target);
    }

    private static Point2D copy(Point2D point) {
        return new Point2D.Double(point.getX(), point.getY());
    }

    private static void requirePoint(Point2D point) {
        if (point == null) {
            throw new IllegalArgumentException("El punto es obligatorio.");
        }
    }
}
