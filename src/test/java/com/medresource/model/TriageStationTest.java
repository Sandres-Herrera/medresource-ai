package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las estaciones de triage.
 */
class TriageStationTest {

    @Test
    void hasCodeAndDescription() {
        TriageStation station = new TriageStation(2);

        assertEquals("T2", station.getCode());
        assertEquals("T2 · Estación de triage", station.describe());
        assertTrue(station.isAvailable());
    }

    @Test
    void numberMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new TriageStation(0));
    }

    @Test
    void valuesOnePatientAtATime() {
        TriageStation station = new TriageStation(1);
        Patient patient = new Patient(1, 40, Sex.FEMALE, BloodType.A_POSITIVE,
                DiseaseCatalog.findById(1).orElseThrow(), List.of(Symptom.FEVER), 0, 90.0, false);

        station.assign(patient, 3);

        assertFalse(station.isAvailable());
        assertThrows(IllegalStateException.class, () -> station.assign(patient, 5));
        station.release();
        assertTrue(station.isAvailable());
    }

    @Test
    void readsTheNumberOfAStationCode() {
        assertEquals(1, TriageStation.numberOf("T1"));
        assertEquals(12, TriageStation.numberOf("T12"));
    }

    @Test
    void otherCodesAreNotStations() {
        assertEquals(-1, TriageStation.numberOf(null));
        assertEquals(-1, TriageStation.numberOf("T"));
        assertEquals(-1, TriageStation.numberOf("T0"));
        assertEquals(-1, TriageStation.numberOf("Q1"));
        assertEquals(-1, TriageStation.numberOf("TX"));
        assertEquals(-1, TriageStation.numberOf("H1-C1"));
    }
}
