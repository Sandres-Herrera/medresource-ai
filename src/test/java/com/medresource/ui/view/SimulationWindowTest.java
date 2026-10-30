package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del reloj de la ventana de simulación. La ventana no se prueba porque necesita pantalla.
 */
class SimulationWindowTest {

    @Test
    void showsTheMinuteAsDayAndTime() {
        assertEquals("día 1, 00:00", SimulationWindow.clock(0));
        assertEquals("día 1, 01:15", SimulationWindow.clock(75));
        assertEquals("día 2, 01:15", SimulationWindow.clock(1515));
    }

    @Test
    void rejectsANegativeMinute() {
        assertThrows(IllegalArgumentException.class, () -> SimulationWindow.clock(-1));
    }
}
