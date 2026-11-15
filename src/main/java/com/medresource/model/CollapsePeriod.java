package com.medresource.model;

/**
 * Periodo en que el servicio estuvo colapsado: siempre hubo al menos un paciente esperando más allá de la meta
 * de su nivel de triage.
 *
 * @param startMinute     minuto en que empezó el colapso
 * @param endMinute       último minuto del colapso; el servicio se recuperó en el siguiente
 * @param peakOverTarget  mayor cantidad de pacientes fuera de su meta al mismo tiempo
 * @param peakMinute      primer minuto en que se alcanzó ese máximo
 */
public record CollapsePeriod(int startMinute, int endMinute, int peakOverTarget, int peakMinute) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si los minutos no van en orden o si el máximo no es positivo
     */
    public CollapsePeriod {
        if (startMinute < 0 || endMinute < startMinute || peakMinute < startMinute || peakMinute > endMinute) {
            throw new IllegalArgumentException("Los minutos de un colapso deben ir en orden.");
        }
        if (peakOverTarget < 1) {
            throw new IllegalArgumentException("En un colapso hay al menos un paciente fuera de su meta.");
        }
    }

    /**
     * Calcula cuánto duró el colapso.
     *
     * @return minutos, incluidos los extremos
     */
    public int durationMinutes() {
        return endMinute - startMinute + 1;
    }
}
