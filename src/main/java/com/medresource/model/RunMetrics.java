package com.medresource.model;

/**
 * Números que resumen una ejecución: cuántos pacientes murieron, cuánto esperaron y cuántos años de
 * vida se salvaron.
 *
 * <p>Se calculan con {@code MetricsCalculator} a partir de un {@link SimulationResult}. Los promedios de
 * espera y las mortalidades de un grupo valen {@link Double#NaN} cuando el grupo no tiene pacientes en esa
 * ejecución (por ejemplo, si nadie llegó con triage I o II): así no se confunden con "cero minutos".</p>
 *
 * @param strategyName             nombre de la estrategia que se usó
 * @param patients                 pacientes del caso
 * @param deaths                   fallecidos
 * @param avoidableDeaths          fallecidos cuya muerte se pudo evitar con otra decisión
 * @param survivors                pacientes dados de alta o estabilizados sin cura
 * @param referred                 pacientes remitidos a otro hospital
 * @param unattended               pacientes que nunca fueron atendidos
 * @param averageWaitMinutes       minutos promedio que esperaron los pacientes antes de su tratamiento
 * @param urgentAverageWaitMinutes minutos promedio que esperaron los pacientes de triage I o II
 * @param lifeYearsSaved           suma de los años de vida que le quedaban a cada sobreviviente
 * @param minors                   pacientes menores de {@link Person#MINOR_AGE_LIMIT} años
 * @param minorDeaths              menores fallecidos
 * @param elderly                  pacientes de {@link HealthModel#ELDERLY_AGE_LIMIT} años o más
 * @param elderlyDeaths            adultos mayores fallecidos
 */
public record RunMetrics(String strategyName, int patients, int deaths, int avoidableDeaths, int survivors,
                         int referred, int unattended, double averageWaitMinutes,
                         double urgentAverageWaitMinutes, double lifeYearsSaved, int minors, int minorDeaths,
                         int elderly, int elderlyDeaths) {

    /**
     * Valida que los números sean coherentes.
     *
     * @throws IllegalArgumentException si falta el nombre, si hay números negativos, si una parte es
     *                                  mayor que su total o si los desenlaces suman más que los pacientes
     */
    public RunMetrics {
        if (strategyName == null || strategyName.isBlank()) {
            throw new IllegalArgumentException("El nombre de la estrategia es obligatorio.");
        }
        if (patients < 0 || deaths < 0 || avoidableDeaths < 0 || survivors < 0 || referred < 0 || unattended < 0
                || minors < 0 || minorDeaths < 0 || elderly < 0 || elderlyDeaths < 0 || lifeYearsSaved < 0) {
            throw new IllegalArgumentException("Las métricas no pueden ser negativas.");
        }
        requireDurationOrNaN(averageWaitMinutes);
        requireDurationOrNaN(urgentAverageWaitMinutes);
        if (avoidableDeaths > deaths || minorDeaths > minors || elderlyDeaths > elderly
                || minorDeaths > deaths || elderlyDeaths > deaths) {
            throw new IllegalArgumentException("Una parte de las métricas no puede ser mayor que su total.");
        }
        if (deaths + survivors + referred + unattended > patients) {
            throw new IllegalArgumentException("Los desenlaces no pueden sumar más que los pacientes.");
        }
    }

    private static void requireDurationOrNaN(double minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("Las métricas no pueden ser negativas.");
        }
    }

    /**
     * Calcula la fracción de pacientes que murió.
     *
     * @return mortalidad de 0 a 1; 0 si no había pacientes
     */
    public double mortalityRate() {
        return patients == 0 ? 0.0 : deaths / (double) patients;
    }

    /**
     * Calcula la fracción de menores que murió.
     *
     * @return mortalidad de los menores de 0 a 1; {@link Double#NaN} si no había menores
     */
    public double minorMortality() {
        return minors == 0 ? Double.NaN : minorDeaths / (double) minors;
    }

    /**
     * Calcula la fracción de adultos mayores que murió.
     *
     * @return mortalidad de los adultos mayores de 0 a 1; {@link Double#NaN} si no había adultos mayores
     */
    public double elderlyMortality() {
        return elderly == 0 ? Double.NaN : elderlyDeaths / (double) elderly;
    }
}