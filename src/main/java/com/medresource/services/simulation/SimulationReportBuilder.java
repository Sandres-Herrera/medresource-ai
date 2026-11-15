package com.medresource.services.simulation;

import com.medresource.model.Hospital;
import com.medresource.model.PatientOutcome;
import com.medresource.model.SimulationReport;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;

/**
 * Arma el reporte de fin de simulación con todos sus números, a partir del resultado, la traza y el hospital
 * de una ejecución terminada.
 *
 * <p>No calcula nada por sí mismo: le pide cada sección al analizador que sabe hacerla (métricas, triage,
 * fallecidos, cuellos de botella, recursos, personal, sangre y casos) y las junta.</p>
 */
public class SimulationReportBuilder {

    private final MetricsCalculator metrics = new MetricsCalculator();
    private final TriageAnalyzer triage = new TriageAnalyzer();
    private final OutcomeAnalyzer outcomes = new OutcomeAnalyzer();
    private final BottleneckAnalyzer bottlenecks = new BottleneckAnalyzer();
    private final ResourceUsageAnalyzer resources = new ResourceUsageAnalyzer();
    private final StaffLoadAnalyzer staff = new StaffLoadAnalyzer();
    private final BloodAnalyzer blood = new BloodAnalyzer();
    private final CaseSelector cases = new CaseSelector();

    /**
     * Arma el reporte de una ejecución terminada.
     *
     * @param result   resultado de la simulación
     * @param trace    traza de la misma simulación ({@link SimulationEngine#getTrace()})
     * @param hospital hospital que se simuló, tal como quedó al terminar
     * @return reporte con todos los números
     * @throws IllegalArgumentException si falta algún dato o si la traza no tiene la salud de llegada de todos
     *                                  los pacientes del resultado
     */
    public SimulationReport build(SimulationResult result, SimulationTrace trace, Hospital hospital) {
        if (result == null || trace == null || hospital == null) {
            throw new IllegalArgumentException("El resultado, la traza y el hospital son obligatorios.");
        }
        if (!trace.arrivalHealth().keySet().containsAll(
                result.outcomes().stream().map(PatientOutcome::patientCode).toList())) {
            throw new IllegalArgumentException("La traza no corresponde al resultado de la simulación.");
        }
        int treated = (int) result.outcomes().stream().filter(PatientOutcome::wasTreated).count();
        int maxWait = result.outcomes().stream().mapToInt(PatientOutcome::waitingMinutes).max().orElse(0);
        return new SimulationReport(result.config(), result.totalMinutes(), metrics.calculate(result), treated,
                maxWait, triage.analyze(result, trace), outcomes.deaths(result), outcomes.unfinished(result, trace),
                outcomes.failures(result, trace), bottlenecks.bottlenecks(trace), bottlenecks.collapses(trace),
                resources.analyze(trace, hospital), staff.staff(trace, hospital),
                staff.specialties(result, trace, hospital), blood.analyze(result, trace),
                cases.select(result, trace), ReportAssumptions.all());
    }
}
