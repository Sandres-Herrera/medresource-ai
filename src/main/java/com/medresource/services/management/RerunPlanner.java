package com.medresource.services.management;

import com.medresource.model.HospitalConfig;
import com.medresource.model.RerunPlan;
import com.medresource.model.ResourceChange;
import com.medresource.model.RunRecord;
import com.medresource.model.SavedReport;
import com.medresource.model.WhatIfResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Prepara una simulación nueva del caso de una ejecución guardada, para compararla después con la original.
 *
 * <p>El caso (semilla, escenario, pacientes y duración) es siempre el de la ejecución guardada, así la simulación
 * nueva tiene los mismos pacientes. Lo que cambia es el hospital o la estrategia: se puede repetir tal cual, para
 * que el usuario haga sus propios cambios, o con las recomendaciones del reporte ya aplicadas.</p>
 *
 * <p>Las recomendaciones se aplican en el orden del reporte (la que más mejora primero). Un cambio de recursos se
 * aplica con su especialista incluido; si ya no cabe en el hospital (por ejemplo, porque se llegó al máximo de
 * médicos extra) se deja fuera. De las estrategias recomendadas se usa solo la primera.</p>
 */
public class RerunPlanner {

    private final SimulationService runs;

    /**
     * Crea el planificador.
     *
     * @param runs acceso a las ejecuciones guardadas
     * @throws IllegalArgumentException si es nulo
     */
    public RerunPlanner(SimulationService runs) {
        if (runs == null) {
            throw new IllegalArgumentException("El servicio de ejecuciones es obligatorio.");
        }
        this.runs = runs;
    }

    /**
     * Prepara la simulación del mismo caso con el mismo hospital y la misma estrategia.
     *
     * @param saved reporte guardado de la ejecución
     * @return plan sin cambios
     * @throws IllegalArgumentException si falta el reporte o su ejecución ya no está guardada
     */
    public RerunPlan sameCase(SavedReport saved) {
        RunRecord run = runOf(saved);
        return new RerunPlan(saved.resultId(), run.result().config(), run.hospital(), run.result().strategyName(),
                List.of());
    }

    /**
     * Prepara la simulación del mismo caso con las recomendaciones del reporte aplicadas.
     *
     * @param saved reporte guardado de la ejecución
     * @return plan con los cambios recomendados; sin cambios si el reporte no recomendó nada
     * @throws IllegalArgumentException si falta el reporte o su ejecución ya no está guardada
     */
    public RerunPlan withRecommendations(SavedReport saved) {
        RunRecord run = runOf(saved);
        HospitalConfig hospital = run.hospital();
        String strategy = run.result().strategyName();
        boolean strategyChanged = false;
        List<String> changes = new ArrayList<>();
        for (WhatIfResult recommendation : saved.report().whatIf().recommendations()) {
            if (recommendation.isPolicy()) {
                if (!strategyChanged && !recommendation.strategyName().equals(strategy)) {
                    strategy = recommendation.strategyName();
                    strategyChanged = true;
                    changes.add("Estrategia: " + strategy);
                }
            } else {
                ResourceChange change = recommendation.change();
                if (change.fitsWithDoctorsIn(hospital)) {
                    hospital = change.applyWithDoctorsTo(hospital);
                    changes.add(change.describe());
                }
            }
        }
        return new RerunPlan(saved.resultId(), run.result().config(), hospital, strategy, changes);
    }

    private RunRecord runOf(SavedReport saved) {
        if (saved == null) {
            throw new IllegalArgumentException("El reporte guardado es obligatorio.");
        }
        return runs.find(saved.resultId()).orElseThrow(() -> new IllegalArgumentException(
                "La ejecución " + saved.resultId() + " de ese reporte ya no está guardada."));
    }
}
