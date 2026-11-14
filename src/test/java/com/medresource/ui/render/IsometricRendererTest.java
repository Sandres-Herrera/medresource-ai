package com.medresource.ui.render;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.Sex;
import com.medresource.model.StaffSeat;
import com.medresource.model.TriageLevel;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la vista 2.5D completa sobre el hospital estándar.
 */
class IsometricRendererTest {

    private final Renderer2D flat = new Renderer2D();
    private final IsometricRenderer renderer = new IsometricRenderer(flat);
    private final HospitalLayout layout =
            new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));

    private PatientMarker patientAt(String code, Position cell) {
        Point2D point = flat.centerOf(cell);
        return new PatientMarker(code, TriageLevel.II, Sex.MALE, false, false, point.getX(), point.getY());
    }

    private BufferedImage paint(List<PatientMarker> patients, int overflow) {
        BufferedImage image = new BufferedImage(renderer.worldSize(layout).width, renderer.worldSize(layout).height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        renderer.paint(graphics, layout, patients, List.of(), List.of(), null, overflow);
        graphics.dispose();
        return image;
    }

    @Test
    void thePeopleKeepWalkingOnThePlanOfTheFlatView() {
        Position cell = new Position(4, 6);
        StaffSeat seat = layout.staffSeatOf("D01").orElseThrow();

        assertEquals(flat.centerOf(cell), renderer.centerOf(cell));
        assertEquals(flat.seatOf(seat), renderer.seatOf(seat));
    }

    @Test
    void theWorldIsTheProjectionOfThePlan() {
        assertEquals(IsoProjection.of(layout, flat).size(), renderer.worldSize(layout));
        assertSame(renderer.projectionOf(layout), renderer.projectionOf(layout), "se arma una sola vez por plano");
    }

    @Test
    void drawsTheHospitalAndItsPatients() {
        PatientMarker patient = patientAt("P001", layout.getEntrance());
        BufferedImage empty = paint(List.of(), 0);
        BufferedImage busy = paint(List.of(patient), 0);

        Point2D body = renderer.projectionOf(layout).fromPlan(patient.x(), patient.y(), 0.2);
        assertNotEquals(empty.getRGB((int) body.getX(), (int) body.getY()),
                busy.getRGB((int) body.getX(), (int) body.getY()));
        Point2D floor = renderer.projectionOf(layout).at(10.5, 5.5, 0);
        assertNotEquals(Color.BLACK.getRGB(), empty.getRGB((int) floor.getX(), (int) floor.getY()));
    }

    @Test
    void picksThePatientWhoseBodyIsUnderThePointer() {
        PatientMarker first = patientAt("P001", layout.getEntrance());
        PatientMarker second = patientAt("P002", layout.positionOf("H1-C1").orElseThrow());
        Point2D focus = renderer.focusOf(layout, second);

        assertEquals("P002", renderer.pick(layout, List.of(first, second), focus.getX() + 3, focus.getY())
                .orElseThrow());
        assertTrue(renderer.pick(layout, List.of(first, second), focus.getX() + IsometricRenderer.PICK_RADIUS + 20,
                focus.getY()).isEmpty());
    }

    @Test
    void showsHowManyWaitingPatientsDoNotFitInTheChairs() {
        BufferedImage without = paint(List.of(), 0);
        BufferedImage with = paint(List.of(), 7);

        Position last = layout.getWaitingSpots().get(layout.getWaitingSpots().size() - 1);
        Point2D badge = renderer.projectionOf(layout).at(last.col() + 0.9, last.row() + 0.5, 0.7);
        int x = (int) badge.getX() + 3;
        int y = (int) badge.getY() - 3;
        assertNotEquals(without.getRGB(x, y), with.getRGB(x, y));
    }

    @Test
    void startsLightAndRebuildsTheHospitalWithTheDarkTheme() {
        Point2D floor = renderer.projectionOf(layout).at(layout.getEntrance().col() + 1.25, 3.25, 0);
        BufferedImage light = paint(List.of(), 0);

        renderer.setTheme(IsoTheme.DARK);
        BufferedImage dark = paint(List.of(), 0);

        assertEquals(IsoTheme.DARK, renderer.getTheme());
        assertEquals(IsoTheme.DARK.color(IsoTheme.Part.BACKGROUND), renderer.background());
        Color before = new Color(light.getRGB((int) floor.getX(), (int) floor.getY()));
        Color after = new Color(dark.getRGB((int) floor.getX(), (int) floor.getY()));
        assertTrue(after.getRed() + after.getGreen() < before.getRed() + before.getGreen(), before + " " + after);
        assertThrows(IllegalArgumentException.class, () -> renderer.setTheme(null));
    }

    @Test
    void startsWithTheUsualViewAndTurnsTheHospital() {
        assertEquals(0.0, renderer.getYaw(), 0.0);
        assertEquals(IsoProjection.DEFAULT_ELEVATION, renderer.getElevation(), 0.0);
        IsoProjection before = renderer.projectionOf(layout);

        renderer.setView(-90, 60);

        assertEquals(270.0, renderer.getYaw(), 1e-9);
        assertEquals(60.0, renderer.getElevation(), 0.0);
        assertEquals(before.withView(270, 60), renderer.projectionOf(layout));
        assertEquals(renderer.projectionOf(layout).size(), renderer.worldSize(layout));
    }

    @Test
    void stillPicksThePatientAfterTurningTheHospital() {
        renderer.setView(135, 50);
        PatientMarker patient = patientAt("P002", layout.positionOf("H1-C1").orElseThrow());
        Point2D focus = renderer.focusOf(layout, patient);

        assertEquals("P002", renderer.pick(layout, List.of(patient), focus.getX(), focus.getY()).orElseThrow());
    }

    @Test
    void rejectsAViewOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> renderer.setView(Double.NaN, 40));
        assertThrows(IllegalArgumentException.class, () -> renderer.setView(0, 95));
        assertEquals(IsoProjection.DEFAULT_ELEVATION, renderer.getElevation(), 0.0);
    }

    @Test
    void theMonitorOfAGravePatientInTheIcuRaisesTheAlarm() {
        Position bed = layout.positionOf("UCI-C1").orElseThrow();
        Point2D point = flat.centerOf(bed);
        PatientMarker grave = new PatientMarker("P001", TriageLevel.I, Sex.MALE, false, false, point.getX(),
                point.getY(), 8, 0.5);
        renderer.setClock(0.1);

        paint(List.of(grave), 0);

        assertTrue(renderer.alarmOn(layout, bed));
        paint(List.of(), 0);
        assertFalse(renderer.alarmOn(layout, bed), "sin paciente la alarma se apaga");
        assertThrows(IllegalArgumentException.class, () -> renderer.setClock(Double.NaN));
    }

    @Test
    void rejectsMissingData() {
        Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();

        assertThrows(IllegalArgumentException.class, () -> new IsometricRenderer(null));
        assertThrows(IllegalArgumentException.class, () -> renderer.worldSize(null));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(null, layout));
        assertThrows(IllegalArgumentException.class,
                () -> renderer.paint(graphics, layout, List.of(), List.of(), List.of(), null, -1));
        assertThrows(IllegalArgumentException.class, () -> renderer.pick(layout, null, 0, 0));
    }
}
