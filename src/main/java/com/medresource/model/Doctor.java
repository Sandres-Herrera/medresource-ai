package com.medresource.model;

/**
 * Médico con una especialidad que define qué enfermedades puede tratar y con
 * qué efectividad.
 */
public class Doctor extends StaffMember {

    private final Specialty specialty;

    /**
     * Crea un médico; su código visible será {@code D01}, {@code D02}…
     *
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public Doctor(int id, Specialty specialty, int age, Sex sex) {
        super(id, String.format("D%02d", id), age, sex);
        if (specialty == null) {
            throw new IllegalArgumentException("La especialidad del médico es obligatoria.");
        }
        this.specialty = specialty;
    }

    /**
     * Calcula qué tan apto es el médico para una enfermedad.
     *
     * <p>Reglas, en este orden:</p>
     * <ol>
     *   <li>Un pediatra solo atiende pacientes menores de
     *       {@link Person#MINOR_AGE_LIMIT} años.</li>
     *   <li>Un médico general nunca atiende enfermedades que requieren cirugía.</li>
     *   <li>Si su especialidad es la principal de la enfermedad: {@code PRIMARY}.</li>
     *   <li>Si es una de las secundarias: {@code SECONDARY}.</li>
     *   <li>Un médico de urgencias puede estabilizar (como {@code SECONDARY})
     *       las enfermedades no quirúrgicas cuyo triage típico es I o II.</li>
     *   <li>En cualquier otro caso: {@code NOT_SUITABLE}.</li>
     * </ol>
     *
     * <p>Recibe la enfermedad y no el paciente porque las estrategias usan el
     * diagnóstico, que puede ser distinto de la enfermedad real.</p>
     *
     * @param disease    enfermedad diagnosticada o real
     * @param patientAge edad del paciente
     * @return nivel de compatibilidad
     * @throws IllegalArgumentException si la enfermedad es nula
     */
    public CompatibilityLevel compatibilityWith(Disease disease, int patientAge) {
        if (disease == null) {
            throw new IllegalArgumentException("La enfermedad es obligatoria.");
        }
        if (specialty == Specialty.PEDIATRICS && patientAge >= MINOR_AGE_LIMIT) {
            return CompatibilityLevel.NOT_SUITABLE;
        }
        if (specialty == Specialty.GENERAL_MEDICINE && isSurgical(disease)) {
            return CompatibilityLevel.NOT_SUITABLE;
        }
        if (disease.getMainSpecialty() == specialty) {
            return CompatibilityLevel.PRIMARY;
        }
        if (disease.hasSecondarySpecialty(specialty) || canStabilize(disease)) {
            return CompatibilityLevel.SECONDARY;
        }
        return CompatibilityLevel.NOT_SUITABLE;
    }

    public boolean canTreat(Disease disease, int patientAge) {
        return compatibilityWith(disease, patientAge).isSuitable();
    }

    /**
     * @return 1.0 si es la especialidad principal, 0.6 si es secundaria y 0 si no es apto
     */
    public double effectivenessFor(Disease disease, int patientAge) {
        return compatibilityWith(disease, patientAge).getEffectiveness();
    }

    @Override
    public String describe() {
        return getCode() + " · " + specialty.getDisplayName();
    }

    public Specialty getSpecialty() {
        return specialty;
    }

    private static boolean isSurgical(Disease disease) {
        return disease.getTreatment() == TreatmentType.SURGERY;
    }

    /**
     * Un médico de urgencias estabiliza los casos no quirúrgicos de triage I o II.
     */
    private boolean canStabilize(Disease disease) {
        return specialty == Specialty.EMERGENCY_MEDICINE
                && !isSurgical(disease)
                && disease.typicalTriage().isMoreUrgentThan(TriageLevel.III);
    }
}