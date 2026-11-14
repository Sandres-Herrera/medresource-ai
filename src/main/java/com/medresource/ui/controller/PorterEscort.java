package com.medresource.ui.controller;

import com.medresource.model.Area;
import com.medresource.model.Door;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.ui.render.SceneRenderer;
import com.medresource.ui.render.Walker;

import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.Optional;

/**
 * Camillero que va a buscar a un paciente a la sala de espera y lo lleva hasta su lugar de atención.
 *
 * <p>Pasa por tres etapas: primero <em>va por él</em>, caminando desde la puerta de la sala de espera hasta el lado
 * de su silla; después <em>lo acompaña</em>, caminando a su lado mientras el paciente va a su cama, su quirófano
 * o su consultorio; y al final <em>se va</em> por la puerta más cercana del área donde lo dejó y desaparece. El
 * paciente no sale de la sala de espera hasta que el camillero llega ({@link #readyAt()}); mientras tanto, el médico
 * y el enfermero lo esperan en su lugar de atención.</p>
 *
 * <p>Es solo de la vista: los camilleros no son personal del motor y no cambian el resultado.</p>
 */
public final class PorterEscort {

    /** Cuánto se para el camillero a la derecha del paciente, en píxeles del mundo. */
    public static final double SIDE_OFFSET = 36.0;

    private final String patientCode;
    private final HospitalLayout layout;
    private final WalkPlanner planner;
    private final SceneRenderer renderer;
    private final Walker fetch;
    private final double readyAt;
    private Walker leaving;
    private Point2D position;
    private boolean moving;

    private PorterEscort(String patientCode, HospitalLayout layout, WalkPlanner planner, SceneRenderer renderer,
                         Walker fetch, double readyAt) {
        this.patientCode = patientCode;
        this.layout = layout;
        this.planner = planner;
        this.renderer = renderer;
        this.fetch = fetch;
        this.readyAt = readyAt;
        this.position = fetch.getCurrent();
    }

    /**
     * Manda a un camillero a buscar a un paciente que espera en una silla: sale de la puerta de la sala de espera
     * más cercana a la silla en el momento dado.
     *
     * @param patientCode código del paciente
     * @param chair       casilla de la silla donde espera
     * @param layout      plano del hospital
     * @param planner     planificador de los recorridos
     * @param renderer    renderizador que da el centro de cada casilla
     * @param at          momento en que sale, en segundos simulados
     * @return el camillero, o vacío si la sala de la silla no tiene puertas
     * @throws IllegalArgumentException si falta algún dato o el momento es negativo
     */
    public static Optional<PorterEscort> fetch(String patientCode, Position chair, HospitalLayout layout,
                                               WalkPlanner planner, SceneRenderer renderer, double at) {
        if (patientCode == null || chair == null || layout == null || planner == null || renderer == null) {
            throw new IllegalArgumentException("Faltan datos para mandar al camillero.");
        }
        if (at < 0) {
            throw new IllegalArgumentException("El momento no puede ser negativo.");
        }
        Optional<Door> door = nearestDoor(layout, chair);
        if (door.isEmpty()) {
            return Optional.empty();
        }
        Position start = door.get().outside();
        Walker walker = new Walker(start, renderer.centerOf(start));
        walker.walk(planner.legs(start, renderer.centerOf(start), chair, beside(renderer.centerOf(chair))), at);
        return Optional.of(new PorterEscort(patientCode, layout, planner, renderer, walker,
                at + walker.tripSeconds()));
    }

    /** Puerta del área de una casilla que queda más cerca de ella. */
    private static Optional<Door> nearestDoor(HospitalLayout layout, Position cell) {
        Optional<Area> area = layout.getAreas().stream().filter(each -> each.covers(cell)).findFirst();
        return area.flatMap(found -> layout.doorsOf(found).stream()
                .min(Comparator.comparingInt(door -> door.inside().stepsTo(cell))));
    }

    /** Punto junto a un paciente: a su derecha. */
    private static Point2D beside(Point2D point) {
        return new Point2D.Double(point.getX() + SIDE_OFFSET, point.getY());
    }

    /** Código del paciente que busca. */
    public String getPatientCode() {
        return patientCode;
    }

    /** Momento en que llega a la silla del paciente, en segundos simulados: desde ahí el paciente puede salir. */
    public double readyAt() {
        return readyAt;
    }

    /**
     * Coloca al camillero donde le toca estar en un momento: camino a la silla, al lado del paciente mientras este
     * camina, o camino a la puerta por la que se va cuando el paciente ya llegó o ya no está en la escena.
     *
     * @param time      momento actual, en segundos simulados
     * @param itinerary itinerario del paciente, o nulo si ya salió de la escena
     */
    public void update(double time, Itinerary itinerary) {
        if (leaving != null) {
            leaving.update(time);
            position = leaving.getCurrent();
            moving = leaving.isMoving();
            return;
        }
        fetch.update(Math.min(time, readyAt));
        position = fetch.getCurrent();
        moving = fetch.isMoving();
        if (time < readyAt) {
            return;
        }
        if (itinerary != null) {
            position = beside(itinerary.getCurrent());
            moving = !itinerary.hasArrived();
            if (!itinerary.hasArrived()) {
                return;
            }
        }
        Position cell = cellOf(itinerary);
        double start = itinerary == null ? time : Math.max(readyAt, Math.min(time, itinerary.arrivalTime()));
        leaving = new Walker(cell, position);
        nearestDoor(layout, cell).ifPresent(door -> leaving.walk(planner.legs(cell, position, door.outside(),
                renderer.centerOf(door.outside())), start));
        leaving.update(time);
        position = leaving.getCurrent();
        moving = leaving.isMoving();
    }

    /** Casilla donde dejó al paciente (o donde está el camillero, si el paciente ya no está). */
    private Position cellOf(Itinerary itinerary) {
        if (itinerary != null && itinerary.heading().isPresent()) {
            return itinerary.heading().get().cell();
        }
        return fetch.getCell();
    }

    /** Punto del mundo donde está ahora. */
    public Point2D getPosition() {
        return new Point2D.Double(position.getX(), position.getY());
    }

    /** Indica si va caminando: hacia la silla, junto al paciente o hacia la puerta por la que sale. */
    public boolean isMoving() {
        return moving;
    }

    /** Indica si ya se fue: dejó al paciente y llegó a la puerta por la que sale. */
    public boolean isGone() {
        return leaving != null && !leaving.isMoving();
    }
}
