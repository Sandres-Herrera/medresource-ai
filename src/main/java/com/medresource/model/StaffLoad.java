package com.medresource.model;

/**
 * Carga de trabajo de un médico o un enfermero durante la simulación.
 *
 * <p>Un integrante del personal está saturado si estuvo ocupado el {@value #SATURATED_PERCENT} % del tiempo o
 * más, y libre si estuvo ocupado menos del {@value #IDLE_PERCENT} % (supuestos del reporte). El tiempo ocupado
 * incluye los tratamientos y los primeros auxilios.</p>
 *
 * @param code         código, por ejemplo {@code D03} o {@code E07}
 * @param specialty    especialidad del médico; {@code null} si es un enfermero
 * @param patients     pacientes cuyo tratamiento acompañó
 * @param busyMinutes  minutos que estuvo ocupado
 * @param occupancy    fracción de 0 a 1 del tiempo de la simulación que estuvo ocupado
 */
public record StaffLoad(String code, Specialty specialty, int patients, int busyMinutes, double occupancy) {

    /** Porcentaje de tiempo ocupado desde el que alguien se considera saturado (supuesto). */
    public static final int SATURATED_PERCENT = 85;

    /** Porcentaje de tiempo ocupado por debajo del cual alguien se considera libre (supuesto). */
    public static final int IDLE_PERCENT = 25;

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta el código, si hay números negativos o si la ocupación no está
     *                                  entre 0 y 1
     */
    public StaffLoad {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código es obligatorio.");
        }
        if (patients < 0 || busyMinutes < 0) {
            throw new IllegalArgumentException("La carga de trabajo no puede ser negativa.");
        }
        if (occupancy < 0 || occupancy > 1) {
            throw new IllegalArgumentException("La ocupación debe estar entre 0 y 1.");
        }
    }

    /**
     * Indica si es un médico.
     *
     * @return {@code true} si tiene especialidad
     */
    public boolean isDoctor() {
        return specialty != null;
    }

    /**
     * Indica si estuvo saturado.
     *
     * @return {@code true} si estuvo ocupado {@value #SATURATED_PERCENT} % del tiempo o más
     */
    public boolean isSaturated() {
        return occupancy * 100 >= SATURATED_PERCENT;
    }

    /**
     * Indica si estuvo libre la mayor parte del tiempo.
     *
     * @return {@code true} si estuvo ocupado menos del {@value #IDLE_PERCENT} % del tiempo
     */
    public boolean isIdle() {
        return occupancy * 100 < IDLE_PERCENT;
    }
}
