package com.medresource.ui.render;

import com.medresource.model.Position;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Objects;

/**
 * Una persona que camina por el plano siguiendo una ruta, tramo a tramo, a velocidad constante.
 *
 * <p>Cada {@linkplain Leg tramo} lleva al caminante hasta un punto del mundo y dice cuántos metros mide y en
 * qué casilla queda ese punto. El caminante avanza {@value #WALKING_SPEED} m por segundo simulado, así que un
 * recorrido largo tarda más que uno corto: a ×1 cruzar 20 m de pasillo toma 8 segundos reales. Mientras camina
 * recuerda la casilla del último punto al que llegó, para que una ruta nueva pueda empezar desde ahí.</p>
 *
 * <p>Es solo estado de la vista: el modelo no lo conoce y no cambia el resultado.</p>
 */
public final class Walker {

    /**
     * Velocidad al caminar, en metros por segundo simulado (supuesto visual, ajustable). Es más rápida que un
     * paso normal (1,4 m/s) para que a ×1 un recorrido típico del hospital, de 15 a 20 m, dure de 6 a 8 s.
     */
    public static final double WALKING_SPEED = 2.5;

    private Position cell;
    private Point2D from;
    private Point2D current;
    private List<Leg> legs = List.of();
    private double startTime;
    private int reached;

    /**
     * Crea un caminante quieto en un punto.
     *
     * @param cell  casilla donde está
     * @param start punto del mundo donde está, dentro de esa casilla
     * @throws IllegalArgumentException si falta la casilla o el punto
     */
    public Walker(Position cell, Point2D start) {
        if (cell == null || start == null) {
            throw new IllegalArgumentException("La casilla y el punto de partida son obligatorios.");
        }
        this.cell = cell;
        this.from = copy(start);
        this.current = copy(start);
    }

    /**
     * Empieza a caminar por unos tramos, desde donde está ahora y en el momento indicado. Si el último tramo
     * termina donde ya iba, no cambia nada; si no hay tramos, se queda donde está.
     *
     * @param newLegs tramos en orden; el primero sale del punto donde está ahora
     * @param time    momento en que empieza a caminar, en segundos simulados desde el inicio
     * @throws IllegalArgumentException si los tramos son nulos o el momento es negativo
     */
    public void walk(List<Leg> newLegs, double time) {
        if (newLegs == null || newLegs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Los tramos son obligatorios.");
        }
        if (time < 0) {
            throw new IllegalArgumentException("El momento no puede ser negativo.");
        }
        if (newLegs.isEmpty() || newLegs.get(newLegs.size() - 1).point().equals(getTarget())) {
            return;
        }
        from = copy(current);
        legs = List.copyOf(newLegs);
        startTime = time;
        reached = 0;
    }

    /**
     * Coloca al caminante donde le toca estar en un momento dado, según lo que lleva caminado desde que
     * empezó el recorrido.
     *
     * @param time momento actual, en segundos simulados desde el inicio
     */
    public void update(double time) {
        double walked = Math.max(0.0, time - startTime) * WALKING_SPEED;
        Point2D start = from;
        for (int index = 0; index < legs.size(); index++) {
            Leg leg = legs.get(index);
            if (walked < leg.meters()) {
                double share = walked / leg.meters();
                current = new Point2D.Double(start.getX() + (leg.point().getX() - start.getX()) * share,
                        start.getY() + (leg.point().getY() - start.getY()) * share);
                reach(index);
                return;
            }
            walked -= leg.meters();
            start = leg.point();
        }
        current = copy(getTarget());
        reach(legs.size());
    }

    /** Punto donde está ahora. */
    public Point2D getCurrent() {
        return copy(current);
    }

    /** Punto al que va; si está quieto, el punto donde está. */
    public Point2D getTarget() {
        return legs.isEmpty() ? copy(current) : copy(legs.get(legs.size() - 1).point());
    }

    /** Casilla del último punto al que llegó; antes de llegar al primero, la casilla donde empezó. */
    public Position getCell() {
        return cell;
    }

    /** Indica si todavía no llegó a su destino. */
    public boolean isMoving() {
        return !current.equals(getTarget());
    }

    /** Segundos que tarda el recorrido actual completo, desde que empezó hasta que llega, a su velocidad. */
    public double tripSeconds() {
        return legs.stream().mapToDouble(Leg::meters).sum() / WALKING_SPEED;
    }

    /** Momento en que termina (o terminó) su recorrido actual, en segundos simulados; 0 si nunca caminó. */
    public double arrivalTime() {
        return startTime + tripSeconds();
    }

    /** Anota la casilla del último punto alcanzado: el de cada tramo ya terminado. */
    private void reach(int completedLegs) {
        for (; reached < completedLegs; reached++) {
            cell = legs.get(reached).cell();
        }
    }

    private static Point2D copy(Point2D point) {
        return new Point2D.Double(point.getX(), point.getY());
    }

    /**
     * Tramo de un recorrido: hasta un punto del mundo, dentro de una casilla.
     *
     * @param cell   casilla donde queda el punto
     * @param point  punto del mundo al que llega el tramo
     * @param meters lo que mide el tramo, mayor que cero
     */
    public record Leg(Position cell, Point2D point, double meters) {

        /**
         * Valida el tramo.
         *
         * @throws IllegalArgumentException si falta la casilla o el punto, o si el tramo no mide nada
         */
        public Leg {
            if (cell == null || point == null) {
                throw new IllegalArgumentException("La casilla y el punto del tramo son obligatorios.");
            }
            if (!(meters > 0)) {
                throw new IllegalArgumentException("El tramo debe medir más de cero metros.");
            }
            point = copy(point);
        }

        /** Punto del mundo al que llega el tramo. */
        @Override
        public Point2D point() {
            return copy(point);
        }
    }
}
