package com.medresource.model;

/**
 * Carga de los médicos de una especialidad y cuánto esperaron los pacientes que la necesitaban.
 *
 * @param specialty         especialidad
 * @param doctors           médicos de esa especialidad
 * @param patients          pacientes que atendieron
 * @param averageOccupancy  ocupación promedio de sus médicos, de 0 a 1
 * @param doctorWaitMinutes minutos-paciente que esperaron por un médico los pacientes cuya enfermedad tiene
 *                          esta especialidad como principal
 */
public record SpecialtyLoad(Specialty specialty, int doctors, int patients, double averageOccupancy,
                            int doctorWaitMinutes) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta la especialidad, si hay números negativos o si la ocupación no
     *                                  está entre 0 y 1
     */
    public SpecialtyLoad {
        if (specialty == null) {
            throw new IllegalArgumentException("La especialidad es obligatoria.");
        }
        if (doctors < 0 || patients < 0 || doctorWaitMinutes < 0) {
            throw new IllegalArgumentException("La carga de una especialidad no puede ser negativa.");
        }
        if (averageOccupancy < 0 || averageOccupancy > 1) {
            throw new IllegalArgumentException("La ocupación debe estar entre 0 y 1.");
        }
    }
}
