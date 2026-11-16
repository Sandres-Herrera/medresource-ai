package com.medresource.model;

import java.util.List;

/**
 * Comparaciones de ejemplo para las pruebas de acceso a datos y de servicios.
 *
 * <p>No es una prueba: arma a mano una comparación de estrategias del caso 1 entre dos ejecuciones dadas.</p>
 */
public final class ComparisonFixtures {

    private ComparisonFixtures() {
    }

    /**
     * Arma la comparación de dos ejecuciones del caso 1: FIFO contra prioridad clínica.
     *
     * @param runA ejecución de referencia
     * @param runB ejecución que se compara
     * @return la comparación
     */
    public static RunComparison strategies(int runA, int runB) {
        return new RunComparison(1, runA, runB, "FIFO (orden de llegada)", "Prioridad clínica (triage)",
                ComparisonType.STRATEGY,
                List.of(new SettingChange("Estrategia", "FIFO (orden de llegada)", "Prioridad clínica (triage)")),
                List.of(new IndicatorComparison("Pacientes atendidos", "", 80, 92, false),
                        new IndicatorComparison("Fallecidos", "", 8, 4, true),
                        new IndicatorComparison("Espera promedio", "min", 35.5, 22.25, true)),
                "La ejecución #" + runB + " mejoró frente a la #" + runA + ".",
                List.of("Se compara un solo caso."));
    }
}
