package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del campo de texto con el tema oscuro, sin ventana.
 */
class ThemedTextFieldTest {

    @Test
    void keepsTheTextWithoutTheSpacesAtTheEnds() {
        ThemedTextField field = new ThemedTextField("Título", "  Reporte  ");

        assertEquals("Reporte", field.getText());
        assertEquals(0, field.field().getCaretPosition());
        field.setText(" Otro ");
        assertEquals(0, field.field().getCaretPosition());
        assertEquals("Otro", field.getText());
        field.setText(null);
        assertEquals("", field.getText());
        assertEquals("", new ThemedTextField("Curso", null).getText());
    }

    @Test
    void hasItsNameForScreenReadersAndAFixedHeight() {
        ThemedTextField field = new ThemedTextField("Docente", "");

        assertEquals("Docente", field.field().getAccessibleContext().getAccessibleName());
        assertEquals(ThemedTextField.HEIGHT, field.getPreferredSize().height);
        assertEquals(ThemedTextField.HEIGHT, field.getMaximumSize().height);
    }

    @Test
    void canBePainted() {
        ThemedTextField field = new ThemedTextField("Título", "Reporte");
        field.setSize(300, ThemedTextField.HEIGHT);
        BufferedImage image = new BufferedImage(300, ThemedTextField.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D surface = image.createGraphics();

        assertDoesNotThrow(() -> field.paint(surface));
        surface.dispose();
    }

    @Test
    void theNameIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new ThemedTextField(" ", "texto"));
        assertThrows(IllegalArgumentException.class, () -> new ThemedTextField(null, "texto"));
    }
}
