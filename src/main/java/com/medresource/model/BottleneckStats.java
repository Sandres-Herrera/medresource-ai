package com.medresource.model;

/**
 * Un recurso que hizo esperar a los pacientes: cuánta espera causó, a cuántos, desde cuándo y hasta cuándo.
 *
 * <p>Si a un paciente le faltaban dos recursos a la vez, su minuto de espera se le cuenta a los dos; por eso
 * la parte de cada recurso se calcula sobre la suma de los minutos de todos.</p>
 *
 * @param resource         recurso que faltaba
 * @param waitingMinutes   minutos-paciente de espera por este recurso
 * @param share            parte de 0 a 1 de toda la espera por recursos
 * @param patientsAffected pacientes que esperaron por él al menos un minuto
 * @param firstMinute      primer minuto en que le faltó a alguien
 * @param lastMinute       último minuto en que le faltó a alguien
 * @param peakPatients     mayor cantidad de pacientes esperándolo al mismo tiempo
 * @param peakMinute       primer minuto en que se alcanzó ese máximo
 */
public record BottleneckStats(MissingResource resource, int waitingMinutes, double share, int patientsAffected,
                              int firstMinute, int lastMinute, int peakPatients, int peakMinute) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta el recurso, si hay números negativos, si la parte no está
     *                                  entre 0 y 1 o si los minutos no van en orden
     */
    public BottleneckStats {
        if (resource == null) {
            throw new IllegalArgumentException("El recurso es obligatorio.");
        }
        if (waitingMinutes < 0 || patientsAffected < 0 || firstMinute < 0 || peakPatients < 0) {
            throw new IllegalArgumentException("Los datos de un cuello de botella no pueden ser negativos.");
        }
        if (share < 0 || share > 1) {
            throw new IllegalArgumentException("La parte de la espera debe estar entre 0 y 1.");
        }
        if (lastMinute < firstMinute || peakMinute < firstMinute || peakMinute > lastMinute) {
            throw new IllegalArgumentException("Los minutos de un cuello de botella deben ir en orden.");
        }
    }

    /**
     * Calcula cuántos minutos pasaron entre la primera y la última vez que faltó.
     *
     * @return duración del periodo en minutos, incluidos los extremos
     */
    public int spanMinutes() {
        return lastMinute - firstMinute + 1;
    }
}
