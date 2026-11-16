package com.medresource.ui.view;

import com.medresource.model.HospitalConfig;
import com.medresource.model.Specialty;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la lista de médicos extra por especialidad.
 */
class ExtraDoctorsPanelTest {

    @Test
    void showsTheExtraDoctorsOfTheHospital() {
        HospitalConfig hospital = HospitalConfig.standard()
                .withExtraDoctors(List.of(Specialty.SURGERY, Specialty.SURGERY, Specialty.PEDIATRICS));

        ExtraDoctorsPanel panel = new ExtraDoctorsPanel(hospital);

        assertEquals(2, panel.stepper(Specialty.SURGERY).getValue());
        assertEquals(1, panel.stepper(Specialty.PEDIATRICS).getValue());
        assertEquals(0, panel.stepper(Specialty.TRAUMATOLOGY).getValue());
    }

    @Test
    void tellsEveryChangeWithItsSpecialty() {
        ExtraDoctorsPanel panel = new ExtraDoctorsPanel(HospitalConfig.standard());
        List<String> changes = new ArrayList<>();
        panel.onChange((specialty, count) -> changes.add(specialty + "=" + count));

        panel.stepper(Specialty.SURGERY).plusButton().doClick();
        panel.stepper(Specialty.SURGERY).plusButton().doClick();
        panel.stepper(Specialty.PEDIATRICS).setValue(3);

        assertEquals(List.of("SURGERY=1", "SURGERY=2", "PEDIATRICS=3"), changes);
    }

    @Test
    void showingACountDoesNotTellItAsAChange() {
        ExtraDoctorsPanel panel = new ExtraDoctorsPanel(HospitalConfig.standard());
        List<String> changes = new ArrayList<>();
        panel.onChange((specialty, count) -> changes.add(specialty + "=" + count));

        panel.showCount(Specialty.SURGERY, 4);

        assertEquals(4, panel.stepper(Specialty.SURGERY).getValue());
        assertTrue(changes.isEmpty());
    }

    @Test
    void eachCountGoesFromZeroToTheMaximumOfExtraDoctors() {
        ExtraDoctorsPanel panel = new ExtraDoctorsPanel(HospitalConfig.standard());

        panel.stepper(Specialty.SURGERY).setValue(-3);
        assertEquals(0, panel.stepper(Specialty.SURGERY).getValue());
        panel.stepper(Specialty.SURGERY).setValue(99);
        assertEquals(HospitalConfig.MAX_EXTRA_DOCTORS, panel.stepper(Specialty.SURGERY).getValue());
    }

    @Test
    void rejectsMissingData() {
        ExtraDoctorsPanel panel = new ExtraDoctorsPanel(HospitalConfig.standard());

        assertThrows(IllegalArgumentException.class, () -> new ExtraDoctorsPanel(null));
        assertThrows(IllegalArgumentException.class, () -> panel.onChange(null));
        assertThrows(IllegalArgumentException.class, () -> panel.stepper(null));
        assertThrows(IllegalArgumentException.class, () -> panel.showCount(null, 1));
    }
}
