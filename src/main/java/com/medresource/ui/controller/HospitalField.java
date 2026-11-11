package com.medresource.ui.controller;

import com.medresource.model.HospitalConfig;

/**
 * Cada recurso del hospital que se puede cambiar en el formulario de una simulación nueva, con su nombre, su
 * rango y la forma de leerlo o cambiarlo en una {@link HospitalConfig}.
 *
 * <p>Los rangos son los mismos que valida {@link HospitalConfig}: así el formulario nunca deja escribir un valor
 * que después se rechace.</p>
 */
public enum HospitalField {

    /** Camas de hospitalización. */
    BEDS("Camas", 1, HospitalConfig.MAX_BEDS),
    /** Camas de UCI. */
    ICU_BEDS("Camas UCI", 1, HospitalConfig.MAX_ICU_BEDS),
    /** Quirófanos. */
    OPERATING_ROOMS("Quirófanos", 1, HospitalConfig.MAX_OPERATING_ROOMS),
    /** Médicos. */
    DOCTORS("Médicos", 1, HospitalConfig.MAX_DOCTORS),
    /** Enfermeros. */
    NURSES("Enfermeros", 1, HospitalConfig.MAX_NURSES),
    /** Unidades de cada tipo de sangre. */
    BLOOD("Sangre por tipo", 0, HospitalConfig.MAX_BLOOD_UNITS_PER_TYPE),
    /** Cupos de la morgue. */
    MORGUE("Cupos morgue", 1, HospitalConfig.MAX_MORGUE_CAPACITY);

    private final String displayName;
    private final int min;
    private final int max;

    HospitalField(String displayName, int min, int max) {
        this.displayName = displayName;
        this.min = min;
        this.max = max;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    /**
     * Lee este recurso en una configuración.
     *
     * @param config configuración del hospital
     * @return la cantidad de este recurso
     * @throws IllegalArgumentException si la configuración es nula
     */
    public int valueIn(HospitalConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración del hospital es obligatoria.");
        }
        return switch (this) {
            case BEDS -> config.hospitalizationBeds();
            case ICU_BEDS -> config.icuBeds();
            case OPERATING_ROOMS -> config.operatingRooms();
            case DOCTORS -> config.doctors();
            case NURSES -> config.nurses();
            case BLOOD -> config.bloodUnitsPerType();
            case MORGUE -> config.morgueCapacity();
        };
    }

    /**
     * Crea una configuración igual a otra pero con otra cantidad de este recurso.
     *
     * @param config configuración de partida
     * @param value  cantidad nueva, entre {@link #getMin()} y {@link #getMax()}
     * @return configuración nueva; la de partida no cambia
     * @throws IllegalArgumentException si la configuración es nula o la cantidad está fuera del rango
     */
    public HospitalConfig with(HospitalConfig config, int value) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración del hospital es obligatoria.");
        }
        int beds = this == BEDS ? value : config.hospitalizationBeds();
        int icu = this == ICU_BEDS ? value : config.icuBeds();
        int rooms = this == OPERATING_ROOMS ? value : config.operatingRooms();
        int doctors = this == DOCTORS ? value : config.doctors();
        int nurses = this == NURSES ? value : config.nurses();
        int blood = this == BLOOD ? value : config.bloodUnitsPerType();
        int morgue = this == MORGUE ? value : config.morgueCapacity();
        return new HospitalConfig(beds, icu, rooms, doctors, nurses, blood, morgue);
    }

    /**
     * Ajusta una cantidad al rango de este recurso.
     *
     * @param value cantidad cualquiera
     * @return la misma cantidad, o el mínimo o el máximo si se sale del rango
     */
    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }
}
