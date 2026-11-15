package com.medresource.model;

/**
 * Resultados de los pacientes de un nivel de triage: cuántos llegaron, cuántos murieron, cuánto esperaron,
 * cuántos se atendieron dentro de la meta de su nivel y cuánta salud perdieron esperando.
 *
 * <p>Los promedios valen {@link Double#NaN} cuando el nivel no tuvo pacientes que los midan, para no
 * confundirlos con cero. Los remitidos cuentan como pacientes, pero no entran en la meta de espera porque
 * el hospital no podía atenderlos.</p>
 *
 * @param level              nivel de triage
 * @param patients           pacientes con este nivel
 * @param deaths             fallecidos
 * @param referred           remitidos a otra institución
 * @param averageWaitMinutes minutos promedio entre la llegada y el inicio del tratamiento
 * @param maxWaitMinutes     espera más larga; 0 si no hubo pacientes
 * @param withinTarget       pacientes que empezaron su tratamiento dentro de la meta del nivel
 * @param averageHealthLost  salud promedio que se perdió entre la llegada y el inicio del tratamiento (o el
 *                           final del caso, si nunca empezó)
 */
public record TriageLevelStats(TriageLevel level, int patients, int deaths, int referred, double averageWaitMinutes,
                               int maxWaitMinutes, int withinTarget, double averageHealthLost) {

    /**
     * Valida que los números sean coherentes.
     *
     * @throws IllegalArgumentException si falta el nivel, si hay números negativos o si una parte es mayor que
     *                                  su total
     */
    public TriageLevelStats {
        if (level == null) {
            throw new IllegalArgumentException("El nivel de triage es obligatorio.");
        }
        if (patients < 0 || deaths < 0 || referred < 0 || maxWaitMinutes < 0 || withinTarget < 0
                || averageWaitMinutes < 0 || averageHealthLost < 0) {
            throw new IllegalArgumentException("Los resultados de un nivel de triage no pueden ser negativos.");
        }
        if (deaths > patients || referred > patients || withinTarget > patients - referred) {
            throw new IllegalArgumentException("Una parte no puede ser mayor que los pacientes del nivel.");
        }
    }

    /**
     * Calcula la fracción de pacientes del nivel que murió.
     *
     * @return mortalidad de 0 a 1; {@link Double#NaN} si no hubo pacientes
     */
    public double mortality() {
        return patients == 0 ? Double.NaN : deaths / (double) patients;
    }

    /**
     * Calcula la fracción de pacientes que se atendió dentro de la meta, sin contar a los remitidos.
     *
     * @return fracción de 0 a 1; {@link Double#NaN} si no hubo a quién medir
     */
    public double withinTargetRate() {
        int measured = patients - referred;
        return measured == 0 ? Double.NaN : withinTarget / (double) measured;
    }
}
