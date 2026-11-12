package com.medresource.ui.view;

import com.medresource.ui.controller.SimulationClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la barra de arriba de la ventana de la simulación, sin ventana.
 */
class SimulationTopBarTest {

    private static final String CASE = "Hospital normal · Prioridad clínica (triage) · Semilla 1";

    private final SimulationTopBar bar = new SimulationTopBar(CASE);

    @Test
    void showsTheDataOfTheCase() {
        assertEquals(CASE, bar.caseText());
    }

    @Test
    void theMainButtonIsFilledAndSwitchesBetweenPlayAndPause() {
        assertTrue(bar.playButton().isFilled());

        bar.setRunning(true);

        assertTrue(bar.isRunning());
        assertEquals(HomeIcon.PAUSE, bar.playButton().getIcon());
        assertEquals("Pausar", bar.playButton().getToolTipText());

        bar.setRunning(false);

        assertEquals(HomeIcon.PLAY, bar.playButton().getIcon());
    }

    @Test
    void choosingASpeedMarksItAndTellsTheListener() {
        List<Integer> heard = new ArrayList<>();
        bar.onSpeed(heard::add);

        bar.speedPill(5).doClick();

        assertEquals(List.of(5), heard);
        assertTrue(bar.speedPill(5).isSelected());
        for (int speed : SimulationClock.SPEEDS) {
            if (speed != 5) {
                assertFalse(bar.speedPill(speed).isSelected());
            }
        }
    }

    @Test
    void theClockShowsTheTimeAndTheDayWithItsMinute() {
        bar.showTime(20, 12);

        assertEquals("00:20:12", bar.clockText());
        assertEquals("Día 1 · minuto 20", bar.dayText());

        bar.showTime(1515, 30);

        assertEquals("01:15:30", bar.clockText());
        assertEquals("Día 2 · minuto 1515", bar.dayText());
    }

    @Test
    void theOtherButtonsHaveTheirLook() {
        assertTrue(bar.restartButton().isOutlined());
        assertTrue(bar.captureButton().isOutlined());
        assertEquals("Siguiente evento", bar.skipButton().getAccessibleContext().getAccessibleName());
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationTopBar(" "));
        assertThrows(IllegalArgumentException.class, () -> bar.setSpeed(3));
        assertThrows(IllegalArgumentException.class, () -> bar.onSpeed(null));
    }
}
