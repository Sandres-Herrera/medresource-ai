package com.medresource.services.simulation;

import com.medresource.model.BloodType;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.Patient;
import com.medresource.model.Sex;
import com.medresource.model.Symptom;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la clasificación de triage.
 */
class TriageServiceTest {

    private static final Disease ANY_DISEASE = DiseaseCatalog.findById(1).orElseThrow();

    private final TriageService triage = new TriageService();

    private Patient patient(int id, int age, Sex sex, Symptom... symptoms) {
        return new Patient(id, age, sex, BloodType.O_POSITIVE, ANY_DISEASE, List.of(symptoms), 0, 90.0, false);
    }

    @Test
    void everySymptomAloneKeepsItsOwnLevel() {
        for (Symptom symptom : Symptom.values()) {
            assertEquals(symptom.getTriageLevel(), triage.classify(patient(1, 30, Sex.MALE, symptom)),
                    symptom.name());
        }
    }

    @Test
    void mostUrgentSymptomDecides() {
        Patient patient = patient(1, 30, Sex.MALE, Symptom.FEVER, Symptom.CHEST_PAIN, Symptom.FATIGUE);
        assertEquals(TriageLevel.I, triage.classify(patient));
    }

    @Test
    void symptomOrderDoesNotMatter() {
        Patient first = patient(1, 30, Sex.MALE, Symptom.FEVER, Symptom.BLEEDING);
        Patient second = patient(2, 30, Sex.MALE, Symptom.BLEEDING, Symptom.FEVER);
        assertEquals(TriageLevel.II, triage.classify(first));
        assertEquals(triage.classify(first), triage.classify(second));
    }

    @Test
    void ageAndSexDoNotChangeTheLevel() {
        TriageLevel expected = triage.classify(patient(1, 70, Sex.MALE, Symptom.ABDOMINAL_PAIN));
        assertEquals(TriageLevel.II, expected);
        assertEquals(expected, triage.classify(patient(2, 3, Sex.FEMALE, Symptom.ABDOMINAL_PAIN)));
        assertEquals(expected, triage.classify(patient(3, 25, Sex.FEMALE, Symptom.ABDOMINAL_PAIN)));
    }

    @Test
    void priorityFollowsTheSymptomsAndNotThePerson() {
        Patient boyWithFlu = patient(1, 8, Sex.MALE, Symptom.FEVER, Symptom.COUGH);
        Patient girlWithPain = patient(2, 10, Sex.FEMALE, Symptom.GENERALIZED_PAIN);
        Patient womanWhoCannotWalk = patient(3, 45, Sex.FEMALE, Symptom.LEG_PAIN, Symptom.UNABLE_TO_WALK);
        Patient unconsciousMan = patient(4, 30, Sex.MALE, Symptom.UNCONSCIOUS);

        List<Patient> order = List.of(boyWithFlu, girlWithPain, womanWhoCannotWalk, unconsciousMan).stream()
                .sorted(Comparator.comparingInt(patient -> triage.classify(patient).getLevel()))
                .toList();

        assertEquals(List.of(unconsciousMan, womanWhoCannotWalk, girlWithPain, boyWithFlu), order);
    }

    @Test
    void classifyDoesNotAssignTheLevel() {
        Patient patient = patient(1, 30, Sex.MALE, Symptom.COUGH);
        triage.classify(patient);
        assertFalse(patient.hasTriage());
    }

    @Test
    void nullPatientIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> triage.classify(null));
    }
}