package com.medresource.model;

/**
 * Paciente representativo de lo que pasó en la simulación, para revisarlo en el reporte y verlo en el plano.
 *
 * @param patientCode código del paciente
 * @param reason      por qué se eligió
 * @param triage      nivel de triage
 * @param waitMinutes minutos que esperó antes de su tratamiento (o hasta el final de su caso)
 * @param healthLost  salud que perdió mientras esperaba
 * @param status      cómo terminó
 * @param focusMinute minuto que conviene ver en el plano (su muerte, el inicio de su tratamiento o cuando pasó
 *                    la meta)
 */
public record ReviewCase(String patientCode, ReviewReason reason, TriageLevel triage, int waitMinutes,
                         double healthLost, PatientStatus status, int focusMinute) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta algún dato, si el estado no es final o si hay números negativos
     */
    public ReviewCase {
        if (patientCode == null || patientCode.isBlank() || reason == null || triage == null || status == null) {
            throw new IllegalArgumentException("Faltan datos del caso para revisar.");
        }
        if (!status.isFinal()) {
            throw new IllegalArgumentException("Un caso para revisar ya debe haber terminado.");
        }
        if (waitMinutes < 0 || healthLost < 0 || focusMinute < 0) {
            throw new IllegalArgumentException("Los datos de un caso para revisar no pueden ser negativos.");
        }
    }
}
