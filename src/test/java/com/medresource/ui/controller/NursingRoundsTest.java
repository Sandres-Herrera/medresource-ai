package com.medresource.ui.controller;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las rondas de enfermería.
 */
class NursingRoundsTest {

    @Test
    void withoutHospitalizedPatientsNobodyMakesRounds() {
        for (int minute = 0; minute < 20; minute++) {
            assertTrue(NursingRounds.visit(0, minute, 0).isEmpty());
        }
    }

    @Test
    void halfOfTheNursesVisitWhileTheOtherHalfRestsAndThenTheyChange() {
        assertEquals(OptionalInt.of(0), NursingRounds.visit(0, 0, 4));
        assertTrue(NursingRounds.visit(1, 0, 4).isEmpty());

        assertTrue(NursingRounds.visit(0, NursingRounds.VISIT_MINUTES, 4).isEmpty());
        assertEquals(OptionalInt.of(0), NursingRounds.visit(1, NursingRounds.VISIT_MINUTES, 4));
    }

    @Test
    void aNurseStaysWithTheSamePatientDuringTheWholeTurn() {
        for (int minute = 0; minute < NursingRounds.VISIT_MINUTES; minute++) {
            assertEquals(OptionalInt.of(0), NursingRounds.visit(0, minute, 4));
        }
    }

    @Test
    void eachNewRoundGoesToTheNextPatient() {
        assertEquals(OptionalInt.of(0), NursingRounds.visit(0, 0, 3));
        assertEquals(OptionalInt.of(1), NursingRounds.visit(0, 2 * NursingRounds.VISIT_MINUTES, 3));
        assertEquals(OptionalInt.of(2), NursingRounds.visit(0, 4 * NursingRounds.VISIT_MINUTES, 3));
        assertEquals(OptionalInt.of(0), NursingRounds.visit(0, 6 * NursingRounds.VISIT_MINUTES, 3));
    }

    @Test
    void twoNursesOnRoundsAtTheSameTimeVisitDifferentPatients() {
        Set<Integer> visited = new HashSet<>();
        for (int nurse = 0; nurse < 8; nurse += 2) {
            visited.add(NursingRounds.visit(nurse, 7, 4).orElseThrow());
        }

        assertEquals(4, visited.size());
    }

    @Test
    void theNursesWhoAreLeftOverRest() {
        assertEquals(OptionalInt.of(0), NursingRounds.visit(0, 0, 1));
        assertTrue(NursingRounds.visit(2, 0, 1).isEmpty());
        assertTrue(NursingRounds.visit(4, 0, 1).isEmpty());
    }

    @Test
    void rejectsNegativeData() {
        assertThrows(IllegalArgumentException.class, () -> NursingRounds.visit(-1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> NursingRounds.visit(0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> NursingRounds.visit(0, 0, -1));
    }
}
