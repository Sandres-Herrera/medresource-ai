package com.medresource.model;

/**
 * Tamaño del hospital que se simula: cuántos recursos tiene de cada tipo.
 *
 * <p>Se separa de {@link CaseConfig} porque se puede cambiar el hospital y
 * mantener los mismos pacientes, o al contrario. La fábrica de hospitales usa
 * estos números para construir los recursos.</p>
 *
 * @param hospitalizationBeds camas de hospitalización, de 1 a {@value #MAX_BEDS}
 * @param icuBeds             camas de UCI, de 1 a {@value #MAX_ICU_BEDS}
 * @param operatingRooms      quirófanos, de 1 a {@value #MAX_OPERATING_ROOMS}
 * @param doctors             médicos, de 1 a {@value #MAX_DOCTORS}
 * @param nurses              enfermeros, de 1 a {@value #MAX_NURSES}
 * @param bloodUnitsPerType   unidades de sangre de cada uno de los 8 tipos,
 *                            de 0 a {@value #MAX_BLOOD_UNITS_PER_TYPE}
 * @param morgueCapacity      cupos de la morgue, de 1 a {@value #MAX_MORGUE_CAPACITY}
 */
public record HospitalConfig(int hospitalizationBeds, int icuBeds, int operatingRooms,
                             int doctors, int nurses, int bloodUnitsPerType, int morgueCapacity) {

    /** Máximo de camas de hospitalización. */
    public static final int MAX_BEDS = 200;

    /** Máximo de camas de UCI. */
    public static final int MAX_ICU_BEDS = 50;

    /** Máximo de quirófanos. */
    public static final int MAX_OPERATING_ROOMS = 20;

    /** Máximo de médicos. */
    public static final int MAX_DOCTORS = 50;

    /** Máximo de enfermeros. */
    public static final int MAX_NURSES = 100;

    /** Máximo de unidades de sangre por tipo. */
    public static final int MAX_BLOOD_UNITS_PER_TYPE = 100;

    /** Máximo de cupos de la morgue. */
    public static final int MAX_MORGUE_CAPACITY = 50;

    /**
     * Valida que cada cantidad esté dentro de su rango.
     *
     * @throws IllegalArgumentException si alguna cantidad está fuera de rango
     */
    public HospitalConfig {
        requireRange(hospitalizationBeds, 1, MAX_BEDS, "camas de hospitalización");
        requireRange(icuBeds, 1, MAX_ICU_BEDS, "camas de UCI");
        requireRange(operatingRooms, 1, MAX_OPERATING_ROOMS, "quirófanos");
        requireRange(doctors, 1, MAX_DOCTORS, "médicos");
        requireRange(nurses, 1, MAX_NURSES, "enfermeros");
        requireRange(bloodUnitsPerType, 0, MAX_BLOOD_UNITS_PER_TYPE, "unidades de sangre por tipo");
        requireRange(morgueCapacity, 1, MAX_MORGUE_CAPACITY, "cupos de la morgue");
    }

    /**
     * Crea el hospital estándar del proyecto: 30 camas, 6 de UCI, 4 quirófanos,
     * 16 médicos, 24 enfermeros, 10 unidades de cada tipo de sangre y 5 cupos en la morgue.
     *
     * @return configuración estándar
     */
    public static HospitalConfig standard() {
        return new HospitalConfig(30, 6, 4, 16, 24, 10, 5);
    }

    private static void requireRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("La cantidad de " + name + " debe estar entre " + min + " y " + max + ".");
        }
    }
}