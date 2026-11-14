package com.medresource.ui.controller;

import com.medresource.model.Area;
import com.medresource.model.Doctor;
import com.medresource.model.EventType;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Nurse;
import com.medresource.model.Patient;
import com.medresource.model.Position;
import com.medresource.model.SimulationEvent;
import com.medresource.model.StaffMember;
import com.medresource.model.StaffSeat;
import com.medresource.model.ZoneType;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.ui.render.IdleMotion;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.PorterMarker;
import com.medresource.ui.render.SceneRenderer;
import com.medresource.ui.render.SceneState;
import com.medresource.ui.render.SceneTimeline;
import com.medresource.ui.render.SceneTimeline.TimedEvent;
import com.medresource.ui.render.StaffMarker;
import com.medresource.ui.render.Walker;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Reproduce una simulación de forma continua sobre el plano, segundo a segundo.
 *
 * <p>Une el motor, la línea de tiempo ({@link SceneTimeline}), el lugar de cada paciente ({@link SceneState})
 * y su itinerario ({@link Itinerary}). El motor avanza por minutos y va un minuto adelante de lo que se ve:
 * al llegar al minuto {@code m}, el reproductor simula ese minuto y la línea de tiempo reparte sus eventos en
 * sus 60 segundos. Cada evento se aplica a la escena en su momento, y los pacientes que cambian de lugar
 * empiezan a caminar justo entonces; así los pacientes llegan uno tras otro y se mueven al mismo tiempo que
 * otros están en tratamiento. Quien sale de la escena desaparece cuando llega a la puerta de salida.</p>
 *
 * <p>Nadie atraviesa paredes: cada recorrido sigue la ruta más corta por las puertas y los pasillos
 * ({@link WalkPlanner}) a velocidad de caminata ({@value Walker#WALKING_SPEED} m por segundo simulado), así que
 * un recorrido largo tarda más que uno corto. Y nadie se salta una parada: si el motor cambia a un paciente de
 * lugar mientras camina, primero llega a donde iba y después sigue al lugar nuevo.</p>
 *
 * <p>Los médicos y enfermeros también se mueven: libres, están sentados en su puesto de la sala del personal;
 * ocupados, se paran junto al paciente que atienden desde que se ve que su tratamiento empezó (o, si le dan
 * primeros auxilios mientras espera, junto a su silla, su cama o la entrada), y vuelven a su
 * puesto en el minuto en que el motor los libera. Si el paciente que van a atender todavía está en la sala de
 * espera, no van a buscarlo: lo esperan en su lugar de atención (la cama, el quirófano o el consultorio).</p>
 *
 * <p>A buscarlo va un {@linkplain PorterEscort camillero}: sale de la puerta de la sala de espera, llega hasta su
 * silla, lo acompaña a su lado hasta su lugar de atención y se va por la puerta de esa área. El paciente no se
 * levanta de la silla hasta que el camillero llega. Los camilleros no son personal del motor.</p>
 *
 * <p> Después de simular cada minuto, el reproductor anota a quién
 * atiende cada uno y a qué lugar de atención va cada paciente, porque el motor va un minuto adelante de lo que se
 * ve.</p>
 *
 * <p>Los enfermeros libres no se quedan todo el tiempo sentados: hacen {@linkplain NursingRounds rondas} entre
 * los pacientes que están en una cama de hospitalización o de UCI, se paran un rato a su lado y vuelven a la sala
 * del personal. Para el motor siguen libres: si los necesita, van a donde los llama.</p>
 *
 * <p>Cada paciente muestra su salud y, en tratamiento, cuánto lleva de él. La salud que se ve pasa poco a poco de
 * la que tenía al terminar el minuto anterior a la que tiene al terminar el minuto que se ve, así que el color
 * cambia sin saltos aunque el motor vaya un minuto adelante. El avance del tratamiento sale del minuto en que
 * empezó y del minuto en que termina, que el reproductor anota en cuanto el motor lo empieza.</p>
 *
 * <p>Quien no puede caminar va en camilla ({@link Patient#canWalk()}): con una fractura, inconsciente o sin poder
 * caminar, va acostado en ella desde la entrada hasta que sale de alta, y espera en ella en vez de sentarse.
 * Los fallecidos también: quedan acostados donde murieron y la camilla los lleva a la morgue. Mientras la camilla
 * se mueve, un camillero la empuja; los camilleros no son personal del motor, así que no le quitan a nadie
 * enfermeros ni médicos.</p>
 *
 * <p>Y quien está quieto respira: los pacientes que esperan o están en su cama y el personal en su puesto o junto a
 * un paciente suben y bajan apenas con el {@linkplain IdleMotion movimiento de reposo}, así que el hospital nunca
 * se ve congelado aunque nadie cambie de lugar. Los fallecidos no se mueven.</p>
 *
 * <p>De cada paciente que está en la escena da también su {@linkplain #card(String) ficha}: lo que se ve de él en
 * ese instante, con el lugar, la etapa, la salud, el avance y el personal que lo atiende.</p>
 *
 * <p>Solo lee el motor: nunca cambia el resultado, y el movimiento no gasta minutos simulados.</p>
 */
public final class ScenePlayer {

    private static final double SECONDS_PER_MINUTE = 60.0;
    /** Dónde se para cada miembro del personal junto a su paciente, a la izquierda del centro de su lugar. */
    private static final double[] STAFF_OFFSETS_X = {-32.0, -16.0, 0.0};
    /** Eventos que llevan a un paciente a su lugar de atención. */
    private static final Set<EventType> TO_CARE_PLACE = EnumSet.of(EventType.TREATMENT_STARTED,
            EventType.ICU_ADMISSION, EventType.RECOVERY_STARTED, EventType.FIRST_AID);
    /** Etapas a las que un paciente sale de la sala de espera acompañado por un camillero. */
    private static final Set<SceneState.Phase> ESCORTED_TO = EnumSet.of(SceneState.Phase.TREATING,
            SceneState.Phase.FIRST_AID);

    private final SimulationEngine engine;
    private final HospitalLayout layout;
    private final SceneRenderer renderer;
    private final SceneState state;
    private final SceneTimeline timeline = new SceneTimeline();
    private final Map<String, Patient> patients = new HashMap<>();
    private final WalkPlanner planner;
    private final Map<String, Itinerary> itineraries = new HashMap<>();
    private final Set<String> arrived = new HashSet<>();
    private final List<SimulationEvent> released = new ArrayList<>();
    private final Map<String, StaffMember> staff = new LinkedHashMap<>();
    private final Map<String, Walker> staffWalkers = new HashMap<>();
    private final Map<Integer, Map<String, String>> attendedByMinute = new HashMap<>();
    private final Map<String, Map<String, Integer>> attendedHistory = new HashMap<>();
    private final Map<Integer, Map<String, Double>> healthByMinute = new HashMap<>();
    private final Map<String, SimulationEngine.TreatmentSpan> treatments = new HashMap<>();
    private final Map<String, Position> carePlaces = new HashMap<>();
    private final Map<String, PorterEscort> escorts = new LinkedHashMap<>();
    private final Map<String, Double> departures = new HashMap<>();
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
        this.state = new SceneState(layout, this::passesDirectly);
        this.planner = new WalkPlanner(layout, renderer);
        engine.getPatients().forEach(patient -> patients.put(patient.getCode(), patient));
        List<StaffMember> members = new ArrayList<>(engine.getHospital().getDoctors());
        members.addAll(engine.getHospital().getNurses());
        for (StaffMember member : members) {
            layout.staffSeatOf(member.getCode()).ifPresent(seat -> {
                staff.put(member.getCode(), member);
                staffWalkers.put(member.getCode(), new Walker(seat.table(), renderer.seatOf(seat)));
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
        healthByMinute.keySet().removeIf(minute -> minute < getMinute() - 1);
        itineraries.values().forEach(itinerary -> itinerary.update(time));
        staffWalkers.values().forEach(walker -> walker.update(time));
        escorts.forEach((patient, escort) -> escort.update(time, itineraries.get(patient)));
        escorts.values().removeIf(PorterEscort::isGone);
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
            Itinerary itinerary = itineraries.get(placement.patientCode());
            Point2D at = itinerary.getCurrent();
            boolean dead = isDead(placement.phase());
            boolean onStretcher = dead || !patient.canWalk() && placement.phase() != SceneState.Phase.LEAVING;
            boolean moving = !itinerary.hasArrived();
            markers.add(new PatientMarker(patient.getCode(), patient.getTriageLevel(), patient.getSex(),
                    patient.isMinor(), dead, at.getX(), at.getY(), dead ? 0.0 : healthOf(patient),
                    placement.phase() == SceneState.Phase.TREATING ? progressOf(patient.getCode())
                            : PatientMarker.NO_PROGRESS, onStretcher, moving,
                    IdleMotion.bob(patient.getCode(), time, moving || dead)));
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
            Walker walker = staffWalkers.get(member.getCode());
            Point2D at = walker.getCurrent();
            boolean seated = at.equals(renderer.seatOf(layout.staffSeatOf(member.getCode()).orElseThrow()));
            markers.add(new StaffMarker(member.getCode(),
                    member instanceof Doctor doctor ? doctor.getSpecialty() : null, member.getSex(), seated,
                    at.getX(), at.getY(), IdleMotion.bob(member.getCode(), time, walker.isMoving()),
                    walker.isMoving()));
        }
        return markers;
    }

    /**
     * Camilleros que hay que dibujar ahora: los que van a buscar a un paciente a la sala de espera, lo acompañan o se
     * van después de dejarlo.
     *
     * @return un {@link PorterMarker} por camillero, en el orden en que salieron
     */
    public List<PorterMarker> porterMarkers() {
        List<PorterMarker> markers = new ArrayList<>();
        escorts.forEach((patient, escort) -> markers.add(new PorterMarker(patient, escort.getPosition().getX(),
                escort.getPosition().getY(), escort.isMoving())));
        return markers;
    }

    /**
     * Ficha de un paciente tal como se ve ahora: etapa, lugar (o hacia dónde va), salud, avance del tratamiento y
     * los médicos y enfermeros que lo atienden en este minuto.
     *
     * @param patientCode código del paciente
     * @return su ficha, o vacío si no está en la escena (todavía no llegó o ya salió)
     */
    public Optional<PatientCard> card(String patientCode) {
        Optional<SceneState.Phase> phase = state.phaseOf(patientCode);
        Optional<Position> position = state.positionOf(patientCode);
        Itinerary itinerary = itineraries.get(patientCode);
        if (phase.isEmpty() || position.isEmpty() || itinerary == null) {
            return Optional.empty();
        }
        Patient patient = patients.get(patientCode);
        boolean dead = isDead(phase.get());
        return Optional.of(new PatientCard(patient.getCode(), patient.getAge(), patient.getSex(),
                patient.getBloodType(), patient.getDisease().getName(), patient.getSymptoms(),
                patient.getTriageLevel(), phase.get().getDisplayName(), placeName(position.get()),
                !itinerary.hasArrived(),
                dead ? 0.0 : healthOf(patient),
                phase.get() == SceneState.Phase.TREATING ? progressOf(patientCode) : PatientMarker.NO_PROGRESS,
                patient.getArrivalMinute(), Math.max(0, getMinute() - patient.getArrivalMinute()),
                caregiversOf(patientCode)));
    }

    /**
     * Ficha de un médico o de un enfermero tal como se ve ahora: su especialidad, qué está haciendo y los pacientes que
     * se ha visto que atendió hasta este momento, con cómo están ahora.
     *
     * @param staffCode código del médico o del enfermero
     * @return su ficha, o vacío si no es del hospital
     */
    public Optional<StaffCard> staffCard(String staffCode) {
        StaffMember member = staff.get(staffCode);
        if (member == null) {
            return Optional.empty();
        }
        String current = attendedByMinute.getOrDefault(staffMinute, Map.of()).get(staffCode);
        Walker walker = staffWalkers.get(staffCode);
        String activity = current != null ? "Atiende a " + current
                : walker != null && walker.isMoving() ? "Caminando" : "Libre";
        List<StaffCard.Attended> attended = new ArrayList<>();
        attendedHistory.getOrDefault(staffCode, Map.of()).forEach((code, minute) -> {
            Patient patient = patients.get(code);
            attended.add(new StaffCard.Attended(code, patient.getTriageLevel(), minute, statusOf(patient)));
        });
        return Optional.of(new StaffCard(staffCode, member instanceof Doctor doctor ? doctor.getSpecialty() : null,
                member.getSex(), activity, current, attended));
    }

    /**
     * Lo que se ve ahora en una casilla del plano: el nombre del lugar y los pacientes que ya llegaron a ella, con
     * desde hace cuánto están en su tratamiento y cuánto les falta.
     *
     * @param cell casilla del plano
     * @return la información, o vacío si en esa casilla no hay un recurso ni un paciente
     */
    public Optional<PlaceInfo> placeInfo(Position cell) {
        if (cell == null) {
            return Optional.empty();
        }
        List<PlaceInfo.Occupant> occupants = new ArrayList<>();
        for (SceneState.Placement placement : state.placements()) {
            Itinerary itinerary = itineraries.get(placement.patientCode());
            if (!placement.position().equals(cell) || itinerary == null || !itinerary.hasArrived()) {
                continue;
            }
            Patient patient = patients.get(placement.patientCode());
            SimulationEngine.TreatmentSpan span = treatments.get(placement.patientCode());
            double there = Double.NaN;
            double remaining = Double.NaN;
            if (span != null && placement.phase() == SceneState.Phase.TREATING) {
                there = Math.max(0.0, time - span.startMinute() * SECONDS_PER_MINUTE);
                remaining = Math.max(0.0, span.endMinute() * SECONDS_PER_MINUTE - time);
            }
            occupants.add(new PlaceInfo.Occupant(placement.patientCode(), patient.getTriageLevel(),
                    placement.phase().getDisplayName(), there, remaining));
        }
        if (occupants.isEmpty() && layout.resourceAt(cell).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PlaceInfo(placeName(cell), occupants));
    }

    /** Cómo está un paciente ahora: su etapa en la escena o, si ya salió, cómo terminó su caso. */
    private String statusOf(Patient patient) {
        return state.phaseOf(patient.getCode()).map(SceneState.Phase::getDisplayName)
                .orElse(patient.getStatus().getDisplayName());
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

    /**
     * Salud que se ve de un paciente: la del final del minuto anterior, que va pasando a la del final del minuto
     * que se ve a medida que corren sus segundos.
     */
    private double healthOf(Patient patient) {
        int minute = getMinute();
        Double now = healthByMinute.getOrDefault(minute, Map.of()).get(patient.getCode());
        if (now == null) {
            return patient.getHealth();
        }
        Double before = healthByMinute.getOrDefault(minute - 1, Map.of()).get(patient.getCode());
        if (before == null) {
            return now;
        }
        double share = time / SECONDS_PER_MINUTE - minute;
        return Math.max(0.0, Math.min(Patient.MAX_HEALTH, before + (now - before) * share));
    }

    /** Parte del tratamiento que ya pasó en el momento que se ve, de 0 a 1. */
    private double progressOf(String patientCode) {
        SimulationEngine.TreatmentSpan span = treatments.get(patientCode);
        if (span == null) {
            return PatientMarker.NO_PROGRESS;
        }
        double start = span.startMinute() * SECONDS_PER_MINUTE;
        double length = (span.endMinute() - span.startMinute()) * SECONDS_PER_MINUTE;
        if (length <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (time - start) / length));
    }

    /** Nombre de un lugar del plano: la zona y, si hay un recurso en esa casilla, su código. */
    private String placeName(Position position) {
        String zone = layout.areaAt(position).map(Area::zone).map(ZoneType::getDisplayName).orElse("Hospital");
        return layout.resourceAt(position).map(code -> zone + " · " + code).orElse(zone);
    }

    /** Médicos y enfermeros que atienden a un paciente en el minuto que se ve, con su especialidad o su oficio. */
    private List<String> caregiversOf(String patientCode) {
        List<String> caregivers = new ArrayList<>();
        attendedByMinute.getOrDefault(staffMinute, Map.of()).forEach((code, patient) -> {
            if (patient.equals(patientCode)) {
                StaffMember member = staff.get(code);
                caregivers.add(code + " · " + (member instanceof Doctor doctor
                        ? doctor.getSpecialty().getDisplayName() : "Enfermería"));
            }
        });
        return caregivers;
    }

    /** Indica si un paciente es crítico y pasa directo, sin hacer fila en el triage: el motor ya lo clasificó. */
    private boolean passesDirectly(String patientCode) {
        Patient patient = patients.get(patientCode);
        return patient != null && patient.getTriageLevel() != null && patient.getTriageLevel().skipsTriageStation();
    }

    /**
     * Indica si el personal que atiende a un paciente debe pararse a su lado: mientras recibe su tratamiento o
     * primeros auxilios, no mientras hace fila en el triage, ya murió o va de salida.
     */
    private static boolean isAttendedWhereHeIs(SceneState.Phase phase) {
        return phase == SceneState.Phase.TREATING || phase == SceneState.Phase.FIRST_AID
                || phase == SceneState.Phase.QUEUED || phase == SceneState.Phase.DIRECT_PASS;
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
        List<SimulationEvent> fresh = List.copyOf(events.subList(fedEvents, events.size()));
        timeline.schedule(minute, fresh);
        fedEvents = events.size();
        attendedByMinute.put(minute, whoAttendsWhom());
        fresh.forEach(this::noteCarePlace);
        Map<String, Double> health = new HashMap<>();
        for (Patient patient : patients.values()) {
            boolean present = patient.getArrivalMinute() <= minute
                    && (!patient.isFinished() || patient.getOutcomeMinute() >= minute - 1);
            if (present) {
                health.put(patient.getCode(), patient.getHealth());
                engine.getTreatmentSpan(patient)
                        .ifPresent(span -> treatments.putIfAbsent(patient.getCode(), span));
            }
        }
        healthByMinute.put(minute, health);
    }

    /**
     * Anota el lugar de atención al que el motor ya mandó a un paciente, antes de que se vea: ahí lo espera el
     * personal que lo va a atender.
     */
    private void noteCarePlace(SimulationEvent event) {
        if (TO_CARE_PLACE.contains(event.type()) && event.resourceCode() != null) {
            layout.treatmentPlaceOf(event.resourceCode())
                    .ifPresent(place -> carePlaces.put(event.patientCode(), place));
        }
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
        attendedByMinute.getOrDefault(minute, Map.of()).forEach((member, patient) -> attendedHistory
                .computeIfAbsent(member, code -> new LinkedHashMap<>()).putIfAbsent(patient, minute));
        retargetStaff(Math.max(time, minute * SECONDS_PER_MINUTE));
    }

    /**
     * Da a cada miembro del personal su destino: junto a su paciente si se ve que el tratamiento o los primeros
     * auxilios ya empezaron; un enfermero libre, junto al paciente que visita en su ronda; y si no, su puesto en
     * la sala del personal. Junto a un paciente, quien lo atiende para el motor toma los primeros lugares, y quien
     * solo lo visita en su ronda se para después.
     */
    private void retargetStaff(double at) {
        Map<String, String> attended = attendedByMinute.getOrDefault(staffMinute, Map.of());
        List<SceneState.Placement> inBed = hospitalizedPatients();
        Map<String, Integer> companions = new HashMap<>();
        Map<StaffMember, Itinerary.Stop> rounds = new LinkedHashMap<>();
        Map<StaffMember, String> visited = new HashMap<>();
        int freeNurses = 0;
        for (StaffMember member : staff.values()) {
            String patient = attended.get(member.getCode());
            if (patient != null) {
                Optional<Itinerary.Stop> stop = Optional.ofNullable(itineraries.get(patient))
                        .flatMap(Itinerary::heading).map(heading -> waitingPlace(patient, heading));
                standNextTo(member, patient, stop.orElse(null), companions, at);
            } else if (member instanceof Nurse) {
                OptionalInt visit = NursingRounds.visit(freeNurses++, Math.max(0, staffMinute), inBed.size());
                if (visit.isPresent()) {
                    SceneState.Placement bed = inBed.get(visit.getAsInt());
                    rounds.put(member, new Itinerary.Stop(bed.phase(), bed.position()));
                    visited.put(member, bed.patientCode());
                } else {
                    standNextTo(member, null, null, companions, at);
                }
            } else {
                standNextTo(member, null, null, companions, at);
            }
        }
        rounds.forEach((nurse, stop) -> standNextTo(nurse, visited.get(nurse), stop, companions, at));
    }

    /**
     * Dónde espera el personal a un paciente: si el paciente todavía está en la sala de espera y el motor ya lo
     * mandó a su lugar de atención, en ese lugar (no van a buscarlo); si no, donde está el paciente.
     */
    private Itinerary.Stop waitingPlace(String patient, Itinerary.Stop heading) {
        Position place = carePlaces.get(patient);
        if (heading.phase() == SceneState.Phase.QUEUED && place != null) {
            return new Itinerary.Stop(SceneState.Phase.TREATING, place);
        }
        return heading;
    }

    /**
     * Pone a caminar a un miembro del personal junto a un paciente, en el siguiente lugar libre a su izquierda, si
     * el paciente está donde se le atiende; si no, a su puesto en la sala del personal.
     */
    private void standNextTo(StaffMember member, String patient, Itinerary.Stop stop, Map<String, Integer> companions,
                             double at) {
        StaffSeat seat = layout.staffSeatOf(member.getCode()).orElseThrow();
        Position cell = seat.table();
        Point2D target = renderer.seatOf(seat);
        if (patient != null && stop != null && isAttendedWhereHeIs(stop.phase())) {
            int index = companions.merge(patient, 1, Integer::sum) - 1;
            cell = stop.cell();
            Point2D place = renderer.centerOf(cell);
            target = new Point2D.Double(place.getX() + STAFF_OFFSETS_X[Math.min(index,
                    STAFF_OFFSETS_X.length - 1)], place.getY());
        }
        walkTo(staffWalkers.get(member.getCode()), cell, target, at);
    }

    /** Pacientes en tratamiento en una cama de hospitalización o de UCI, en orden de llegada: los de las rondas. */
    private List<SceneState.Placement> hospitalizedPatients() {
        List<SceneState.Placement> inBed = new ArrayList<>();
        for (SceneState.Placement placement : state.placements()) {
            if (placement.phase() == SceneState.Phase.TREATING && layout.areaAt(placement.position())
                    .map(area -> area.zone() == ZoneType.HOSPITALIZATION || area.zone() == ZoneType.ICU)
                    .orElse(false)) {
                inBed.add(placement);
            }
        }
        return inBed;
    }

    /** Aplica un evento a la escena y pone a caminar, desde ese momento, a quien cambió de lugar. */
    private void apply(TimedEvent timed) {
        SimulationEvent event = timed.event();
        Optional<SceneState.Phase> before = state.phaseOf(event.patientCode());
        Optional<Position> from = state.positionOf(event.patientCode());
        state.apply(event);
        released.add(event);
        if (TO_CARE_PLACE.contains(event.type())) {
            carePlaces.remove(event.patientCode());
        }
        if (before.orElse(null) == SceneState.Phase.QUEUED) {
            sendPorter(event.patientCode(), from.orElseThrow(), timed.time());
        }
        if (event.type() == EventType.ARRIVAL) {
            arrived.add(event.patientCode());
        }
        if (SceneState.changesPlacement(event.type())) {
            retarget(timed.time());
            retargetStaff(timed.time());
        }
    }

    /**
     * Si un paciente que caminaba sale de la sala de espera hacia su lugar de atención, manda un camillero a buscarlo:
     * el paciente no sale hasta que el camillero llega a su silla. Quien no puede caminar ya va en camilla.
     */
    private void sendPorter(String patientCode, Position chair, double at) {
        Optional<SceneState.Phase> after = state.phaseOf(patientCode);
        Patient patient = patients.get(patientCode);
        boolean leavesTheChair = after.isPresent() && ESCORTED_TO.contains(after.get())
                && !chair.equals(state.positionOf(patientCode).orElse(chair));
        if (!leavesTheChair || patient == null || !patient.canWalk() || escorts.containsKey(patientCode)) {
            return;
        }
        PorterEscort.fetch(patientCode, chair, layout, planner, renderer, at).ifPresent(escort -> {
            escorts.put(patientCode, escort);
            departures.put(patientCode, escort.readyAt());
        });
    }

    /**
     * Agrega a cada paciente de la escena su lugar como siguiente parada de su itinerario; los que acaban de
     * llegar parten de la entrada. Quien espera a un camillero sale cuando el camillero llega por él.
     */
    private void retarget(double at) {
        Map<String, Itinerary> alive = new HashMap<>();
        for (SceneState.Placement placement : state.placements()) {
            Itinerary itinerary = itineraries.get(placement.patientCode());
            if (itinerary == null) {
                itinerary = new Itinerary(layout.getEntrance(), planner, renderer);
            }
            Double departure = departures.remove(placement.patientCode());
            itinerary.goTo(new Itinerary.Stop(placement.phase(), placement.position()), at,
                    departure == null ? at : departure);
            alive.put(placement.patientCode(), itinerary);
        }
        itineraries.clear();
        itineraries.putAll(alive);
    }

    /**
     * Pone a caminar a un miembro del personal por los pasillos hacia un punto de una casilla, desde donde está en
     * ese momento; si ya va hacia ese punto, sigue igual.
     */
    private void walkTo(Walker walker, Position cell, Point2D target, double at) {
        walker.update(at);
        if (!walker.getTarget().equals(target)) {
            walker.walk(planner.legs(walker.getCell(), walker.getCurrent(), cell, target), at);
        }
    }

    /** Quita de la escena a los pacientes que salían y ya llegaron a la puerta. */
    private void removeArrivedDepartures() {
        for (SceneState.Placement placement : state.placements()) {
            Itinerary itinerary = itineraries.get(placement.patientCode());
            if (placement.phase() == SceneState.Phase.LEAVING && itinerary.hasArrived()
                    && state.removeDeparted(placement.patientCode())) {
                itineraries.remove(placement.patientCode());
            }
        }
    }
}
