package com.medresource.services.simulation;

import com.medresource.model.CareFailures;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.DeathBreakdown;
import com.medresource.model.MinuteSample;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientStatus;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.model.UnfinishedPatients;

import java.util.EnumMap;
import java.util.Map;

/**
 * Calcula, para el reporte, cuándo murieron los fallecidos respecto a su atención, cuántos pacientes seguían en
 * el hospital al cerrar el periodo de llegadas y las fallas de la atención que no son muertes.
 *
 * <p>Un fallecido murió esperando si nunca empezó su tratamiento; murió después de una atención tardía si lo
 * empezó después de la meta de su nivel de triage; y en otro caso murió aunque se le atendió a tiempo. Un
 * paciente crítico no recibió primeros auxilios a tiempo si esperó sin ellos
 * {@value #FIRST_AID_TOLERANCE_MINUTES} minutos o más (supuesto del reporte).</p>
 */
public class OutcomeAnalyzer {

    /** Minutos que un crítico puede esperar sin primeros auxilios antes de contarse como falla (supuesto). */
    public static final int FIRST_AID_TOLERANCE_MINUTES = 5;

    /**
     * Clasifica a los fallecidos según cuándo murieron respecto a su atención.
     *
     * @param result resultado de la simulación
     * @return fallecidos esperando, atendidos tarde y atendidos a tiempo, con sus causas
     * @throws IllegalArgumentException si falta el resultado
     */
    public DeathBreakdown deaths(SimulationResult result) {
        requireResult(result);
        int whileWaiting = 0;
        int afterLateCare = 0;
        int despiteTimelyCare = 0;
        Map<CauseOfDeath, Integer> byCause = new EnumMap<>(CauseOfDeath.class);
        for (PatientOutcome outcome : result.outcomes()) {
            if (outcome.status() != PatientStatus.DECEASED) {
                continue;
            }
            byCause.merge(outcome.causeOfDeath(), 1, Integer::sum);
            if (!outcome.wasTreated()) {
                whileWaiting++;
            } else if (TriageAnalyzer.treatedWithinTarget(outcome)) {
                despiteTimelyCare++;
            } else {
                afterLateCare++;
            }
        }
        return new DeathBreakdown(whileWaiting, afterLateCare, despiteTimelyCare, byCause);
    }

    /**
     * Cuenta los pacientes que seguían en el hospital en el último minuto del periodo de llegadas.
     *
     * @param result resultado de la simulación
     * @param trace  traza de la misma simulación
     * @return pacientes sin terminar, minutos extra y pacientes que se fueron sin atención
     * @throws IllegalArgumentException si falta el resultado o la traza
     */
    public UnfinishedPatients unfinished(SimulationResult result, SimulationTrace trace) {
        requireResult(result);
        requireTrace(trace);
        int periodEnd = result.config().durationMinutes();
        int unattended = (int) result.count(PatientStatus.UNATTENDED);
        int extra = Math.max(0, result.totalMinutes() - periodEnd);
        int lastMinute = Math.min(periodEnd, trace.samples().size()) - 1;
        if (lastMinute < 0) {
            return new UnfinishedPatients(periodEnd, 0, 0, 0, extra, unattended);
        }
        MinuteSample sample = trace.samples().get(lastMinute);
        return new UnfinishedPatients(periodEnd, sample.inTriage(), sample.waiting(),
                sample.inTreatment(), extra, unattended);
    }

    /**
     * Cuenta las esperas por encima de la meta y los críticos que esperaron sin primeros auxilios.
     *
     * @param result resultado de la simulación
     * @param trace  traza de la misma simulación
     * @return fallas de la atención
     * @throws IllegalArgumentException si falta el resultado o la traza
     */
    public CareFailures failures(SimulationResult result, SimulationTrace trace) {
        requireResult(result);
        requireTrace(trace);
        int overTarget = (int) result.outcomes().stream()
                .filter(outcome -> outcome.status() != PatientStatus.REFERRED)
                .filter(outcome -> !TriageAnalyzer.treatedWithinTarget(outcome))
                .count();
        Map<String, Integer> withoutAid = trace.criticalWithoutAidMinutes();
        int late = (int) withoutAid.values().stream()
                .filter(minutes -> minutes >= FIRST_AID_TOLERANCE_MINUTES)
                .count();
        int longest = withoutAid.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new CareFailures(overTarget, late, longest);
    }

    private static void requireResult(SimulationResult result) {
        if (result == null) {
            throw new IllegalArgumentException("El resultado de la simulación es obligatorio.");
        }
    }

    private static void requireTrace(SimulationTrace trace) {
        if (trace == null) {
            throw new IllegalArgumentException("La traza de la simulación es obligatoria.");
        }
    }
}
