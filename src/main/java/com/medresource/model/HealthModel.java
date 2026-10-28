package com.medresource.model;

/**
 * Reglas de cómo cambia la salud de un paciente.
 *
 * <p>Todo depende de la <b>efectividad</b> de la atención, un número de 0 a 1:
 * 0 si nadie lo atiende, 0,6 con un especialista secundario y 1 con el
 * especialista principal (ver {@code CompatibilityLevel}). El motor de
 * simulación calcula esa efectividad y le pregunta a este modelo qué pasa. El
 * sexo del paciente nunca interviene.</p>
 */
public class HealthModel {

    /** Menores de esta edad se deterioran más rápido. */
    public static final int INFANT_AGE_LIMIT = 5;

    /** Mayores o iguales a esta edad se deterioran más rápido. */
    public static final int ELDERLY_AGE_LIMIT = 65;

    /** Multiplicador del deterioro en bebés y adultos mayores (supuesto). */
    public static final double VULNERABLE_AGE_FACTOR = 1.25;

    private static final double ROUNDING_TOLERANCE = 1e-9;

    /**
     * Calcula los puntos de salud que pierde el paciente en un minuto.
     *
     * <p>Es la tasa de deterioro de su enfermedad, aumentada si es bebé o adulto
     * mayor y reducida según la efectividad de la atención que recibe.</p>
     *
     * @param patient       paciente
     * @param effectiveness efectividad de la atención, de 0 (sin atención) a 1
     * @return puntos de salud perdidos por minuto
     * @throws IllegalArgumentException si el paciente es nulo o la efectividad está fuera de 0 a 1
     */
    public double deteriorationPerMinute(Patient patient, double effectiveness) {
        requirePatient(patient);
        return deteriorationPerMinute(patient.getDisease(), patient.getAge(), effectiveness);
    }

    /**
     * Igual que {@link #deteriorationPerMinute(Patient, double)}, pero con la enfermedad
     * y la edad por separado. Sirve para estimar con el diagnóstico, que puede no ser
     * la enfermedad real del paciente.
     *
     * @param disease       enfermedad (o diagnóstico)
     * @param age           edad del paciente en años
     * @param effectiveness efectividad de la atención, de 0 (sin atención) a 1
     * @return puntos de salud perdidos por minuto
     * @throws IllegalArgumentException si falta la enfermedad, la edad es negativa
     *                                  o la efectividad está fuera de 0 a 1
     */
    public double deteriorationPerMinute(Disease disease, int age, double effectiveness) {
        requireDisease(disease);
        if (age < 0) {
            throw new IllegalArgumentException("La edad no puede ser negativa.");
        }
        requireEffectiveness(effectiveness, true);
        return disease.getDeteriorationRate() * ageFactor(age) * (1.0 - effectiveness);
    }

    /**
     * Calcula cuántos minutos dura el tratamiento.
     *
     * <p>Va de la duración mínima de la enfermedad (paciente sano) a la máxima
     * (paciente muy grave), y se alarga cuando la efectividad es menor que 1.</p>
     *
     * @param patient       paciente
     * @param effectiveness efectividad de la atención, mayor que 0 y hasta 1
     * @return minutos de tratamiento, redondeados hacia arriba
     * @throws IllegalArgumentException si el paciente es nulo o la efectividad no es mayor que 0 y hasta 1
     */
    public int treatmentMinutes(Patient patient, double effectiveness) {
        requirePatient(patient);
        return treatmentMinutes(patient.getDisease(), patient.getHealth(), effectiveness);
    }

    /**
     * Igual que {@link #treatmentMinutes(Patient, double)}, pero con la enfermedad y la
     * salud por separado. Sirve para estimar con el diagnóstico y con la salud que el
     * paciente tendrá cuando empiece su tratamiento.
     *
     * @param disease       enfermedad (o diagnóstico)
     * @param health        salud del paciente, de 0 a 100
     * @param effectiveness efectividad de la atención, mayor que 0 y hasta 1
     * @return minutos de tratamiento, redondeados hacia arriba
     * @throws IllegalArgumentException si falta la enfermedad, la salud está fuera de 0 a 100
     *                                  o la efectividad no es mayor que 0 y hasta 1
     */
    public int treatmentMinutes(Disease disease, double health, double effectiveness) {
        requireDisease(disease);
        if (health < 0.0 || health > Patient.MAX_HEALTH) {
            throw new IllegalArgumentException("La salud debe estar entre 0 y 100.");
        }
        requireEffectiveness(effectiveness, false);
        double severity = 1.0 - health / Patient.MAX_HEALTH;
        double base = disease.getMinDurationMinutes()
                + (disease.getMaxDurationMinutes() - disease.getMinDurationMinutes()) * severity;
        return (int) Math.ceil(base / effectiveness - ROUNDING_TOLERANCE);
    }

    /**
     * Calcula los puntos de salud que recupera el paciente al terminar el tratamiento.
     *
     * <p>Con efectividad 1 recupera toda la salud que le falta; con menos, solo
     * esa proporción.</p>
     *
     * @param patient       paciente
     * @param effectiveness efectividad de la atención, de 0 a 1
     * @return puntos de salud recuperados
     * @throws IllegalArgumentException si el paciente es nulo o la efectividad está fuera de 0 a 1
     */
    public double recoveryPoints(Patient patient, double effectiveness) {
        requirePatient(patient);
        requireEffectiveness(effectiveness, true);
        return (Patient.MAX_HEALTH - patient.getHealth()) * effectiveness;
    }

    private static double ageFactor(int age) {
        return age < INFANT_AGE_LIMIT || age >= ELDERLY_AGE_LIMIT ? VULNERABLE_AGE_FACTOR : 1.0;
    }

    private static void requireDisease(Disease disease) {
        if (disease == null) {
            throw new IllegalArgumentException("La enfermedad es obligatoria.");
        }
    }

    private static void requirePatient(Patient patient) {
        if (patient == null) {
            throw new IllegalArgumentException("El paciente es obligatorio.");
        }
    }

    private static void requireEffectiveness(double effectiveness, boolean zeroAllowed) {
        boolean tooLow = zeroAllowed ? effectiveness < 0.0 : effectiveness <= 0.0;
        if (tooLow || effectiveness > 1.0) {
            throw new IllegalArgumentException(zeroAllowed
                    ? "La efectividad debe estar entre 0 y 1."
                    : "La efectividad debe ser mayor que 0 y como máximo 1.");
        }
    }
}