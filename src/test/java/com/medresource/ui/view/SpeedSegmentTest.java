package com.medresource.ui.view;

import com.medresource.ui.controller.SimulationClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del grupo de pastillas de velocidad, sin ventana.
 */
class SpeedSegmentTest {

    private final SpeedSegment segment = new SpeedSegment();

    @Test
    void hasOnePillPerSpeed() {
        for (int speed : SimulationClock.SPEEDS) {
            assertNotNull(segment.pill(speed));
            assertEquals("×" + speed, segment.pill(speed).getName());
        }
        assertNull(segment.pill(3));
    }

    @Test
    void choosingAPillMarksItAndTellsTheListener() {
        List<Integer> heard = new ArrayList<>();
        segment.onSpeed(heard::add);

        segment.pill(5).doClick();

        assertTrue(segment.pill(5).isSelected());
        assertFalse(segment.pill(1).isSelected());
        assertEquals(List.of(5), heard);
    }

    @Test
    void marksTheSpeedItIsGiven() {
        segment.setSpeed(10);

        assertTrue(segment.pill(10).isSelected());
        assertFalse(segment.pill(2).isSelected());
    }

    @Test
    void rejectsAnUnknownSpeedOrAMissingListener() {
        assertThrows(IllegalArgumentException.class, () -> segment.setSpeed(3));
        assertThrows(IllegalArgumentException.class, () -> segment.onSpeed(null));
    }
}
