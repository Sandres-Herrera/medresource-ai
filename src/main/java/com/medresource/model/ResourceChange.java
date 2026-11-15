package com.medresource.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Cambio de recursos que se prueba en el análisis «¿y si…?»: agregar al hospital cierta cantidad de un
 * recurso. Un médico se agrega siempre de una especialidad concreta, porque no da lo mismo un cirujano que un
 * pediatra.
 *
 * @param resource  recurso que se agrega
 * @param specialty especialidad del médico que se agrega; {@code null} si el recurso no es un médico
 */
public record ResourceChange(ExtraResource resource, Specialty specialty) {

    /**
     * Valida que solo los médicos tengan especialidad.
     *
     * @throws IllegalArgumentException si falta el recurso, o si un médico no tiene especialidad o un recurso
     *                                  que no es médico tiene una
     */
    public ResourceChange {
        if (resource == null) {
            throw new IllegalArgumentException("El recurso es obligatorio.");
        }
        if ((resource == ExtraResource.DOCTOR) != (specialty != null)) {
            throw new IllegalArgumentException("Solo un médico tiene especialidad, y siempre la tiene.");
        }
    }

    /**
     * Crea el cambio que agrega un recurso que no es un médico.
     *
     * @param resource recurso
     * @return cambio
     */
    public static ResourceChange of(ExtraResource resource) {
        return new ResourceChange(resource, null);
    }

    /**
     * Crea el cambio que agrega un médico de una especialidad.
     *
     * @param specialty especialidad
     * @return cambio
     */
    public static ResourceChange doctor(Specialty specialty) {
        return new ResourceChange(ExtraResource.DOCTOR, specialty);
    }

    /**
     * Devuelve todos los cambios que caben en un hospital, en este orden: camas, UCI, quirófanos, un médico de
     * cada especialidad, enfermeros, sangre y morgue. Un cambio que pasaría del máximo permitido no aparece.
     *
     * @param config hospital al que se le agregaría
     * @return cambios posibles
     * @throws IllegalArgumentException si falta el hospital
     */
    public static List<ResourceChange> allFor(HospitalConfig config) {
        List<ResourceChange> changes = new ArrayList<>();
        for (ExtraResource resource : ExtraResource.values()) {
            if (resource == ExtraResource.DOCTOR) {
                for (Specialty specialty : Specialty.values()) {
                    changes.add(doctor(specialty));
                }
            } else {
                changes.add(of(resource));
            }
        }
        return changes.stream().filter(change -> change.fitsIn(config)).toList();
    }

    /**
     * Indica si el cambio se puede hacer sin pasar del máximo permitido de ese recurso.
     *
     * @param config hospital al que se le agregaría
     * @return {@code true} si cabe
     * @throws IllegalArgumentException si falta el hospital
     */
    public boolean fitsIn(HospitalConfig config) {
        requireConfig(config);
        int amount = resource.getAmount();
        return switch (resource) {
            case BEDS -> config.hospitalizationBeds() + amount <= HospitalConfig.MAX_BEDS;
            case ICU_BEDS -> config.icuBeds() + amount <= HospitalConfig.MAX_ICU_BEDS;
            case OPERATING_ROOMS -> config.operatingRooms() + amount <= HospitalConfig.MAX_OPERATING_ROOMS;
            case DOCTOR -> config.doctors() + amount <= HospitalConfig.MAX_DOCTORS;
            case NURSES -> config.nurses() + amount <= HospitalConfig.MAX_NURSES;
            case BLOOD -> config.bloodUnitsPerType() + amount <= HospitalConfig.MAX_BLOOD_UNITS_PER_TYPE;
            case MORGUE -> config.morgueCapacity() + amount <= HospitalConfig.MAX_MORGUE_CAPACITY;
        };
    }

    /**
     * Devuelve el hospital con el cambio hecho. Un médico no cambia la configuración, porque la configuración
     * no dice especialidades: se agrega aparte con {@link #extraDoctors()}.
     *
     * @param config hospital original
     * @return hospital con más del recurso
     * @throws IllegalArgumentException si falta el hospital o si el cambio no cabe
     */
    public HospitalConfig applyTo(HospitalConfig config) {
        if (!fitsIn(config)) {
            throw new IllegalArgumentException("El cambio pasa del máximo permitido: " + describe() + ".");
        }
        int amount = resource.getAmount();
        return switch (resource) {
            case BEDS -> new HospitalConfig(config.hospitalizationBeds() + amount, config.icuBeds(),
                    config.operatingRooms(), config.doctors(), config.nurses(), config.bloodUnitsPerType(),
                    config.morgueCapacity());
            case ICU_BEDS -> new HospitalConfig(config.hospitalizationBeds(), config.icuBeds() + amount,
                    config.operatingRooms(), config.doctors(), config.nurses(), config.bloodUnitsPerType(),
                    config.morgueCapacity());
            case OPERATING_ROOMS -> new HospitalConfig(config.hospitalizationBeds(), config.icuBeds(),
                    config.operatingRooms() + amount, config.doctors(), config.nurses(), config.bloodUnitsPerType(),
                    config.morgueCapacity());
            case DOCTOR -> config;
            case NURSES -> new HospitalConfig(config.hospitalizationBeds(), config.icuBeds(), config.operatingRooms(),
                    config.doctors(), config.nurses() + amount, config.bloodUnitsPerType(), config.morgueCapacity());
            case BLOOD -> new HospitalConfig(config.hospitalizationBeds(), config.icuBeds(), config.operatingRooms(),
                    config.doctors(), config.nurses(), config.bloodUnitsPerType() + amount, config.morgueCapacity());
            case MORGUE -> new HospitalConfig(config.hospitalizationBeds(), config.icuBeds(), config.operatingRooms(),
                    config.doctors(), config.nurses(), config.bloodUnitsPerType(), config.morgueCapacity() + amount);
        };
    }

    /**
     * Devuelve las especialidades de los médicos que el cambio agrega.
     *
     * @return una especialidad por médico agregado; vacío si el cambio no es de médicos
     */
    public List<Specialty> extraDoctors() {
        List<Specialty> doctors = new ArrayList<>();
        if (resource == ExtraResource.DOCTOR) {
            for (int i = 0; i < resource.getAmount(); i++) {
                doctors.add(specialty);
            }
        }
        return List.copyOf(doctors);
    }

    /**
     * Describe el cambio para el reporte, por ejemplo {@code 1 quirófano más} o
     * {@code 1 médico de Cirugía más}.
     *
     * @return descripción en español
     */
    public String describe() {
        String amount = resource.describeAmount();
        return specialty == null ? amount + " más" : amount + " de " + specialty.getDisplayName() + " más";
    }

    private static void requireConfig(HospitalConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración del hospital es obligatoria.");
        }
    }
}
