package com.medresource.services.strategy;

import com.medresource.model.BloodType;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.Hospital;
import com.medresource.model.Patient;
import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import com.medresource.model.Symptom;
import com.medresource.model.TreatmentType;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.medresource.services.strategy.StrategyFixtures.APPENDICITIS;
import static com.medresource.services.strategy.StrategyFixtures.DENGUE;
import static com.medresource.services.strategy.StrategyFixtures.GENERAL_FLU;
import static com.medresource.services.strategy.StrategyFixtures.HEART_ATTACK;
import static com.medresource.services.strategy.StrategyFixtures.disease;
import static com.medresource.services.strategy.StrategyFixtures.hospital;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del extractor de características.
 */
class FeatureExtractorTest {

    private static final int PNEUMONIA = 4;

    private final FeatureExtractor extractor = new FeatureExtractor();

    private static WaitingPatient waiting(int diseaseId, int age, double health, Sex sex, int arrivalMinute) {
        Disease disease = disease(diseaseId);
        Patient patient = new Patient(1, age, sex, BloodType.O_POSITIVE, disease, disease.getSymptoms(),
                arrivalMinute, health, false);
        return new WaitingPatient(patient, disease);
    }

    private static WaitingPatient waiting(int diseaseId, int age, double health) {
        return waiting(diseaseId, age, health, Sex.MALE, 0);
    }

    private double[] extract(WaitingPatient candidate, Hospital hospital, int minute) {
        return extractor.extract(candidate, List.of(candidate), hospital, minute);
    }

    private double value(double[] features, String name) {
        return features[FeatureExtractor.FEATURE_NAMES.indexOf(name)];
    }

    private static void assertClose(double expected, double actual) {
        assertTrue(Math.abs(expected - actual) < 1e-9, "esperado " + expected + " pero fue " + actual);
    }

    private static Hospital fullHospital() {
        return hospital(Specialty.GENERAL_MEDICINE, Specialty.INTERNAL_MEDICINE, Specialty.SURGERY,
                Specialty.TRAUMATOLOGY);
    }

    // ---------- forma del vector ----------

    @Test
    void theVectorHasTheDeclaredSize() {
        double[] features = extract(waiting(DENGUE, 30, 80.0), fullHospital(), 0);

        assertEquals(FeatureExtractor.FEATURE_COUNT, features.length);
        assertEquals(FeatureExtractor.FEATURE_COUNT, FeatureExtractor.FEATURE_NAMES.size());
    }

    @Test
    void everyFeatureIsBetweenZeroAndOneForEveryDisease() {
        Hospital[] hospitals = {fullHospital(), hospital(0, 0, 1, Specialty.PEDIATRICS), hospital(Specialty.EMERGENCY_MEDICINE)};
        for (Disease disease : DiseaseCatalog.defaultDiseases()) {
            int age = Math.max(disease.getMinAge(), Math.min(40, disease.getMaxAge()));
            for (double health : new double[]{1.0, 55.5, 100.0}) {
                for (Hospital hospital : hospitals) {
                    double[] features = extract(waiting(disease.getId(), age, health), hospital, 5000);
                    for (int i = 0; i < features.length; i++) {
                        assertTrue(features[i] >= 0.0 && features[i] <= 1.0,
                                FeatureExtractor.FEATURE_NAMES.get(i) + " fuera de rango en " + disease + ": " + features[i]);
                    }
                }
            }
        }
    }

    @Test
    void theNamesAreUnique() {
        assertEquals(FeatureExtractor.FEATURE_COUNT, FeatureExtractor.FEATURE_NAMES.stream().distinct().count());
    }

    // ---------- justicia y diagnóstico ----------

    @Test
    void sexDoesNotChangeTheFeatures() {
        Hospital hospital = fullHospital();

        double[] man = extract(waiting(APPENDICITIS, 30, 70.0, Sex.MALE, 0), hospital, 20);
        double[] woman = extract(waiting(APPENDICITIS, 30, 70.0, Sex.FEMALE, 0), hospital, 20);

        assertTrue(Arrays.equals(man, woman));
    }

    @Test
    void theRealDiseaseIsNotUsedOnlyTheDiagnosis() {
        Hospital hospital = fullHospital();
        Disease flu = disease(GENERAL_FLU);
        Disease dengue = disease(DENGUE);
        Patient reallyDengue = new Patient(1, 30, Sex.MALE, BloodType.O_POSITIVE, dengue, dengue.getSymptoms(), 0, 80.0, false);
        Patient reallyFlu = new Patient(1, 30, Sex.MALE, BloodType.O_POSITIVE, flu, dengue.getSymptoms(), 0, 80.0, false);

        double[] first = extract(new WaitingPatient(reallyDengue, dengue), hospital, 10);
        double[] second = extract(new WaitingPatient(reallyFlu, dengue), hospital, 10);

        assertTrue(Arrays.equals(first, second));
    }

    @Test
    void theDiagnosisChangesTheFeatures() {
        Hospital hospital = fullHospital();
        Disease flu = disease(GENERAL_FLU);
        Disease dengue = disease(DENGUE);
        Patient patient = new Patient(1, 30, Sex.MALE, BloodType.O_POSITIVE, dengue, dengue.getSymptoms(), 0, 30.0, false);

        double[] asDengue = extract(new WaitingPatient(patient, dengue), hospital, 10);
        double[] asFlu = extract(new WaitingPatient(patient, flu), hospital, 10);

        assertNotEquals(value(asDengue, "minutesUntilDeath"), value(asFlu, "minutesUntilDeath"));
    }

    // ---------- valores ----------

    @Test
    void healthAndAgeAreScaled() {
        double[] features = extract(waiting(DENGUE, 30, 80.0), fullHospital(), 0);

        assertClose(0.8, value(features, "health"));
        assertClose(0.3, value(features, "age"));
    }

    @Test
    void ageIsCappedAtOneHundred() {
        Disease ageless = Disease.builder(92, "Enfermedad sin límite de edad")
                .mainSpecialty(Specialty.GENERAL_MEDICINE)
                .treatment(TreatmentType.CONSULTATION)
                .deteriorationRate(0.05)
                .durationMinutes(10, 20)
                .ageRange(0, 130)
                .symptoms(Symptom.UNCONSCIOUS)
                .build();
        Patient patient = new Patient(1, 110, Sex.MALE, BloodType.O_POSITIVE, ageless, ageless.getSymptoms(), 0, 80.0, false);

        double[] features = extract(new WaitingPatient(patient, ageless), fullHospital(), 0);

        assertClose(1.0, value(features, "age"));
    }

    @Test
    void withoutAFreeDoctorTheTreatmentTimeUsesFullEffectiveness() {
        WaitingPatient candidate = waiting(PNEUMONIA, 30, 50.0);

        double noDoctor = value(extract(candidate, hospital(Specialty.PEDIATRICS), 0), "treatmentMinutes");
        double perfectDoctor = value(extract(candidate, hospital(Specialty.INTERNAL_MEDICINE), 0), "treatmentMinutes");

        assertClose(perfectDoctor, noDoctor);
    }

    @Test
    void urgencyFollowsTheTriageLevel() {
        Hospital hospital = fullHospital();
        WaitingPatient candidate = waiting(DENGUE, 30, 80.0); // sin triage: usa el típico (III)
        assertClose(0.5, value(extract(candidate, hospital, 0), "urgency"));

        candidate.patient().assignTriage(TriageLevel.I);
        assertClose(1.0, value(extract(candidate, hospital, 0), "urgency"));

        candidate.patient().assignTriage(TriageLevel.V);
        assertClose(0.0, value(extract(candidate, hospital, 0), "urgency"));

        candidate.patient().assignTriage(TriageLevel.II);
        assertClose(0.75, value(extract(candidate, hospital, 0), "urgency"));
    }

    @Test
    void minutesUntilDeathAreScaledAndCapped() {
        // Dengue: 0,10 por minuto → 30 de salud duran 300 minutos.
        assertClose(300.0 / 480.0, value(extract(waiting(DENGUE, 30, 30.0), fullHospital(), 0), "minutesUntilDeath"));
        // Con 80 de salud serían 800 minutos: se corta en 1.
        assertClose(1.0, value(extract(waiting(DENGUE, 30, 80.0), fullHospital(), 0), "minutesUntilDeath"));
    }

    @Test
    void anUrgentPatientHasLessTimeThanAMildOne() {
        Hospital hospital = fullHospital();

        double urgent = value(extract(waiting(HEART_ATTACK, 50, 40.0), hospital, 0), "minutesUntilDeath");
        double mild = value(extract(waiting(GENERAL_FLU, 50, 40.0), hospital, 0), "minutesUntilDeath");

        assertTrue(urgent < mild);
    }

    @Test
    void waitingMinutesAreScaledFromTheArrival() {
        WaitingPatient candidate = waiting(DENGUE, 30, 80.0, Sex.MALE, 100);

        assertClose(0.0, value(extract(candidate, fullHospital(), 100), "waitingMinutes"));
        assertClose(240.0 / 480.0, value(extract(candidate, fullHospital(), 340), "waitingMinutes"));
        assertClose(1.0, value(extract(candidate, fullHospital(), 5000), "waitingMinutes"));
    }

    @Test
    void treatmentMinutesUseTheBestFreeDoctor() {
        WaitingPatient candidate = waiting(PNEUMONIA, 30, 50.0);
        Hospital expert = hospital(Specialty.INTERNAL_MEDICINE);
        Hospital noExpert = hospital(Specialty.GENERAL_MEDICINE);

        double withExpert = value(extract(candidate, expert, 0), "treatmentMinutes");
        double withoutExpert = value(extract(candidate, noExpert, 0), "treatmentMinutes");

        assertTrue(withExpert < withoutExpert);
    }

    @Test
    void bestDoctorEffectivenessIsTheBestOfTheFreeOnes() {
        WaitingPatient candidate = waiting(PNEUMONIA, 30, 80.0);

        assertClose(1.0, value(extract(candidate, hospital(Specialty.INTERNAL_MEDICINE), 0), "bestDoctorEffectiveness"));
        assertClose(0.6, value(extract(candidate, hospital(Specialty.GENERAL_MEDICINE), 0), "bestDoctorEffectiveness"));
        assertClose(1.0, value(extract(candidate, hospital(Specialty.GENERAL_MEDICINE, Specialty.INTERNAL_MEDICINE), 0),
                "bestDoctorEffectiveness"));
        assertClose(0.0, value(extract(candidate, hospital(Specialty.PEDIATRICS), 0), "bestDoctorEffectiveness"));
    }

    @Test
    void extractingDoesNotChangeThePatientOrTheHospital() {
        Hospital hospital = fullHospital();
        WaitingPatient candidate = waiting(DENGUE, 30, 80.0);

        extract(candidate, hospital, 50);

        assertEquals(80.0, candidate.patient().getHealth());
        assertEquals(4, hospital.freeDoctors().size());
        assertEquals(3, hospital.freeNurses().size());
    }

    // ---------- validación ----------

    @Test
    void missingDataIsRejected() {
        WaitingPatient candidate = waiting(DENGUE, 30, 80.0);
        Hospital hospital = fullHospital();

        assertThrows(IllegalArgumentException.class, () -> extractor.extract(null, List.of(), hospital, 0));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(candidate, null, hospital, 0));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(candidate, List.of(candidate), null, 0));
        assertThrows(IllegalArgumentException.class, () -> new FeatureExtractor(null));
    }
}