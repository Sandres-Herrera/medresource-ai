package com.medresource.ui.render;

import java.awt.geom.Point2D;

/**
 * Movimiento suave de un paciente entre dos puntos del mundo.
 *
 * <p>Cuando el paciente recibe un destino nuevo, el movimiento arranca desde donde está en ese momento y
 * avanza con una curva suave (empieza y termina despacio). Es solo estado de la vista: el modelo no lo
 * conoce y no gasta minutos simulados.</p>
 */
public final class Motion {

    private Point2D from;
    private Point2D target;
    private Point2D current;

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
     * Da un destino nuevo. Si es el mismo que ya tiene, no cambia nada; si no, el recorrido vuelve a
     * empezar desde donde está ahora.
     *
     * @param newTarget destino
     * @throws IllegalArgumentException si el destino es nulo
     */
    public void moveTo(Point2D newTarget) {
        requirePoint(newTarget);
        if (newTarget.equals(target)) {
            return;
        }
        from = copy(current);
        target = copy(newTarget);
    }

    /**
     * Coloca el paciente en el punto que le toca cuando ha avanzado una parte del recorrido.
     *
     * @param progress avance del recorrido: 0 es el inicio y 1 es el destino
     * @throws IllegalArgumentException si el avance no está entre 0 y 1
     */
    public void advance(double progress) {
        if (progress < 0.0 || progress > 1.0) {
            throw new IllegalArgumentException("El avance debe estar entre 0 y 1.");
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