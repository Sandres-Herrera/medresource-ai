package com.medresource.services.simulation;

import com.medresource.model.Area;
import com.medresource.model.Door;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.Route;
import com.medresource.model.ZoneType;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la búsqueda de rutas por los pasillos.
 *
 * <p>Usa un plano pequeño armado a mano, de 6 columnas por 7 filas: arriba el triage, la sala de espera y la
 * hospitalización; en medio un pasillo a todo lo ancho; abajo el banco de sangre y la morgue. Cada sala tiene
 * su puerta hacia el pasillo. También usa el plano del hospital estándar.</p>
 */
class PathFinderTest {

    private final Area triage = new Area(ZoneType.TRIAGE, "Triage", 0, 0, 2, 3);
    private final Area waiting = new Area(ZoneType.WAITING_ROOM, "Sala de espera", 2, 0, 2, 3);
    private final Area ward = new Area(ZoneType.HOSPITALIZATION, "Hospitalización", 4, 0, 2, 3);
    private final Area corridor = new Area(ZoneType.CORRIDOR, "Pasillo", 0, 3, 6, 1);
    private final Area blood = new Area(ZoneType.BLOOD_BANK, "Banco de sangre", 0, 4, 3, 3);
    private final Area morgue = new Area(ZoneType.MORGUE, "Morgue", 3, 4, 3, 3);

    private final Door waitingDoor = new Door(new Position(3, 2), new Position(3, 3));
    private final Door wardDoor = new Door(new Position(5, 2), new Position(5, 3));
    private final Door morgueDoor = new Door(new Position(4, 4), new Position(4, 3));

    private HospitalLayout smallPlan(List<Door> doors) {
        return new HospitalLayout(6, 7, List.of(triage, waiting, ward, corridor, blood, morgue),
                Map.of("B1", ward.slot(0)), triage.slot(0), List.of(triage.slot(1)), List.of(waiting.slot(0)),
                List.of(), List.of(), Map.of(), Map.of(), doors);
    }

    private final HospitalLayout small = smallPlan(List.of(waitingDoor, wardDoor, morgueDoor));
    private final PathFinder finder = new PathFinder(small);

    private static HospitalLayout standardPlan() {
        return new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    }

    /** Comprueba que cada paso de la ruta vaya a una casilla vecina sin atravesar una pared. */
    private static void assertEveryStepIsAllowed(HospitalLayout layout, Route route) {
        for (int index = 1; index < route.cells().size(); index++) {
            assertTrue(layout.canStep(route.cells().get(index - 1), route.cells().get(index)),
                    "Paso " + index + " de " + route);
        }
    }

    /** Pasos del camino más corto contados con una búsqueda a lo ancho, que no usa ninguna estimación. */
    private static int shortestSteps(HospitalLayout layout, Position from, Position to) {
        Map<Position, Integer> steps = new HashMap<>();
        Queue<Position> pending = new ArrayDeque<>();
        steps.put(from, 0);
        pending.add(from);
        while (!pending.isEmpty()) {
            Position cell = pending.poll();
            if (cell.equals(to)) {
                return steps.get(cell);
            }
            for (int[] move : new int[][]{{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
                int col = cell.col() + move[0];
                int row = cell.row() + move[1];
                if (col < 0 || row < 0) {
                    continue;
                }
                Position next = new Position(col, row);
                if (!steps.containsKey(next) && layout.canStep(cell, next)) {
                    steps.put(next, steps.get(cell) + 1);
                    pending.add(next);
                }
            }
        }
        return -1;
    }

    // ---------- plano pequeño ----------

    @Test
    void whoIsAlreadyThereHasARouteOfOneCell() {
        Route route = finder.find(waiting.slot(0), waiting.slot(0)).orElseThrow();

        assertEquals(List.of(waiting.slot(0)), route.cells());
        assertEquals(0.0, route.lengthInMeters(), 0.0);
    }

    @Test
    void insideARoomItWalksStraightToThePlace() {
        Route route = finder.find(new Position(0, 0), new Position(1, 2)).orElseThrow();

        assertEquals(new Position(0, 0), route.start());
        assertEquals(new Position(1, 2), route.end());
        assertEquals(3, route.steps());
        assertEveryStepIsAllowed(small, route);
    }

    @Test
    void toGoToAnotherRoomItLeavesByOneDoorAndEntersByTheOther() {
        Route route = finder.find(waiting.slot(0), ward.slot(0)).orElseThrow();

        assertEquals(8, route.steps());
        assertTrue(route.passesThrough(waitingDoor.inside()));
        assertTrue(route.passesThrough(waitingDoor.outside()));
        assertTrue(route.passesThrough(wardDoor.outside()));
        assertTrue(route.passesThrough(wardDoor.inside()));
        assertEveryStepIsAllowed(small, route);
    }

    @Test
    void theRouteWorksBothWaysWithTheSameLength() {
        Route there = finder.find(waiting.slot(0), morgue.slot(4)).orElseThrow();
        Route back = finder.find(morgue.slot(4), waiting.slot(0)).orElseThrow();

        assertEquals(there.steps(), back.steps());
        assertEveryStepIsAllowed(small, back);
    }

    @Test
    void thereIsNoRouteToARoomWithoutADoor() {
        assertTrue(finder.find(waiting.slot(0), blood.slot(0)).isEmpty());
        assertTrue(new PathFinder(smallPlan(List.of(waitingDoor))).find(waiting.slot(0), ward.slot(0)).isEmpty());
    }

    @Test
    void alwaysFindsTheSameRoute() {
        assertEquals(finder.find(new Position(0, 3), morgue.slot(5)), finder.find(new Position(0, 3), morgue.slot(5)));
    }

    @Test
    void rejectsMissingDataAndCellsThatCannotBeWalked() {
        assertThrows(IllegalArgumentException.class, () -> new PathFinder(null));
        assertThrows(IllegalArgumentException.class, () -> finder.find(null, waiting.slot(0)));
        assertThrows(IllegalArgumentException.class, () -> finder.find(waiting.slot(0), null));
        assertThrows(IllegalArgumentException.class, () -> finder.find(new Position(6, 0), waiting.slot(0)));
        assertThrows(IllegalArgumentException.class, () -> finder.find(waiting.slot(0), new Position(0, 7)));
    }

    // ---------- plano del hospital estándar ----------

    @Test
    void fromTheEntranceItFindsTheShortestRouteToEveryResource() {
        HospitalLayout layout = standardPlan();
        PathFinder standard = new PathFinder(layout);

        for (String code : layout.getResourceCodes()) {
            Position place = layout.positionOf(code).orElseThrow();
            Route route = standard.find(layout.getEntrance(), place).orElseThrow();

            assertEquals(shortestSteps(layout, layout.getEntrance(), place), route.steps(), code);
            assertEveryStepIsAllowed(layout, route);
        }
    }

    @Test
    void fromTheWaitingRoomToAConsultationItGoesDownTheVerticalCorridor() {
        HospitalLayout layout = standardPlan();
        Area vertical = layout.areasOf(ZoneType.CORRIDOR).stream().filter(area -> area.cols() == 2)
                .findFirst().orElseThrow();
        Position desk = layout.consultDeskOf("D01").orElseThrow();

        Route route = new PathFinder(layout).find(layout.getWaitingSpots().get(0), desk).orElseThrow();

        assertTrue(route.cells().stream().anyMatch(vertical::covers));
        assertEquals(shortestSteps(layout, route.start(), desk), route.steps());
        assertEveryStepIsAllowed(layout, route);
    }
}
