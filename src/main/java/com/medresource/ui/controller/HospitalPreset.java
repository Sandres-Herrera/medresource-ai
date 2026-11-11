package com.medresource.ui.controller;

import com.medresource.model.HospitalConfig;

import java.util.Optional;

/**
 * Tamaños de hospital ya armados que se eligen con un clic en el formulario de una simulación nueva: pequeño,
 * estándar y grande.
 */
public enum HospitalPreset {

    /** La mitad del estándar: {@link HospitalConfig#small()}. */
    SMALL("Pequeño"),
    /** El hospital calibrado del proyecto: {@link HospitalConfig#standard()}. */
    STANDARD("Estándar"),
    /** El doble del estándar: {@link HospitalConfig#large()}. */
    LARGE("Grande");

    private final String displayName;

    HospitalPreset(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Configuración del hospital de este tamaño. */
    public HospitalConfig config() {
        return switch (this) {
            case SMALL -> HospitalConfig.small();
            case STANDARD -> HospitalConfig.standard();
            case LARGE -> HospitalConfig.large();
        };
    }

    /**
     * Busca el tamaño que tiene exactamente esa configuración.
     *
     * @param config configuración del hospital
     * @return el tamaño, o vacío si la configuración es personalizada (o nula)
     */
    public static Optional<HospitalPreset> matching(HospitalConfig config) {
        for (HospitalPreset preset : values()) {
            if (preset.config().equals(config)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }
}
