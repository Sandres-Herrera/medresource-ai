package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de las puertas del plano.
 */
class DoorTest {

    @Test
    void joinsTwoNeighbourCells() {
        Door door = new Door(new Position(3, 6), new Position(3, 7));

        assertEquals(new Position(3, 6), door.inside());
        assertEquals(new Position(3, 7), door.outside());
    }

    @Test
    void needsBothCells() {
        assertThrows(IllegalArgumentException.class, () -> new Door(null, new Position(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new Door(new Position(0, 0), null));
    }

    @Test
    void theCellsMustTouch() {
        assertThrows(IllegalArgumentException.class, () -> new Door(new Position(0, 0), new Position(0, 2)));
        assertThrows(IllegalArgumentException.class, () -> new Door(new Position(0, 0), new Position(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> new Door(new Position(0, 0), new Position(0, 0)));
    }
}
