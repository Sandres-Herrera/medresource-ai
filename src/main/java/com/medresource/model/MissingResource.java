package com.medresource.model;

/**
 * Recurso que le falta a un paciente para dejar de esperar.
 *
 * <p>Sirve para explicar las esperas en el reporte de la simulación: cada minuto que un paciente sigue
 * esperando se le cuenta a cada recurso que le faltaba en ese momento. Un paciente puede esperar por
 * varios recursos a la vez (por ejemplo, un cirujano y un quirófano).</p>
 */
public enum MissingResource {

    TRIAGE_STATION("Estación de triage"),
    DOCTOR("Médico compatible"),
    NURSE("Enfermeros libres"),
    BED("Cama de hospitalización"),
    ICU_BED("Cama de UCI"),
    NURSING_CAPACITY("Enfermería para vigilar otra cama"),
    OPERATING_ROOM("Quirófano"),
    BLOOD("Sangre compatible");

    private final String displayName;

    MissingResource(String displayName) {
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
