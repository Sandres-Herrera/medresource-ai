package com.medresource.services.strategy;

import com.medresource.model.Hospital;
import com.medresource.model.Specialty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.medresource.services.strategy.StrategyFixtures.GENERAL_FLU;
import static com.medresource.services.strategy.StrategyFixtures.hospital;
import static com.medresource.services.strategy.StrategyFixtures.waiting;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del decorador que anota las decisiones.
 */
class RecordingStrategyTest {

    private final FeatureExtractor extractor = new FeatureExtractor();
    private final FifoStrategy fifo = new FifoStrategy();
    private final RecordingStrategy recorder = new RecordingStrategy(fifo, extractor);

    @Test
    void keepsTheNameOfTheWrappedStrategy() {
        assertEquals(fifo.getName(), recorder.getName());
    }

    @Test
    void returnsTheSameDecisionAsTheWrappedStrategy() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        List<WaitingPatient> patients = List.of(waiting(2, 20, GENERAL_FLU, 30), waiting(1, 5, GENERAL_FLU, 40));

        Assignment expected = fifo.nextAssignment(60, patients, hospital).orElseThrow();
        Assignment actual = recorder.nextAssignment(60, patients, hospital).orElseThrow();

        assertEquals(expected.patient(), actual.patient());
        assertEquals(expected.doctor(), actual.doctor());
        assertEquals(expected.nurses(), actual.nurses());
    }

    @Test
    void returnsNothingWhenTheWrappedStrategyDecidesNothing() {
        Hospital noGeneralDoctor = hospital(Specialty.PEDIATRICS);

        Optional<Assignment> decision = recorder.nextAssignment(10,
                List.of(waiting(1, 0, GENERAL_FLU, 30)), noGeneralDoctor);

        assertTrue(decision.isEmpty());
    }

    @Test
    void recordsEveryWaitingPatientNotOnlyTheChosenOne() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        WaitingPatient chosen = waiting(1, 0, GENERAL_FLU, 30);
        WaitingPatient other = waiting(2, 10, GENERAL_FLU, 40);
        List<WaitingPatient> patients = List.of(chosen, other);

        recorder.nextAssignment(20, patients, hospital);

        assertArrayEquals(extractor.extract(chosen, patients, hospital, 20),
                recorder.featuresFor(chosen.patient().getCode()).orElseThrow());
        assertArrayEquals(extractor.extract(other, patients, hospital, 20),
                recorder.featuresFor(other.patient().getCode()).orElseThrow());
    }

    @Test
    void recordsTheStateBeforeTheDecisionIsApplied() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        WaitingPatient patient = waiting(1, 0, GENERAL_FLU, 30);
        List<WaitingPatient> patients = List.of(patient);
        double[] expected = extractor.extract(patient, patients, hospital, 5);

        Assignment assignment = recorder.nextAssignment(5, patients, hospital).orElseThrow();
        StrategyFixtures.apply(hospital, assignment, 100);

        assertArrayEquals(expected, recorder.featuresFor(patient.patient().getCode()).orElseThrow());
    }

    @Test
    void keepsTheLastTimeThePatientWasSeen() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        WaitingPatient patient = waiting(1, 0, GENERAL_FLU, 30);
        List<WaitingPatient> patients = List.of(patient);

        recorder.nextAssignment(5, patients, hospital);
        recorder.nextAssignment(200, patients, hospital);

        assertArrayEquals(extractor.extract(patient, patients, hospital, 200),
                recorder.featuresFor(patient.patient().getCode()).orElseThrow());
    }

    @Test
    void unknownPatientsHaveNoFeatures() {
        assertTrue(recorder.featuresFor("P999").isEmpty());
    }

    @Test
    void theReturnedFeaturesAreACopy() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        WaitingPatient patient = waiting(1, 0, GENERAL_FLU, 30);
        recorder.nextAssignment(5, List.of(patient), hospital);
        String code = patient.patient().getCode();
        double original = recorder.featuresFor(code).orElseThrow()[0];

        recorder.featuresFor(code).orElseThrow()[0] = original + 0.5;

        assertEquals(original, recorder.featuresFor(code).orElseThrow()[0]);
    }

    @Test
    void rejectsMissingArguments() {
        assertThrows(IllegalArgumentException.class, () -> new RecordingStrategy(null, extractor));
        assertThrows(IllegalArgumentException.class, () -> new RecordingStrategy(fifo, null));
    }
}