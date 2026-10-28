package com.medresource.model;

import java.util.List;

/**
 * Resultado completo de una ejecución: qué estrategia se usó, sobre qué caso,
 * cómo terminó cada paciente y qué ocurrió minuto a minuto.
 *
 * <p>Es inmutable. El cálculo de métricas y la comparación entre estrategias
 * se hacen a partir de este objeto.</p>
 *
 * @param strategyName nombre de la estrategia usada
 * @param config       configuración del caso (semilla y escenario)
 * @param totalMinutes minutos que duró la simulación
 * @param outcomes     resultado de cada paciente, en orden de llegada
 * @param events       registro de eventos en orden cronológico
 */
public record SimulationResult(String strategyName, CaseConfig config, int totalMinutes,
                               List<PatientOutcome> outcomes, List<SimulationEvent> events) {

    /**
     * Valida los datos y guarda copias inmutables de las listas.
     *
     * @throws IllegalArgumentException si falta algún dato, si los minutos son
     *                                  negativos o si hay un resultado por paciente distinto de lo configurado
     */
    public SimulationResult {
        if (strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("El nombre de la estrategia es obligatorio.");
        }
        if (config == null || outcomes == null || events == null) {
            throw new IllegalArgumentException("Faltan datos del resultado de la simulación.");
        }
        if (totalMinutes < 0) {
            throw new IllegalArgumentException("La duración de la simulación no puede ser negativa.");
        }
        outcomes = List.copyOf(outcomes);
        events = List.copyOf(events);
        if (outcomes.size() != config.patientCount()) {
            throw new IllegalArgumentException("Debe haber un resultado por cada paciente del caso.");
        }
    }

    /**
     * Cuenta los pacientes que terminaron en un estado.
     *
     * @param status estado final
     * @return cantidad de pacientes con ese estado
     */
    public long count(PatientStatus status) {
        return outcomes.stream().filter(outcome -> outcome.status() == status).count();
    }

    /**
     * Cuenta los pacientes que fallecieron.
     *
     * @return cantidad de fallecidos
     */
    public long deaths() {
        return count(PatientStatus.DECEASED);
    }

    /**
     * Cuenta los fallecidos cuya muerte se pudo evitar con otra decisión.
     *
     * @return cantidad de muertes evitables
     */
    public long avoidableDeaths() {
        return outcomes.stream()
                .filter(outcome -> outcome.causeOfDeath() != null && outcome.causeOfDeath().isAvoidable())
                .count();
    }
}
