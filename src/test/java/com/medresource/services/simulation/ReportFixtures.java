package com.medresource.services.simulation;

import com.medresource.model.BloodType;
import com.medresource.model.CareRecord;
import com.medresource.model.CaseConfig;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.CompatibilityLevel;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.MinuteSample;
import com.medresource.model.MissingResource;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientStatus;
import com.medresource.model.Scenario;
import com.medresource.model.Sex;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos de prueba compartidos por las pruebas del reporte de fin de simulación.
 *
 * <p>No es una prueba: arma resultados de pacientes, fotos del hospital y trazas pequeñas a mano, para que
 * cada número del reporte se pueda calcular de cabeza.</p>
 */
final class ReportFixtures {

    static final Disease FLU = DiseaseCatalog.findById(1).orElseThrow();
    static final Disease APPENDICITIS = DiseaseCatalog.findById(8).orElseThrow();

    private ReportFixtures() {
    }

    /** Paciente que recibió tratamiento en el minuto {@code start} (o {@code -1} si nunca lo recibió). */
    static PatientOutcome outcome(int id, TriageLevel triage, int arrival, int start, int end, PatientStatus status,
                                  CauseOfDeath cause, double finalHealth) {
        return outcome(id, FLU, triage, arrival, start, end, status, cause, finalHealth);
    }

    static PatientOutcome outcome(int id, Disease disease, TriageLevel triage, int arrival, int start, int end,
                                  PatientStatus status, CauseOfDeath cause, double finalHealth) {
        return new PatientOutcome(id, code(id), 40, Sex.MALE, triage, disease, arrival, start, end, status, cause,
                start >= 0 ? CompatibilityLevel.PRIMARY : null, false, finalHealth);
    }

    static String code(int id) {
        return String.format("P%03d", id);
    }

    /** Resultado de una ejecución con los pacientes dados, sin eventos. */
    static SimulationResult result(int durationMinutes, int totalMinutes, PatientOutcome... outcomes) {
        CaseConfig config = new CaseConfig(1L, Scenario.NORMAL, outcomes.length, durationMinutes);
        return new SimulationResult("Prioridad clínica", config, totalMinutes, List.of(outcomes), List.of());
    }

    /** Foto vacía de un minuto: nadie en el hospital y nada ocupado. */
    static MinuteSample emptySample(int minute) {
        return new MinuteSample(minute, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of());
    }

    /** Fotos vacías desde el minuto 0 hasta {@code count - 1}. */
    static List<MinuteSample> emptySamples(int count) {
        List<MinuteSample> samples = new ArrayList<>();
        for (int minute = 0; minute < count; minute++) {
            samples.add(emptySample(minute));
        }
        return samples;
    }

    /** Foto de un minuto con pacientes esperando por recursos. */
    static MinuteSample blockedSample(int minute, int waiting, int overTarget, Map<MissingResource, Integer> blocked) {
        return new MinuteSample(minute, 0, waiting, 0, overTarget, 0, 0, 0, 0, 0, 0, blocked, Map.of());
    }

    /** Atención con un médico general en una consulta, sin cama ni sangre. */
    static CareRecord care(int id, int start, double healthAtStart) {
        return new CareRecord(code(id), BloodType.O_POSITIVE, start, start + 30, start + 30, "D01",
                Specialty.GENERAL_MEDICINE, List.of(), null, null, false, Map.of(), healthAtStart);
    }

    /** Traza con la salud al llegar, las atenciones y las fotos indicadas; lo demás vacío. */
    static SimulationTrace trace(Map<String, Double> arrivalHealth, List<CareRecord> cares,
                                 List<MinuteSample> samples) {
        return new SimulationTrace(arrivalHealth, cares, samples, Map.of(), Map.of(), Map.of(), Map.of());
    }

    /** Salud de llegada igual para los pacientes 1 a {@code count}. */
    static Map<String, Double> arrivalHealth(int count, double health) {
        Map<String, Double> map = new HashMap<>();
        for (int id = 1; id <= count; id++) {
            map.put(code(id), health);
        }
        return map;
    }
}
