package com.medresource.model;

/**
 * Ocupación de un grupo de recursos durante la simulación.
 *
 * <p>Un grupo está subutilizado si en promedio se ocupó menos del {@value #UNDERUSED_PERCENT} % de su
 * capacidad (supuesto del reporte): son recursos que se podrían reasignar.</p>
 *
 * @param group            grupo de recursos
 * @param capacity         cuántos recursos tiene el hospital en ese grupo
 * @param averageOccupancy ocupación promedio de 0 a 1
 * @param peakBusy         mayor cantidad ocupada al mismo tiempo
 * @param fullMinutes      minutos en que estuvieron todos ocupados
 */
public record ResourceUsage(ResourceGroup group, int capacity, double averageOccupancy, int peakBusy,
                            int fullMinutes) {

    /** Ocupación promedio, en porcentaje, por debajo de la cual un grupo se considera subutilizado (supuesto). */
    public static final int UNDERUSED_PERCENT = 40;

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta el grupo, si la capacidad no es positiva, si la ocupación no
     *                                  está entre 0 y 1, o si se ocuparon más recursos de los que hay
     */
    public ResourceUsage {
        if (group == null) {
            throw new IllegalArgumentException("El grupo de recursos es obligatorio.");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("La capacidad debe ser positiva.");
        }
        if (averageOccupancy < 0 || averageOccupancy > 1) {
            throw new IllegalArgumentException("La ocupación promedio debe estar entre 0 y 1.");
        }
        if (peakBusy < 0 || peakBusy > capacity || fullMinutes < 0) {
            throw new IllegalArgumentException("La ocupación máxima debe estar entre 0 y la capacidad.");
        }
    }

    /**
     * Calcula la ocupación máxima.
     *
     * @return fracción de 0 a 1 de la capacidad ocupada en el peor momento
     */
    public double peakOccupancy() {
        return peakBusy / (double) capacity;
    }

    /**
     * Indica si en algún momento se ocupó todo el grupo.
     *
     * @return {@code true} si hubo minutos con todo ocupado
     */
    public boolean wasFull() {
        return fullMinutes > 0;
    }

    /**
     * Indica si el grupo estuvo subutilizado.
     *
     * @return {@code true} si la ocupación promedio quedó por debajo de {@value #UNDERUSED_PERCENT} %
     */
    public boolean isUnderused() {
        return averageOccupancy * 100 < UNDERUSED_PERCENT;
    }
}
