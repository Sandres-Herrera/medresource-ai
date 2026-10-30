package com.medresource.ui.controller;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Patient;
import com.medresource.model.SimulationEvent;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.ui.render.Motion;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.SceneRenderer;
import com.medresource.ui.render.SceneState;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reproduce una simulación minuto a minuto sobre el plano.
 *
 * <p>Une el motor, el lugar de cada paciente ({@link SceneState}) y su movimiento suave ({@link Motion}).
 * Cada llamada a {@link #step()} simula un minuto, entrega los eventos nuevos a la escena y da a cada paciente
 * su destino; después {@link #animate} mueve a los pacientes hacia él, y {@link #markers()} entrega lo que hay
 * que dibujar. Solo lee el motor: nunca cambia el resultado, y el movimiento no gasta minutos simulados.</p>
 */
public final class ScenePlayer {

    private final SimulationEngine engine;
    private final HospitalLayout layout;
    private final SceneRenderer renderer;
    private final SceneState state;
    private final Map<String, Patient> patients = new HashMap<>();
    private final Map<String, Motion> motions = new HashMap<>();
    private int fedEvents;

    /**
     * Prepara la reproducción de una simulación que todavía no empezó.
     *
     * @param engine   motor con la simulación (se avanza desde aquí)
     * @param layout   plano del mismo hospital que usa el motor
     * @param renderer renderizador que da el centro de cada casilla en el mundo
     * @throws IllegalArgumentException si algún argumento es nulo
     * @throws IllegalStateException    si el motor ya empezó
     */
    public ScenePlayer(SimulationEngine engine, HospitalLayout layout, SceneRenderer renderer) {
        if (engine == null || layout == null || renderer == null) {
            throw new IllegalArgumentException("El motor, el plano y el renderizador son obligatorios.");
        }
        if (engine.getMinute() != 0) {
            throw new IllegalStateException("La simulación ya empezó.");
        }
        this.engine = engine;
        this.layout = layout;
        this.renderer = renderer;
        this.state = new SceneState(layout);
        engine.getPatients().forEach(patient -> patients.put(patient.getCode(), patient));
    }

    /**
     * Simula un minuto: quita de la escena a quienes ya salieron, avanza el motor, entrega los eventos nuevos
     * a la escena y da a cada paciente su destino.
     *
     * @return {@code true} si se simuló un minuto; {@code false} si la simulación ya había terminado
     */
    public boolean step() {
        if (engine.isFinished()) {
            return false;
        }
        state.clearDeparted();
        engine.step();
        List<SimulationEvent> events = engine.getEvents();
        state.applyAll(events.subList(fedEvents, events.size()));
        fedEvents = events.size();
        retarget();
        return true;
    }

    /**
     * Mueve a los pacientes la parte indicada del recorrido hacia su destino.
     *
     * @param progress avance entre 0 (recién empezó el minuto) y 1 (llegaron)
     * @throws IllegalArgumentException si el avance no está entre 0 y 1
     */
    public void animate(double progress) {
        motions.values().forEach(motion -> motion.advance(progress));
    }

    /**
     * Pacientes que hay que dibujar ahora, en orden de llegada.
     *
     * @return un {@link PatientMarker} por paciente que está en la escena
     */
    public List<PatientMarker> markers() {
        List<PatientMarker> markers = new ArrayList<>();
        for (SceneState.Placement placement : state.placements()) {
            Patient patient = patients.get(placement.patientCode());
            Point2D at = motions.get(placement.patientCode()).getCurrent();
            markers.add(new PatientMarker(patient.getCode(), patient.getTriageLevel(), patient.getSex(),
                    patient.isMinor(), isDead(placement.phase()), at.getX(), at.getY()));
        }
        return markers;
    }

    private static boolean isDead(SceneState.Phase phase) {
        return phase == SceneState.Phase.DECEASED || phase == SceneState.Phase.IN_MORGUE;
    }

    /** Pacientes esperando que no caben en las sillas de la sala de espera. */
    public int overflow() {
        return state.overflow();
    }

    /** Minutos simulados hasta ahora. */
    public int getMinute() {
        return engine.getMinute();
    }

    /** Indica si la simulación ya terminó. */
    public boolean isFinished() {
        return engine.isFinished();
    }

    /** Dónde está cada paciente, para quien necesite los contadores de la escena. */
    public SceneState getState() {
        return state;
    }

    /** Da a cada paciente de la escena su destino; los que acaban de llegar parten de la entrada. */
    private void retarget() {
        Point2D entrance = renderer.centerOf(layout.getEntrance());
        Map<String, Motion> alive = new HashMap<>();
        for (SceneState.Placement placement : state.placements()) {
            Motion motion = motions.getOrDefault(placement.patientCode(), new Motion(entrance));
            motion.moveTo(renderer.centerOf(placement.position()));
            alive.put(placement.patientCode(), motion);
        }
        motions.clear();
        motions.putAll(alive);
    }
}