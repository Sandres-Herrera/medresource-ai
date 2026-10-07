package com.medresource.model;

/**
 * Tipo de habitación del hospital.
 */
public enum RoomType {

    HOSPITALIZATION("Hospitalización"),
    ICU("Unidad de cuidados intensivos");

    private final String displayName;

    RoomType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Devuelve el nombre que se muestra al usuario.
     *
     * @return nombre en español
     */
    public String getDisplayName() {
        return displayName;
    }
}