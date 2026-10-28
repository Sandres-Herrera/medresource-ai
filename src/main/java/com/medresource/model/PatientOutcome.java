package com.medresource.model;

/**
 * Resultado final de un paciente al terminar la simulación.
 *
 * <p>Es una copia inmutable de los datos que necesitan las métricas y la base de
 * datos, de modo que el resultado no cambia aunque el paciente original sí.</p>
 *
 * @param patientId          identificador del paciente
 * @param patientCode        código visible, por ejemplo {@code P001}
 * @param age                edad en años
 * @param sex                sexo biológico
 * @param triage             nivel de triage asignado
 * @param disease            enfermedad real
 * @param arrivalMinute      minuto de llegada
 * @param treatmentStartMinute minuto en que empezó su tratamiento; {@code -1} si nunca lo recibió
 * @param outcomeMinute      minuto en que terminó su caso
 * @param status             estado final
 * @param causeOfDeath       causa de muerte; {@code null} si no falleció
 * @param careLevel          compatibilidad del médico que lo atendió; {@code null} si nunca fue atendido
 * @param irrecoverable      {@code true} si su mortalidad irreducible lo alcanzó
 * @param finalHealth        salud al terminar
 */
public record PatientOutcome(int patientId, String patientCode, int age, Sex sex, TriageLevel triage,
                             Disease disease, int arrivalMinute, int treatmentStartMinute, int outcomeMinute,
                             PatientStatus status, CauseOfDeath causeOfDeath, CompatibilityLevel careLevel,
                             boolean irrecoverable, double finalHealth) {

    /**
     * Valida que los datos sean coherentes.
     *
     * @throws IllegalArgumentException si falta algún dato, si el estado no es
     *                                  final, si la causa de muerte no coincide con el estado
     *                                  o si la atención no coincide con el inicio del tratamiento
     */
    public PatientOutcome {
        if (patientCode == null || sex == null || triage == null || disease == null || status == null) {
            throw new IllegalArgumentException("Faltan datos del resultado del paciente.");
        }
        if (!status.isFinal()) {
            throw new IllegalArgumentException("El estado del resultado debe ser final.");
        }
        if ((status == PatientStatus.DECEASED) != (causeOfDeath != null)) {
            throw new IllegalArgumentException("La causa de muerte debe existir solo si el paciente falleció.");
        }
        if (treatmentStartMinute < -1) {
            throw new IllegalArgumentException("El minuto de inicio del tratamiento no es válido.");
        }
        if ((treatmentStartMinute == -1) != (careLevel == null)) {
            throw new IllegalArgumentException("El nivel de atención debe existir solo si hubo tratamiento.");
        }
        if (outcomeMinute < arrivalMinute) {
            throw new IllegalArgumentException("El desenlace no puede ocurrir antes de la llegada.");
        }
    }

    /**
     * Resume un paciente que ya terminó su caso.
     *
     * @param patient              paciente con estado final
     * @param treatmentStartMinute minuto en que empezó su tratamiento, o {@code -1} si nunca lo recibió
     * @param careLevel            compatibilidad del médico que lo atendió, o {@code null} si no fue atendido
     * @return resultado inmutable del paciente
     * @throws IllegalStateException si el paciente todavía no terminó su caso
     */
    public static PatientOutcome of(Patient patient, int treatmentStartMinute, CompatibilityLevel careLevel) {
        if (!patient.isFinished()) {
            throw new IllegalStateException(patient.getCode() + " todavía no terminó su caso.");
        }
        return new PatientOutcome(patient.getId(), patient.getCode(), patient.getAge(), patient.getSex(),
                patient.getTriageLevel(), patient.getDisease(), patient.getArrivalMinute(), treatmentStartMinute,
                patient.getOutcomeMinute(), patient.getStatus(), patient.getCauseOfDeath(), careLevel,
                patient.isIrrecoverable(), patient.getHealth());
    }

    /**
     * Calcula cuántos minutos esperó el paciente antes de empezar su tratamiento.
     * Si nunca lo recibió, cuenta hasta que terminó su caso.
     *
     * @return minutos de espera
     */
    public int waitingMinutes() {
        int end = treatmentStartMinute >= 0 ? treatmentStartMinute : outcomeMinute;
        return end - arrivalMinute;
    }

    /**
     * Indica si el paciente fue atendido alguna vez.
     *
     * @return {@code true} si recibió tratamiento
     */
    public boolean wasTreated() {
        return treatmentStartMinute >= 0;
    }
}
