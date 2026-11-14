package com.medresource.ui.controller;

import com.medresource.model.Position;
import com.medresource.ui.render.SceneRenderer;
import com.medresource.ui.render.SceneState.Phase;
import com.medresource.ui.render.Walker;

import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Itinerario de un paciente en la escena: las paradas que recorre, en orden, sin saltarse ninguna.
 *
 * <p>Cada parada es una etapa de la escena y su casilla: la fila del triage, una estación, una silla, la cama de
 * primeros auxilios, la cama o el quirófano del tratamiento, la salida. El paciente camina hacia una parada y solo
 * sale hacia la siguiente cuando llegó: si el motor lo cambia de lugar mientras camina, la parada nueva espera su
 * turno, y el siguiente tramo empieza justo en el momento en que llegó, aunque se mire después.</p>
 *
 * <p>Las filas son la excepción: en la fila del triage y en la sala de espera el paciente se corre de puesto
 * cuando alguien sale, así que un puesto nuevo en la misma fila reemplaza al anterior en vez de esperar turno.</p>
 *
 * <p>Una parada también puede esperar a que alguien venga por el paciente ({@link #goTo(Stop, double, double)}): por
 * ejemplo, desde la sala de espera no sale hasta que llega el camillero que lo busca.</p>
 */
public final class Itinerary {

    /** Etapas que son una fila: un puesto nuevo en la misma fila reemplaza al anterior. */
    private static final Set<Phase> LINES = EnumSet.of(Phase.TRIAGE_QUEUE, Phase.QUEUED);

    /**
     * Parada del itinerario.
     *
     * @param phase etapa del paciente en esa parada
     * @param cell  casilla de la parada
     */
    public record Stop(Phase phase, Position cell) {

        /**
         * Valida la parada.
         *
         * @throws IllegalArgumentException si falta la etapa o la casilla
         */
        public Stop {
            if (phase == null || cell == null) {
                throw new IllegalArgumentException("La etapa y la casilla de la parada son obligatorias.");
            }
        }
    }

    /** Parada que espera su turno, con el momento en que se pidió. */
    private record Pending(Stop stop, double since) {
    }

    private final Walker walker;
    private final WalkPlanner planner;
    private final SceneRenderer renderer;
    private final Deque<Pending> pending = new ArrayDeque<>();
    private Stop heading;

    /**
     * Crea el itinerario de un paciente que aparece en una casilla, sin paradas todavía.
     *
     * @param start    casilla donde aparece, por ejemplo la entrada
     * @param planner  planificador de los recorridos por los pasillos
     * @param renderer renderizador que da el centro de cada casilla
     * @throws IllegalArgumentException si falta algún dato
     */
    public Itinerary(Position start, WalkPlanner planner, SceneRenderer renderer) {
        if (start == null || planner == null || renderer == null) {
            throw new IllegalArgumentException("Faltan la casilla de partida, el planificador o el renderizador.");
        }
        this.planner = planner;
        this.renderer = renderer;
        this.walker = new Walker(start, renderer.centerOf(start));
    }

    /**
     * Agrega una parada al itinerario. Si es la misma que la última, no cambia nada; si es otro puesto de la misma
     * fila, reemplaza a la última; si no, va al final y el paciente la recorre cuando llegue a las anteriores.
     *
     * @param stop parada nueva
     * @param at   momento en que el motor lo manda allí, en segundos simulados
     * @throws IllegalArgumentException si falta la parada o el momento es negativo
     */
    public void goTo(Stop stop, double at) {
        goTo(stop, at, at);
    }

    /**
     * Agrega una parada al itinerario, como {@link #goTo(Stop, double)}, pero sin salir hacia ella antes de un
     * momento dado: el paciente se queda donde está hasta entonces.
     *
     * @param stop     parada nueva
     * @param at       momento en que el motor lo manda allí, en segundos simulados
     * @param notBefore momento desde el que puede salir hacia ella; si es anterior a {@code at}, sale en {@code at}
     * @throws IllegalArgumentException si falta la parada o el momento es negativo
     */
    public void goTo(Stop stop, double at, double notBefore) {
        if (stop == null) {
            throw new IllegalArgumentException("La parada es obligatoria.");
        }
        if (at < 0) {
            throw new IllegalArgumentException("El momento no puede ser negativo.");
        }
        update(at);
        Stop last = pending.isEmpty() ? heading : pending.peekLast().stop();
        if (stop.equals(last)) {
            return;
        }
        if (last != null && last.phase() == stop.phase() && LINES.contains(stop.phase())) {
            if (pending.isEmpty()) {
                walkTo(stop, at);
            } else {
                pending.addLast(new Pending(stop, pending.removeLast().since()));
            }
            return;
        }
        double departure = Math.max(at, notBefore);
        if (heading == null || pending.isEmpty() && !walker.isMoving()) {
            walkTo(stop, departure);
        } else {
            pending.addLast(new Pending(stop, departure));
        }
    }

    /**
     * Coloca al paciente donde le toca estar en un momento dado: si ya llegó a su parada, sale hacia la siguiente
     * desde el momento en que llegó.
     *
     * @param at momento actual, en segundos simulados
     */
    public void update(double at) {
        walker.update(at);
        while (!walker.isMoving() && !pending.isEmpty()) {
            Pending next = pending.removeFirst();
            walkTo(next.stop(), Math.max(walker.arrivalTime(), next.since()));
            walker.update(at);
        }
    }

    /** Punto del mundo donde está ahora el paciente. */
    public Point2D getCurrent() {
        return walker.getCurrent();
    }

    /** Parada hacia la que camina o en la que ya está; vacío si todavía no tiene ninguna. */
    public Optional<Stop> heading() {
        return Optional.ofNullable(heading);
    }

    /** Paradas que esperan su turno, en orden. */
    public List<Stop> pendingStops() {
        return pending.stream().map(Pending::stop).toList();
    }

    /**
     * Momento en que llega (o llegó) a la parada hacia la que camina, en segundos simulados; si ya recorrió todo su
     * itinerario, el momento en que llegó a la última.
     */
    public double arrivalTime() {
        return walker.arrivalTime();
    }

    /** Indica si ya recorrió todo su itinerario: llegó a la última parada y no le queda ninguna. */
    public boolean hasArrived() {
        return pending.isEmpty() && !walker.isMoving();
    }

    private void walkTo(Stop stop, double start) {
        heading = stop;
        walker.walk(planner.legs(walker.getCell(), walker.getCurrent(), stop.cell(), renderer.centerOf(stop.cell())),
                start);
    }
}
