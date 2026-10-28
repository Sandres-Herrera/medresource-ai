package com.medresource.services.simulation;

import com.medresource.model.Assignable;
import com.medresource.model.Bed;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.CompatibilityLevel;
import com.medresource.model.Disease;
import com.medresource.model.EventType;
import com.medresource.model.Hospital;
import com.medresource.model.Morgue;
import com.medresource.model.Patient;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientProfile;
import com.medresource.model.PatientStatus;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationEvent;
import com.medresource.model.SimulationResult;
import com.medresource.model.TriageLevel;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.services.strategy.Assignment;
import com.medresource.services.strategy.WaitingPatient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Motor de simulación: avanza el hospital minuto a minuto aplicando las
 * decisiones de una estrategia.
 *
 * <p>No tiene azar ni interfaz gráfica: con el mismo caso, el mismo hospital y
 * la misma estrategia siempre produce el mismo resultado. La interfaz solo
 * llama a {@link #step()} y lee los eventos nuevos.</p>
 *
 * <p>En cada minuto, en este orden: salen de la morgue los cuerpos que cumplieron
 * su tiempo; los pacientes pierden salud; terminan los tratamientos que
 * correspondan; llegan los pacientes nuevos (se les asigna triage y
 * diagnóstico); la estrategia reparte los recursos libres; y los fallecidos
 * pasan a la morgue si hay cupo. Un fallecido que no cabe en la morgue sigue
 * ocupando su cama.</p>
 *
 * <p>Los pacientes que siguen esperando {@value #EXTRA_MINUTES} minutos después
 * de la última hora de llegada se registran como sin atender.</p>
 */
public class SimulationEngine {

    /** Minutos que se sigue atendiendo después de terminar el periodo de llegadas. */
    public static final int EXTRA_MINUTES = 1440;

    private record ActiveTreatment(Patient patient, Assignment assignment, int endMinute, double effectiveness) {
    }

    private record PendingBody(Patient patient, Bed bed) {
    }

    private final SimulationCase simulationCase;
    private final Hospital hospital;
    private final AllocationStrategy strategy;
    private final TriageService triageService = new TriageService();
    private final HealthModel healthModel = new HealthModel();

    private final List<Patient> patients;
    private final List<Patient> waiting = new ArrayList<>();
    private final List<ActiveTreatment> active = new ArrayList<>();
    private final List<PendingBody> pendingBodies = new ArrayList<>();
    private final List<SimulationEvent> events = new ArrayList<>();
    private final Map<Patient, Integer> treatmentStarts = new HashMap<>();
    private final Map<Patient, CompatibilityLevel> careLevels = new HashMap<>();
    private final int endLimit;

    private int minute;
    private int nextArrival;

    /**
     * Prepara una simulación en el minuto 0.
     *
     * @param simulationCase caso con los pacientes que llegarán
     * @param hospital       hospital nuevo, con todos sus recursos libres; el motor lo modifica
     * @param strategy       estrategia que reparte los recursos
     * @throws IllegalArgumentException si algún argumento es nulo
     */
    public SimulationEngine(SimulationCase simulationCase, Hospital hospital, AllocationStrategy strategy) {
        if (simulationCase == null || hospital == null || strategy == null) {
            throw new IllegalArgumentException("El caso, el hospital y la estrategia son obligatorios.");
        }
        this.simulationCase = simulationCase;
        this.hospital = hospital;
        this.strategy = strategy;
        this.patients = simulationCase.createPatients();
        this.endLimit = simulationCase.config().durationMinutes() + EXTRA_MINUTES;
    }

    /**
     * Simula un minuto y pasa al siguiente.
     *
     * @throws IllegalStateException si la simulación ya terminó, o si la estrategia
     *                               entrega una decisión que no se puede cumplir
     */
    public void step() {
        if (isFinished()) {
            throw new IllegalStateException("La simulación ya terminó.");
        }
        releaseBodiesFromMorgue();
        deteriorate();
        finishTreatments();
        admitArrivals();
        if (minute >= endLimit) {
            abandonWaitingPatients();
        }
        assignResources();
        moveBodiesToMorgue();
        minute++;
    }

    /**
     * Simula hasta que todos los pacientes terminan su caso.
     *
     * @return resultado completo de la ejecución
     */
    public SimulationResult run() {
        while (!isFinished()) {
            step();
        }
        return getResult();
    }

    /**
     * Indica si ya no queda nada por simular: llegaron todos los pacientes y
     * ninguno espera ni está en tratamiento.
     *
     * @return {@code true} si la simulación terminó
     */
    public boolean isFinished() {
        return nextArrival == patients.size() && waiting.isEmpty() && active.isEmpty();
    }

    /**
     * Entrega el resultado de una simulación terminada.
     *
     * @return resultado con un desenlace por paciente y el registro de eventos
     * @throws IllegalStateException si la simulación todavía no terminó
     */
    public SimulationResult getResult() {
        if (!isFinished()) {
            throw new IllegalStateException("La simulación todavía no terminó.");
        }
        List<PatientOutcome> outcomes = patients.stream()
                .map(patient -> PatientOutcome.of(patient, treatmentStarts.getOrDefault(patient, -1),
                        careLevels.get(patient)))
                .toList();
        return new SimulationResult(strategy.getName(), simulationCase.config(), minute, outcomes, events);
    }

    /**
     * Devuelve el minuto que se simulará en la próxima llamada a {@link #step()},
     * que es también la cantidad de minutos ya simulados.
     *
     * @return minuto actual
     */
    public int getMinute() {
        return minute;
    }

    /**
     * Devuelve el registro de eventos hasta ahora, en orden cronológico.
     *
     * @return copia de la lista de eventos
     */
    public List<SimulationEvent> getEvents() {
        return List.copyOf(events);
    }

    /**
     * Devuelve el hospital que se está simulando.
     *
     * @return hospital con el estado actual de sus recursos
     */
    public Hospital getHospital() {
        return hospital;
    }

    /**
     * Devuelve todos los pacientes del caso, hayan llegado o no, en orden de llegada.
     *
     * @return pacientes de esta ejecución
     */
    public List<Patient> getPatients() {
        return List.copyOf(patients);
    }

    private void releaseBodiesFromMorgue() {
        for (Patient patient : hospital.getMorgue().releaseDue(minute)) {
            emit(EventType.MORGUE_RELEASE, patient, null, patient.getCode() + " sale de la morgue.");
        }
    }

    private void deteriorate() {
        for (Patient patient : List.copyOf(waiting)) {
            patient.deteriorate(healthModel.deteriorationPerMinute(patient, 0.0));
            if (!patient.isAlive()) {
                waiting.remove(patient);
                declareDeath(patient, causeOfDeathWhileWaiting(patient), null, null);
            }
        }
        for (ActiveTreatment treatment : List.copyOf(active)) {
            Patient patient = treatment.patient();
            patient.deteriorate(healthModel.deteriorationPerMinute(patient, treatment.effectiveness()));
            if (!patient.isAlive()) {
                active.remove(treatment);
                CauseOfDeath cause = treatment.effectiveness() < 1.0
                        ? CauseOfDeath.NON_IDEAL_SPECIALIST
                        : CauseOfDeath.LACK_OF_TIMELY_CARE;
                declareDeathInTreatment(treatment, cause);
            }
        }
    }

    private void finishTreatments() {
        for (ActiveTreatment treatment : List.copyOf(active)) {
            if (treatment.endMinute() > minute) {
                continue;
            }
            active.remove(treatment);
            Patient patient = treatment.patient();
            if (patient.isIrrecoverable()) {
                declareDeathInTreatment(treatment, CauseOfDeath.IRREDUCIBLE_MORTALITY);
                continue;
            }
            String place = placeOf(treatment.assignment());
            emit(EventType.TREATMENT_FINISHED, patient, place, patient.getCode() + " termina su tratamiento.");
            patient.improve(healthModel.recoveryPoints(patient, treatment.effectiveness()));
            if (patient.getDisease().isCurable()) {
                patient.finish(PatientStatus.DISCHARGED, minute);
                emit(EventType.DISCHARGE, patient, place, patient.getCode() + " recibe el alta.");
            } else {
                patient.finish(PatientStatus.STABILIZED_WITHOUT_CURE, minute);
                emit(EventType.STABILIZED_WITHOUT_CURE, patient, place,
                        patient.getCode() + " queda estabilizado, sin cura.");
            }
            treatment.assignment().resources().forEach(Assignable::release);
        }
    }

    private void admitArrivals() {
        List<PatientProfile> profiles = simulationCase.profiles();
        while (nextArrival < profiles.size() && profiles.get(nextArrival).arrivalMinute() <= minute) {
            arrive(patients.get(nextArrival));
            nextArrival++;
        }
    }

    private void arrive(Patient patient) {
        emit(EventType.ARRIVAL, patient, null,
                patient.getCode() + " llega al hospital (" + patient.getAge() + " años).");
        TriageLevel triage = triageService.classify(patient);
        patient.assignTriage(triage);
        emit(EventType.TRIAGE_ASSIGNED, patient, null,
                patient.getCode() + " clasificado: " + triage.getDisplayName() + ".");
        if (triage == TriageLevel.I) {
            emit(EventType.EMERGENCY_ALERT, patient, null,
                    "¡Emergencia! " + patient.getCode() + " llega en estado crítico.");
        }
        Disease diagnosis = diagnose(patient);
        emit(EventType.DIAGNOSIS, patient, null,
                patient.getCode() + " diagnosticado: " + diagnosis.getName() + ".");
        boolean someoneCanTreat = hospital.getDoctors().stream()
                .anyMatch(doctor -> doctor.canTreat(diagnosis, patient.getAge()));
        if (someoneCanTreat) {
            waiting.add(patient);
        } else {
            patient.finish(PatientStatus.REFERRED, minute);
            emit(EventType.REFERRAL, patient, null, patient.getCode() + " es remitido a otra institución: "
                    + "ningún médico del hospital puede tratar «" + diagnosis.getName() + "».");
        }
    }

    private void abandonWaitingPatients() {
        for (Patient patient : List.copyOf(waiting)) {
            waiting.remove(patient);
            patient.finish(PatientStatus.UNATTENDED, minute);
            emit(EventType.UNATTENDED, patient, null,
                    patient.getCode() + " sale del hospital sin haber sido atendido.");
        }
    }

    private void assignResources() {
        while (true) {
            List<WaitingPatient> candidates = waiting.stream()
                    .map(patient -> new WaitingPatient(patient, diagnose(patient)))
                    .toList();
            Optional<Assignment> decision = strategy.nextAssignment(minute, candidates, hospital);
            if (decision.isEmpty()) {
                return;
            }
            startTreatment(decision.get());
        }
    }

    private void startTreatment(Assignment assignment) {
        Patient patient = assignment.patient();
        requireValid(assignment);
        double effectiveness = assignment.doctor().effectivenessFor(patient.getDisease(), patient.getAge());
        int endMinute = minute + healthModel.treatmentMinutes(patient, effectiveness);

        assignment.resources().forEach(resource -> resource.assign(patient, endMinute));
        if (assignment.bloodUnits() > 0) {
            hospital.getBloodBank().withdraw(patient.getBloodType(), assignment.bloodUnits());
        }
        waiting.remove(patient);
        active.add(new ActiveTreatment(patient, assignment, endMinute, effectiveness));
        treatmentStarts.put(patient, minute);
        careLevels.put(patient, assignment.doctor().compatibilityWith(patient.getDisease(), patient.getAge()));

        String place = placeOf(assignment);
        if (assignment.bed() != null && assignment.bed().isIcu()) {
            patient.admitToIcu();
            emit(EventType.ICU_ADMISSION, patient, assignment.bed().getCode(),
                    patient.getCode() + " ingresa a la UCI (" + assignment.bed().getCode() + ").");
        } else {
            patient.startTreatment();
        }
        emit(EventType.TREATMENT_STARTED, patient, place, patient.getCode() + " inicia tratamiento con "
                + assignment.doctor().getCode() + " (" + assignment.doctor().getSpecialty().getDisplayName()
                + "); termina en el minuto " + endMinute + ".");
    }

    /** Comprueba que la decisión de la estrategia se pueda cumplir antes de aplicarla. */
    private void requireValid(Assignment assignment) {
        Patient patient = assignment.patient();
        if (!waiting.contains(patient)) {
            throw new IllegalStateException(
                    "La estrategia asignó a " + patient.getCode() + ", que no está en espera.");
        }
        for (Assignable resource : assignment.resources()) {
            if (!resource.isAvailable()) {
                throw new IllegalStateException("La estrategia asignó un recurso ocupado a " + patient.getCode() + ".");
            }
        }
        if (assignment.doctor().effectivenessFor(patient.getDisease(), patient.getAge()) <= 0.0) {
            throw new IllegalStateException(
                    "La estrategia asignó a " + patient.getCode() + " un médico que no puede tratarlo.");
        }
        if (assignment.bloodUnits() > 0
                && !hospital.getBloodBank().hasCompatibleUnits(patient.getBloodType(), assignment.bloodUnits())) {
            throw new IllegalStateException(
                    "La estrategia asignó a " + patient.getCode() + " sangre que no hay disponible.");
        }
    }

    private void declareDeathInTreatment(ActiveTreatment treatment, CauseOfDeath cause) {
        Assignment assignment = treatment.assignment();
        for (Assignable resource : assignment.resources()) {
            if (resource != assignment.bed()) {
                resource.release();
            }
        }
        declareDeath(treatment.patient(), cause, placeOf(assignment), assignment.bed());
    }

    private void declareDeath(Patient patient, CauseOfDeath cause, String place, Bed bed) {
        patient.die(cause, minute);
        emit(EventType.DEATH, patient, place,
                patient.getCode() + " fallece: " + cause.getDisplayName().toLowerCase() + ".");
        pendingBodies.add(new PendingBody(patient, bed));
    }

    private void moveBodiesToMorgue() {
        Morgue morgue = hospital.getMorgue();
        for (PendingBody body : List.copyOf(pendingBodies)) {
            if (!morgue.hasSpace()) {
                return;
            }
            morgue.admit(body.patient(), minute);
            pendingBodies.remove(body);
            String place = body.bed() == null ? null : body.bed().getCode();
            if (body.bed() != null) {
                body.bed().release();
            }
            emit(EventType.MORGUE_ADMISSION, body.patient(), place,
                    body.patient().getCode() + " pasa a la morgue.");
        }
    }

    /**
     * Causa de muerte de quien falleció esperando, según lo que le impedía ser atendido:
     * no quedaba sangre compatible, no había cama de UCI libre, o simplemente no se llegó a tiempo.
     */
    private CauseOfDeath causeOfDeathWhileWaiting(Patient patient) {
        Disease diagnosis = diagnose(patient);
        if (diagnosis.requiresBlood() && !hospital.getBloodBank().hasCompatibleUnits(patient.getBloodType(), 1)) {
            return CauseOfDeath.NO_COMPATIBLE_RESOURCE;
        }
        if (diagnosis.requiresIcu() && hospital.freeIcuBeds().isEmpty()) {
            return CauseOfDeath.NO_ICU_BED;
        }
        return CauseOfDeath.LACK_OF_TIMELY_CARE;
    }

    /**
     * Diagnóstico con el que deciden las estrategias. Por ahora es siempre la
     * enfermedad real; aquí se agregarían los diagnósticos equivocados.
     */
    private Disease diagnose(Patient patient) {
        return patient.getDisease();
    }

    /** Lugar donde está el paciente durante el tratamiento: quirófano, cama o consultorio del médico. */
    private String placeOf(Assignment assignment) {
        if (assignment.operatingRoom() != null) {
            return assignment.operatingRoom().getCode();
        }
        if (assignment.bed() != null) {
            return assignment.bed().getCode();
        }
        return assignment.doctor().getCode();
    }

    private void emit(EventType type, Patient patient, String resourceCode, String description) {
        events.add(new SimulationEvent(minute, type, patient.getCode(), resourceCode, description));
    }
}
