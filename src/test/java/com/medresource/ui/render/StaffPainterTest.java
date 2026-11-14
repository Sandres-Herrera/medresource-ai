package com.medresource.ui.render;

import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del dibujo del personal, sobre una imagen en memoria.
 *
 * <p>La persona de prueba está en el punto (50, 50): el cuerpo ocupa de la fila 47 a la 61, la cabeza está
 * en la fila 41, el gorro del enfermero en la 35 y el código debajo de la fila 62.</p>
 */
class StaffPainterTest {

    private static final int BACKGROUND = Palette.BACKGROUND.getRGB();

    private final StaffPainter painter = new StaffPainter();
    private final BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
    private final Graphics2D graphics = image.createGraphics();

    StaffPainterTest() {
        graphics.setColor(Palette.BACKGROUND);
        graphics.fillRect(0, 0, 100, 100);
    }

    private void paint(StaffMarker marker) {
        painter.paint(graphics, List.of(marker));
    }

    @Test
    void aDoctorWearsAWhiteCoatWithTheColorOfTheirSpecialty() {
        paint(new StaffMarker("D01", Specialty.SURGERY, Sex.MALE, true, 50, 50));

        assertEquals(Palette.DOCTOR_COAT.getRGB(), image.getRGB(50, 57));
        assertEquals(Palette.specialty(Specialty.SURGERY).getRGB(), image.getRGB(50, 51));
        assertEquals(Palette.PATIENT_HEAD.getRGB(), image.getRGB(50, 41));
    }

    @Test
    void aNurseWearsABlueUniformAndAWhiteCap() {
        paint(new StaffMarker("E01", null, Sex.FEMALE, true, 50, 50));

        assertEquals(Palette.NURSE_UNIFORM.getRGB(), image.getRGB(50, 57));
        assertEquals(Palette.CARD.getRGB(), image.getRGB(50, 35));
        assertEquals(Palette.PATIENT_HEAD.getRGB(), image.getRGB(50, 41));
    }

    @Test
    void aPorterWearsAGrayBlueUniformAndAGrayCap() {
        painter.paintPorters(graphics, List.of(new PorterMarker("P001", 50, 50)));

        assertEquals(Palette.PORTER_UNIFORM.getRGB(), image.getRGB(50, 57));
        assertEquals(Palette.STRETCHER_FRAME.getRGB(), image.getRGB(50, 35));
        assertEquals(Palette.PATIENT_HEAD.getRGB(), image.getRGB(50, 41));
    }

    @Test
    void aPorterNeedsThePatientHeLooksForAndAPlace() {
        assertThrows(IllegalArgumentException.class, () -> new PorterMarker(" ", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PorterMarker("P001", Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> painter.paintPorters(graphics, null));
        assertThrows(IllegalArgumentException.class, () -> painter.paintPorters(null, List.of()));
    }

    private int labelPixels() {
        int label = 0;
        for (int y = 62; y < 75; y++) {
            for (int x = 35; x < 65; x++) {
                if (image.getRGB(x, y) != BACKGROUND) {
                    label++;
                }
            }
        }
        return label;
    }

    @Test
    void writesTheCodeUnderTheFigureWhileSeated() {
        paint(new StaffMarker("E01", null, Sex.MALE, true, 50, 50));

        assertTrue(labelPixels() > 0);
    }

    @Test
    void doesNotWriteTheCodeNextToAPatient() {
        paint(new StaffMarker("E01", null, Sex.MALE, false, 50, 50));

        assertEquals(0, labelPixels());
    }

    @Test
    void aStandingMemberOfTheStaffHasAShadowOnTheFloorButNotWhileSeated() {
        paint(new StaffMarker("E01", null, Sex.MALE, true, 50, 50));
        assertEquals(BACKGROUND, image.getRGB(58, 60));

        paint(new StaffMarker("E01", null, Sex.MALE, false, 50, 50));
        assertTrue(new Color(image.getRGB(58, 60)).getRed() < Palette.BACKGROUND.getRed());
    }

    @Test
    void paintsNothingFarFromThePoint() {
        paint(new StaffMarker("D01", Specialty.PEDIATRICS, Sex.MALE, true, 50, 50));

        assertEquals(BACKGROUND, image.getRGB(10, 10));
        assertEquals(BACKGROUND, image.getRGB(90, 50));
    }

    @Test
    void doesNotPaintWithMissingData() {
        assertThrows(IllegalArgumentException.class, () -> painter.paint(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> painter.paint(graphics, null));
    }

    @Test
    void theFigureOfTheStaffBobsWithTheIdleMotion() {
        paint(new StaffMarker("E01", null, Sex.MALE, false, 50, 50, 3.0));

        assertEquals(Palette.NURSE_UNIFORM.getRGB(), image.getRGB(50, 62));
    }
}
