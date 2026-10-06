package com.medresource.model;

/**
 * Nivel de triage según la Resolución 5596 de 2015 del Ministerio de Salud
 * y Protección Social de Colombia.
 *
 * <p>El nivel I es el más urgente y el V el menos urgente. El tiempo objetivo
 * de atención de los niveles III a V es un supuesto de la simulación.</p>
 */
public enum TriageLevel {

    I(1, "Triage I — Inmediato", 0),
    II(2, "Triage II — Emergencia", 30),
    III(3, "Triage III — Urgente", 120),
    IV(4, "Triage IV — Menos urgente", 240),
    V(5, "Triage V — No urgente", 480);

    private final int level;
    private final String displayName;
    private final int targetMinutes;

    TriageLevel(int level, String displayName, int targetMinutes) {
        this.level = level;
        this.displayName = displayName;
        this.targetMinutes = targetMinutes;
    }

    /**
     * Devuelve el número del nivel (1 a 5).
     *
     * @return nivel numérico
     */
    public int getLevel() {
        return level;
    }

    /**
     * Devuelve el nombre que se muestra al usuario.
     *
     * @return nombre en español
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Devuelve el tiempo máximo deseable de espera antes de la atención.
     *
     * @return minutos objetivo de atención
     */
    public int getTargetMinutes() {
        return targetMinutes;
    }

    /**
     * Indica si este nivel es más urgente que otro.
     *
     * @param other nivel con el que se compara
     * @return {@code true} si este nivel debe atenderse antes
     */
    public boolean isMoreUrgentThan(TriageLevel other) {
        return this.level < other.level;
    }
}