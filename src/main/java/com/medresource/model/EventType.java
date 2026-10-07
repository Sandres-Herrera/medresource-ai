package com.medresource.model;

/**
 * Tipo de evento que se registra durante una simulación.
 */
public enum EventType {

    ARRIVAL("Llegada de paciente"),
    EMERGENCY_ALERT("Nueva emergencia"),
    TRIAGE_ASSIGNED("Triage asignado"),
    DIAGNOSIS("Diagnóstico"),
    REEVALUATION("Reevaluación"),
    TREATMENT_STARTED("Inicio de tratamiento"),
    TREATMENT_FINISHED("Fin de tratamiento"),
    ICU_ADMISSION("Ingreso a UCI"),
    DISCHARGE("Alta"),
    STABILIZED_WITHOUT_CURE("Estabilizado sin cura"),
    REFERRAL("Remisión"),
    DEATH("Fallecimiento"),
    MORGUE_ADMISSION("Ingreso a la morgue"),
    MORGUE_RELEASE("Retiro de la morgue"),
    UNATTENDED("Paciente sin atender");

    private final String displayName;

    EventType(String displayName) {
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