package com.medresource.ui.controller;

import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la información de un lugar del plano.
 */
class PlaceInfoTest {

    @Test
    void aPlaceWithoutPatientsIsFree() {
        assertTrue(new PlaceInfo("Hospitalización · H1-C1", List.of()).isFree());
        assertFalse(new PlaceInfo("Hospitalización · H1-C1", List.of(new PlaceInfo.Occupant("P001",
                TriageLevel.III, "En tratamiento", 60, 120))).isFree());
    }

    @Test
    void theTimesAreKnownOnlyDuringTheTreatment() {
        assertTrue(new PlaceInfo.Occupant("P001", TriageLevel.III, "En tratamiento", 60, 120).hasTimes());
        assertFalse(new PlaceInfo.Occupant("P001", TriageLevel.III, "Fallecido", Double.NaN, Double.NaN)
                .hasTimes());
    }

    @Test
    void rejectsMissingDataAndNegativeTimes() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceInfo(" ", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PlaceInfo("Q1", null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlaceInfo.Occupant(null, null, "En tratamiento", 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PlaceInfo.Occupant("P001", null, "En tratamiento", -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PlaceInfo.Occupant("P001", null, "En tratamiento", 0, -1));
    }
}
