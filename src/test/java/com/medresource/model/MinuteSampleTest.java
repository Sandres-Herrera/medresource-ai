package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la foto del hospital en un minuto.
 */
class MinuteSampleTest {

    private static MinuteSample sample(Map<MissingResource, Integer> blocked) {
        return new MinuteSample(30, 2, 5, 8, 3, 10, 2, 1, 6, 9, 0, blocked,
                Map.of(BloodType.O_POSITIVE, 4, BloodType.O_NEGATIVE, 0));
    }

    @Test
    void keepsTheNumbersOfTheMinute() {
        MinuteSample sample = sample(Map.of());

        assertEquals(30, sample.minute());
        assertEquals(8, sample.inTreatment());
        assertEquals(10, sample.busyBeds());
        assertEquals(6, sample.busyDoctors());
        assertEquals(7, sample.totalWaiting());
    }

    @Test
    void countsThePatientsThatLackedEachResource() {
        MinuteSample sample = sample(Map.of(MissingResource.DOCTOR, 4, MissingResource.OPERATING_ROOM, 1));

        assertEquals(4, sample.blockedBy(MissingResource.DOCTOR));
        assertEquals(1, sample.blockedBy(MissingResource.OPERATING_ROOM));
        assertEquals(0, sample.blockedBy(MissingResource.BLOOD));
    }

    @Test
    void tellsTheBloodLeftOfEachType() {
        MinuteSample sample = sample(Map.of());

        assertEquals(4, sample.bloodOf(BloodType.O_POSITIVE));
        assertEquals(0, sample.bloodOf(BloodType.O_NEGATIVE));
        assertEquals(0, sample.bloodOf(BloodType.AB_NEGATIVE));
    }

    @Test
    void keepsItsOwnCopyOfTheMaps() {
        Map<MissingResource, Integer> blocked = new EnumMap<>(MissingResource.class);
        blocked.put(MissingResource.BED, 2);
        MinuteSample sample = sample(blocked);
        blocked.put(MissingResource.BED, 9);

        assertEquals(2, sample.blockedBy(MissingResource.BED));
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new MinuteSample(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new MinuteSample(0, 1, 1, 0, 3, 0, 0, 0, 0, 0, 0,
                Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new MinuteSample(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new MinuteSample(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of(MissingResource.BED, -1), Map.of()));
    }
}
