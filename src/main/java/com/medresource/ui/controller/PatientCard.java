package com.medresource.ui.controller;

import com.medresource.model.BloodType;
import com.medresource.model.Patient;
import com.medresource.model.Sex;
import com.medresource.model.Symptom;
import com.medresource.model.TriageLevel;
import com.medresource.ui.render.PatientMarker;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Ficha de un paciente tal como se ve en un instante de la simulación: quién es, qué tiene, dónde está, qué está
 * haciendo, cuánta salud le queda, cuánto lleva de su tratamiento y quién lo atiende.
 *
 * <p>La enfermedad es la real: la ficha es para quien mira la simulación, no para las estrategias, que solo ven el
 * diagnóstico.</p>
 *
 * @param code       código del paciente
 * @param age        edad en años
 * @param sex        sexo
 * @param bloodType  tipo de sangre
 * @param disease    nombre de la enfermedad
 * @param symptoms   síntomas del paciente; cada uno sabe su nombre, su nivel de triage y si impide caminar
 * @param triage     nivel de triage; nulo si todavía no lo clasifican
 * @param activity   lo que está haciendo, por ejemplo «En tratamiento»
 * @param place      dónde está o hacia dónde va, por ejemplo «Hospitalización · H1-C2»
 * @param walking    {@code true} si todavía va caminando (o en camilla) hacia ese lugar
 * @param health     salud, de 0 a {@value Patient#MAX_HEALTH}
 * @param progress   parte de su tratamiento que ya pasó, de 0 a 1; {@value PatientMarker#NO_PROGRESS} si no está en
 *                   tratamiento
 * @param arrivalMinute     minuto en que llegó al hospital
 * @param minutesInHospital minutos que lleva en el hospital
 * @param caregivers médicos y enfermeros que lo atienden en ese instante, por ejemplo «D03 · Medicina interna»
 */
public record PatientCard(String code, int age, Sex sex, BloodType bloodType, String disease, List<Symptom> symptoms,
                          TriageLevel triage, String activity, String place, boolean walking, double health,
                          double progress, int arrivalMinute, int minutesInHospital, List<String> caregivers) {

    /**
     * Valida la ficha y copia sus listas.
     *
     * @throws IllegalArgumentException si falta algún dato obligatorio, si la edad, el minuto de llegada o los
     *                                  minutos en el hospital son negativos, si la salud no está entre 0 y
     *                                  {@value Patient#MAX_HEALTH} o si el avance no está entre 0 y 1 ni es
     *                                  {@value PatientMarker#NO_PROGRESS}
     */
    public PatientCard {
        if (code == null || code.isBlank() || sex == null || bloodType == null || disease == null
                || symptoms == null || activity == null || place == null || caregivers == null) {
            throw new IllegalArgumentException("Faltan datos de la ficha del paciente.");
        }
        if (age < 0 || arrivalMinute < 0 || minutesInHospital < 0) {
            throw new IllegalArgumentException("La edad y los minutos no pueden ser negativos.");
        }
        if (!(health >= 0.0 && health <= Patient.MAX_HEALTH)) {
            throw new IllegalArgumentException("La salud debe estar entre 0 y " + Patient.MAX_HEALTH + ".");
        }
        if (progress != PatientMarker.NO_PROGRESS && !(progress >= 0.0 && progress <= 1.0)) {
            throw new IllegalArgumentException("El avance del tratamiento debe estar entre 0 y 1.");
        }
        symptoms = List.copyOf(symptoms);
        caregivers = List.copyOf(caregivers);
    }

    /**
     * Nivel de triage del síntoma más grave: es el que define el triage del paciente.
     *
     * @return el nivel más urgente de sus síntomas, o vacío si no tiene síntomas
     */
    public Optional<TriageLevel> worstSymptomLevel() {
        return symptoms.stream().map(Symptom::getTriageLevel).min(Comparator.comparingInt(TriageLevel::getLevel));
    }

    /** Indica si alguno de sus síntomas le impide caminar, así que se lo lleva en camilla. */
    public boolean cannotWalk() {
        return symptoms.stream().anyMatch(Symptom::preventsWalking);
    }

    /** Edad y sexo en una línea, por ejemplo «34 años · Masculino». */
    public String ageAndSex() {
        return age + (age == 1 ? " año · " : " años · ") + sex.getDisplayName();
    }

    /** Salud redondeada a un número entero, de 0 a 100. */
    public int healthPercent() {
        return (int) Math.round(health);
    }

    /** Indica si está en tratamiento, así que tiene un avance que mostrar. */
    public boolean inTreatment() {
        return progress != PatientMarker.NO_PROGRESS;
    }

    /** Avance del tratamiento redondeado, de 0 a 100; 0 si no está en tratamiento. */
    public int progressPercent() {
        return inTreatment() ? (int) Math.round(100 * progress) : 0;
    }
}
