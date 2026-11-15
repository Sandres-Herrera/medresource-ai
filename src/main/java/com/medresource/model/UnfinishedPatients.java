package com.medresource.model;

/**
 * Pacientes que seguían en el hospital al terminar el periodo de llegadas de la simulación.
 *
 * <p>Sin este dato una corrida corta parece mejor de lo que fue: el periodo termina, pero el hospital sigue
 * atendiendo a quienes ya llegaron.</p>
 *
 * @param periodEndMinute minuto en que terminó el periodo de llegadas
 * @param inTriage        pacientes en la fila del triage o en valoración en ese minuto
 * @param waiting         pacientes en la sala de espera en ese minuto
 * @param inTreatment     pacientes en tratamiento en ese minuto
 * @param extraMinutes    minutos que necesitó el hospital después del periodo para terminar con todos
 * @param unattended      pacientes que se fueron sin ser atendidos
 */
public record UnfinishedPatients(int periodEndMinute, int inTriage, int waiting, int inTreatment, int extraMinutes,
                                 int unattended) {

    /**
     * Valida los números.
     *
     * @throws IllegalArgumentException si algún número es negativo
     */
    public UnfinishedPatients {
        if (periodEndMinute < 0 || inTriage < 0 || waiting < 0 || inTreatment < 0 || extraMinutes < 0
                || unattended < 0) {
            throw new IllegalArgumentException("Los pacientes sin terminar no pueden ser negativos.");
        }
    }

    /**
     * Suma los pacientes que no habían terminado al cerrar el periodo de llegadas.
     *
     * @return pacientes en el triage, en espera o en tratamiento
     */
    public int total() {
        return inTriage + waiting + inTreatment;
    }

    /**
     * Suma los que todavía no habían empezado su tratamiento al cerrar el periodo de llegadas.
     *
     * @return pacientes en el triage o en espera
     */
    public int stillWaiting() {
        return inTriage + waiting;
    }
}
