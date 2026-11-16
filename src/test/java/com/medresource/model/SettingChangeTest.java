package com.medresource.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de un cambio de configuración entre dos ejecuciones.
 */
class SettingChangeTest {

    @Test
    void describesTheChangeInOneLine() {
        SettingChange change = new SettingChange("Médicos extra", "ninguno", "1 de Cirugía");

        assertEquals("Médicos extra: ninguno → 1 de Cirugía", change.describe());
    }

    @Test
    void rejectsMissingOrEqualValues() {
        assertThrows(IllegalArgumentException.class, () -> new SettingChange(" ", "a", "b"));
        assertThrows(IllegalArgumentException.class, () -> new SettingChange("Camas", null, "b"));
        assertThrows(IllegalArgumentException.class, () -> new SettingChange("Camas", "a", ""));
        assertThrows(IllegalArgumentException.class, () -> new SettingChange("Camas", "30", "30"));
    }
}
