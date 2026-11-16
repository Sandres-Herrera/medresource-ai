package com.medresource.model;

/**
 * Indicadores de una ejecución que se usan para comparar ejecuciones del mismo caso.
 *
 * <p>Tienen las mismas definiciones que el reporte y que las funciones PL/SQL de la base de datos, para que el
 * número sea el mismo en la aplicación y en SQL Developer:</p>
 * <ul>
 *   <li><b>Atendidos:</b> pacientes que empezaron a recibir atención (inicio de atención distinto de −1).</li>
 *   <li><b>Sin atención:</b> pacientes que terminaron con estado {@link PatientStatus#UNATTENDED}.</li>
 *   <li><b>Espera:</b> minutos desde la llegada hasta el inicio de la atención; si nunca lo atendieron, hasta su
 *       desenlace ({@link PatientOutcome#waitingMinutes()}).</li>
 * </ul>
 *
 * @param patients     pacientes del caso
 * @param attended     pacientes que empezaron a recibir atención
 * @param deaths       pacientes que fallecieron
 * @param unattended   pacientes que se quedaron sin atención
 * @param averageWait  espera promedio, en minutos
 * @param maxWait      espera más larga, en minutos
 */
public record RunIndicators(int patients, int attended, int deaths, int unattended, double averageWait,
                            int maxWait) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si algún conteo es negativo o pasa de los pacientes, o si una espera es
     *                                  negativa
     */
    public RunIndicators {
        if (patients < 1) {
            throw new IllegalArgumentException("Una ejecución tiene al menos un paciente.");
        }
        for (int count : new int[]{attended, deaths, unattended}) {
            if (count < 0 || count > patients) {
                throw new IllegalArgumentException("Los conteos deben estar entre 0 y la cantidad de pacientes.");
            }
        }
        if (averageWait < 0 || maxWait < 0 || !Double.isFinite(averageWait)) {
            throw new IllegalArgumentException("Las esperas no pueden ser negativas.");
        }
    }

    /**
     * Calcula los indicadores del resultado de una ejecución.
     *
     * @param result resultado de la ejecución
     * @return sus indicadores
     * @throws IllegalArgumentException si el resultado es nulo
     */
    public static RunIndicators of(SimulationResult result) {
        if (result == null) {
            throw new IllegalArgumentException("El resultado es obligatorio.");
        }
        int attended = (int) result.outcomes().stream().filter(PatientOutcome::wasTreated).count();
        double average = result.outcomes().stream().mapToInt(PatientOutcome::waitingMinutes).average().orElse(0);
        int max = result.outcomes().stream().mapToInt(PatientOutcome::waitingMinutes).max().orElse(0);
        return new RunIndicators(result.outcomes().size(), attended, (int) result.count(PatientStatus.DECEASED),
                (int) result.count(PatientStatus.UNATTENDED), average, max);
    }

    /**
     * Calcula la mortalidad.
     *
     * @return fallecidos sobre pacientes, en porcentaje
     */
    public double mortalityPercent() {
        return 100.0 * deaths / patients;
    }

    /**
     * Calcula qué parte de los pacientes empezó a recibir atención.
     *
     * @return atendidos sobre pacientes, en porcentaje
     */
    public double attendedPercent() {
        return 100.0 * attended / patients;
    }
}
