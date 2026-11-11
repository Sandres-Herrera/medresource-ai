package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.EventType;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.SimulationEvent;
import com.medresource.model.ZoneType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Dónde está cada paciente en el plano, según los eventos de la simulación.
 *
 * <p>Lee los eventos en el orden en que el motor los emite y deduce el lugar de cada paciente: aparece en la
 * entrada al llegar y hace fila en el triage hasta que una estación lo recibe; cuando lo clasifican pasa a una
 * silla de la sala de espera (por orden de llegada, y todos avanzan una silla cuando alguien sale de la cola,
 * igual que en la fila del triage), pasa al lugar de su recurso (cama, quirófano o, en una consulta, el
 * escritorio de su médico en los consultorios) al empezar su tratamiento, se queda donde está al morir y pasa
 * a un lugar de la morgue cuando el motor lo manda; quien recibe el alta, es remitido o se va sin atención
 * sale por la zona de alta o salida.</p>
 *
 * <p>Un paciente crítico (triage I) hace pase directo: no hace fila en el triage ni se sienta en la sala de
 * espera. Espera en la entrada hasta que el motor lo lleva a una cama libre para darle primeros auxilios
 * mientras se desocupa quien lo debe tratar, y de ahí pasa a su tratamiento; si pierde esa cama, vuelve a
 * esperar en la entrada. La escena lo reconoce desde que llega si se le dice quién pasa directo; si no, en
 * cuanto el motor anuncia que empieza su triage sin estación.</p>
 *
 * <p>No tiene nada de Swing: solo calcula lugares. Cuando hay más pacientes esperando que sillas, los que no
 * caben se acumulan en la última silla y {@link #overflow()} dice cuántos son.</p>
 */
public final class SceneState {

    /** Etapa de un paciente dentro de la escena. */
    public enum Phase {
        /** Acaba de llegar y hace fila en el triage hasta que una estación lo recibe. */
        TRIAGE_QUEUE,
        /** Lo están valorando en una estación de triage. */
        IN_TRIAGE,
        /** Ya clasificado, espera en la sala de espera. */
        QUEUED,
        /** Paciente crítico en pase directo: espera en la entrada, sin fila ni silla, hasta que lo reciben. */
        DIRECT_PASS,
        /** Paciente crítico que espera en una cama, con primeros auxilios, a quien lo debe tratar. */
        FIRST_AID,
        /** Está en su cama, su quirófano o con su médico. */
        TREATING,
        /** Murió y sigue en el lugar donde estaba. */
        DECEASED,
        /** Está en un lugar de la morgue. */
        IN_MORGUE,
        /** Va hacia la salida; desaparece con {@link SceneState#removeDeparted(String)}. */
        LEAVING
    }

    /** Lugar de un paciente en un momento dado. */
    public record Placement(String patientCode, Phase phase, Position position) {
    }

    private static final class Entry {
        private Phase phase = Phase.TRIAGE_QUEUE;
        private Position position;
        private boolean direct;
    }

    /** Eventos que cambian el lugar de algún paciente; los demás no mueven a nadie. */
    private static final Set<EventType> PLACE_CHANGES = EnumSet.of(EventType.ARRIVAL,
            EventType.TRIAGE_STARTED, EventType.TRIAGE_ASSIGNED, EventType.FIRST_AID, EventType.TREATMENT_STARTED,
            EventType.ICU_ADMISSION, EventType.RECOVERY_STARTED, EventType.DEATH,
            EventType.MORGUE_ADMISSION, EventType.MORGUE_RELEASE, EventType.DISCHARGE,
            EventType.STABILIZED_WITHOUT_CURE, EventType.REFERRAL, EventType.UNATTENDED);

    private final HospitalLayout layout;
    private final Predicate<String> passesDirectly;
    private final Map<String, Entry> patients = new LinkedHashMap<>();
    private final Map<String, Integer> morgueSlots = new LinkedHashMap<>();
    private int departures;

    /**
     * Crea una escena vacía que reconoce el pase directo cuando el motor lo anuncia.
     *
     * @param layout plano del hospital
     * @throws IllegalArgumentException si el plano es nulo o no tiene zona de alta o salida
     */
    public SceneState(HospitalLayout layout) {
        this(layout, code -> false);
    }

    /**
     * Crea una escena vacía que sabe, desde que llegan, qué pacientes hacen pase directo.
     *
     * @param layout         plano del hospital
     * @param passesDirectly dice, por el código del paciente, si es crítico y pasa directo sin hacer fila
     * @throws IllegalArgumentException si falta algún dato o si el plano no tiene zona de alta o salida
     */
    public SceneState(HospitalLayout layout, Predicate<String> passesDirectly) {
        if (layout == null) {
            throw new IllegalArgumentException("El plano es obligatorio.");
        }
        if (passesDirectly == null) {
            throw new IllegalArgumentException("Hay que decir quién pasa directo.");
        }
        if (layout.areasOf(ZoneType.DISCHARGE).isEmpty()) {
            throw new IllegalArgumentException("El plano no tiene zona de alta o salida.");
        }
        this.layout = layout;
        this.passesDirectly = passesDirectly;
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
     * Aplica un evento. Los eventos que no cambian el lugar de nadie (diagnóstico, alerta de emergencia,
     * reevaluación y fin de tratamiento) se ignoran. Un inicio de triage sin estación es un pase directo: el
     * paciente deja la fila y espera en la entrada, y su clasificación no lo manda a la sala de espera. Los
     * primeros auxilios con cama lo llevan a esa cama; sin cama, lo atienden donde está, salvo que acabe de
     * dejar su cama: entonces vuelve a esperar en la entrada (pase directo) o en la sala de espera.
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
            case TRIAGE_STARTED -> startTriage(event);
            case TRIAGE_ASSIGNED -> classify(event.patientCode());
            case FIRST_AID -> giveFirstAid(event);
            case TREATMENT_STARTED, ICU_ADMISSION, RECOVERY_STARTED -> startTreatment(event);
            case DEATH -> die(event);
            case MORGUE_ADMISSION -> enterMorgue(event.patientCode());
            case MORGUE_RELEASE -> leaveMorgue(event.patientCode());
            case DISCHARGE, STABILIZED_WITHOUT_CURE, REFERRAL, UNATTENDED -> leave(event.patientCode());
            default -> {
                // Diagnóstico, alerta, reevaluación y fin de tratamiento no mueven a nadie.
            }
        }
    }

    /**
     * Indica si un tipo de evento cambia el lugar de algún paciente.
     *
     * @param type tipo de evento
     * @return {@code true} si el evento mueve a alguien; {@code false} si solo informa (diagnóstico, alerta,
     *         reevaluación o fin de tratamiento)
     * @throws IllegalArgumentException si el tipo es nulo
     */
    public static boolean changesPlacement(EventType type) {
        if (type == null) {
            throw new IllegalArgumentException("El tipo de evento es obligatorio.");
        }
        return PLACE_CHANGES.contains(type);
    }

    /**
     * Quita de la escena a un paciente que iba hacia la salida (por ejemplo, cuando ya llegó a la puerta).
     *
     * @param patientCode código del paciente
     * @return {@code true} si se quitó; {@code false} si no estaba en la escena o todavía no se iba
     */
    public boolean removeDeparted(String patientCode) {
        Entry entry = patientCode == null ? null : patients.get(patientCode);
        if (entry == null || entry.phase != Phase.LEAVING) {
            return false;
        }
        patients.remove(patientCode);
        return true;
    }

    /**
     * Lugar de todos los pacientes que hay en la escena, en orden de llegada.
     *
     * @return un lugar por paciente; la lista no se puede modificar
     */
    public List<Placement> placements() {
        List<Placement> result = new ArrayList<>();
        int rank = 0;
        int triageRank = 0;
        for (Map.Entry<String, Entry> patient : patients.entrySet()) {
            Entry entry = patient.getValue();
            Position position = switch (entry.phase) {
                case QUEUED -> seat(rank++);
                case TRIAGE_QUEUE -> triageQueueSpot(triageRank++);
                default -> entry.position;
            };
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
        Entry entry = new Entry();
        if (passesDirectly.test(code)) {
            passDirectly(entry);
        }
        patients.put(code, entry);
    }

    /** Lleva al paciente a su estación de triage; sin estación, hace pase directo y espera en la entrada. */
    private void startTriage(SimulationEvent event) {
        Entry entry = known(event.patientCode());
        if (event.resourceCode() == null) {
            passDirectly(entry);
            return;
        }
        entry.position = layout.positionOf(event.resourceCode())
                .orElseThrow(() -> new IllegalStateException(
                        "El plano no tiene la estación de triage " + event.resourceCode() + "."));
        entry.phase = Phase.IN_TRIAGE;
    }

    /** El paciente clasificado pasa a la sala de espera, salvo que haga pase directo. */
    private void classify(String code) {
        Entry entry = known(code);
        if (entry.phase != Phase.DIRECT_PASS) {
            entry.phase = Phase.QUEUED;
        }
    }

    /**
     * Primeros auxilios: con cama, el paciente pasa a ella; sin cama, lo atienden donde está, y si venía de su
     * cama vuelve a esperar donde le toca.
     */
    private void giveFirstAid(SimulationEvent event) {
        Entry entry = known(event.patientCode());
        if (event.resourceCode() != null) {
            entry.position = layout.positionOf(event.resourceCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "El plano no tiene la cama " + event.resourceCode() + "."));
            entry.phase = Phase.FIRST_AID;
        } else if (entry.phase == Phase.FIRST_AID) {
            if (entry.direct) {
                passDirectly(entry);
            } else {
                entry.phase = Phase.QUEUED;
            }
        }
    }

    /** El paciente crítico no hace fila ni se sienta: espera en la entrada a que lo reciban. */
    private void passDirectly(Entry entry) {
        entry.direct = true;
        entry.phase = Phase.DIRECT_PASS;
        entry.position = layout.getEntrance();
    }

    private void startTreatment(SimulationEvent event) {
        Entry entry = known(event.patientCode());
        Position place = layout.treatmentPlaceOf(event.resourceCode())
                .orElseThrow(() -> new IllegalStateException(
                        "El plano no tiene el recurso " + event.resourceCode() + "."));
        entry.phase = Phase.TREATING;
        entry.position = place;
    }

    private void die(SimulationEvent event) {
        Entry entry = known(event.patientCode());
        Position where = layout.treatmentPlaceOf(event.resourceCode()).orElse(currentPosition(event.patientCode()));
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

    /**
     * Lugar de la fila del triage que le toca al paciente que va en ese puesto; los que no caben usan el
     * último. Sin lugares de fila, esperan en la entrada.
     */
    private Position triageQueueSpot(int rank) {
        List<Position> spots = layout.getTriageQueueSpots();
        return spots.isEmpty() ? layout.getEntrance() : spots.get(Math.min(rank, spots.size() - 1));
    }

    /** Silla que le toca al paciente que va en ese puesto de la cola; los que no caben usan la última. */
    private Position seat(int rank) {
        List<Position> seats = layout.getWaitingSpots();
        return seats.get(Math.min(rank, seats.size() - 1));
    }
}