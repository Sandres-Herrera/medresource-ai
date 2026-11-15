package com.medresource.model;

/**
 * Grupo de recursos del hospital cuya ocupación se mide en el reporte.
 */
public enum ResourceGroup {

    BEDS("Camas de hospitalización"),
    ICU_BEDS("Camas de UCI"),
    OPERATING_ROOMS("Quirófanos"),
    DOCTORS("Médicos"),
    NURSES("Enfermeros"),
    MORGUE("Morgue");

    private final String displayName;

    ResourceGroup(String displayName) {
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
