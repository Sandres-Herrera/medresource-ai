package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de los pacientes sin terminar.
 */
class UnfinishedPatientsTest {

    @Test
    void addsUpThePatientsThatWereStillInTheHospital() {
        UnfinishedPatients unfinished = new UnfinishedPatients(480, 2, 7, 11, 315, 1);

        assertEquals(20, unfinished.total());
        assertEquals(9, unfinished.stillWaiting());
        assertEquals(315, unfinished.extraMinutes());
    }

    @Test
    void rejectsNegativeNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new UnfinishedPatients(480, -1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new UnfinishedPatients(480, 0, 0, 0, -5, 0));
    }
}
