package com.medresource.services.simulation;

import com.medresource.model.Assignable;
import com.medresource.model.Bed;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.CompatibilityLevel;
import com.medresource.model.Disease;
import com.medresource.model.EventType;
import com.medresource.model.HealthModel;
import com.medresource.model.Hospital;
import com.medresource.model.Morgue;
import com.medresource.model.Patient;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientProfile;
import com.medresource.model.PatientStatus;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationEvent;
import com.medresource.model.SimulationResult;
import com.medresource.model.StaffMember;
import com.medresource.model.TreatmentType;
import com.medresource.model.TriageLevel;
import com.medresource.model.TriageStation;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.services.strategy.Assignment;
import com.medresource.services.strategy.WaitingPatient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * correspondan; el médico y los enfermeros que terminaron el ingreso de un
 * paciente hospitalizado o una operación quedan libres; los operados pasan del
 * quirófano a su cama de recuperación; terminan las valoraciones de triage que
 * correspondan (al paciente se le asigna nivel y diagnóstico y pasa a la sala
 * de espera); llegan los pacientes nuevos y hacen fila en el triage; las
 * estaciones de triage libres reciben a los primeros de la fila; la estrategia
 * reparte los recursos libres; el personal que quedó libre da primeros auxilios a
 * los pacientes críticos que siguen esperando; y los fallecidos pasan a la morgue
 * si hay cupo.
 * Un fallecido que no cabe en la morgue sigue ocupando su cama.</p>
 *
 * <p>La valoración de triage dura según el nivel del paciente
 * ({@link TriageLevel#getAssessmentMinutes()}); mientras tanto el paciente no
 * está en la sala de espera, así que la estrategia todavía no puede atenderlo, y
 * sigue perdiendo salud igual que en la espera. Un paciente de nivel I no hace
 * fila ni ocupa una estación: se reconoce en la puerta y pasa directo.</p>
 *
 * <p>En una consulta el personal acompaña al paciente todo el tratamiento. En una
 * hospitalización (cama o UCI) solo durante el ingreso
 * ({@link TreatmentType#staffMinutes(int)}); después el paciente sigue en su cama
 * a cargo de enfermería hasta el final del tratamiento. Una cirugía ocupa al
 * cirujano, a los enfermeros y al quirófano solo durante la operación
 * ({@link TreatmentType#operationMinutes(int)}); el resto del tratamiento el
 * paciente se recupera en la cama que se le reservó (en UCI si la necesita).</p>
 *
 * <p>Primeros auxilios: un paciente que espera y es crítico (triage I, o con salud
 * de {@value #CRITICAL_HEALTH} o menos) recibe primeros auxilios de un enfermero o
 * un médico libre, aunque no sea quien lo debe tratar, mientras se desocupa el
 * médico o el quirófano que necesita. Así pierde salud más despacio
 * ({@link HealthModel#FIRST_AID_EFFECTIVENESS}). Primero los de triage I y después
 * los de menos salud; se prefiere a un enfermero para dejar libres a los médicos. Al
 * empezar cada reparto ese personal vuelve a estar libre, así que la estrategia
 * siempre puede usarlo para un tratamiento; los primeros auxilios se reparten con
 * lo que sobra, y quien ya atendía a un paciente lo sigue atendiendo si puede.</p>
 *
 * <p>Los pacientes que siguen esperando {@value #EXTRA_MINUTES} minutos después
 * de la última hora de llegada se registran como sin atender.</p>
 */
public class SimulationEngine {

    /** Minutos que se sigue atendiendo después de terminar el periodo de llegadas. */
    public static final int EXTRA_MINUTES = 1440;

    /** Salud con la que un paciente que espera ya se considera crítico y recibe primeros auxilios (supuesto). */
    public static final double CRITICAL_HEALTH = 50.0;

    /** Primero los de triage I y después los de menos salud; a igual salud, el que llegó antes. */
    private static final Comparator<Patient> MOST_CRITICAL_FIRST = Comparator
            .comparing((Patient patient) -> !patient.getTriageLevel().skipsTriageStation())
            .thenComparingDouble(Patient::getHealth);

    private record ActiveTreatment(Patient patient, Assignment assignment, int staffEndMinute, int endMinute,
                                   double effectiveness) {
    }

    private record TriageInProgress(Patient patient, TriageStation station, int endMinute) {
    }

    private record PendingBody(Patient patient, Bed bed) {
    }

    /** Primeros auxilios que recibe un paciente mientras espera: quién se los da. */
    private record FirstAid(StaffMember carer) {
    }

    private final SimulationCase simulationCase;
    private final Hospital hospital;
    private final AllocationStrategy strategy;
    private final TriageService triageService = new TriageService();
    private final HealthModel healthModel = new HealthModel();

    private final List<Patient> patients;
    private final List<Patient> triageQueue = new ArrayList<>();
    private final List<TriageInProgress> inTriage = new ArrayList<>();
    private final List<Patient> waiting = new ArrayList<>();
    private final List<ActiveTreatment> active = new ArrayList<>();
    private final List<PendingBody> pendingBodies = new ArrayList<>();
    private final List<SimulationEvent> events = new ArrayList<>();
    private final Map<Patient, Integer> treatmentStarts = new HashMap<>();
    private final Map<Patient, CompatibilityLevel> careLevels = new HashMap<>();
    private final Set<Patient> recovering = new HashSet<>();
    private final Map<Patient, FirstAid> firstAid = new LinkedHashMap<>();
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
        releaseStaffAfterAdmission();
        moveOperatedPatientsToRecovery();
        finishTriages();
        admitArrivals();
        startTriages();
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
     * ninguno está en el triage, espera ni está en tratamiento.
     *
     * @return {@code true} si la simulación terminó
     */
    public boolean isFinished() {
        return nextArrival == patients.size() && triageQueue.isEmpty() && inTriage.isEmpty()
                && waiting.isEmpty() && active.isEmpty();
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
        for (Patient patient : List.copyOf(triageQueue)) {
            patient.deteriorate(healthModel.deteriorationPerMinute(patient, 0.0));
            if (!patient.isAlive()) {
                triageQueue.remove(patient);
                declareDeath(patient, CauseOfDeath.LACK_OF_TIMELY_CARE, null, null);
            }
        }
        for (TriageInProgress triage : List.copyOf(inTriage)) {
            Patient patient = triage.patient();
            patient.deteriorate(healthModel.deteriorationPerMinute(patient, 0.0));
            if (!patient.isAlive()) {
                inTriage.remove(triage);
                triage.station().release();
                declareDeath(patient, CauseOfDeath.LACK_OF_TIMELY_CARE, triage.station().getCode(), null);
            }
        }
        for (Patient patient : List.copyOf(waiting)) {
            double care = firstAid.containsKey(patient) ? HealthModel.FIRST_AID_EFFECTIVENESS : 0.0;
            patient.deteriorate(healthModel.deteriorationPerMinute(patient, care));
            if (!patient.isAlive()) {
                waiting.remove(patient);
                endFirstAid(patient);
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
            String place = placeOf(treatment);
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
            releaseHeld(treatment.assignment().resources(), patient);
            recovering.remove(patient);
        }
    }

    /** Libera al médico y a los enfermeros que terminaron el ingreso de un hospitalizado o una operación. */
    private void releaseStaffAfterAdmission() {
        for (ActiveTreatment treatment : active) {
            if (treatment.staffEndMinute() <= minute) {
                releaseHeld(treatment.assignment().staff(), treatment.patient());
            }
        }
    }

    /**
     * Pasa a recuperación a los pacientes cuya operación terminó: el quirófano queda libre y el paciente sigue
     * en la cama que se le reservó, o ingresa a la UCI si la cama es de UCI.
     */
    private void moveOperatedPatientsToRecovery() {
        for (ActiveTreatment treatment : active) {
            Patient patient = treatment.patient();
            Assignment assignment = treatment.assignment();
            if (!patient.getDisease().getTreatment().hasRecovery() || treatment.staffEndMinute() > minute
                    || recovering.contains(patient)) {
                continue;
            }
            releaseHeld(List.of(assignment.operatingRoom()), patient);
            recovering.add(patient);
            String bedCode = assignment.bed().getCode();
            if (assignment.bed().isIcu()) {
                patient.admitToIcu();
                emit(EventType.ICU_ADMISSION, patient, bedCode,
                        patient.getCode() + " sale del quirófano e ingresa a la UCI (" + bedCode + ").");
            } else {
                emit(EventType.RECOVERY_STARTED, patient, bedCode,
                        patient.getCode() + " sale del quirófano y se recupera en " + bedCode + ".");
            }
        }
    }

    /** Libera los recursos que todavía están ocupados por el paciente (el personal pudo irse antes). */
    private static void releaseHeld(List<? extends Assignable> resources, Patient patient) {
        for (Assignable resource : resources) {
            if (!resource.isAvailable() && resource.getAssignedPatient() == patient) {
                resource.release();
            }
        }
    }

    private void admitArrivals() {
        List<PatientProfile> profiles = simulationCase.profiles();
        while (nextArrival < profiles.size() && profiles.get(nextArrival).arrivalMinute() <= minute) {
            arrive(patients.get(nextArrival));
            nextArrival++;
        }
    }

    /**
     * Recibe a un paciente. Su nivel de triage se calcula de una vez porque de él depende cuánto dura su
     * valoración, pero solo se anuncia cuando la valoración termina.
     */
    private void arrive(Patient patient) {
        emit(EventType.ARRIVAL, patient, null,
                patient.getCode() + " llega al hospital (" + patient.getAge() + " años).");
        patient.assignTriage(triageService.classify(patient));
        if (patient.getTriageLevel().skipsTriageStation()) {
            emit(EventType.TRIAGE_STARTED, patient, null,
                    patient.getCode() + " llega en estado crítico y pasa directo, sin hacer fila en el triage.");
            classify(patient);
        } else {
            triageQueue.add(patient);
        }
    }

    /** Las estaciones de triage libres reciben, en orden de llegada, a los primeros de la fila. */
    private void startTriages() {
        for (TriageStation station : hospital.freeTriageStations()) {
            if (triageQueue.isEmpty()) {
                return;
            }
            Patient patient = triageQueue.remove(0);
            int minutes = patient.getTriageLevel().getAssessmentMinutes();
            station.assign(patient, minute + minutes);
            inTriage.add(new TriageInProgress(patient, station, minute + minutes));
            emit(EventType.TRIAGE_STARTED, patient, station.getCode(), patient.getCode() + " pasa al triage en "
                    + station.getCode() + " (" + minutes + " min).");
        }
    }

    /** Termina las valoraciones de triage que cumplieron su tiempo y libera sus estaciones. */
    private void finishTriages() {
        for (TriageInProgress triage : List.copyOf(inTriage)) {
            if (triage.endMinute() > minute) {
                continue;
            }
            inTriage.remove(triage);
            triage.station().release();
            classify(triage.patient());
        }
    }

    /**
     * Anuncia el nivel de triage y el diagnóstico del paciente y lo pasa a la sala de espera, o lo remite si
     * ningún médico del hospital puede tratarlo.
     */
    private void classify(Patient patient) {
        TriageLevel triage = patient.getTriageLevel();
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
            endFirstAid(patient);
            patient.finish(PatientStatus.UNATTENDED, minute);
            emit(EventType.UNATTENDED, patient, null,
                    patient.getCode() + " sale del hospital sin haber sido atendido.");
        }
    }

    /**
     * La estrategia reparte los recursos libres, contando con el personal que estaba dando primeros auxilios;
     * después, el personal que sobró da primeros auxilios a los críticos que siguen esperando.
     */
    private void assignResources() {
        Map<Patient, FirstAid> before = pauseFirstAid();
        while (true) {
            List<WaitingPatient> candidates = waiting.stream()
                    .map(patient -> new WaitingPatient(patient, diagnose(patient)))
                    .toList();
            Optional<Assignment> decision = strategy.nextAssignment(minute, candidates, hospital);
            if (decision.isEmpty()) {
                break;
            }
            startTreatment(decision.get());
        }
        giveFirstAid(before);
    }

    /** Deja libre a todo el personal que daba primeros auxilios y devuelve a quién atendía cada uno. */
    private Map<Patient, FirstAid> pauseFirstAid() {
        Map<Patient, FirstAid> before = new LinkedHashMap<>(firstAid);
        List.copyOf(firstAid.keySet()).forEach(this::endFirstAid);
        return before;
    }

    /**
     * Da primeros auxilios a los críticos que siguen esperando, del más crítico al menos, mientras quede personal
     * libre. Quien ya atendía a un paciente lo sigue atendiendo si quedó libre.
     */
    private void giveFirstAid(Map<Patient, FirstAid> before) {
        List<Patient> critical = waiting.stream().filter(this::isCritical).sorted(MOST_CRITICAL_FIRST).toList();
        for (Patient patient : critical) {
            FirstAid previous = before.get(patient);
            Optional<StaffMember> carer = carerFor(previous);
            if (carer.isEmpty()) {
                return;
            }
            carer.get().assign(patient, minute + 1);
            firstAid.put(patient, new FirstAid(carer.get()));
            if (previous == null) {
                emit(EventType.FIRST_AID, patient, null, patient.getCode() + " recibe primeros auxilios de "
                        + carer.get().getCode() + " mientras espera.");
            }
        }
    }

    /** Quien da los primeros auxilios: el mismo de antes si está libre; si no, un enfermero o un médico libre. */
    private Optional<StaffMember> carerFor(FirstAid previous) {
        if (previous != null && previous.carer().isAvailable()) {
            return Optional.of(previous.carer());
        }
        if (!hospital.freeNurses().isEmpty()) {
            return Optional.of(hospital.freeNurses().get(0));
        }
        return hospital.freeDoctors().stream().map(StaffMember.class::cast).findFirst();
    }

    /** Termina los primeros auxilios de un paciente, si los recibía, y deja libre a quien se los daba. */
    private void endFirstAid(Patient patient) {
        FirstAid aid = firstAid.remove(patient);
        if (aid != null) {
            releaseHeld(List.of(aid.carer()), patient);
        }
    }

    /** Un paciente que espera es crítico si es de triage I o si su salud ya bajó a la salud crítica. */
    private boolean isCritical(Patient patient) {
        return patient.getTriageLevel().skipsTriageStation() || patient.getHealth() <= CRITICAL_HEALTH;
    }

    private void startTreatment(Assignment assignment) {
        Patient patient = assignment.patient();
        requireValid(assignment);
        double effectiveness = assignment.doctor().effectivenessFor(patient.getDisease(), patient.getAge());
        int treatmentMinutes = healthModel.treatmentMinutes(patient, effectiveness);
        int endMinute = minute + treatmentMinutes;
        int staffEndMinute = minute + patient.getDisease().getTreatment().staffMinutes(treatmentMinutes);

        assignment.staff().forEach(member -> member.assign(patient, staffEndMinute));
        if (assignment.operatingRoom() != null) {
            assignment.operatingRoom().assign(patient, staffEndMinute);
        }
        if (assignment.bed() != null) {
            assignment.bed().assign(patient, endMinute);
        }
        if (assignment.bloodUnits() > 0) {
            hospital.getBloodBank().withdraw(patient.getBloodType(), assignment.bloodUnits());
        }
        waiting.remove(patient);
        active.add(new ActiveTreatment(patient, assignment, staffEndMinute, endMinute, effectiveness));
        treatmentStarts.put(patient, minute);
        careLevels.put(patient, assignment.doctor().compatibilityWith(patient.getDisease(), patient.getAge()));

        String place = placeOf(assignment);
        if (assignment.operatingRoom() == null && assignment.bed() != null && assignment.bed().isIcu()) {
            patient.admitToIcu();
            emit(EventType.ICU_ADMISSION, patient, assignment.bed().getCode(),
                    patient.getCode() + " ingresa a la UCI (" + assignment.bed().getCode() + ").");
        } else {
            patient.startTreatment();
        }
        String staffNote = "";
        if (staffEndMinute < endMinute) {
            staffNote = assignment.operatingRoom() != null
                    ? "; pasa a recuperación en el minuto " + staffEndMinute
                    : "; el médico queda libre en el minuto " + staffEndMinute;
        }
        emit(EventType.TREATMENT_STARTED, patient, place, patient.getCode() + " inicia tratamiento con "
                + assignment.doctor().getCode() + " (" + assignment.doctor().getSpecialty().getDisplayName()
                + "); termina en el minuto " + endMinute + staffNote + ".");
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
        if (assignment.bed() != null && !hospital.hasNursingCapacityFor(assignment.bed().isIcu())) {
            throw new IllegalStateException("La estrategia hospitalizó a " + patient.getCode()
                    + " sin enfermería suficiente para vigilarlo.");
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

    /**
     * Registra la muerte de un paciente en tratamiento. El cuerpo sigue ocupando su cama hasta pasar a la
     * morgue; quien muere en el quirófano no llegó a su cama de recuperación, así que esa cama queda libre.
     */
    private void declareDeathInTreatment(ActiveTreatment treatment, CauseOfDeath cause) {
        Assignment assignment = treatment.assignment();
        boolean inOperatingRoom = assignment.operatingRoom() != null && !recovering.contains(treatment.patient());
        Bed bodyBed = inOperatingRoom ? null : assignment.bed();
        List<Assignable> withoutBodyBed = assignment.resources().stream()
                .filter(resource -> resource != bodyBed)
                .toList();
        String place = placeOf(treatment);
        releaseHeld(withoutBodyBed, treatment.patient());
        recovering.remove(treatment.patient());
        declareDeath(treatment.patient(), cause, place, bodyBed);
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

    /** Lugar donde está el paciente en tratamiento: su cama si ya salió del quirófano a recuperación. */
    private String placeOf(ActiveTreatment treatment) {
        if (recovering.contains(treatment.patient())) {
            return treatment.assignment().bed().getCode();
        }
        return placeOf(treatment.assignment());
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
