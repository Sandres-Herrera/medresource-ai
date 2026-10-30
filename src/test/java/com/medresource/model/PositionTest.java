package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la posición en el plano y del tipo de zona.
 */
class PositionTest {

    @Test
    void keepsTheColumnAndTheRow() {
        Position position = new Position(4, 7);

        assertEquals(4, position.col());
        assertEquals(7, position.row());
    }

    @Test
    void acceptsTheTopLeftCorner() {
        assertEquals(0, new Position(0, 0).col());
    }

    @Test
    void rejectsNegativeCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new Position(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Position(0, -1));
    }

    @Test
    void twoPositionsWithTheSameCoordinatesAreEqual() {
        assertEquals(new Position(2, 3), new Position(2, 3));
    }

    @Test
    void countsOnlyHorizontalAndVerticalSteps() {
        assertEquals(7, new Position(1, 2).stepsTo(new Position(4, 6)));
        assertEquals(7, new Position(4, 6).stepsTo(new Position(1, 2)));
        assertEquals(0, new Position(3, 3).stepsTo(new Position(3, 3)));
    }

    @Test
    void doesNotMeasureTheDistanceToNothing() {
        assertThrows(IllegalArgumentException.class, () -> new Position(1, 1).stepsTo(null));
    }

    @Test
    void everyZoneTypeHasADifferentDisplayName() {
        long different = Arrays.stream(ZoneType.values()).map(ZoneType::getDisplayName).distinct().count();

        assertEquals(ZoneType.values().length, different);
        assertEquals("Pasillo", ZoneType.CORRIDOR.getDisplayName());
        assertEquals("Quirófano", ZoneType.OPERATING_ROOM.getDisplayName());
    }
}