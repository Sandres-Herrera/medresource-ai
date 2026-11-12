package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la barra del plano a pantalla completa, sin ventana.
 */
class FullscreenBarTest {

    private final FullscreenBar bar = new FullscreenBar();

    @Test
    void startsPausedWithoutFollowingAnybody() {
        assertFalse(bar.isRunning());
        assertEquals("Reproducir", bar.playButton().getAccessibleContext().getAccessibleName());
        assertEquals("", bar.followText());
        assertEquals("Salir de pantalla completa", bar.exitButton().getAccessibleContext().getAccessibleName());
    }

    @Test
    void thePlayButtonSaysPauseWhileRunning() {
        bar.setRunning(true);

        assertTrue(bar.isRunning());
        assertEquals("Pausar", bar.playButton().getAccessibleContext().getAccessibleName());
    }

    @Test
    void showsTheClockAndTheDay() {
        bar.showTime(20, 12);

        assertEquals("00:20:12", bar.clockText());
        assertEquals("Día 1 · minuto 20", bar.dayText());
    }

    @Test
    void choosingASpeedMarksItAndTellsTheListener() {
        List<Integer> heard = new ArrayList<>();
        bar.onSpeed(heard::add);

        bar.speedPill(10).doClick();

        assertTrue(bar.speedPill(10).isSelected());
        assertEquals(List.of(10), heard);

        bar.setSpeed(2);

        assertTrue(bar.speedPill(2).isSelected());
        assertFalse(bar.speedPill(10).isSelected());
    }

    @Test
    void saysWhoTheCameraIsFollowing() {
        bar.showFollowing("P003");

        assertEquals("Siguiendo a P003", bar.followText());

        bar.showFollowing(null);

        assertEquals("", bar.followText());
    }

    @Test
    void theButtonsTellTheirListeners() {
        List<String> heard = new ArrayList<>();
        bar.playButton().addActionListener(event -> heard.add("reproducir"));
        bar.skipButton().addActionListener(event -> heard.add("siguiente"));
        bar.exitButton().addActionListener(event -> heard.add("salir"));

        bar.playButton().doClick();
        bar.skipButton().doClick();
        bar.exitButton().doClick();

        assertEquals(List.of("reproducir", "siguiente", "salir"), heard);
    }
}
