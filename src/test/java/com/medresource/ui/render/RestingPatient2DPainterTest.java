package com.medresource.ui.render;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.Sex;
import com.medresource.model.TriageLevel;
import com.medresource.model.ZoneType;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los pacientes que se ven acostados en la vista 2D.
 *
 * <p>Usa un reloj falso para avanzar la transición de «de pie» a «acostado» sin esperar.</p>
 */
class RestingPatient2DPainterTest {

    private static final int CELL_W = Renderer2D.CELL_W;
    private static final int CELL_H = Renderer2D.CELL_H;
    /** Lo que dura la transición, en nanosegundos, más un poco. */
    private static final long AFTER_LYING_DOWN = 500_000_000L;

    private final Renderer2D renderer = new Renderer2D();
    private final HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    private final long[] now = {1_000_000_000L};
    private final RestingPatient2DPainter painter = new RestingPatient2DPainter(() -> now[0]);

    /** Paciente quieto (o caminando) en el centro de la casilla de un recurso del plano. */
    private PatientMarker at(String code, String resource, TriageLevel triage, boolean moving, boolean deceased) {
        Point2D center = renderer.centerOf(layout.positionOf(resource).orElseThrow());
        return new PatientMarker(code, triage, Sex.FEMALE, false, deceased, center.getX(), center.getY(),
                80, 0.5, false, moving, 0);
    }

    private PatientMarker still(String code, String resource) {
        return at(code, resource, TriageLevel.III, false, false);
    }

    private BufferedImage paint() {
        BufferedImage image = new BufferedImage(layout.getCols() * CELL_W, layout.getRows() * CELL_H,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        painter.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static int count(BufferedImage image, int color, Position cell) {
        int total = 0;
        for (int y = 0; y < CELL_H; y++) {
            for (int x = 0; x < CELL_W; x++) {
                if (image.getRGB(cell.col() * CELL_W + x, cell.row() * CELL_H + y) == color) {
                    total++;
                }
            }
        }
        return total;
    }

    // ---------- quién está acostado ----------

    @Test
    void aPatientStillAtTheCenterOfABedIsLyingOnIt() {
        List<RestingPatient2DPainter.Resting> resting = RestingPatient2DPainter.locate(layout,
                List.of(still("P001", "H1-C1"), still("P002", "UCI-C1"), still("P003", "Q1")));

        assertEquals(3, resting.size());
        assertEquals(ZoneType.HOSPITALIZATION, resting.get(0).zone());
        assertEquals(ZoneType.ICU, resting.get(1).zone());
        assertEquals(ZoneType.OPERATING_ROOM, resting.get(2).zone());
        assertEquals(layout.positionOf("Q1").orElseThrow(), resting.get(2).slot());
    }

    @Test
    void aPatientWhoIsStillWalkingIsNotLyingDown() {
        assertTrue(RestingPatient2DPainter.locate(layout,
                List.of(at("P001", "H1-C1", TriageLevel.II, true, false))).isEmpty());
    }

    @Test
    void aPatientNearABedButNotAtItsCenterIsNotLyingDown() {
        PatientMarker passing = still("P001", "H1-C1");
        PatientMarker near = new PatientMarker("P001", TriageLevel.II, Sex.MALE, false, false,
                passing.x() + 5, passing.y(), 80, 0, false, false, 0);

        assertTrue(RestingPatient2DPainter.locate(layout, List.of(near)).isEmpty());
    }

    @Test
    void onlyBedsAndOperatingTablesHoldALyingPatient() {
        Point2D seat = renderer.centerOf(layout.getWaitingSpots().get(0));
        PatientMarker waiting = new PatientMarker("P001", TriageLevel.IV, Sex.MALE, false, false,
                seat.getX(), seat.getY(), 90, 0, false, false, 0);

        assertTrue(RestingPatient2DPainter.locate(layout, List.of(waiting)).isEmpty());
    }

    @Test
    void onlyOnePatientLiesOnEachBed() {
        List<RestingPatient2DPainter.Resting> resting = RestingPatient2DPainter.locate(layout,
                List.of(still("P001", "H1-C1"), still("P002", "H1-C1")));

        assertEquals(1, resting.size());
        assertEquals("P001", resting.get(0).patient().code());
    }

    @Test
    void rejectsMissingData() {
        PatientMarker patient = still("P001", "H1-C1");
        Position slot = layout.positionOf("H1-C1").orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> RestingPatient2DPainter.locate(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> RestingPatient2DPainter.locate(layout, null));
        assertThrows(IllegalArgumentException.class, () -> painter.observe(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> painter.observe(layout, null));
        assertThrows(IllegalArgumentException.class, () -> new RestingPatient2DPainter(null));
        assertThrows(IllegalArgumentException.class,
                () -> new RestingPatient2DPainter.Resting(null, slot, ZoneType.ICU));
        assertThrows(IllegalArgumentException.class,
                () -> new RestingPatient2DPainter.Resting(patient, null, ZoneType.ICU));
        assertThrows(IllegalArgumentException.class,
                () -> new RestingPatient2DPainter.Resting(patient, slot, null));
    }

    @Test
    void knowsTheCodesOfThePatientsLyingDown() {
        painter.observe(layout, List.of(still("P001", "H1-C1"), at("P002", "H1-C2", TriageLevel.I, true, false)));

        assertEquals(Set.of("P001"), painter.restingCodes());
    }

    // ---------- transición de «de pie» a «acostado» ----------

    @Test
    void lyingDownTakesLessThanHalfASecond() {
        painter.observe(layout, List.of(still("P001", "H1-C1")));
        assertTrue(painter.transitioning());

        now[0] += AFTER_LYING_DOWN;

        assertFalse(painter.transitioning());
    }

    @Test
    void movingToAnotherBedLiesDownAgain() {
        painter.observe(layout, List.of(still("P001", "H1-C1")));
        now[0] += AFTER_LYING_DOWN;

        painter.observe(layout, List.of(still("P001", "UCI-C1")));

        assertTrue(painter.transitioning());
    }

    @Test
    void stayingInTheSameBedDoesNotRepeatTheTransition() {
        painter.observe(layout, List.of(still("P001", "H1-C1")));
        now[0] += AFTER_LYING_DOWN;

        painter.observe(layout, List.of(still("P001", "H1-C1")));

        assertFalse(painter.transitioning());
    }

    @Test
    void clearingForgetsThePatientsOfThePreviousHospital() {
        painter.observe(layout, List.of(still("P001", "H1-C1")));

        painter.clear();

        assertTrue(painter.restingCodes().isEmpty());
        assertFalse(painter.transitioning());
        assertTrue(painter.pick(renderer.centerOf(layout.positionOf("H1-C1").orElseThrow()).getX(),
                renderer.centerOf(layout.positionOf("H1-C1").orElseThrow()).getY()).isEmpty());
    }

    // ---------- clic y selección ----------

    @Test
    void aClickOnTheBedFindsThePatientLyingOnIt() {
        painter.observe(layout, List.of(still("P001", "H1-C1"), still("P002", "Q1")));
        Position bed = layout.positionOf("H1-C1").orElseThrow();
        Position table = layout.positionOf("Q1").orElseThrow();

        assertEquals(Optional.of("P001"), painter.pick(bed.col() * CELL_W + 70, bed.row() * CELL_H + 40));
        assertEquals(Optional.of("P002"), painter.pick(table.col() * CELL_W + 100, table.row() * CELL_H + 25));
        assertEquals(Optional.empty(), painter.pick(bed.col() * CELL_W + 5, bed.row() * CELL_H + 5));
    }

    @Test
    void theSelectionRingSurroundsTheBedOfTheSelectedPatient() {
        painter.observe(layout, List.of(still("P001", "H1-C1")));
        BufferedImage image = new BufferedImage(layout.getCols() * CELL_W, layout.getRows() * CELL_H,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        assertTrue(painter.paintSelection(graphics, "P001"));
        assertFalse(painter.paintSelection(graphics, "P999"));
        assertFalse(painter.paintSelection(graphics, null));
        graphics.dispose();

        assertTrue(count(image, Palette.SELECTION.getRGB(), layout.positionOf("H1-C1").orElseThrow()) > 0);
    }

    // ---------- dibujo ----------

    @Test
    void whenItHasLainDownTheSheetHasTheColorOfItsTriage() {
        painter.observe(layout, List.of(at("P001", "H1-C1", TriageLevel.I, false, false)));
        now[0] += AFTER_LYING_DOWN;
        Position bed = layout.positionOf("H1-C1").orElseThrow();

        int sheet = paint().getRGB(bed.col() * CELL_W + 51, bed.row() * CELL_H + 29);

        int red = (sheet >> 16) & 0xFF;
        int green = (sheet >> 8) & 0xFF;
        int blue = sheet & 0xFF;
        assertTrue(red > green + 40 && red > blue + 40, Integer.toHexString(sheet));
    }

    @Test
    void whileLyingDownItStillLooksAsItArrived() {
        painter.observe(layout, List.of(at("P001", "H1-C1", TriageLevel.I, false, false)));
        BufferedImage arriving = paint();
        now[0] += AFTER_LYING_DOWN;

        BufferedImage lying = paint();

        Position bed = layout.positionOf("H1-C1").orElseThrow();
        int x = bed.col() * CELL_W + 51;
        int y = bed.row() * CELL_H + 29;
        assertNotEquals(arriving.getRGB(x, y), lying.getRGB(x, y));
    }

    @Test
    void showsTheHealthOfALivingPatientButNotOfADeceasedOne() {
        Position living = layout.positionOf("H1-C1").orElseThrow();
        Position deceased = layout.positionOf("H1-C2").orElseThrow();
        painter.observe(layout, List.of(at("P001", "H1-C1", TriageLevel.II, false, false),
                at("P002", "H1-C2", TriageLevel.II, false, true)));
        now[0] += AFTER_LYING_DOWN;

        BufferedImage image = paint();

        int health = Palette.health(80).getRGB();
        assertTrue(count(image, health, living) > 0);
        assertEquals(0, count(image, health, deceased));
    }
}
