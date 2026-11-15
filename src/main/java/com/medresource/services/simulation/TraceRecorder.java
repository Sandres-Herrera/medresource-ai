package com.medresource.services.simulation;

import com.medresource.model.Assignable;
import com.medresource.model.Bed;
import com.medresource.model.BloodType;
import com.medresource.model.CareRecord;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.MinuteSample;
import com.medresource.model.MissingResource;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.Patient;
import com.medresource.model.SimulationTrace;
import com.medresource.model.TriageStation;
import com.medresource.services.strategy.Assignment;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Va armando la {@link SimulationTrace} de una ejecución mientras el motor simula.
 *
 * <p>Solo lee el estado del hospital y de los pacientes; nunca lo cambia, así que registrar la traza no
 * altera el resultado. El motor le avisa cuando llega un paciente, cuando empieza una atención, qué le
 * faltaba a cada paciente después del reparto y cuando termina cada minuto.</p>
 *
 * <p>Un paciente en la fila del triage espera por una {@link MissingResource#TRIAGE_STATION}; uno que ya
 * está en valoración no espera por nada. Un paciente pasó la meta de su nivel de triage si lleva más
 * minutos sin tratamiento que {@link com.medresource.model.TriageLevel#getTargetMinutes()}.</p>
 */
class TraceRecorder {

    private final Hospital hospital;
    private final Map<BloodType, Integer> initialBlood;
    private final Map<String, Double> arrivalHealth = new LinkedHashMap<>();
    private final List<CareRecord> cares = new ArrayList<>();
    private final List<MinuteSample> samples = new ArrayList<>();
    private final Map<String, Map<MissingResource, Integer>> blockedMinutes = new HashMap<>();
    private final Map<String, Integer> busyMinutes = new HashMap<>();
    private final Map<String, Integer> criticalWithoutAid = new HashMap<>();
    private Map<Patient, Set<MissingResource>> blockedNow = Map.of();

    /**
     * Prepara la traza de un hospital que todavía no empezó a simular.
     *
     * @param hospital hospital de la ejecución, con su sangre inicial
     * @throws IllegalArgumentException si el hospital es nulo
     */
    TraceRecorder(Hospital hospital) {
        if (hospital == null) {
            throw new IllegalArgumentException("El hospital es obligatorio.");
        }
        this.hospital = hospital;
        this.initialBlood = hospital.getBloodBank().getUnits();
    }

    /** Guarda la salud con la que llegó un paciente. */
    void arrival(Patient patient) {
        arrivalHealth.put(patient.getCode(), patient.getHealth());
    }

    /**
     * Registra el inicio de una atención.
     *
     * @param assignment     recursos que se le dieron al paciente
     * @param startMinute    minuto de inicio
     * @param staffEndMinute minuto en que se libera el personal
     * @param endMinute      minuto en que termina el tratamiento
     * @param bloodTaken     unidades retiradas por tipo de donante
     * @param healthAtStart  salud del paciente al empezar
     */
    void careStarted(Assignment assignment, int startMinute, int staffEndMinute, int endMinute,
                     Map<BloodType, Integer> bloodTaken, double healthAtStart) {
        Patient patient = assignment.patient();
        cares.add(new CareRecord(patient.getCode(), patient.getBloodType(), startMinute, staffEndMinute, endMinute,
                assignment.doctor().getCode(), assignment.doctor().getSpecialty(),
                assignment.nurses().stream().map(Nurse::getCode).toList(),
                assignment.operatingRoom() == null ? null : assignment.operatingRoom().getCode(),
                assignment.bed() == null ? null : assignment.bed().getCode(),
                assignment.bed() != null && assignment.bed().isIcu(), bloodTaken, healthAtStart));
    }

    /**
     * Anota qué le faltaba a cada paciente de la sala de espera justo después del reparto, antes de que el
     * personal libre se vaya a dar primeros auxilios.
     *
     * @param waiting pacientes que siguen esperando
     * @param missing qué le falta a un paciente
     */
    void blockedAfterAssignment(List<Patient> waiting, Function<Patient, Set<MissingResource>> missing) {
        Map<Patient, Set<MissingResource>> now = new HashMap<>();
        for (Patient patient : waiting) {
            now.put(patient, missing.apply(patient));
        }
        blockedNow = now;
    }

    /**
     * Cierra un minuto: suma los minutos de espera por recurso y los de ocupación, y guarda la foto del
     * hospital.
     *
     * @param minute             minuto que termina
     * @param triageQueue        pacientes en la fila del triage
     * @param inTriage           pacientes en valoración
     * @param waiting            pacientes en la sala de espera
     * @param inTreatment        pacientes en tratamiento
     * @param lacksFirstAid      indica si un paciente de la sala de espera es crítico y nadie le da primeros
     *                           auxilios
     */
    void minuteEnded(int minute, List<Patient> triageQueue, List<Patient> inTriage, List<Patient> waiting,
                     int inTreatment, Predicate<Patient> lacksFirstAid) {
        Map<MissingResource, Integer> blocked = new EnumMap<>(MissingResource.class);
        for (Patient patient : triageQueue) {
            addBlocked(patient, Set.of(MissingResource.TRIAGE_STATION), blocked);
        }
        for (Patient patient : waiting) {
            addBlocked(patient, blockedNow.getOrDefault(patient, Set.of()), blocked);
            if (lacksFirstAid.test(patient)) {
                criticalWithoutAid.merge(patient.getCode(), 1, Integer::sum);
            }
        }
        int overTarget = countOverTarget(triageQueue, minute) + countOverTarget(inTriage, minute)
                + countOverTarget(waiting, minute);
        countBusy(hospital.getTriageStations(), TriageStation::getCode);
        samples.add(new MinuteSample(minute, triageQueue.size() + inTriage.size(), waiting.size(), inTreatment,
                overTarget, countBusy(hospital.getBeds(), Bed::getCode),
                countBusy(hospital.getIcuBeds(), Bed::getCode),
                countBusy(hospital.getOperatingRooms(), OperatingRoom::getCode),
                countBusy(hospital.getDoctors(), Doctor::getCode), countBusy(hospital.getNurses(), Nurse::getCode),
                hospital.getMorgue().occupancy(), blocked, hospital.getBloodBank().getUnits()));
    }

    /**
     * Entrega la traza registrada hasta ahora.
     *
     * @return traza inmutable
     */
    SimulationTrace toTrace() {
        return new SimulationTrace(arrivalHealth, cares, samples, blockedMinutes, busyMinutes, criticalWithoutAid,
                initialBlood);
    }

    private void addBlocked(Patient patient, Set<MissingResource> resources, Map<MissingResource, Integer> blocked) {
        for (MissingResource resource : resources) {
            blocked.merge(resource, 1, Integer::sum);
            blockedMinutes.computeIfAbsent(patient.getCode(), code -> new EnumMap<>(MissingResource.class))
                    .merge(resource, 1, Integer::sum);
        }
    }

    private static int countOverTarget(List<Patient> patients, int minute) {
        return (int) patients.stream()
                .filter(patient -> patient.waitingTime(minute) > patient.getTriageLevel().getTargetMinutes())
                .count();
    }

    /** Cuenta los ocupados y suma un minuto de ocupación a cada uno. */
    private <T extends Assignable> int countBusy(List<T> resources, Function<T, String> code) {
        int busy = 0;
        for (T resource : resources) {
            if (!resource.isAvailable()) {
                busy++;
                busyMinutes.merge(code.apply(resource), 1, Integer::sum);
            }
        }
        return busy;
    }
}
