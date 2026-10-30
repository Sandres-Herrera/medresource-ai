package com.medresource.ui.view;

import com.medresource.model.HospitalConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la lectura de argumentos de la vista previa (la ventana en sí no se prueba).
 */
class HospitalMapDemoTest {

    @Test
    void withoutArgumentsItIsTheStandardHospital() {
        assertEquals(HospitalConfig.standard(), HospitalMapDemo.configFrom(new String[0]));
    }

    @Test
    void readsTheSevenSizesInOrder() {
        HospitalConfig config = HospitalMapDemo.configFrom(new String[]{"60", "12", "8", "32", "48", "20", "10"});

        assertEquals(new HospitalConfig(60, 12, 8, 32, 48, 20, 10), config);
    }

    @Test
    void ignoresSpacesAroundTheNumbers() {
        HospitalConfig config = HospitalMapDemo.configFrom(new String[]{" 1", "1 ", "1", "1", "1", "0", "1"});

        assertEquals(new HospitalConfig(1, 1, 1, 1, 1, 0, 1), config);
    }

    @Test
    void rejectsAWrongNumberOfArguments() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> HospitalMapDemo.configFrom(new String[]{"60", "12"}));
        assertThrows(IllegalArgumentException.class,
                () -> HospitalMapDemo.configFrom(new String[]{"60", "12", "8", "32", "48", "20", "10", "5"}));

        assertTrue(error.getMessage().contains("7"));
    }

    @Test
    void rejectsAnArgumentThatIsNotANumber() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> HospitalMapDemo.configFrom(new String[]{"60", "12", "8", "x", "48", "20", "10"}));

        assertTrue(error.getMessage().contains("argumento 4"));
        assertTrue(error.getMessage().contains("x"));
    }

    @Test
    void rejectsAValueOutsideItsRange() {
        assertThrows(IllegalArgumentException.class,
                () -> HospitalMapDemo.configFrom(new String[]{"999", "12", "8", "32", "48", "20", "10"}));
    }
}