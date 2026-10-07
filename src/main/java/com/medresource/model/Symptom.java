package com.medresource.model;

/**
 * Síntoma visible que presenta un paciente al llegar.
 *
 * <p>Cada síntoma tiene asociado el nivel de triage que justifica por sí solo;
 * el triage de un paciente es el del síntoma más grave que presenta.</p>
 */
public enum Symptom {

    UNCONSCIOUS("Inconsciente", TriageLevel.I),
    CHEST_PAIN("Dolor en el pecho", TriageLevel.I),
    BREATHING_DIFFICULTY("Dificultad para respirar", TriageLevel.II),
    BLEEDING("Hemorragia", TriageLevel.II),
    EXPOSED_BONE("Hueso expuesto", TriageLevel.II),
    UNABLE_TO_WALK("Incapacidad para caminar", TriageLevel.II),
    ABDOMINAL_PAIN("Dolor abdominal intenso", TriageLevel.II),
    HIGH_FEVER("Fiebre alta", TriageLevel.III),
    GENERALIZED_PAIN("Dolor en todo el cuerpo", TriageLevel.III),
    LIMB_DEFORMITY("Deformidad en una extremidad", TriageLevel.III),
    LEG_PAIN("Dolor en las piernas", TriageLevel.III),
    WEIGHT_LOSS("Pérdida de peso", TriageLevel.III),
    FATIGUE("Fatiga", TriageLevel.III),
    FEVER("Fiebre", TriageLevel.IV),
    COUGH("Tos", TriageLevel.IV),
    CONGESTION("Congestión nasal", TriageLevel.V);

    private final String displayName;
    private final TriageLevel triageLevel;

    Symptom(String displayName, TriageLevel triageLevel) {
        this.displayName = displayName;
        this.triageLevel = triageLevel;
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
     * Devuelve el nivel de triage que justifica este síntoma.
     *
     * @return nivel de triage asociado
     */
    public TriageLevel getTriageLevel() {
        return triageLevel;
    }
}