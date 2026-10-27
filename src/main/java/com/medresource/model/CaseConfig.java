package com.medresource.model;

/**
 * Datos que definen un caso de simulación: la semilla, el escenario y el
 * tamaño de la demanda.
 *
 * <p>Con la misma configuración el generador produce siempre los mismos
 * pacientes, de modo que todas las estrategias se comparan sobre el mismo
 * caso. Los recursos del hospital se configuran aparte.</p>
 *
 * @param seed            semilla del generador aleatorio; cualquier número
 * @param scenario        forma en que llegan los pacientes
 * @param patientCount    cantidad de pacientes que llegan, de {@value #MIN_PATIENTS}
 *                        a {@value #MAX_PATIENTS}
 * @param durationMinutes minutos durante los cuales pueden llegar pacientes,
 *                        de {@value #MIN_DURATION_MINUTES} a {@value #MAX_DURATION_MINUTES}
 */
public record CaseConfig(long seed, Scenario scenario, int patientCount, int durationMinutes) {

    /** Cantidad mínima de pacientes de un caso. */
    public static final int MIN_PATIENTS = 1;

    /** Cantidad máxima de pacientes de un caso. */
    public static final int MAX_PATIENTS = 1000;

    /** Duración mínima de un caso, en minutos. */
    public static final int MIN_DURATION_MINUTES = 60;

    /** Duración máxima de un caso, en minutos (un día). */
    public static final int MAX_DURATION_MINUTES = 1440;

    /** Pacientes de un caso cuando no se indica otra cantidad. */
    public static final int DEFAULT_PATIENTS = 100;

    /** Duración de un caso cuando no se indica otra (ocho horas). */
    public static final int DEFAULT_DURATION_MINUTES = 480;

    /**
     * Valida los datos de la configuración.
     *
     * @throws IllegalArgumentException si el escenario es nulo o si la cantidad
     *                                  de pacientes o la duración están fuera de rango
     */
    public CaseConfig {
        if (scenario == null) {
            throw new IllegalArgumentException("El escenario es obligatorio.");
        }
        if (patientCount < MIN_PATIENTS || patientCount > MAX_PATIENTS) {
            throw new IllegalArgumentException(
                    "La cantidad de pacientes debe estar entre " + MIN_PATIENTS + " y " + MAX_PATIENTS + ".");
        }
        if (durationMinutes < MIN_DURATION_MINUTES || durationMinutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException(
                    "La duración debe estar entre " + MIN_DURATION_MINUTES + " y " + MAX_DURATION_MINUTES + " minutos.");
        }
    }

    /**
     * Crea una configuración con la cantidad de pacientes y la duración por defecto.
     *
     * @param seed     semilla del generador
     * @param scenario escenario de llegada
     * @return configuración con {@value #DEFAULT_PATIENTS} pacientes en
     *         {@value #DEFAULT_DURATION_MINUTES} minutos
     */
    public static CaseConfig of(long seed, Scenario scenario) {
        return new CaseConfig(seed, scenario, DEFAULT_PATIENTS, DEFAULT_DURATION_MINUTES);
    }
}