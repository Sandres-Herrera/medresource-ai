package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del campo de número con «−» y «+», sin ventana.
 */
class StepperTest {

    private final Stepper stepper = new Stepper("Camas", 1, 200, 1, 30);
    private final List<Integer> heard = new ArrayList<>();

    StepperTest() {
        stepper.addChangeListener(heard::add);
    }

    @Test
    void showsItsNumber() {
        assertEquals(30, stepper.getValue());
        assertEquals("30", stepper.field().getText());
    }

    @Test
    void theButtonsGoDownAndUpOneStepAndTellTheListeners() {
        stepper.plusButton().doClick();
        stepper.plusButton().doClick();
        stepper.minusButton().doClick();

        assertEquals(31, stepper.getValue());
        assertEquals(List.of(31, 32, 31), heard);
    }

    @Test
    void theButtonsTurnOffAtTheEndsOfTheRange() {
        stepper.setValue(1);

        assertFalse(stepper.minusButton().isEnabled());
        assertTrue(stepper.plusButton().isEnabled());

        stepper.setValue(200);

        assertTrue(stepper.minusButton().isEnabled());
        assertFalse(stepper.plusButton().isEnabled());
    }

    @Test
    void neverLeavesItsRange() {
        stepper.setValue(5000);
        assertEquals(200, stepper.getValue());

        stepper.setValue(-4);
        assertEquals(1, stepper.getValue());
    }

    @Test
    void takesWhatIsTypedAdjustedToTheRange() {
        stepper.field().setText("45");
        stepper.commitText();
        assertEquals(45, stepper.getValue());

        stepper.field().setText("9999");
        stepper.commitText();
        assertEquals(200, stepper.getValue());
        assertEquals("200", stepper.field().getText());
    }

    @Test
    void anEmptyFieldGoesBackToTheLastNumber() {
        stepper.field().setText("");
        stepper.commitText();

        assertEquals(30, stepper.getValue());
        assertEquals("30", stepper.field().getText());
        assertTrue(heard.isEmpty());
    }

    @Test
    void onlyDigitsCanBeTyped() {
        stepper.field().setText("4a-5");

        assertEquals("45", stepper.field().getText());
    }

    @Test
    void doesNotTellTheListenersWhenTheNumberStaysTheSame() {
        stepper.setValue(30);
        stepper.setValue(999);
        stepper.setValue(999);

        assertEquals(List.of(200), heard);
    }

    @Test
    void stepsByTheGivenAmount() {
        Stepper patients = new Stepper("Pacientes", 1, 1000, 10, 100);

        patients.plusButton().doClick();

        assertEquals(110, patients.getValue());
    }

    @Test
    void theButtonsSayWhatTheyDo() {
        assertEquals("Bajar camas", stepper.minusButton().getToolTipText());
        assertEquals("Subir camas", stepper.plusButton().getToolTipText());
        assertEquals(Stepper.HEIGHT, stepper.getPreferredSize().height);
    }

    @Test
    void rejectsInvalidData() {
        assertThrows(IllegalArgumentException.class, () -> new Stepper(" ", 1, 2, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Stepper("Camas", 5, 2, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> new Stepper("Camas", 1, 2, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> stepper.addChangeListener(null));
    }
}
