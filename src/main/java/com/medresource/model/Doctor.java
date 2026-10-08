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
     * <p>Reglas: si su especialidad es la principal de la enfermedad es
     * {@code PRIMARY}; si es secundaria, {@code SECONDARY}; si no aparece,
     * {@code NOT_SUITABLE}. Un pediatra solo atiende pacientes menores de
     * {@link Person#MINOR_AGE_LIMIT} años.</p>
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
        if (disease.getMainSpecialty() == specialty) {
            return CompatibilityLevel.PRIMARY;
        }
        if (disease.hasSecondarySpecialty(specialty)) {
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
}
