package com.medresource.model;

/**
 * Nivel de triage según la Resolución 5596 de 2015 del Ministerio de Salud
 * y Protección Social de Colombia.
 *
 * <p>El nivel I es el más urgente y el V el menos urgente. El tiempo objetivo
 * de atención de los niveles III a V es un supuesto de la simulación.</p>
 *
 * <p>Cada nivel indica además cuánto dura su valoración en una estación de triage
 * (supuesto): cuanto más estable el paciente, más completa es la valoración. El
 * nivel I no se detiene en el triage: se reconoce en la puerta y pasa directo.</p>
 */
public enum TriageLevel {

    I(1, "Triage I — Inmediato", 0, 0),
    II(2, "Triage II — Emergencia", 30, 1),
    III(3, "Triage III — Urgente", 120, 2),
    IV(4, "Triage IV — Menos urgente", 240, 3),
    V(5, "Triage V — No urgente", 480, 3);

    private final int level;
    private final String displayName;
    private final int targetMinutes;
    private final int assessmentMinutes;

    TriageLevel(int level, String displayName, int targetMinutes, int assessmentMinutes) {
        this.level = level;
        this.displayName = displayName;
        this.targetMinutes = targetMinutes;
        this.assessmentMinutes = assessmentMinutes;
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
     * Devuelve cuántos minutos pasa el paciente en una estación de triage mientras lo valoran.
     *
     * @return minutos de valoración; 0 si pasa directo sin detenerse (nivel I)
     */
    public int getAssessmentMinutes() {
        return assessmentMinutes;
    }

    /**
     * Indica si el paciente pasa directo, sin hacer fila ni ocupar una estación de triage.
     *
     * @return {@code true} si su valoración no toma tiempo
     */
    public boolean skipsTriageStation() {
        return assessmentMinutes == 0;
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