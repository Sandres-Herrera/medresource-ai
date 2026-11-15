package com.medresource.services.simulation;

import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientStatus;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.model.TriageLevel;
import com.medresource.model.TriageLevelStats;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcula los resultados de cada nivel de triage para el reporte: pacientes, fallecidos, esperas, cuántos se
 * atendieron dentro de la meta de su nivel y cuánta salud perdieron esperando.
 *
 * <p>Un paciente se atendió dentro de la meta si empezó su tratamiento a más tardar
 * {@link TriageLevel#getTargetMinutes()} minutos después de llegar. La salud perdida es la diferencia entre la
 * salud con la que llegó y la que tenía al empezar su tratamiento; si nunca lo empezó, la que tenía al terminar
 * su caso.</p>
 */
public class TriageAnalyzer {

    /**
     * Calcula los resultados de los cinco niveles de triage, del I al V; un nivel sin pacientes aparece en
     * cero.
     *
     * @param result resultado de la simulación
     * @param trace  traza de la misma simulación
     * @return resultados de cada nivel
     * @throws IllegalArgumentException si falta el resultado o la traza
     */
    public List<TriageLevelStats> analyze(SimulationResult result, SimulationTrace trace) {
        if (result == null || trace == null) {
            throw new IllegalArgumentException("El resultado y la traza son obligatorios.");
        }
        List<TriageLevelStats> stats = new ArrayList<>();
        for (TriageLevel level : TriageLevel.values()) {
            List<PatientOutcome> patients = result.outcomes().stream()
                    .filter(outcome -> outcome.triage() == level)
                    .toList();
            stats.add(statsOf(level, patients, trace));
        }
        return stats;
    }

    /**
     * Calcula la salud que perdió un paciente mientras esperaba su tratamiento.
     *
     * @param outcome resultado del paciente
     * @param trace   traza de la simulación
     * @return salud perdida, 0 o más
     */
    static double healthLost(PatientOutcome outcome, SimulationTrace trace) {
        double arrival = trace.arrivalHealthOf(outcome.patientCode());
        double atCare = trace.careOf(outcome.patientCode())
                .map(care -> care.healthAtStart())
                .orElse(outcome.finalHealth());
        return Math.max(0.0, arrival - atCare);
    }

    /**
     * Indica si un paciente empezó su tratamiento dentro de la meta de su nivel.
     *
     * @param outcome resultado del paciente
     * @return {@code true} si fue atendido a tiempo
     */
    static boolean treatedWithinTarget(PatientOutcome outcome) {
        return outcome.wasTreated() && outcome.waitingMinutes() <= outcome.triage().getTargetMinutes();
    }

    private static TriageLevelStats statsOf(TriageLevel level, List<PatientOutcome> patients, SimulationTrace trace) {
        int deaths = (int) patients.stream().filter(outcome -> outcome.status() == PatientStatus.DECEASED).count();
        int referred = (int) patients.stream().filter(outcome -> outcome.status() == PatientStatus.REFERRED).count();
        double averageWait = patients.stream().mapToInt(PatientOutcome::waitingMinutes).average().orElse(Double.NaN);
        int maxWait = patients.stream().mapToInt(PatientOutcome::waitingMinutes).max().orElse(0);
        int withinTarget = (int) patients.stream().filter(TriageAnalyzer::treatedWithinTarget).count();
        double averageLost = patients.stream().mapToDouble(outcome -> healthLost(outcome, trace)).average()
                .orElse(Double.NaN);
        return new TriageLevelStats(level, patients.size(), deaths, referred, averageWait, maxWait, withinTarget,
                averageLost);
    }
}
