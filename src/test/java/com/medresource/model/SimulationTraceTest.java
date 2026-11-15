package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la traza de una ejecución.
 */
class SimulationTraceTest {

    private static MinuteSample sample(int minute) {
        return new MinuteSample(minute, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(MissingResource.DOCTOR, 1), Map.of());
    }

    private static CareRecord care(String patient) {
        return new CareRecord(patient, BloodType.O_POSITIVE, 1, 31, 31, "D01", Specialty.GENERAL_MEDICINE,
                List.of(), null, null, false, Map.of(), 70.0);
    }

    private static SimulationTrace trace(List<CareRecord> cares, List<MinuteSample> samples) {
        return new SimulationTrace(Map.of("P001", 90.0, "P002", 60.0), cares, samples,
                Map.of("P001", Map.of(MissingResource.DOCTOR, 1)), Map.of("D01", 30),
                Map.of("P002", 4), Map.of(BloodType.O_POSITIVE, 10));
    }

    @Test
    void findsTheCareOfAPatient() {
        SimulationTrace trace = trace(List.of(care("P001")), List.of(sample(0), sample(1)));

        assertEquals("D01", trace.careOf("P001").orElseThrow().doctorCode());
        assertTrue(trace.careOf("P002").isEmpty());
    }

    @Test
    void findsTheSampleOfAMinute() {
        SimulationTrace trace = trace(List.of(), List.of(sample(0), sample(1)));

        assertEquals(1, trace.sampleAt(1).orElseThrow().minute());
        assertTrue(trace.sampleAt(2).isEmpty());
        assertTrue(trace.sampleAt(-1).isEmpty());
    }

    @Test
    void answersWhatEachPatientAndResourceWentThrough() {
        SimulationTrace trace = trace(List.of(), List.of());

        assertEquals(Map.of(MissingResource.DOCTOR, 1), trace.blockedMinutesOf("P001"));
        assertEquals(Map.of(), trace.blockedMinutesOf("P002"));
        assertEquals(30, trace.busyMinutesOf("D01"));
        assertEquals(0, trace.busyMinutesOf("Q1"));
        assertEquals(60.0, trace.arrivalHealthOf("P002"), 0.0);
        assertThrows(IllegalArgumentException.class, () -> trace.arrivalHealthOf("P009"));
    }

    @Test
    void theSamplesMustGoOnePerMinuteFromZero() {
        assertThrows(IllegalArgumentException.class, () -> trace(List.of(), List.of(sample(0), sample(2))));
        assertThrows(IllegalArgumentException.class, () -> trace(List.of(), List.of(sample(1))));
    }

    @Test
    void aPatientHasAtMostOneCare() {
        assertThrows(IllegalArgumentException.class, () -> trace(List.of(care("P001"), care("P001")), List.of()));
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationTrace(null, List.of(), List.of(),
                Map.of(), Map.of(), Map.of(), Map.of()));
    }
}
