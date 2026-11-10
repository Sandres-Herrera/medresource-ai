package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la ruta que recorre una persona por el plano.
 */
class RouteTest {

    private final Route route = new Route(List.of(new Position(0, 0), new Position(1, 0), new Position(1, 1),
            new Position(1, 2)));

    @Test
    void startsAtTheFirstCellAndEndsAtTheLast() {
        assertEquals(new Position(0, 0), route.start());
        assertEquals(new Position(1, 2), route.end());
    }

    @Test
    void measuresOneMeterForEachStep() {
        assertEquals(3, route.steps());
        assertEquals(3.0, route.lengthInMeters(), 0.0);
    }

    @Test
    void aRouteOfOneCellHasNoStepsAndMeasuresZero() {
        Route still = new Route(List.of(new Position(4, 2)));

        assertEquals(still.start(), still.end());
        assertEquals(0, still.steps());
        assertEquals(0.0, still.lengthInMeters(), 0.0);
    }

    @Test
    void tellsWhichCellsItPassesThrough() {
        assertTrue(route.passesThrough(new Position(1, 1)));
        assertFalse(route.passesThrough(new Position(0, 1)));
    }

    @Test
    void cannotBeChangedFromOutside() {
        List<Position> cells = new ArrayList<>(List.of(new Position(0, 0), new Position(0, 1)));
        Route copy = new Route(cells);

        cells.add(new Position(0, 2));

        assertEquals(2, copy.cells().size());
        assertThrows(UnsupportedOperationException.class, () -> copy.cells().add(new Position(0, 2)));
    }

    @Test
    void rejectsARouteWithoutCells() {
        assertThrows(IllegalArgumentException.class, () -> new Route(null));
        assertThrows(IllegalArgumentException.class, () -> new Route(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Route(Arrays.asList(new Position(0, 0), null)));
    }

    @Test
    void rejectsAStepThatDoesNotGoToANeighbouringCell() {
        Position start = new Position(1, 1);

        assertThrows(IllegalArgumentException.class, () -> new Route(List.of(start, new Position(1, 3))));
        assertThrows(IllegalArgumentException.class, () -> new Route(List.of(start, new Position(2, 2))));
        assertThrows(IllegalArgumentException.class, () -> new Route(List.of(start, start)));
    }
}
