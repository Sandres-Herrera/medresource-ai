package com.medresource.services.strategy;

import com.medresource.model.BloodType;
import com.medresource.model.Disease;
import com.medresource.model.HealthModel;
import com.medresource.model.Patient;
import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import com.medresource.model.Symptom;
import com.medresource.model.TreatmentType;
import org.junit.jupiter.api.Test;

import static com.medresource.services.strategy.StrategyFixtures.DENGUE;
import static com.medresource.services.strategy.StrategyFixtures.GENERAL_FLU;
import static com.medresource.services.strategy.StrategyFixtures.HEART_ATTACK;
import static com.medresource.services.strategy.StrategyFixtures.disease;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del estimador de supervivencia.
 */
class SurvivalEstimatorTest {

    private final SurvivalEstimator estimator = new SurvivalEstimator();

    /** Paciente en espera; la enfermedad real y el diagnóstico son la misma. */
    private static WaitingPatient waiting(int diseaseId, int age, double health) {
        Disease disease = disease(diseaseId);
        Patient patient = new Patient(1, age, Sex.MALE, BloodType.O_POSITIVE, disease, disease.getSymptoms(),
                0, health, false);
        return new WaitingPatient(patient, disease);
    }

    private static void assertClose(double expected, double actual) {
        assertTrue(Math.abs(expected - actual) < 1e-9, "esperado " + expected + " pero fue " + actual);
    }

    // ---------- minutesUntilDeath ----------

    @Test
    void timeUntilDeathIsHealthOverTheUntreatedRate() {
        // Dengue: 0,10 por minuto; 50 de salud duran 500 minutos.
        assertEquals(500, estimator.minutesUntilDeath(waiting(DENGUE, 30, 50.0)));
    }

    @Test
    void timeUntilDeathIsRoundedUp() {
        // 25 / 0,10 = 250 exactos; 25,05 / 0,10 = 250,5 → 251.
        assertEquals(250, estimator.minutesUntilDeath(waiting(DENGUE, 30, 25.0)));
        assertEquals(251, estimator.minutesUntilDeath(waiting(DENGUE, 30, 25.05)));
    }

    @Test
    void babiesAndTheElderlyDieSooner() {
        int adult = estimator.minutesUntilDeath(waiting(DENGUE, 30, 50.0));
        int baby = estimator.minutesUntilDeath(waiting(DENGUE, 2, 50.0));
        int elderly = estimator.minutesUntilDeath(waiting(DENGUE, 80, 50.0));

        assertEquals(400, baby);
        assertEquals(400, elderly);
        assertTrue(baby < adult);
    }

    @Test
    void aDiseaseThatDoesNotDeteriorateNeverKills() {
        Disease stable = Disease.builder(91, "Enfermedad estable")
                .mainSpecialty(Specialty.GENERAL_MEDICINE)
                .treatment(TreatmentType.CONSULTATION)
                .deteriorationRate(0.0)
                .durationMinutes(10, 20)
                .symptoms(Symptom.UNCONSCIOUS)
                .build();
        Patient patient = new Patient(1, 30, Sex.MALE, BloodType.O_POSITIVE, stable, stable.getSymptoms(),
                0, 50.0, false);

        assertEquals(Integer.MAX_VALUE, estimator.minutesUntilDeath(new WaitingPatient(patient, stable)));
    }

    // ---------- treatmentMinutes ----------

    @Test
    void treatmentTimeFollowsTheHealthModel() {
        WaitingPatient patient = waiting(DENGUE, 30, 40.0);

        assertEquals(new HealthModel().treatmentMinutes(disease(DENGUE), 40.0, 0.6),
                estimator.treatmentMinutes(patient, 0.6));
        assertTrue(estimator.treatmentMinutes(patient, 0.6) > estimator.treatmentMinutes(patient, 1.0));
    }

    // ---------- survivalProbability ----------

    @Test
    void treatingNowWithTheIdealDoctorLeavesOnlyTheIrreducibleMortality() {
        // Infarto: mortalidad irreducible 0,25.
        assertClose(0.75, estimator.survivalProbability(waiting(HEART_ATTACK, 60, 50.0), 1.0, 0));
        assertClose(1.0, estimator.survivalProbability(waiting(GENERAL_FLU, 30, 50.0), 1.0, 0));
    }

    @Test
    void aShortDelayDoesNotChangeTheOutcome() {
        assertClose(0.99, estimator.survivalProbability(waiting(DENGUE, 30, 50.0), 1.0, 100));
    }

    @Test
    void aPatientWhoWouldDieWaitingHasNoChance() {
        // 50 de salud duran 500 minutos: con 499 de demora llega vivo, con 500 ya no.
        assertClose(0.99, estimator.survivalProbability(waiting(DENGUE, 30, 50.0), 1.0, 499));
        assertClose(0.0, estimator.survivalProbability(waiting(DENGUE, 30, 50.0), 1.0, 500));
        assertClose(0.0, estimator.survivalProbability(waiting(DENGUE, 30, 50.0), 1.0, 5000));
    }

    @Test
    void theHealthAtTheStartOfTheTreatmentLengthensIt() {
        // Dengue con salud 20 y un especialista no ideal (0,6). Con 80 minutos de demora empieza con 12
        // de salud y la termina; con 92 empieza con 10,8, el tratamiento dura más y no alcanza a terminarlo.
        WaitingPatient patient = waiting(DENGUE, 30, 20.0);

        assertClose(0.99, estimator.survivalProbability(patient, 0.6, 0));
        assertClose(0.99, estimator.survivalProbability(patient, 0.6, 80));
        assertClose(0.0, estimator.survivalProbability(patient, 0.6, 92));
    }

    @Test
    void aPatientWhoEndsTheTreatmentWithoutHealthDoesNotSurvive() {
        // Pierde exactamente 50 puntos durante el tratamiento: con 50 de salud llega a cero y muere.
        Disease exact = Disease.builder(92, "Enfermedad exacta")
                .mainSpecialty(Specialty.GENERAL_MEDICINE)
                .treatment(TreatmentType.CONSULTATION)
                .deteriorationRate(0.5)
                .durationMinutes(100, 100)
                .symptoms(Symptom.UNCONSCIOUS)
                .build();
        WaitingPatient dies = new WaitingPatient(new Patient(1, 30, Sex.MALE, BloodType.O_POSITIVE, exact,
                exact.getSymptoms(), 0, 50.0, false), exact);
        WaitingPatient lives = new WaitingPatient(new Patient(2, 30, Sex.MALE, BloodType.O_POSITIVE, exact,
                exact.getSymptoms(), 0, 50.5, false), exact);

        assertClose(0.0, estimator.survivalProbability(dies, 0.5, 0));
        assertClose(1.0, estimator.survivalProbability(lives, 0.5, 0));
    }

    @Test
    void aNonIdealDoctorCanLoseAPatientDuringTheTreatment() {
        // Neumonía de un adulto con salud 3: con efectividad 0,6 se muere antes de terminar.
        WaitingPatient patient = waiting(4, 30, 3.0);

        assertClose(0.0, estimator.survivalProbability(patient, 0.6, 0));
        assertClose(0.97, estimator.survivalProbability(waiting(4, 30, 80.0), 0.6, 0));
    }

    @Test
    void theIdealDoctorStopsTheDeterioration() {
        // Con efectividad 1 el paciente no pierde salud durante el tratamiento, por grave que esté.
        assertClose(0.97, estimator.survivalProbability(waiting(4, 30, 3.0), 1.0, 0));
    }

    @Test
    void usesTheDiagnosisNotTheRealDisease() {
        // Enfermedad real: gripa (leve). Diagnóstico equivocado: infarto (mortalidad 0,25).
        Disease realDisease = disease(GENERAL_FLU);
        Patient patient = new Patient(1, 60, Sex.MALE, BloodType.O_POSITIVE, realDisease,
                realDisease.getSymptoms(), 0, 50.0, false);
        WaitingPatient misdiagnosed = new WaitingPatient(patient, disease(HEART_ATTACK));

        assertClose(0.75, estimator.survivalProbability(misdiagnosed, 1.0, 0));
        assertEquals(estimator.minutesUntilDeath(waiting(HEART_ATTACK, 60, 50.0)),
                estimator.minutesUntilDeath(misdiagnosed));
    }

    @Test
    void survivalIsConsistentWithTheTimeUntilDeath() {
        WaitingPatient patient = waiting(HEART_ATTACK, 70, 33.0);
        int limit = estimator.minutesUntilDeath(patient);

        assertTrue(estimator.survivalProbability(patient, 1.0, limit - 1) > 0.0);
        assertEquals(0.0, estimator.survivalProbability(patient, 1.0, limit));
    }

    // ---------- validación ----------

    @Test
    void rejectsInvalidArguments() {
        WaitingPatient patient = waiting(DENGUE, 30, 50.0);

        assertThrows(IllegalArgumentException.class, () -> estimator.minutesUntilDeath(null));
        assertThrows(IllegalArgumentException.class, () -> estimator.treatmentMinutes(null, 1.0));
        assertThrows(IllegalArgumentException.class, () -> estimator.treatmentMinutes(patient, 0.0));
        assertThrows(IllegalArgumentException.class, () -> estimator.survivalProbability(null, 1.0, 0));
        assertThrows(IllegalArgumentException.class, () -> estimator.survivalProbability(patient, 1.0, -1));
        assertThrows(IllegalArgumentException.class, () -> estimator.survivalProbability(patient, 0.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SurvivalEstimator(null));
    }
}
