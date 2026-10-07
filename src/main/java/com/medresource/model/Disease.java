package com.medresource.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Enfermedad del catálogo con sus características clínicas.
 *
 * <p>Define qué especialidades pueden tratarla, qué tratamiento requiere, qué
 * tan rápido empeora el paciente sin atención y qué síntomas presenta. Los
 * objetos son inmutables y se construyen con {@link Builder}, que valida
 * todos los datos.</p>
 */
public final class Disease {

    private final int id;
    private final String name;
    private final Specialty mainSpecialty;
    private final Set<Specialty> secondarySpecialties;
    private final TreatmentType treatment;
    private final boolean curable;
    private final boolean requiresBlood;
    private final boolean requiresIcu;
    private final double deteriorationRate;
    private final double irreducibleMortality;
    private final int minDurationMinutes;
    private final int maxDurationMinutes;
    private final int minAge;
    private final int maxAge;
    private final double frequency;
    private final List<Symptom> symptoms;

    private Disease(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.mainSpecialty = builder.mainSpecialty;
        this.secondarySpecialties = Collections.unmodifiableSet(EnumSet.copyOf(builder.secondarySpecialties));
        this.treatment = builder.treatment;
        this.curable = builder.curable;
        this.requiresBlood = builder.requiresBlood;
        this.requiresIcu = builder.requiresIcu;
        this.deteriorationRate = builder.deteriorationRate;
        this.irreducibleMortality = builder.irreducibleMortality;
        this.minDurationMinutes = builder.minDurationMinutes;
        this.maxDurationMinutes = builder.maxDurationMinutes;
        this.minAge = builder.minAge;
        this.maxAge = builder.maxAge;
        this.frequency = builder.frequency;
        this.symptoms = List.copyOf(builder.symptoms);
    }

    /**
     * Crea un constructor de enfermedades.
     *
     * @param id   identificador único de la enfermedad
     * @param name nombre que se muestra al usuario
     * @return un nuevo {@link Builder}
     */
    public static Builder builder(int id, String name) {
        return new Builder(id, name);
    }

    /**
     * Calcula el nivel de triage típico: el del síntoma más grave.
     *
     * @return nivel de triage más urgente entre sus síntomas
     */
    public TriageLevel typicalTriage() {
        TriageLevel mostUrgent = TriageLevel.V;
        for (Symptom symptom : symptoms) {
            if (symptom.getTriageLevel().isMoreUrgentThan(mostUrgent)) {
                mostUrgent = symptom.getTriageLevel();
            }
        }
        return mostUrgent;
    }

    /**
     * Indica si una persona de la edad dada puede padecer esta enfermedad.
     *
     * @param age edad en años
     * @return {@code true} si la edad está dentro del rango de la enfermedad
     */
    public boolean affectsAge(int age) {
        return age >= minAge && age <= maxAge;
    }

    /**
     * Indica si la especialidad dada aparece como secundaria para esta enfermedad.
     *
     * @param specialty especialidad a consultar
     * @return {@code true} si es una especialidad secundaria
     */
    public boolean hasSecondarySpecialty(Specialty specialty) {
        return secondarySpecialties.contains(specialty);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Specialty getMainSpecialty() {
        return mainSpecialty;
    }

    public Set<Specialty> getSecondarySpecialties() {
        return secondarySpecialties;
    }

    public TreatmentType getTreatment() {
        return treatment;
    }

    public boolean isCurable() {
        return curable;
    }

    public boolean requiresBlood() {
        return requiresBlood;
    }

    public boolean requiresIcu() {
        return requiresIcu;
    }

    /**
     * Devuelve los puntos de salud (de 0 a 100) que pierde el paciente por cada
     * minuto sin tratamiento adecuado.
     *
     * @return tasa de deterioro por minuto
     */
    public double getDeteriorationRate() {
        return deteriorationRate;
    }

    /**
     * Devuelve la probabilidad (0 a 1) de que el paciente fallezca aunque reciba
     * la mejor atención.
     *
     * @return mortalidad irreducible
     */
    public double getIrreducibleMortality() {
        return irreducibleMortality;
    }

    public int getMinDurationMinutes() {
        return minDurationMinutes;
    }

    public int getMaxDurationMinutes() {
        return maxDurationMinutes;
    }

    public int getMinAge() {
        return minAge;
    }

    public int getMaxAge() {
        return maxAge;
    }

    /**
     * Devuelve el peso relativo con que aparece la enfermedad al generar pacientes.
     *
     * @return frecuencia relativa (mayor que cero)
     */
    public double getFrequency() {
        return frequency;
    }

    public List<Symptom> getSymptoms() {
        return symptoms;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Disease disease)) {
            return false;
        }
        return id == disease.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Construye objetos {@link Disease} paso a paso y valida sus datos al final.
     */
    public static final class Builder {

        private final int id;
        private final String name;
        private Specialty mainSpecialty;
        private final Set<Specialty> secondarySpecialties = EnumSet.noneOf(Specialty.class);
        private TreatmentType treatment;
        private boolean curable = true;
        private boolean requiresBlood;
        private boolean requiresIcu;
        private double deteriorationRate;
        private double irreducibleMortality;
        private int minDurationMinutes;
        private int maxDurationMinutes;
        private int minAge = 0;
        private int maxAge = 100;
        private double frequency = 1.0;
        private final List<Symptom> symptoms = new ArrayList<>();

        private Builder(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder mainSpecialty(Specialty specialty) {
            this.mainSpecialty = specialty;
            return this;
        }

        public Builder secondarySpecialties(Specialty... specialties) {
            this.secondarySpecialties.addAll(List.of(specialties));
            return this;
        }

        public Builder treatment(TreatmentType treatment) {
            this.treatment = treatment;
            return this;
        }

        public Builder curable(boolean curable) {
            this.curable = curable;
            return this;
        }

        public Builder requiresBlood(boolean requiresBlood) {
            this.requiresBlood = requiresBlood;
            return this;
        }

        public Builder requiresIcu(boolean requiresIcu) {
            this.requiresIcu = requiresIcu;
            return this;
        }

        public Builder deteriorationRate(double rate) {
            this.deteriorationRate = rate;
            return this;
        }

        public Builder irreducibleMortality(double mortality) {
            this.irreducibleMortality = mortality;
            return this;
        }

        public Builder durationMinutes(int min, int max) {
            this.minDurationMinutes = min;
            this.maxDurationMinutes = max;
            return this;
        }

        public Builder ageRange(int min, int max) {
            this.minAge = min;
            this.maxAge = max;
            return this;
        }

        public Builder frequency(double frequency) {
            this.frequency = frequency;
            return this;
        }

        public Builder symptoms(Symptom... symptoms) {
            this.symptoms.addAll(List.of(symptoms));
            return this;
        }

        /**
         * Valida los datos y crea la enfermedad.
         *
         * @return la enfermedad construida
         * @throws IllegalArgumentException si algún dato es inválido
         */
        public Disease build() {
            validate();
            return new Disease(this);
        }

        private void validate() {
            requireThat(id > 0, "el id debe ser mayor que cero");
            requireThat(name != null && !name.isBlank(), "el nombre es obligatorio");
            requireThat(mainSpecialty != null, "la especialidad principal es obligatoria");
            requireThat(treatment != null, "el tipo de tratamiento es obligatorio");
            requireThat(!secondarySpecialties.contains(mainSpecialty),
                    "la especialidad principal no puede ser también secundaria");
            requireThat(deteriorationRate >= 0, "la tasa de deterioro no puede ser negativa");
            requireThat(irreducibleMortality >= 0 && irreducibleMortality <= 1,
                    "la mortalidad irreducible debe estar entre 0 y 1");
            requireThat(minDurationMinutes > 0, "la duración mínima debe ser mayor que cero");
            requireThat(minDurationMinutes <= maxDurationMinutes,
                    "la duración mínima no puede ser mayor que la máxima");
            requireThat(minAge >= 0, "la edad mínima no puede ser negativa");
            requireThat(minAge <= maxAge, "la edad mínima no puede ser mayor que la máxima");
            requireThat(frequency > 0, "la frecuencia debe ser mayor que cero");
            requireThat(!symptoms.isEmpty(), "la enfermedad debe tener al menos un síntoma");
        }

        private void requireThat(boolean condition, String message) {
            if (!condition) {
                throw new IllegalArgumentException("Enfermedad '" + name + "': " + message);
            }
        }
    }
}