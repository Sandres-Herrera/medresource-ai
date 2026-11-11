package com.medresource.ui.render;

import com.medresource.model.Sex;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del dibujo de los pacientes, sobre una imagen en memoria.
 *
 * <p>El paciente de prueba está en el punto (50, 50), así que su figura se centra en (68, 50) por el
 * desplazamiento a la derecha. El cuerpo ocupa de la fila 47 a la 61 y la cabeza está en la fila 41. La barra
 * de salud va de la columna 59 a la 76 en las filas 64 a 66, y la del tratamiento, de la columna 57 a la 78 en
 * las filas 29 a 31.</p>
 */
class PatientPainterTest {

    private static final int BACKGROUND = Palette.BACKGROUND.getRGB();
    private static final int CX = 68;

    private final PatientPainter painter = new PatientPainter();
    private final BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
    private final Graphics2D graphics = image.createGraphics();

    PatientPainterTest() {
        graphics.setColor(Palette.BACKGROUND);
        graphics.fillRect(0, 0, 200, 100);
    }

    private PatientMarker marker(TriageLevel triage, Sex sex, boolean minor, boolean deceased) {
        return new PatientMarker("P001", triage, sex, minor, deceased, 50, 50);
    }

    private void paint(PatientMarker... markers) {
        painter.paint(graphics, List.of(markers));
    }

    @Test
    void paintsTheBodyWithTheColorOfTheTriageLevel() {
        paint(marker(TriageLevel.I, Sex.MALE, false, false));

        assertEquals(Palette.triage(TriageLevel.I).getRGB(), image.getRGB(CX, 55));
        assertEquals(BACKGROUND, image.getRGB(50, 55));

        BufferedImage other = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D otherGraphics = other.createGraphics();
        painter.paint(otherGraphics, List.of(marker(TriageLevel.V, Sex.MALE, false, false)));

        assertEquals(Palette.triage(TriageLevel.V).getRGB(), other.getRGB(CX, 55));
    }

    @Test
    void paintsTheHeadWithTheSkinColor() {
        paint(marker(TriageLevel.I, Sex.MALE, false, false));

        assertEquals(Palette.PATIENT_HEAD.getRGB(), image.getRGB(CX, 41));
    }

    @Test
    void aManHasATrunkAndAWomanHasADressThatIsNarrowerAtTheTop() {
        paint(marker(TriageLevel.I, Sex.MALE, false, false));
        int manAtTheTop = image.getRGB(CX + 3, 49);
        BufferedImage woman = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D womanGraphics = woman.createGraphics();
        womanGraphics.setColor(Palette.BACKGROUND);
        womanGraphics.fillRect(0, 0, 200, 100);
        painter.paint(womanGraphics, List.of(marker(TriageLevel.I, Sex.FEMALE, false, false)));

        assertEquals(Palette.triage(TriageLevel.I).getRGB(), manAtTheTop);
        assertEquals(BACKGROUND, woman.getRGB(CX + 3, 49));
        assertEquals(Palette.triage(TriageLevel.I).getRGB(), woman.getRGB(CX, 55));
    }

    @Test
    void aMinorIsSmallerThanAnAdult() {
        paint(marker(TriageLevel.II, Sex.MALE, false, false));
        int adult = image.getRGB(CX, 59);
        BufferedImage child = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D childGraphics = child.createGraphics();
        childGraphics.setColor(Palette.BACKGROUND);
        childGraphics.fillRect(0, 0, 200, 100);
        painter.paint(childGraphics, List.of(marker(TriageLevel.II, Sex.MALE, true, false)));

        assertEquals(Palette.triage(TriageLevel.II).getRGB(), adult);
        assertEquals(BACKGROUND, child.getRGB(CX, 59));
        assertEquals(Palette.triage(TriageLevel.II).getRGB(), child.getRGB(CX, 54));
    }

    @Test
    void aDeceasedPatientIsGrayWhateverTheTriage() {
        paint(marker(TriageLevel.I, Sex.MALE, false, true));

        assertEquals(Palette.DECEASED.getRGB(), image.getRGB(CX, 55));
        assertEquals(Palette.DECEASED.getRGB(), image.getRGB(CX, 41));
    }

    @Test
    void aPatientWithoutTriageHasANeutralColor() {
        paint(marker(null, Sex.MALE, false, false));

        int pixel = image.getRGB(CX, 55);
        assertEquals(new Color(0x9E, 0x9E, 0x9E).getRGB(), pixel);
    }

    @Test
    void theLastPatientOfTheListIsPaintedOnTop() {
        paint(marker(TriageLevel.I, Sex.MALE, false, false), marker(TriageLevel.V, Sex.MALE, false, false));

        assertEquals(Palette.triage(TriageLevel.V).getRGB(), image.getRGB(CX, 55));
    }

    @Test
    void paintsNothingWithoutPatients() {
        paint();

        assertEquals(BACKGROUND, image.getRGB(CX, 55));
    }

    @Test
    void doesNotPaintWithMissingData() {
        assertThrows(IllegalArgumentException.class, () -> painter.paint(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> painter.paint(graphics, null));
        assertThrows(IllegalArgumentException.class, () -> painter.paintOverflow(null, new Point2D.Double(0, 0), 1));
        assertThrows(IllegalArgumentException.class, () -> painter.paintOverflow(graphics, null, 1));
    }

    @Test
    void theOverflowBadgeIsPaintedNextToTheLastSeat() {
        painter.paintOverflow(graphics, new Point2D.Double(100, 60), 95);

        assertEquals(Palette.BADGE.getRGB(), image.getRGB(116, 48));
    }

    @Test
    void thereIsNoBadgeWhenNoOneIsLeftOut() {
        painter.paintOverflow(graphics, new Point2D.Double(100, 60), 0);
        painter.paintOverflow(graphics, new Point2D.Double(100, 60), -3);

        assertEquals(BACKGROUND, image.getRGB(116, 48));
    }

    // ---------- salud y avance del tratamiento ----------

    private PatientMarker withCare(double health, double progress, boolean deceased) {
        return new PatientMarker("P001", TriageLevel.III, Sex.MALE, false, deceased, 50, 50, health, progress);
    }

    @Test
    void aHealthyPatientHasAFullGreenBarUnderHisFeet() {
        paint(withCare(100, PatientMarker.NO_PROGRESS, false));

        assertEquals(Palette.HEALTH_GOOD.getRGB(), image.getRGB(CX - 8, 65));
        assertEquals(Palette.HEALTH_GOOD.getRGB(), image.getRGB(CX + 8, 65));
    }

    @Test
    void aSickPatientHasAShortBarOfTheColorOfHisHealth() {
        paint(withCare(30, PatientMarker.NO_PROGRESS, false));

        assertEquals(Palette.HEALTH_POOR.getRGB(), image.getRGB(CX - 8, 65));
        assertEquals(Palette.BAR_TRACK.getRGB(), image.getRGB(CX + 8, 65));
    }

    @Test
    void aPatientInTreatmentHasABlueBarOverHisHeadThatFillsAsItGoesOn() {
        paint(withCare(80, 0.5, false));

        assertEquals(Palette.TREATMENT_PROGRESS.getRGB(), image.getRGB(CX - 10, 30));
        assertEquals(Palette.BAR_TRACK.getRGB(), image.getRGB(CX + 10, 30));
    }

    @Test
    void aPatientWhoIsNotInTreatmentHasNoProgressBar() {
        paint(withCare(80, PatientMarker.NO_PROGRESS, false));

        assertEquals(BACKGROUND, image.getRGB(CX - 10, 30));
    }

    @Test
    void aDeceasedPatientHasNoBars() {
        paint(withCare(0, 0.5, true));

        assertEquals(BACKGROUND, image.getRGB(CX - 8, 65));
        assertEquals(BACKGROUND, image.getRGB(CX - 10, 30));
    }

    @Test
    void anEmptyHealthBarStillShowsItsTrack() {
        paint(withCare(0, PatientMarker.NO_PROGRESS, false));

        assertEquals(Palette.BAR_TRACK.getRGB(), image.getRGB(CX - 8, 65));
        assertNotEquals(BACKGROUND, image.getRGB(CX, 65));
    }
}
