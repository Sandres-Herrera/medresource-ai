package com.medresource.model;

/**
 * Tipo de zona del plano del hospital: lo que hay en cada área.
 *
 * <p>Cada vista usa el tipo para elegir el color y el dibujo de las casillas de la zona.</p>
 */
public enum ZoneType {

    TRIAGE("Triage"),
    WAITING_ROOM("Sala de espera"),
    HOSPITALIZATION("Hospitalización"),
    ICU("Unidad de cuidados intensivos"),
    OPERATING_ROOM("Quirófano"),
    BLOOD_BANK("Banco de sangre"),
    MORGUE("Morgue"),
    DISCHARGE("Alta / salida"),
    STAFF_ROOM("Sala del personal");

    private final String displayName;

    ZoneType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}