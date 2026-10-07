package com.medresource.model;

import java.util.List;

/**
 * Paciente que llega al hospital durante la simulación.
 *
 * <p>Tiene una enfermedad real que solo conoce el simulador: las estrategias
 * de asignación deciden con lo que se ve (síntomas, triage, edad y tiempo de
 * espera). Su salud va de 0 a 100 y baja mientras no recibe atención.</p>
 */
public class Patient extends Person {

    /** Salud máxima de un paciente. */
    public static final double MAX_HEALTH = 100.0;

    private final BloodType bloodType;
    private final Disease disease;
    private final List<Symptom> symptoms;
    private final int arrivalMinute;
    private final boolean irrecoverable;

    private TriageLevel triageLevel;
    private double health;
    private PatientStatus status;
    private CauseOfDeath causeOfDeath;
    private int outcomeMinute;

    /**
     * Crea un paciente en espera y sin triage asignado.
     *
     * @param id            identificador único; el código visible será {@code P001}, {@code P002}…
     * @param age           edad en años
     * @param sex           sexo biológico
     * @param bloodType     tipo de sangre
     * @param disease       enfermedad real (oculta para las estrategias)
     * @param symptoms      síntomas que presenta al llegar; al menos uno
     * @param arrivalMinute minuto de la simulación en que llega, desde 0
     * @param initialHealth salud al llegar, mayor que 0 y hasta {@link #MAX_HEALTH}
     * @param irrecoverable {@code true} si la mortalidad irreducible de su
     *                      enfermedad lo alcanzó y morirá aunque se le atienda
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public Patient(int id, int age, Sex sex, BloodType bloodType, Disease disease,
                   List<Symptom> symptoms, int arrivalMinute, double initialHealth,
                   boolean irrecoverable) {
        super(id, String.format("P%03d", id), age, sex);
        if (bloodType == null) {
            throw new IllegalArgumentException("El tipo de sangre del paciente es obligatorio.");
        }
        if (disease == null) {
            throw new IllegalArgumentException("La enfermedad del paciente es obligatoria.");
        }
        if (!disease.affectsAge(age)) {
            throw new IllegalArgumentException(
                    "La enfermedad '" + disease.getName() + "' no afecta a una persona de " + age + " años.");
        }
        if (symptoms == null || symptoms.isEmpty()) {
            throw new IllegalArgumentException("El paciente debe presentar al menos un síntoma.");
        }
        if (arrivalMinute < 0) {
            throw new IllegalArgumentException("El minuto de llegada no puede ser negativo.");
        }
        if (initialHealth <= 0 || initialHealth > MAX_HEALTH) {
            throw new IllegalArgumentException("La salud inicial debe ser mayor que 0 y como máximo " + MAX_HEALTH + ".");
        }
        this.bloodType = bloodType;
        this.disease = disease;
        this.symptoms = List.copyOf(symptoms);
        this.arrivalMinute = arrivalMinute;
        this.irrecoverable = irrecoverable;
        this.health = initialHealth;
        this.status = PatientStatus.WAITING;
        this.outcomeMinute = -1;
    }

    /**
     * @param level nivel de triage
     * @throws IllegalArgumentException si el nivel es nulo
     * @throws IllegalStateException    si el paciente ya terminó su atención
     */
    public void assignTriage(TriageLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("El nivel de triage es obligatorio.");
        }
        requireNotFinished();
        this.triageLevel = level;
    }

    /**
     * Indica si el paciente ya fue clasificado.
     *
     * @return {@code true} si tiene nivel de triage
     */
    public boolean hasTriage() {
        return triageLevel != null;
    }

    /**
     * @param points puntos que pierde, cero o más
     * @throws IllegalArgumentException si los puntos son negativos
     * @throws IllegalStateException    si el paciente ya terminó su atención
     */
    public void deteriorate(double points) {
        if (points < 0) {
            throw new IllegalArgumentException("Los puntos de deterioro no pueden ser negativos.");
        }
        requireNotFinished();
        health = Math.max(0.0, health - points);
    }

    /**
     * Suma puntos de salud por efecto del tratamiento. La salud nunca supera
     * {@link #MAX_HEALTH}.
     *
     * @param points puntos que recupera, cero o más
     * @throws IllegalArgumentException si los puntos son negativos
     * @throws IllegalStateException    si el paciente ya terminó su atención
     */
    public void improve(double points) {
        if (points < 0) {
            throw new IllegalArgumentException("Los puntos de mejoría no pueden ser negativos.");
        }
        requireNotFinished();
        health = Math.min(MAX_HEALTH, health + points);
    }

    /**
     * @return {@code true} si su salud es mayor que cero y no ha fallecido
     */
    public boolean isAlive() {
        return health > 0 && status != PatientStatus.DECEASED;
    }

    /**
     * @param now minuto actual de la simulación
     * @return minutos de espera
     * @throws IllegalArgumentException si {@code now} es anterior a la llegada
     */
    public int waitingTime(int now) {
        if (now < arrivalMinute) {
            throw new IllegalArgumentException("El minuto actual no puede ser anterior a la llegada.");
        }
        return now - arrivalMinute;
    }

    /**
     * @throws IllegalStateException si el paciente no estaba esperando
     */
    public void startTreatment() {
        if (status != PatientStatus.WAITING) {
            throw new IllegalStateException(getCode() + " no está en espera: " + status.getDisplayName() + ".");
        }
        status = PatientStatus.IN_TREATMENT;
    }

    /**
     * @throws IllegalStateException si el paciente ya está en UCI o terminó su atención
     */
    public void admitToIcu() {
        requireNotFinished();
        if (status == PatientStatus.IN_ICU) {
            throw new IllegalStateException(getCode() + " ya está en la UCI.");
        }
        status = PatientStatus.IN_ICU;
    }

    /**
     * @param outcome estado final: alta, estabilizado, remitido o sin atender
     * @param minute  minuto de la simulación en que ocurre
     * @throws IllegalArgumentException si el estado no es final o es {@code DECEASED}
     * @throws IllegalStateException    si el paciente ya terminó su atención
     */
    public void finish(PatientStatus outcome, int minute) {
        if (outcome == null || !outcome.isFinal()) {
            throw new IllegalArgumentException("El desenlace debe ser un estado final.");
        }
        if (outcome == PatientStatus.DECEASED) {
            throw new IllegalArgumentException("Para registrar una muerte se usa die(cause, minute).");
        }
        closeCase(outcome, minute);
    }

    /**
     * @param cause  causa de la muerte
     * @param minute minuto de la simulación en que ocurre
     * @throws IllegalArgumentException si la causa es nula
     * @throws IllegalStateException    si el paciente ya terminó su atención
     */
    public void die(CauseOfDeath cause, int minute) {
        if (cause == null) {
            throw new IllegalArgumentException("La causa de muerte es obligatoria.");
        }
        closeCase(PatientStatus.DECEASED, minute);
        this.health = 0.0;
        this.causeOfDeath = cause;
    }

    /**
     * @return {@code true} si su estado es final
     */
    public boolean isFinished() {
        return status.isFinal();
    }

    @Override
    public String describe() {
        String triage = hasTriage() ? "Triage " + triageLevel.name() : "Sin triage";
        return getCode() + " · " + getAge() + " años · " + triage;
    }

    public BloodType getBloodType() {
        return bloodType;
    }

    public Disease getDisease() {
        return disease;
    }

    public List<Symptom> getSymptoms() {
        return symptoms;
    }

    public int getArrivalMinute() {
        return arrivalMinute;
    }

    public boolean isIrrecoverable() {
        return irrecoverable;
    }

    /**
     * @return nivel de triage, o {@code null} si todavía no se ha clasificado
     */
    public TriageLevel getTriageLevel() {
        return triageLevel;
    }

    public double getHealth() {
        return health;
    }

    public PatientStatus getStatus() {
        return status;
    }

    /**
     * @return causa, o {@code null} si el paciente no ha fallecido
     */
    public CauseOfDeath getCauseOfDeath() {
        return causeOfDeath;
    }

    /**
     * @return minuto del desenlace, o {@code -1} si sigue en el hospital
     */
    public int getOutcomeMinute() {
        return outcomeMinute;
    }

    private void closeCase(PatientStatus outcome, int minute) {
        requireNotFinished();
        if (minute < arrivalMinute) {
            throw new IllegalArgumentException("El desenlace no puede ocurrir antes de la llegada.");
        }
        this.status = outcome;
        this.outcomeMinute = minute;
    }

    private void requireNotFinished() {
        if (isFinished()) {
            throw new IllegalStateException(getCode() + " ya terminó su atención: " + status.getDisplayName() + ".");
        }
    }
}