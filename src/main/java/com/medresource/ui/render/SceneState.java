package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.EventType;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.SimulationEvent;
import com.medresource.model.ZoneType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Dónde está cada paciente en el plano, según los eventos de la simulación.
 *
 * <p>Lee los eventos en el orden en que el motor los emite y deduce el lugar de cada paciente: aparece en la
 * entrada al llegar, espera en una silla de la sala de espera (por orden de llegada, y todos avanzan una silla
 * cuando alguien sale de la cola), pasa al lugar de su recurso (cama, quirófano o médico) al empezar su
 * tratamiento, se queda donde está al morir y pasa a un lugar de la morgue cuando el motor lo manda; quien
 * recibe el alta, es remitido o se va sin atención sale por la zona de alta o salida.</p>
 *
 * <p>No tiene nada de Swing: solo calcula lugares. Cuando hay más pacientes esperando que sillas, los que no
 * caben se acumulan en la última silla y {@link #overflow()} dice cuántos son.</p>
 */
public final class SceneState {

    /** Etapa de un paciente dentro de la escena. */
    public enum Phase {
        /** Espera en la sala de espera (o llegó hace un instante y va hacia ella). */
        QUEUED,
        /** Está en su cama, su quirófano o con su médico. */
        TREATING,
        /** Murió y sigue en el lugar donde estaba. */
        DECEASED,
        /** Está en un lugar de la morgue. */
        IN_MORGUE,
        /** Va hacia la salida; desaparece cuando se llama a {@link SceneState#clearDeparted()}. */
        LEAVING
    }

    /** Lugar de un paciente en un momento dado. */
    public record Placement(String patientCode, Phase phase, Position position) {
    }

    private static final class Entry {
        private Phase phase = Phase.QUEUED;
        private Position position;
    }

    private final HospitalLayout layout;
    private final Map<String, Entry> patients = new LinkedHashMap<>();
    private final Map<String, Integer> morgueSlots = new LinkedHashMap<>();
    private int departures;

    /**
     * Crea una escena vacía.
     *
     * @param layout plano del hospital
     * @throws IllegalArgumentException si el plano es nulo o no tiene zona de alta o salida
     */
    public SceneState(HospitalLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("El plano es obligatorio.");
        }
        if (layout.areasOf(ZoneType.DISCHARGE).isEmpty()) {
            throw new IllegalArgumentException("El plano no tiene zona de alta o salida.");
        }
        this.layout = layout;
    }

    /**
     * Aplica los eventos de un grupo, en orden.
     *
     * @param events eventos en el orden en que el motor los emitió
     * @throws IllegalArgumentException si la lista es nula
     * @throws IllegalStateException    si algún evento no se puede aplicar (ver {@link #apply})
     */
    public void applyAll(List<SimulationEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("La lista de eventos es obligatoria.");
        }
        events.forEach(this::apply);
    }

    /**
     * Aplica un evento. Los eventos que no cambian el lugar de nadie (triage, diagnóstico, alerta de
     * emergencia, reevaluación y fin de tratamiento) se ignoran.
     *
     * @param event evento de la simulación
     * @throws IllegalArgumentException si el evento es nulo
     * @throws IllegalStateException    si el paciente llega dos veces, si un evento cambia a un paciente que
     *                                  no ha llegado, o si el evento nombra un recurso que el plano no tiene
     */
    public void apply(SimulationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("El evento es obligatorio.");
        }
        EventType type = event.type();
        if (type == EventType.ARRIVAL) {
            arrive(event.patientCode());
            return;
        }
        switch (type) {
            case TREATMENT_STARTED, ICU_ADMISSION -> startTreatment(event);
            case DEATH -> die(event);
            case MORGUE_ADMISSION -> enterMorgue(event.patientCode());
            case MORGUE_RELEASE -> leaveMorgue(event.patientCode());
            case DISCHARGE, STABILIZED_WITHOUT_CURE, REFERRAL, UNATTENDED -> leave(event.patientCode());
            default -> {
                // Triage, diagnóstico, alerta, reevaluación y fin de tratamiento no mueven a nadie.
            }
        }
    }

    /** Quita de la escena a los pacientes que iban hacia la salida. */
    public void clearDeparted() {
        patients.values().removeIf(entry -> entry.phase == Phase.LEAVING);
    }

    /**
     * Lugar de todos los pacientes que hay en la escena, en orden de llegada.
     *
     * @return un lugar por paciente; la lista no se puede modificar
     */
    public List<Placement> placements() {
        List<Placement> result = new ArrayList<>();
        int rank = 0;
        for (Map.Entry<String, Entry> patient : patients.entrySet()) {
            Entry entry = patient.getValue();
            Position position = entry.phase == Phase.QUEUED ? seat(rank++) : entry.position;
            result.add(new Placement(patient.getKey(), entry.phase, position));
        }
        return List.copyOf(result);
    }

    /**
     * Etapa de un paciente.
     *
     * @param patientCode código del paciente
     * @return su etapa, o vacío si no está en la escena
     */
    public Optional<Phase> phaseOf(String patientCode) {
        Entry entry = patientCode == null ? null : patients.get(patientCode);
        return entry == null ? Optional.empty() : Optional.of(entry.phase);
    }

    /**
     * Lugar de un paciente.
     *
     * @param patientCode código del paciente
     * @return su casilla, o vacío si no está en la escena
     */
    public Optional<Position> positionOf(String patientCode) {
        return placements().stream()
                .filter(placement -> placement.patientCode().equals(patientCode))
                .map(Placement::position)
                .findFirst();
    }

    /** Cantidad de pacientes en la escena que están en la etapa dada. */
    public int count(Phase phase) {
        return (int) patients.values().stream().filter(entry -> entry.phase == phase).count();
    }

    /** Cantidad de pacientes esperando que no caben en las sillas de la sala de espera. */
    public int overflow() {
        return Math.max(0, count(Phase.QUEUED) - layout.getWaitingSpots().size());
    }

    private void arrive(String code) {
        if (patients.containsKey(code)) {
            throw new IllegalStateException("El paciente " + code + " ya llegó.");
        }
        patients.put(code, new Entry());
    }

    private void startTreatment(SimulationEvent event) {
        Entry entry = known(event.patientCode());
        Position place = layout.positionOf(event.resourceCode())
                .orElseThrow(() -> new IllegalStateException(
                        "El plano no tiene el recurso " + event.resourceCode() + "."));
        entry.phase = Phase.TREATING;
        entry.position = place;
    }

    private void die(SimulationEvent event) {
        Entry entry = known(event.patientCode());
        Position where = layout.positionOf(event.resourceCode()).orElse(currentPosition(event.patientCode()));
        entry.phase = Phase.DECEASED;
        entry.position = where;
    }

    private void enterMorgue(String code) {
        Entry entry = known(code);
        List<Position> places = layout.getMorgueSpots();
        Set<Integer> taken = new HashSet<>(morgueSlots.values());
        int slot = 0;
        while (taken.contains(slot) && slot < places.size() - 1) {
            slot++;
        }
        morgueSlots.put(code, slot);
        entry.phase = Phase.IN_MORGUE;
        entry.position = places.get(slot);
    }

    private void leaveMorgue(String code) {
        known(code);
        morgueSlots.remove(code);
        patients.remove(code);
    }

    private void leave(String code) {
        Entry entry = known(code);
        Area exit = layout.areasOf(ZoneType.DISCHARGE).get(0);
        entry.phase = Phase.LEAVING;
        entry.position = exit.slot(departures % exit.slotCount());
        departures++;
    }

    private Entry known(String code) {
        Entry entry = patients.get(code);
        if (entry == null) {
            throw new IllegalStateException("El paciente " + code + " no ha llegado.");
        }
        return entry;
    }

    /** Lugar actual de un paciente que está en escena; la entrada si todavía no tiene otro. */
    private Position currentPosition(String code) {
        return positionOf(code).orElse(layout.getEntrance());
    }

    /** Silla que le toca al paciente que va en ese puesto de la cola; los que no caben usan la última. */
    private Position seat(int rank) {
        List<Position> seats = layout.getWaitingSpots();
        return seats.get(Math.min(rank, seats.size() - 1));
    }
}