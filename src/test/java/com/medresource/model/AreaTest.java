package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de un área del plano.
 */
class AreaTest {

    /** Área de 3 columnas por 3 filas (título y dos filas de lugares) que empieza en la columna 2, fila 1. */
    private final Area area = new Area(ZoneType.HOSPITALIZATION, "Hospitalización", 2, 1, 3, 3);

    @Test
    void keepsItsData() {
        assertEquals(ZoneType.HOSPITALIZATION, area.zone());
        assertEquals("Hospitalización", area.title());
        assertEquals(2, area.col());
        assertEquals(1, area.row());
        assertEquals(3, area.cols());
        assertEquals(3, area.rows());
    }

    @Test
    void rejectsMissingOrBlankData() {
        assertThrows(IllegalArgumentException.class, () -> new Area(null, "Título", 0, 0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Area(ZoneType.ICU, null, 0, 0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Area(ZoneType.ICU, "  ", 0, 0, 1, 2));
    }

    @Test
    void rejectsNegativePositionsAndTooSmallSizes() {
        assertThrows(IllegalArgumentException.class, () -> new Area(ZoneType.ICU, "UCI", -1, 0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Area(ZoneType.ICU, "UCI", 0, -1, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new Area(ZoneType.ICU, "UCI", 0, 0, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new Area(ZoneType.ICU, "UCI", 0, 0, 1, 1));
    }

    @Test
    void acceptsTheSmallestArea() {
        Area smallest = new Area(ZoneType.ICU, "UCI", 0, 0, 1, 2);

        assertEquals(1, smallest.slotCount());
    }

    @Test
    void theTitleRowIsNotASlotRow() {
        assertEquals(2, area.slotRows());
        assertEquals(6, area.slotCount());
    }

    @Test
    void knowsTheFirstColumnAndRowOutsideOfIt() {
        assertEquals(5, area.right());
        assertEquals(4, area.bottom());
    }

    @Test
    void numbersTheSlotsFromLeftToRightAndFromTopToBottomBelowTheTitle() {
        assertEquals(new Position(2, 2), area.slot(0));
        assertEquals(new Position(4, 2), area.slot(2));
        assertEquals(new Position(2, 3), area.slot(3));
        assertEquals(new Position(4, 3), area.slot(5));
    }

    @Test
    void doesNotGiveASlotThatDoesNotExist() {
        assertThrows(IllegalArgumentException.class, () -> area.slot(-1));
        assertThrows(IllegalArgumentException.class, () -> area.slot(6));
    }

    @Test
    void containsItsSlotsButNotTheTitleRowNorTheOutside() {
        assertTrue(area.containsSlot(new Position(2, 2)));
        assertTrue(area.containsSlot(new Position(4, 3)));
        assertFalse(area.containsSlot(new Position(2, 1)));
        assertFalse(area.containsSlot(new Position(1, 2)));
        assertFalse(area.containsSlot(new Position(5, 2)));
        assertFalse(area.containsSlot(new Position(2, 4)));
        assertFalse(area.containsSlot(null));
    }

    @Test
    void detectsAreasThatOverlap() {
        assertTrue(area.overlaps(new Area(ZoneType.ICU, "UCI", 4, 3, 2, 2)));
        assertTrue(area.overlaps(new Area(ZoneType.ICU, "UCI", 0, 0, 3, 2)));
        assertTrue(new Area(ZoneType.ICU, "UCI", 0, 0, 3, 2).overlaps(area));
    }

    @Test
    void areasThatTouchOnlyAtTheEdgeDoNotOverlap() {
        assertFalse(area.overlaps(new Area(ZoneType.ICU, "UCI", 5, 1, 2, 2)));
        assertFalse(area.overlaps(new Area(ZoneType.ICU, "UCI", 2, 4, 2, 2)));
        assertFalse(area.overlaps(new Area(ZoneType.ICU, "UCI", 0, 1, 2, 2)));
    }

    @Test
    void doesNotCompareWithNothing() {
        assertThrows(IllegalArgumentException.class, () -> area.overlaps(null));
    }
}