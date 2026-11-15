package com.medresource.model;

/**
 * Motivo por el que un paciente aparece entre los casos para revisar del reporte.
 */
public enum ReviewReason {

    LONGEST_WAIT_BEFORE_DEATH("El que más esperó antes de morir sin ser atendido"),
    DIED_AFTER_LATE_CARE("Murió después de una atención tardía"),
    LONGEST_URGENT_WAIT("El paciente urgente (triage I o II) que más esperó"),
    LARGEST_HEALTH_LOSS("El que más salud perdió esperando"),
    FURTHEST_OVER_TARGET("El que más se pasó de la meta de su nivel de triage"),
    LEFT_UNATTENDED("Se fue sin ser atendido");

    private final String displayName;

    ReviewReason(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Devuelve el motivo como se muestra al usuario.
     *
     * @return texto en español
     */
    public String getDisplayName() {
        return displayName;
    }
}
