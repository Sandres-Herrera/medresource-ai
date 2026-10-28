package com.medresource.services.simulation;

import com.medresource.model.BloodType;
import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.PatientProfile;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.Specialty;
import com.medresource.model.Symptom;
import com.medresource.model.TreatmentType;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del generador de casos.
 */
class CaseGeneratorTest {

    private static final int SEEDS_TO_TRY = 20;

    private final CaseGenerator generator = new CaseGenerator();

    private static boolean isUrgent(PatientProfile profile) {
        return profile.disease().typicalTriage().isMoreUrgentThan(TriageLevel.III);
    }

    private static long countUrgent(SimulationCase simulationCase) {
        return simulationCase.profiles().stream().filter(CaseGeneratorTest::isUrgent).count();
    }

    @Test
    void sameSeedProducesTheSameCase() {
        CaseConfig config = CaseConfig.of(12345L, Scenario.NORMAL);

        assertEquals(generator.generate(config), generator.generate(config));
    }

    @Test
    void differentSeedsProduceDifferentCases() {
        assertNotEquals(generator.generate(CaseConfig.of(1L, Scenario.NORMAL)),
                generator.generate(CaseConfig.of(2L, Scenario.NORMAL)));
    }

    @Test
    void generatesTheRequestedNumberOfPatientsInEveryScenario() {
        for (Scenario scenario : Scenario.values()) {
            SimulationCase simulationCase = generator.generate(new CaseConfig(9L, scenario, 37, 300));

            assertEquals(37, simulationCase.profiles().size(), scenario.name());
        }
    }

    @Test
    void patientsAreNumberedInArrivalOrderAndArriveInsideTheDuration() {
        for (Scenario scenario : Scenario.values()) {
            SimulationCase simulationCase = generator.generate(CaseConfig.of(3L, scenario));
            List<PatientProfile> profiles = simulationCase.profiles();

            for (int i = 0; i < profiles.size(); i++) {
                assertEquals(i + 1, profiles.get(i).id(), scenario.name());
                assertTrue(profiles.get(i).arrivalMinute() >= 0, scenario.name());
                assertTrue(profiles.get(i).arrivalMinute() < 480, scenario.name());
                if (i > 0) {
                    assertTrue(profiles.get(i).arrivalMinute() >= profiles.get(i - 1).arrivalMinute(),
                            scenario.name());
                }
            }
        }
    }

    @Test
    void everyGeneratedPatientIsValidAndShowsTheSymptomsOfItsDisease() {
        for (Scenario scenario : Scenario.values()) {
            SimulationCase simulationCase = generator.generate(new CaseConfig(11L, scenario, 300, 480));

            simulationCase.createPatients();
            for (PatientProfile profile : simulationCase.profiles()) {
                assertEquals(profile.disease().getSymptoms(), profile.symptoms(), scenario.name());
                assertTrue(profile.initialHealth() > 0 && profile.initialHealth() <= 100, scenario.name());
            }
        }
    }

    @Test
    void normalScenarioSpreadsArrivalsOverTheWholeDuration() {
        for (long seed = 1; seed <= SEEDS_TO_TRY; seed++) {
            List<PatientProfile> profiles = generator.generate(CaseConfig.of(seed, Scenario.NORMAL)).profiles();

            assertTrue(profiles.get(profiles.size() - 1).arrivalMinute() >= 240, "semilla " + seed);
            assertTrue(profiles.get(0).arrivalMinute() < 240, "semilla " + seed);
        }
    }

    @Test
    void highDemandCompressesArrivalsIntoTheFirstHalf() {
        for (long seed = 1; seed <= SEEDS_TO_TRY; seed++) {
            for (PatientProfile profile : generator.generate(CaseConfig.of(seed, Scenario.HIGH_DEMAND)).profiles()) {
                assertTrue(profile.arrivalMinute() < 240, "semilla " + seed);
            }
        }
    }

    @Test
    void catastropheConcentratesMostPatientsAtTheStart() {
        for (long seed = 1; seed <= SEEDS_TO_TRY; seed++) {
            long early = generator.generate(CaseConfig.of(seed, Scenario.CATASTROPHE)).profiles().stream()
                    .filter(profile -> profile.arrivalMinute() < 48)
                    .count();

            assertTrue(early >= 70, "semilla " + seed);
        }
    }

    @Test
    void catastropheBringsMoreUrgentPatientsThanANormalDay() {
        long normal = 0;
        long catastrophe = 0;
        for (long seed = 1; seed <= SEEDS_TO_TRY; seed++) {
            normal += countUrgent(generator.generate(CaseConfig.of(seed, Scenario.NORMAL)));
            catastrophe += countUrgent(generator.generate(CaseConfig.of(seed, Scenario.CATASTROPHE)));
        }

        assertTrue(catastrophe > normal);
    }

    @Test
    void unexpectedEmergenciesAreTheOnlyUrgentPatientsAndArriveAfterTheFirstQuarter() {
        // En este catálogo la enfermedad urgente casi nunca sale por frecuencia:
        // si aparece, es porque el escenario la agregó como emergencia.
        Disease flu = DiseaseCatalog.findById(1).orElseThrow();
        Disease rareEmergency = Disease.builder(90, "Emergencia rara")
                .mainSpecialty(Specialty.EMERGENCY_MEDICINE)
                .treatment(TreatmentType.HOSPITALIZATION)
                .durationMinutes(60, 120)
                .frequency(0.0001)
                .symptoms(Symptom.UNCONSCIOUS)
                .build();
        CaseGenerator rareCatalog = new CaseGenerator(List.of(flu, rareEmergency));

        for (long seed = 1; seed <= SEEDS_TO_TRY; seed++) {
            List<PatientProfile> urgent = rareCatalog.generate(CaseConfig.of(seed, Scenario.UNEXPECTED_EMERGENCIES))
                    .profiles().stream()
                    .filter(CaseGeneratorTest::isUrgent)
                    .toList();

            assertEquals(20, urgent.size(), "semilla " + seed);
            for (PatientProfile profile : urgent) {
                assertTrue(profile.arrivalMinute() >= 120, "semilla " + seed);
            }
        }
    }

    @Test
    void moreFrequentDiseasesAppearMoreOften() {
        SimulationCase simulationCase = generator.generate(new CaseConfig(5L, Scenario.NORMAL, 1000, 480));
        long flu = simulationCase.profiles().stream().filter(p -> p.disease().getId() == 1).count();
        long bronchiolitis = simulationCase.profiles().stream().filter(p -> p.disease().getId() == 3).count();

        assertTrue(flu > bronchiolitis);
    }

    @Test
    void mostCommonBloodTypeIsOPositive() {
        SimulationCase simulationCase = generator.generate(new CaseConfig(5L, Scenario.NORMAL, 1000, 480));
        long oPositive = simulationCase.profiles().stream()
                .filter(p -> p.bloodType() == BloodType.O_POSITIVE).count();

        for (BloodType type : BloodType.values()) {
            long others = simulationCase.profiles().stream().filter(p -> p.bloodType() == type).count();
            assertTrue(oPositive >= others, type.getLabel());
        }
    }

    @Test
    void mostUrgentPatientsArriveWithLessHealthThanTheLeastUrgent() {
        SimulationCase simulationCase = generator.generate(new CaseConfig(5L, Scenario.NORMAL, 1000, 480));

        for (PatientProfile profile : simulationCase.profiles()) {
            TriageLevel triage = profile.disease().typicalTriage();
            if (triage == TriageLevel.I) {
                assertTrue(profile.initialHealth() <= 70.0);
            }
            if (triage == TriageLevel.IV || triage == TriageLevel.V) {
                assertTrue(profile.initialHealth() >= 85.0);
            }
        }
    }

    @Test
    void onlyDiseasesWithMortalityCanBeIrrecoverable() {
        SimulationCase simulationCase = generator.generate(new CaseConfig(5L, Scenario.NORMAL, 1000, 480));

        boolean anyIrrecoverable = false;
        for (PatientProfile profile : simulationCase.profiles()) {
            if (profile.irrecoverable()) {
                anyIrrecoverable = true;
                assertTrue(profile.disease().getIrreducibleMortality() > 0);
            }
        }
        assertTrue(anyIrrecoverable);
    }

    @Test
    void rejectsAnEmptyCatalog() {
        assertThrows(IllegalArgumentException.class, () -> new CaseGenerator(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CaseGenerator(null));
    }

    @Test
    void rejectsAMissingConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(null));
    }

    @Test
    void scenariosWithEmergenciesNeedUrgentDiseasesInTheCatalog() {
        Disease flu = DiseaseCatalog.findById(1).orElseThrow();
        CaseGenerator mildOnly = new CaseGenerator(List.of(flu));

        assertThrows(IllegalArgumentException.class,
                () -> mildOnly.generate(CaseConfig.of(1L, Scenario.CATASTROPHE)));
        assertThrows(IllegalArgumentException.class,
                () -> mildOnly.generate(CaseConfig.of(1L, Scenario.UNEXPECTED_EMERGENCIES)));
        assertFalse(mildOnly.generate(CaseConfig.of(1L, Scenario.NORMAL)).profiles().isEmpty());
    }
}