package com.medresource.model;

/**
 * Tipo de zona del plano del hospital: lo que hay en cada casilla.
 *
 * <p>Cada vista usa el tipo para elegir el color y el dibujo de la casilla.</p>
 */
public enum ZoneType {

    CORRIDOR("Pasillo"),
    ENTRANCE("Entrada"),
    WAITING_ROOM("Sala de espera"),
    TRIAGE("Triage"),
    HOSPITALIZATION("Hospitalización"),
    ICU("Unidad de cuidados intensivos"),
    OPERATING_ROOM("Quirófano"),
    MORGUE("Morgue"),
    STAFF_ROOM("Sala del personal");

    private final String displayName;

    ZoneType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}