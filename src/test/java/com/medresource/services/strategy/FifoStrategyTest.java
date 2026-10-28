package com.medresource.services.strategy;

import com.medresource.model.Hospital;
import com.medresource.model.Specialty;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.medresource.services.strategy.StrategyFixtures.DENGUE;
import static com.medresource.services.strategy.StrategyFixtures.GENERAL_FLU;
import static com.medresource.services.strategy.StrategyFixtures.HEART_ATTACK;
import static com.medresource.services.strategy.StrategyFixtures.hospital;
import static com.medresource.services.strategy.StrategyFixtures.waiting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la estrategia FIFO.
 */
class FifoStrategyTest {

    private final FifoStrategy strategy = new FifoStrategy();

    @Test
    void servesTheEarliestArrivalFirst() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        List<WaitingPatient> waiting = List.of(
                waiting(2, 20, GENERAL_FLU, 30),
                waiting(1, 5, GENERAL_FLU, 30),
                waiting(3, 40, GENERAL_FLU, 30));

        Assignment assignment = strategy.nextAssignment(60, waiting, hospital).orElseThrow();

        assertEquals(1, assignment.patient().getId());
    }

    @Test
    void ignoresHowSeriousThePatientIs() {
        // Un infarto (triage I) llegó después de un dengue (triage III): igual va después.
        Hospital hospital = hospital(Specialty.INTERNAL_MEDICINE);
        List<WaitingPatient> waiting = List.of(
                waiting(2, 10, HEART_ATTACK, 60),
                waiting(1, 0, DENGUE, 30));

        Assignment assignment = strategy.nextAssignment(15, waiting, hospital).orElseThrow();

        assertEquals(1, assignment.patient().getId());
    }

    @Test
    void ignoresTheAgeOfThePatient() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        List<WaitingPatient> waiting = List.of(
                waiting(1, 0, GENERAL_FLU, 5),
                waiting(2, 1, GENERAL_FLU, 90));

        assertEquals(1, strategy.nextAssignment(10, waiting, hospital).orElseThrow().patient().getId());
    }

    @Test
    void patientsThatArrivedTogetherGoByPatientNumber() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        List<WaitingPatient> waiting = List.of(
                waiting(3, 10, GENERAL_FLU, 30),
                waiting(2, 10, GENERAL_FLU, 30));

        assertEquals(2, strategy.nextAssignment(15, waiting, hospital).orElseThrow().patient().getId());
    }

    @Test
    void theOrderOfTheListDoesNotMatter() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        WaitingPatient early = waiting(1, 0, GENERAL_FLU, 30);
        WaitingPatient late = waiting(2, 30, GENERAL_FLU, 30);

        assertEquals(1, strategy.nextAssignment(40, List.of(early, late), hospital).orElseThrow().patient().getId());
        assertEquals(1, strategy.nextAssignment(40, List.of(late, early), hospital).orElseThrow().patient().getId());
    }

    @Test
    void servesALaterPatientWhenTheFirstOneCannotBeServed() {
        // El primero necesita un internista, que no hay; el segundo solo necesita un médico general.
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        List<WaitingPatient> waiting = List.of(
                waiting(1, 0, HEART_ATTACK, 60),
                waiting(2, 5, GENERAL_FLU, 30));

        assertEquals(2, strategy.nextAssignment(10, waiting, hospital).orElseThrow().patient().getId());
    }

    @Test
    void hasAReadableName() {
        assertFalse(strategy.getName().isBlank());
        assertTrue(strategy.getName().startsWith("FIFO"));
    }
}