package com.medresource.ui.controller;

import com.medresource.model.Doctor;
import com.medresource.model.EventType;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Patient;
import com.medresource.model.SimulationEvent;
import com.medresource.model.StaffMember;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.ui.render.Motion;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.SceneRenderer;
import com.medresource.ui.render.SceneState;
import com.medresource.ui.render.SceneTimeline;
import com.medresource.ui.render.SceneTimeline.TimedEvent;
import com.medresource.ui.render.StaffMarker;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Reproduce una simulación de forma continua sobre el plano, segundo a segundo.
 *
 * <p>Une el motor, la línea de tiempo ({@link SceneTimeline}), el lugar de cada paciente ({@link SceneState})
 * y su movimiento suave ({@link Motion}). El motor avanza por minutos y va un minuto adelante de lo que se ve:
 * al llegar al minuto {@code m}, el reproductor simula ese minuto y la línea de tiempo reparte sus eventos en
 * sus 60 segundos. Cada evento se aplica a la escena en su momento, y los pacientes que cambian de lugar
 * empiezan a caminar justo entonces; así los pacientes llegan uno tras otro y se mueven al mismo tiempo que
 * otros están en tratamiento. Quien sale de la escena desaparece cuando llega a la puerta de salida.</p>
 *
 * <p>Los médicos y enfermeros también se mueven: libres, están sentados en su puesto de la sala del personal;
 * ocupados, se paran junto al paciente que atienden desde que se ve que su tratamiento empezó, y vuelven a su
 * puesto en el minuto en que el motor los libera. Después de simular cada minuto, el reproductor anota a quién
 * atiende cada uno, porque el motor va un minuto adelante de lo que se ve.</p>
 *
 * <p>Solo lee el motor: nunca cambia el resultado, y el movimiento no gasta minutos simulados.</p>
 */
public final class ScenePlayer {

    private static final double SECONDS_PER_MINUTE = 60.0;
    /** Dónde se para cada miembro del personal junto a su paciente, a la izquierda del centro de su lugar. */
    private static final double[] STAFF_OFFSETS_X = {-32.0, -16.0, 0.0};

    private final SimulationEngine engine;
    private final HospitalLayout layout;
    private final SceneRenderer renderer;
    private final SceneState state;
    private final SceneTimeline timeline = new SceneTimeline();
    private final Map<String, Patient> patients = new HashMap<>();
    private final Map<String, Motion> motions = new HashMap<>();
    private final Set<String> arrived = new HashSet<>();
    private final List<SimulationEvent> released = new ArrayList<>();
    private final Map<String, StaffMember> staff = new LinkedHashMap<>();
    private final Map<String, Motion> staffMotions = new HashMap<>();
    private final Map<Integer, Map<String, String>> attendedByMinute = new HashMap<>();
    private int fedEvents;
    private double time;
    private int staffMinute = -1;

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
        List<StaffMember> members = new ArrayList<>(engine.getHospital().getDoctors());
        members.addAll(engine.getHospital().getNurses());
        for (StaffMember member : members) {
            layout.staffSeatOf(member.getCode()).ifPresent(seat -> {
                staff.put(member.getCode(), member);
                staffMotions.put(member.getCode(), new Motion(renderer.seatOf(seat)));
            });
        }
    }

    /**
     * Avanza la escena hasta un momento: simula en el motor los minutos que hagan falta, aplica en orden los
     * eventos cuyo momento ya llegó, mueve a los pacientes y quita a los que ya llegaron a la salida.
     *
     * @param newTime momento, en segundos simulados desde el inicio
     * @throws IllegalArgumentException si el momento es anterior al actual
     */
    public void advanceTo(double newTime) {
        if (newTime < time) {
            throw new IllegalArgumentException("La escena no puede retroceder.");
        }
        loadUntil((int) (newTime / SECONDS_PER_MINUTE));
        for (TimedEvent timed : timeline.releaseUntil(newTime)) {
            followStaffMinute(timed.time());
            apply(timed);
        }
        followStaffMinute(newTime);
        time = newTime;
        motions.values().forEach(motion -> motion.update(time));
        staffMotions.values().forEach(motion -> motion.update(time));
        removeArrivedDepartures();
    }

    /**
     * Momento del siguiente evento que todavía no se ve; si no hay ninguno pendiente, simula minutos en el
     * motor hasta encontrar uno.
     *
     * @return momento del siguiente evento, en segundos simulados; vacío si la simulación ya no tiene más
     */
    public OptionalDouble nextEventTime() {
        while (timeline.isEmpty() && !engine.isFinished()) {
            loadNextMinute();
        }
        return timeline.nextTime();
    }

    /**
     * Eventos que ya se vieron desde la última llamada, para el registro de la ventana.
     *
     * @return eventos en orden
     */
    public List<SimulationEvent> drainReleasedEvents() {
        List<SimulationEvent> fresh = List.copyOf(released);
        released.clear();
        return fresh;
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

    /**
     * Médicos y enfermeros que hay que dibujar ahora: primero los médicos y después los enfermeros.
     *
     * @return un {@link StaffMarker} por miembro del personal que tiene puesto en el plano
     */
    public List<StaffMarker> staffMarkers() {
        List<StaffMarker> markers = new ArrayList<>();
        for (StaffMember member : staff.values()) {
            Point2D at = staffMotions.get(member.getCode()).getCurrent();
            boolean seated = at.equals(renderer.seatOf(layout.staffSeatOf(member.getCode()).orElseThrow()));
            markers.add(new StaffMarker(member.getCode(),
                    member instanceof Doctor doctor ? doctor.getSpecialty() : null, member.getSex(), seated,
                    at.getX(), at.getY()));
        }
        return markers;
    }

    /**
     * Indica si la llegada de un paciente ya se vio en la escena.
     *
     * @param patientCode código del paciente
     * @return {@code true} si ya llegó al hospital en lo que se ve
     */
    public boolean hasArrived(String patientCode) {
        return arrived.contains(patientCode);
    }

    /** Pacientes esperando que no caben en las sillas de la sala de espera. */
    public int overflow() {
        return state.overflow();
    }

    /** Momento que se ve, en segundos simulados desde el inicio. */
    public double getTime() {
        return time;
    }

    /** Minuto que se ve. */
    public int getMinute() {
        return (int) (time / SECONDS_PER_MINUTE);
    }

    /** Indica si ya se vio todo: el motor terminó y no quedan eventos por mostrar. */
    public boolean isFinished() {
        return engine.isFinished() && timeline.isEmpty();
    }

    /** Dónde está cada paciente, para quien necesite los contadores de la escena. */
    public SceneState getState() {
        return state;
    }

    private static boolean isDead(SceneState.Phase phase) {
        return phase == SceneState.Phase.DECEASED || phase == SceneState.Phase.IN_MORGUE;
    }

    /** Simula en el motor todos los minutos hasta el indicado (inclusive) que todavía no se simularon. */
    private void loadUntil(int minute) {
        while (!engine.isFinished() && engine.getMinute() <= minute) {
            loadNextMinute();
        }
    }

    /** Simula un minuto en el motor y pone sus eventos en la línea de tiempo. */
    private void loadNextMinute() {
        int minute = engine.getMinute();
        engine.step();
        List<SimulationEvent> events = engine.getEvents();
        timeline.schedule(minute, events.subList(fedEvents, events.size()));
        fedEvents = events.size();
        attendedByMinute.put(minute, whoAttendsWhom());
    }

    /** Anota a qué paciente atiende cada médico y enfermero ocupado, después de simular un minuto. */
    private Map<String, String> whoAttendsWhom() {
        Map<String, String> attended = new LinkedHashMap<>();
        for (StaffMember member : staff.values()) {
            if (!member.isAvailable() && member.getAssignedPatient() != null) {
                attended.put(member.getCode(), member.getAssignedPatient().getCode());
            }
        }
        return attended;
    }

    /** Cuando lo que se ve pasa a otro minuto, el personal toma las tareas que tenía en ese minuto. */
    private void followStaffMinute(double at) {
        int minute = (int) (at / SECONDS_PER_MINUTE);
        if (minute == staffMinute) {
            return;
        }
        staffMinute = minute;
        attendedByMinute.keySet().removeIf(old -> old < minute);
        retargetStaff(Math.max(time, minute * SECONDS_PER_MINUTE));
    }

    /**
     * Da a cada miembro del personal su destino: junto a su paciente si se ve que el tratamiento ya empezó, o
     * su puesto en la sala del personal si está libre.
     */
    private void retargetStaff(double at) {
        Map<String, String> attended = attendedByMinute.getOrDefault(staffMinute, Map.of());
        Map<String, Integer> companions = new HashMap<>();
        for (StaffMember member : staff.values()) {
            Point2D target = renderer.seatOf(layout.staffSeatOf(member.getCode()).orElseThrow());
            String patient = attended.get(member.getCode());
            if (patient != null && state.phaseOf(patient).orElse(null) == SceneState.Phase.TREATING) {
                int index = companions.merge(patient, 1, Integer::sum) - 1;
                Point2D place = renderer.centerOf(state.positionOf(patient).orElseThrow());
                target = new Point2D.Double(place.getX() + STAFF_OFFSETS_X[Math.min(index,
                        STAFF_OFFSETS_X.length - 1)], place.getY());
            }
            Motion motion = staffMotions.get(member.getCode());
            motion.update(at);
            motion.moveTo(target, at);
        }
    }

    /** Aplica un evento a la escena y pone a caminar, desde ese momento, a quien cambió de lugar. */
    private void apply(TimedEvent timed) {
        SimulationEvent event = timed.event();
        state.apply(event);
        released.add(event);
        if (event.type() == EventType.ARRIVAL) {
            arrived.add(event.patientCode());
        }
        if (SceneState.changesPlacement(event.type())) {
            retarget(timed.time());
            retargetStaff(timed.time());
        }
    }

    /** Da a cada paciente de la escena su destino; los que acaban de llegar parten de la entrada. */
    private void retarget(double at) {
        Point2D entrance = renderer.centerOf(layout.getEntrance());
        Map<String, Motion> alive = new HashMap<>();
        for (SceneState.Placement placement : state.placements()) {
            Motion motion = motions.getOrDefault(placement.patientCode(), new Motion(entrance));
            motion.update(at);
            motion.moveTo(renderer.centerOf(placement.position()), at);
            alive.put(placement.patientCode(), motion);
        }
        motions.clear();
        motions.putAll(alive);
    }

    /** Quita de la escena a los pacientes que salían y ya llegaron a la puerta. */
    private void removeArrivedDepartures() {
        for (SceneState.Placement placement : state.placements()) {
            Motion motion = motions.get(placement.patientCode());
            if (placement.phase() == SceneState.Phase.LEAVING && !motion.isMoving()
                    && state.removeDeparted(placement.patientCode())) {
                motions.remove(placement.patientCode());
            }
        }
    }
}
