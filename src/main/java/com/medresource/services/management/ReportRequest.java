package com.medresource.services.management;

import com.medresource.model.CaseConfig;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.services.strategy.AllocationStrategy;

import java.util.function.Supplier;

/**
 * Lo que hace falta para generar el reporte de una simulación que ya terminó.
 *
 * <p>El resultado, la traza y el hospital son los de la simulación que se vio; la configuración del hospital y la
 * estrategia sirven para volver a simular el mismo caso con un recurso más o con otra estrategia.</p>
 *
 * @param result         resultado de la simulación
 * @param trace          traza de la misma simulación
 * @param hospital       hospital con el que se simuló
 * @param hospitalConfig recursos de ese hospital
 * @param strategy       crea la estrategia con la que se simuló, una nueva cada vez
 * @param baseRunId      ejecución guardada en la que se basa la simulación (por ejemplo, la que se volvió a
 *                       simular para probar sus recomendaciones); {@code null} si es una simulación nueva
 */
public record ReportRequest(SimulationResult result, SimulationTrace trace, Hospital hospital,
                            HospitalConfig hospitalConfig, Supplier<AllocationStrategy> strategy, Integer baseRunId) {

    /**
     * Valida que no falte nada.
     *
     * @throws IllegalArgumentException si falta algún dato o si la ejecución base no es positiva
     */
    public ReportRequest {
        if (result == null || trace == null || hospital == null || hospitalConfig == null || strategy == null) {
            throw new IllegalArgumentException("Faltan datos de la simulación para generar el reporte.");
        }
        if (baseRunId != null && baseRunId < 1) {
            throw new IllegalArgumentException("El identificador de la ejecución base debe ser positivo.");
        }
    }

    /**
     * Crea el pedido de una simulación nueva, que no se basa en otra ejecución.
     *
     * @param result         resultado de la simulación
     * @param trace          traza de la misma simulación
     * @param hospital       hospital con el que se simuló
     * @param hospitalConfig recursos de ese hospital
     * @param strategy       crea la estrategia con la que se simuló
     * @throws IllegalArgumentException si falta algún dato
     */
    public ReportRequest(SimulationResult result, SimulationTrace trace, Hospital hospital,
                         HospitalConfig hospitalConfig, Supplier<AllocationStrategy> strategy) {
        this(result, trace, hospital, hospitalConfig, strategy, null);
    }

    /**
     * Devuelve el caso que se simuló: escenario, semilla, pacientes y duración.
     *
     * @return el caso
     */
    public CaseConfig caseConfig() {
        return result.config();
    }
}
