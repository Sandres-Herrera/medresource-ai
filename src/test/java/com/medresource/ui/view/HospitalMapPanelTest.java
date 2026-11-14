package com.medresource.ui.view;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;
import com.medresource.model.ZoneType;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.ui.controller.PlaceInfo;
import com.medresource.ui.render.Camera;
import com.medresource.ui.render.IsoProjection;
import com.medresource.ui.render.IsoTheme;
import com.medresource.ui.render.IsometricRenderer;
import com.medresource.ui.render.Palette;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.PatientPainter;
import com.medresource.ui.render.PlanAlert;
import com.medresource.ui.render.PorterMarker;
import com.medresource.ui.render.Renderer2D;
import com.medresource.ui.render.StaffMarker;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del panel que muestra el plano del hospital.
 *
 * <p>Dibuja en imágenes en memoria, así no necesita pantalla.</p>
 */
class HospitalMapPanelTest {

    private static final int MARGIN = HospitalMapPanel.MARGIN;
    private static final int CELL_W = Renderer2D.CELL_W;
    private static final int CELL_H = Renderer2D.CELL_H;

    private final Renderer2D renderer = new Renderer2D();
    private final HospitalLayout standard = layoutOf(HospitalConfig.standard());
    private final HospitalMapPanel panel = new HospitalMapPanel(standard, renderer);

    private static HospitalLayout layoutOf(HospitalConfig config) {
        return new LayoutFactory().create(new HospitalFactory().create(config));
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new HospitalMapPanel(null, renderer));
        assertThrows(IllegalArgumentException.class, () -> new HospitalMapPanel(standard, null));
        assertThrows(IllegalArgumentException.class, () -> panel.showLayout(null));
    }

    @Test
    void thePictureHasTheRequestedSize() {
        BufferedImage image = panel.toImage(300, 200);

        assertEquals(300, image.getWidth());
        assertEquals(200, image.getHeight());
    }

    @Test
    void doesNotDrawAPictureWithoutSize() {
        assertThrows(IllegalArgumentException.class, () -> panel.toImage(0, 100));
        assertThrows(IllegalArgumentException.class, () -> panel.toImage(100, 0));
    }

    @Test
    void theBackgroundSurroundsThePlan() {
        BufferedImage image = panel.toImage(1000, 700);

        assertEquals(Palette.BACKGROUND.getRGB(), image.getRGB(0, 0));
        assertEquals(Palette.BACKGROUND.getRGB(), image.getRGB(999, 699));
    }

    @Test
    void thePlanKeepsItsRealSizeWhenThePictureJustFitsIt() {
        Dimension world = renderer.worldSize(standard);
        BufferedImage image = panel.toImage(world.width + 2 * MARGIN, world.height + 2 * MARGIN);

        Position bed = standard.positionOf("H1-C1").orElseThrow();

        assertEquals(Palette.zone(ZoneType.TRIAGE).getRGB(), image.getRGB(MARGIN + 40, MARGIN + 5));
        assertEquals(Palette.CARD.getRGB(), image.getRGB(MARGIN + bed.col() * CELL_W + 12, MARGIN + bed.row() * CELL_H + 12));
        assertEquals(Palette.BACKGROUND.getRGB(), image.getRGB(MARGIN - 1, MARGIN - 1));
    }

    @Test
    void thePlanShrinksToFitASmallerPicture() {
        Dimension world = renderer.worldSize(standard);
        BufferedImage image = panel.toImage(world.width / 2 + 2 * MARGIN, world.height / 2 + 2 * MARGIN);

        assertEquals(Palette.zone(ZoneType.TRIAGE).getRGB(), image.getRGB(MARGIN + 20, MARGIN + 3));
        assertEquals(Palette.BACKGROUND.getRGB(), image.getRGB(MARGIN - 1, MARGIN - 1));
    }

    @Test
    void aPictureNotBiggerThanTheMarginsOnlyHasTheBackground() {
        BufferedImage image = panel.toImage(2 * MARGIN, 2 * MARGIN);

        assertEquals(Palette.BACKGROUND.getRGB(), image.getRGB(MARGIN, MARGIN));
    }

    @Test
    void paintsItselfWhenSwingAsksIt() {
        Dimension world = renderer.worldSize(standard);
        int width = world.width + 2 * MARGIN;
        int height = world.height + 2 * MARGIN;
        panel.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        panel.paint(graphics);
        graphics.dispose();

        assertEquals(Palette.zone(ZoneType.TRIAGE).getRGB(), image.getRGB(MARGIN + 40, MARGIN + 5));
    }

    @Test
    void showsTheNewPlanWhenItIsChanged() {
        double before = panel.cameraFor(3000, 3000).orElseThrow().zoom();

        panel.showLayout(layoutOf(new HospitalConfig(1, 1, 1, 1, 1, 0, 1)));

        assertNotEquals(before, panel.cameraFor(3000, 3000).orElseThrow().zoom());
    }

    @Test
    void thePreferredSizeIsTheWorldPlusTheMarginsButNeverBiggerThanAScreen() {
        HospitalLayout small = layoutOf(new HospitalConfig(1, 1, 1, 1, 1, 0, 1));
        Dimension smallWorld = renderer.worldSize(small);

        panel.showLayout(small);

        assertEquals(new Dimension(Math.min(smallWorld.width, HospitalMapPanel.MAX_PREFERRED_WIDTH) + 2 * MARGIN,
                Math.min(smallWorld.height, HospitalMapPanel.MAX_PREFERRED_HEIGHT) + 2 * MARGIN),
                panel.getPreferredSize());

        panel.showLayout(layoutOf(new HospitalConfig(200, 50, 20, 50, 100, 100, 50)));

        assertEquals(new Dimension(HospitalMapPanel.MAX_PREFERRED_WIDTH + 2 * MARGIN,
                HospitalMapPanel.MAX_PREFERRED_HEIGHT + 2 * MARGIN), panel.getPreferredSize());
    }

    // ---------- pacientes ----------

    private BufferedImage fullSizePicture() {
        Dimension world = renderer.worldSize(standard);
        return panel.toImage(world.width + 2 * MARGIN, world.height + 2 * MARGIN);
    }

    @Test
    void showsThePatientsOnTopOfThePlan() {
        Point2D seat = renderer.centerOf(standard.getWaitingSpots().get(0));
        panel.showPatients(List.of(new PatientMarker("P001", TriageLevel.I, Sex.MALE, false, false,
                seat.getX(), seat.getY())), 0);

        BufferedImage image = fullSizePicture();

        int x = MARGIN + (int) seat.getX() + PatientPainter.OFFSET_X;
        int y = MARGIN + (int) seat.getY();
        assertEquals(Palette.triage(TriageLevel.I).getRGB(), image.getRGB(x, y + 5));
    }

    @Test
    void noLongerShowsThePatientsWhenTheyAreRemoved() {
        Point2D seat = renderer.centerOf(standard.getWaitingSpots().get(0));
        panel.showPatients(List.of(new PatientMarker("P001", TriageLevel.I, Sex.MALE, false, false,
                seat.getX(), seat.getY())), 0);
        int x = MARGIN + (int) seat.getX() + PatientPainter.OFFSET_X;
        int y = MARGIN + (int) seat.getY() + 5;
        int withPatient = fullSizePicture().getRGB(x, y);

        panel.showPatients(List.of(), 0);

        assertNotEquals(withPatient, fullSizePicture().getRGB(x, y));
    }

    @Test
    void showsTheBadgeOfThePatientsWhoDoNotFitInTheWaitingRoom() {
        List<Position> seats = standard.getWaitingSpots();
        Point2D last = renderer.centerOf(seats.get(seats.size() - 1));
        int x = MARGIN + (int) last.getX() + 16;
        int y = MARGIN + (int) last.getY() - 12;
        int without = fullSizePicture().getRGB(x, y);

        panel.showPatients(List.of(), 7);

        assertNotEquals(without, fullSizePicture().getRGB(x, y));
        assertEquals(Palette.BADGE.getRGB(), fullSizePicture().getRGB(x, y));
    }

    @Test
    void doesNotShowMissingPatientsOrANegativeOverflow() {
        assertThrows(IllegalArgumentException.class, () -> panel.showPatients(null, 0));
        assertThrows(IllegalArgumentException.class, () -> panel.showPatients(List.of(), -1));
    }

    // ---------- personal ----------

    @Test
    void showsTheStaffOnTopOfThePlan() {
        Point2D seat = renderer.seatOf(standard.staffSeatOf("E01").orElseThrow());
        int x = MARGIN + (int) seat.getX();
        int y = MARGIN + (int) seat.getY() + 7;
        int without = fullSizePicture().getRGB(x, y);

        panel.showStaff(List.of(new StaffMarker("E01", null, Sex.FEMALE, true, seat.getX(), seat.getY())));

        assertNotEquals(without, fullSizePicture().getRGB(x, y));
        assertEquals(Palette.NURSE_UNIFORM.getRGB(), fullSizePicture().getRGB(x, y));
    }

    @Test
    void doesNotShowAMissingStaff() {
        assertThrows(IllegalArgumentException.class, () -> panel.showStaff(null));
    }

    // ---------- selección y cámara que sigue ----------

    private Point2D showOnePatientOnTheFirstSeat() {
        Point2D seat = renderer.centerOf(standard.getWaitingSpots().get(0));
        panel.showPatients(List.of(new PatientMarker("P001", TriageLevel.III, Sex.FEMALE, false, false,
                seat.getX(), seat.getY())), 0);
        return seat;
    }

    private void fullSizePanel() {
        Dimension world = renderer.worldSize(standard);
        panel.setSize(world.width + 2 * MARGIN, world.height + 2 * MARGIN);
    }

    @Test
    void findsThePatientUnderAPointOfThePanel() {
        Point2D seat = showOnePatientOnTheFirstSeat();
        fullSizePanel();
        int x = MARGIN + (int) seat.getX() + PatientPainter.OFFSET_X;
        int y = MARGIN + (int) seat.getY();

        assertEquals("P001", panel.patientAt(x, y).orElseThrow());
        assertTrue(panel.patientAt(MARGIN, MARGIN).isEmpty());
    }

    @Test
    void findsNobodyInAPanelWithoutSize() {
        showOnePatientOnTheFirstSeat();

        assertTrue(panel.patientAt(0, 0).isEmpty());
        assertTrue(panel.cameraFor(2 * MARGIN, 100).isEmpty());
    }

    @Test
    void theSelectedPatientIsPaintedWithARing() {
        Point2D seat = showOnePatientOnTheFirstSeat();
        int x = MARGIN + (int) (seat.getX() + PatientPainter.OFFSET_X + PatientPainter.SELECTION_RADIUS);
        int y = MARGIN + (int) seat.getY();

        assertNotEquals(Palette.SELECTION.getRGB(), fullSizePicture().getRGB(x, y));

        panel.select("P001");

        assertEquals("P001", panel.getSelected().orElseThrow());
        assertEquals(Palette.SELECTION.getRGB(), fullSizePicture().getRGB(x, y));

        panel.select(null);

        assertTrue(panel.getSelected().isEmpty());
        assertNotEquals(Palette.SELECTION.getRGB(), fullSizePicture().getRGB(x, y));
    }

    @Test
    void whileItDoesNotFollowTheCameraShowsTheWholePlan() {
        showOnePatientOnTheFirstSeat();
        panel.select("P001");
        Dimension world = renderer.worldSize(standard);

        Camera camera = panel.cameraFor(world.width + 2 * MARGIN, world.height + 2 * MARGIN).orElseThrow();

        assertFalse(panel.isFollowing());
        assertEquals(1.0, camera.zoom(), 1e-9);
    }

    @Test
    void theCameraFollowsTheSelectedPatientCloser() {
        Point2D seat = showOnePatientOnTheFirstSeat();
        panel.select("P001");
        panel.setFollowing(true);

        Camera camera = panel.cameraFor(400, 300).orElseThrow();
        Dimension world = renderer.worldSize(standard);
        double fit = Camera.fit(world.width, world.height, 400, 300, MARGIN).zoom();

        assertTrue(panel.isFollowing());
        assertEquals(fit * HospitalMapPanel.FOLLOW_ZOOM, camera.zoom(), 1e-9);
        Point2D onScreen = camera.toScreen(seat.getX() + PatientPainter.OFFSET_X, seat.getY());
        assertTrue(onScreen.getX() >= 0 && onScreen.getX() <= 400, "el paciente se ve: " + onScreen);
        assertTrue(onScreen.getY() >= 0 && onScreen.getY() <= 300, "el paciente se ve: " + onScreen);
    }

    @Test
    void withoutASelectedPatientInTheSceneTheCameraShowsTheWholePlan() {
        showOnePatientOnTheFirstSeat();
        panel.setFollowing(true);
        panel.select("P999");
        Dimension world = renderer.worldSize(standard);

        Camera camera = panel.cameraFor(world.width + 2 * MARGIN, world.height + 2 * MARGIN).orElseThrow();

        assertEquals(1.0, camera.zoom(), 1e-9);
    }

    @Test
    void aClickSelectsThePatientAndTellsTheListener() {
        Point2D seat = showOnePatientOnTheFirstSeat();
        fullSizePanel();
        List<String> heard = new ArrayList<>();
        panel.setSelectionListener(heard::add);
        int x = MARGIN + (int) seat.getX() + PatientPainter.OFFSET_X;
        int y = MARGIN + (int) seat.getY();

        click(x, y);

        assertEquals("P001", panel.getSelected().orElseThrow());

        click(MARGIN, MARGIN);

        assertTrue(panel.getSelected().isEmpty());
        assertEquals(2, heard.size());
        assertEquals("P001", heard.get(0));
        assertNull(heard.get(1));
    }

    // ---------- plano que llena el panel ----------

    private static final int WIDE = 1000;
    private static final int LOW = 300;

    private double coverZoom(int width, int height) {
        Dimension world = renderer.worldSize(standard);
        return Math.max(width / (double) world.width, height / (double) world.height);
    }

    private void press(int x, int y) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0L, 0, x, y, 1, false,
                MouseEvent.BUTTON1));
    }

    private void drag(int x, int y) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 0L, 0, x, y, 0, false,
                MouseEvent.BUTTON1));
    }

    private void release(int x, int y) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0L, 0, x, y, 1, false,
                MouseEvent.BUTTON1));
    }

    @Test
    void coveringThePlanLeavesNoBackgroundAtTheSides() {
        panel.setCovering(true);

        Camera camera = panel.cameraFor(WIDE, LOW).orElseThrow();
        Dimension world = renderer.worldSize(standard);

        assertTrue(panel.isCovering());
        assertEquals(coverZoom(WIDE, LOW), camera.zoom(), 1e-9);
        assertTrue(camera.toScreen(0, 0).getX() <= 0 && camera.toScreen(0, 0).getY() <= 0);
        assertTrue(camera.toScreen(world.width, world.height).getX() >= WIDE);
        assertTrue(camera.toScreen(world.width, world.height).getY() >= LOW);
    }

    @Test
    void draggingMovesTheCoveringPlanAndIsNotAClick() {
        panel.setCovering(true);
        panel.setSize(WIDE, LOW);
        List<String> heard = new ArrayList<>();
        panel.setSelectionListener(heard::add);
        double before = panel.cameraFor(WIDE, LOW).orElseThrow().offsetY();

        press(500, 150);
        drag(500, 130);
        release(500, 130);
        click(500, 130);

        assertEquals(before - 20, panel.cameraFor(WIDE, LOW).orElseThrow().offsetY(), 1e-6);
        assertTrue(heard.isEmpty());
    }

    @Test
    void theCoveringPlanStopsAtItsEdges() {
        panel.setCovering(true);
        panel.setSize(WIDE, LOW);
        Dimension world = renderer.worldSize(standard);

        panel.panBy(0, -10_000);

        assertEquals(LOW - world.height * coverZoom(WIDE, LOW), panel.cameraFor(WIDE, LOW).orElseThrow().offsetY(),
                1e-6);

        panel.panBy(0, 10_000);

        assertEquals(0, panel.cameraFor(WIDE, LOW).orElseThrow().offsetY(), 1e-6);
    }

    @Test
    void thePlanDoesNotMoveWhenItShowsWhole() {
        panel.setSize(WIDE, LOW);
        Camera before = panel.cameraFor(WIDE, LOW).orElseThrow();

        panel.panBy(0, -50);

        assertEquals(before, panel.cameraFor(WIDE, LOW).orElseThrow());
    }

    @Test
    void whileCoveringTheCameraFollowsCloserThanTheCoveringPlan() {
        showOnePatientOnTheFirstSeat();
        panel.setCovering(true);
        panel.select("P001");
        panel.setFollowing(true);

        Camera camera = panel.cameraFor(WIDE, LOW).orElseThrow();

        assertEquals(Math.min(Camera.MAX_ZOOM, coverZoom(WIDE, LOW) * HospitalMapPanel.FOLLOW_ZOOM), camera.zoom(),
                1e-9);
    }

    // ---------- zoom con la rueda ----------

    @Test
    void theWheelZoomsInKeepingThePointUnderThePointer() {
        panel.setCovering(true);
        panel.setSize(WIDE, LOW);
        Point2D before = panel.cameraFor(WIDE, LOW).orElseThrow().toWorld(300, 120);

        panel.zoomAt(300, 120, 2);

        Camera camera = panel.cameraFor(WIDE, LOW).orElseThrow();
        Point2D after = camera.toWorld(300, 120);
        assertEquals(2, panel.getZoomLevel(), 1e-9);
        assertEquals(coverZoom(WIDE, LOW) * 2, camera.zoom(), 1e-9);
        assertEquals(before.getX(), after.getX(), 1e-6);
        assertEquals(before.getY(), after.getY(), 1e-6);
    }

    @Test
    void turningTheWheelUpZoomsIn() {
        panel.setCovering(true);
        panel.setSize(WIDE, LOW);

        panel.dispatchEvent(new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL, 0L, 0, 500, 150, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1));

        assertEquals(HospitalMapPanel.WHEEL_STEP, panel.getZoomLevel(), 1e-9);
    }

    @Test
    void theZoomStaysBetweenNormalAndItsLimit() {
        panel.setCovering(true);
        panel.setSize(WIDE, LOW);

        panel.zoomAt(500, 150, 100);
        assertEquals(HospitalMapPanel.MAX_ZOOM_LEVEL, panel.getZoomLevel(), 1e-9);

        panel.zoomAt(500, 150, 0.0001);
        assertEquals(1, panel.getZoomLevel(), 1e-9);
        assertEquals(coverZoom(WIDE, LOW), panel.cameraFor(WIDE, LOW).orElseThrow().zoom(), 1e-9);
    }

    @Test
    void theWholePlanCanBeZoomedAndDraggedAndGoesBackWhenZoomedOut() {
        panel.setSize(WIDE, LOW);
        Camera whole = panel.cameraFor(WIDE, LOW).orElseThrow();

        panel.zoomAt(500, 150, 3);
        Camera zoomed = panel.cameraFor(WIDE, LOW).orElseThrow();
        panel.panBy(40, 0);

        assertEquals(whole.zoom() * 3, zoomed.zoom(), 1e-9);
        assertEquals(zoomed.offsetX() + 40, panel.cameraFor(WIDE, LOW).orElseThrow().offsetX(), 1e-6);

        panel.zoomAt(500, 150, 1 / 3.0);

        assertEquals(whole, panel.cameraFor(WIDE, LOW).orElseThrow());
    }

    @Test
    void whileFollowingTheWheelChangesHowCloseAndFollowingAgainStartsOver() {
        showOnePatientOnTheFirstSeat();
        panel.setCovering(true);
        panel.setSize(WIDE, LOW);
        panel.select("P001");
        panel.setFollowing(true);

        panel.zoomAt(500, 150, 100);

        assertEquals(coverZoom(WIDE, LOW) * HospitalMapPanel.MAX_ZOOM_LEVEL,
                panel.cameraFor(WIDE, LOW).orElseThrow().zoom(), 1e-9);
        assertEquals(1, panel.getZoomLevel(), 1e-9);

        panel.setFollowing(false);
        panel.setFollowing(true);

        assertEquals(coverZoom(WIDE, LOW) * HospitalMapPanel.FOLLOW_ZOOM,
                panel.cameraFor(WIDE, LOW).orElseThrow().zoom(), 1e-9);
    }

    @Test
    void resettingTheZoomShowsTheNormalViewAgain() {
        panel.setCovering(true);
        panel.setSize(WIDE, LOW);
        Camera normal = panel.cameraFor(WIDE, LOW).orElseThrow();
        panel.zoomAt(200, 100, 4);
        panel.panBy(30, 30);

        panel.resetZoom();

        assertEquals(1, panel.getZoomLevel(), 1e-9);
        assertEquals(normal, panel.cameraFor(WIDE, LOW).orElseThrow());
    }

    @Test
    void withoutCoveringAndWithoutZoomTheWholePlanIsSeen() {
        panel.setCovering(true);
        panel.setSize(WIDE, LOW);
        panel.zoomAt(200, 100, 2);

        panel.setCovering(false);
        panel.resetZoom();

        Dimension world = renderer.worldSize(standard);
        assertEquals(Camera.fit(world.width, world.height, WIDE, LOW, MARGIN),
                panel.cameraFor(WIDE, LOW).orElseThrow());
    }

    @Test
    void rejectsAZoomFactorThatIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> panel.zoomAt(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> panel.zoomAt(0, 0, Double.NaN));
    }

    @Test
    void rejectsAMissingListener() {
        assertThrows(IllegalArgumentException.class, () -> panel.setSelectionListener(null));
    }

    // ---------- vista 2.5D y camilleros ----------

    @Test
    void startsInTheFlatViewAndChangesTo25dWithItsOwnWorld() {
        assertFalse(panel.isIsometric());

        panel.setIsometric(true);

        assertTrue(panel.isIsometric());
        BufferedImage image = panel.toImage(1000, 700);
        assertEquals(IsoTheme.LIGHT.color(IsoTheme.Part.BACKGROUND).getRGB(), image.getRGB(0, 0));
        Dimension world = new IsometricRenderer(renderer).worldSize(standard);
        Camera camera = panel.cameraFor(1000, 700).orElseThrow();
        assertEquals(Camera.fit(world.width, world.height, 1000, 700, MARGIN), camera);
    }

    @Test
    void the25dViewCanBeLightOrDarkWithoutMovingTheCamera() {
        panel.setIsometric(true);
        Camera before = panel.cameraFor(1000, 700).orElseThrow();

        panel.setIsoTheme(IsoTheme.DARK);

        assertEquals(IsoTheme.DARK, panel.getIsoTheme());
        assertEquals(IsoTheme.DARK.color(IsoTheme.Part.BACKGROUND).getRGB(), panel.toImage(1000, 700).getRGB(0, 0));
        assertEquals(before, panel.cameraFor(1000, 700).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> panel.setIsoTheme(null));
    }

    @Test
    void changingTheViewGoesBackToTheNormalZoomAndKeepsTheSelection() {
        showOnePatientOnTheFirstSeat();
        panel.select("P001");
        panel.setSize(600, 400);
        panel.zoomAt(300, 200, 2.0);

        panel.setIsometric(true);

        assertEquals(1.0, panel.getZoomLevel(), 0.0);
        assertEquals("P001", panel.getSelected().orElseThrow());
    }

    @Test
    void in25dAClickOnTheBodyOfAPatientSelectsHim() {
        showOnePatientOnTheFirstSeat();
        panel.setIsometric(true);
        panel.setSize(1200, 800);
        IsometricRenderer isometric = new IsometricRenderer(renderer);
        Point2D seat = renderer.centerOf(standard.getWaitingSpots().get(0));
        Point2D body = isometric.focusOf(standard, new PatientMarker("P001", TriageLevel.III, Sex.FEMALE, false,
                false, seat.getX(), seat.getY()));
        Point2D onScreen = panel.cameraFor(1200, 800).orElseThrow().toScreen(body.getX(), body.getY());

        assertEquals("P001", panel.patientAt((int) onScreen.getX(), (int) onScreen.getY()).orElseThrow());
        assertTrue(panel.patientAt(5, 5).isEmpty());
    }

    @Test
    void in25dTheCameraFollowsTheSelectedPatient() {
        showOnePatientOnTheFirstSeat();
        panel.setIsometric(true);
        panel.select("P001");
        panel.setFollowing(true);
        Dimension world = new IsometricRenderer(renderer).worldSize(standard);

        Camera camera = panel.cameraFor(400, 300).orElseThrow();

        double fit = Camera.fit(world.width, world.height, 400, 300, MARGIN).zoom();
        assertEquals(fit * HospitalMapPanel.FOLLOW_ZOOM, camera.zoom(), 1e-9);
    }

    @Test
    void showsThePortersOnTopOfThePlan() {
        Point2D cell = renderer.centerOf(standard.getEntrance());
        int x = MARGIN + (int) cell.getX();
        int y = MARGIN + (int) cell.getY() + 7;
        int before = fullSizePicture().getRGB(x, y);

        panel.showPorters(List.of(new PorterMarker("P001", cell.getX(), cell.getY())));

        assertNotEquals(before, fullSizePicture().getRGB(x, y));
        assertEquals(Palette.PORTER_UNIFORM.getRGB(), fullSizePicture().getRGB(x, y));
        assertThrows(IllegalArgumentException.class, () -> panel.showPorters(null));
    }

    private void click(int x, int y) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_CLICKED, 0L, 0, x, y, 1, false,
                MouseEvent.BUTTON1));
    }

    // ---------- cámara libre de la vista 2.5D ----------

    private void rightDrag(int fromX, int fromY, int toX, int toY) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0L, MouseEvent.BUTTON3_DOWN_MASK, fromX,
                fromY, 1, false, MouseEvent.BUTTON3));
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 0L, MouseEvent.BUTTON3_DOWN_MASK, toX, toY,
                0, false, MouseEvent.BUTTON3));
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0L, 0, toX, toY, 1, false,
                MouseEvent.BUTTON3));
    }

    @Test
    void draggingWithTheRightButtonTurnsTheHospitalAndRaisesTheLook() {
        panel.setSize(900, 600);
        panel.setIsometric(true);

        rightDrag(400, 300, 500, 250);

        assertEquals(100 * HospitalMapPanel.YAW_PER_PIXEL, panel.getYaw(), 1e-9);
        assertEquals(IsoProjection.DEFAULT_ELEVATION + 50 * HospitalMapPanel.ELEVATION_PER_PIXEL,
                panel.getElevation(), 1e-9);
    }

    @Test
    void inTheFlatViewTheRightButtonDoesNotTurnAnything() {
        panel.setSize(900, 600);

        rightDrag(400, 300, 500, 250);

        assertEquals(0.0, panel.getYaw(), 0.0);
        assertEquals(IsoProjection.DEFAULT_ELEVATION, panel.getElevation(), 0.0);
    }

    @Test
    void theLookStaysBetweenTheUsualDiagonalAndAbove() {
        panel.rotateBy(-30, 500);
        assertEquals(IsoProjection.MAX_ELEVATION, panel.getElevation(), 0.0);
        assertEquals(330.0, panel.getYaw(), 1e-9);

        panel.rotateBy(0, -500);
        assertEquals(IsoProjection.DEFAULT_ELEVATION, panel.getElevation(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> panel.setView(Double.NaN, 40));
    }

    @Test
    void turningKeepsInTheCenterThePointOfTheFloorThatWasThere() {
        panel.setSize(900, 600);
        panel.setIsometric(true);
        panel.zoomAt(450, 300, 3);
        Camera before = panel.cameraFor(900, 600).orElseThrow();
        IsometricRenderer iso = new IsometricRenderer(renderer);
        Point2D world = before.toWorld(450, 300);
        Point2D floor = iso.projectionOf(standard).toPlan(world.getX(), world.getY());

        panel.rotateBy(20, 0);

        iso.setView(20, IsoProjection.DEFAULT_ELEVATION);
        Point2D center = panel.cameraFor(900, 600).orElseThrow().toWorld(450, 300);
        Point2D after = iso.projectionOf(standard).toPlan(center.getX(), center.getY());
        assertEquals(floor.getX(), after.getX(), 1.0);
        assertEquals(floor.getY(), after.getY(), 1.0);
    }

    @Test
    void theCompassGivesBackTheInitialView() {
        panel.setSize(900, 600);
        panel.setIsometric(true);
        panel.setView(135, 70);
        panel.zoomAt(450, 300, 2);

        panel.clickAt(900 - (int) MapOverlay.COMPASS_INSET, (int) MapOverlay.COMPASS_INSET);

        assertEquals(0.0, panel.getYaw(), 0.0);
        assertEquals(IsoProjection.DEFAULT_ELEVATION, panel.getElevation(), 0.0);
        assertEquals(1.0, panel.getZoomLevel(), 0.0);
    }

    @Test
    void theCompassIsDrawnOnlyInThe25DView() {
        int x = 900 - (int) MapOverlay.COMPASS_INSET;
        int y = (int) MapOverlay.COMPASS_INSET;
        int flat = panel.toImage(900, 600).getRGB(x, y);

        panel.setIsometric(true);

        assertNotEquals(flat, panel.toImage(900, 600).getRGB(x, y));
    }

    // ---------- personal, avisos y panel flotante ----------

    @Test
    void aClickOnADoctorTellsWhoHeIs() {
        fullSizePanel();
        Point2D cell = renderer.centerOf(standard.positionOf("H1-C1").orElseThrow());
        panel.showStaff(List.of(new StaffMarker("D02", Specialty.SURGERY, Sex.MALE, false, cell.getX(),
                cell.getY())));
        List<String> clicked = new ArrayList<>();
        panel.setStaffListener(clicked::add);
        Point2D screen = panel.cameraFor(panel.getWidth(), panel.getHeight()).orElseThrow()
                .toScreen(cell.getX(), cell.getY() - 4);

        panel.clickAt((int) screen.getX(), (int) screen.getY());

        assertEquals(List.of("D02"), clicked);
        assertEquals("D02", panel.staffAt((int) screen.getX(), (int) screen.getY()).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> panel.setStaffListener(null));
    }

    @Test
    void aClickOnAnAlertTakesTheCameraThereAndStopsFollowing() {
        fullSizePanel();
        showOnePatientOnTheFirstSeat();
        panel.select("P001");
        panel.setFollowing(true);
        List<Boolean> follow = new ArrayList<>();
        panel.setFollowListener(follow::add);
        Position bed = standard.positionOf("UCI-C1").orElseThrow();
        panel.showAlerts(List.of(new PlanAlert(PlanAlert.Kind.DEATH, bed, "Falleció P009")));
        panel.setFollowing(false);
        Camera camera = panel.cameraFor(panel.getWidth(), panel.getHeight()).orElseThrow();
        Point2D center = renderer.centerOf(bed);
        Point2D icon = camera.toScreen(center.getX(), center.getY());
        assertEquals(PlanAlert.Kind.DEATH, panel.alertAt((int) icon.getX(), (int) icon.getY() - 14).orElseThrow()
                .kind());
        panel.setFollowing(true);

        panel.flyTo(bed);

        assertFalse(panel.isFollowing());
        assertEquals(List.of(false), follow);
        assertTrue(panel.getZoomLevel() >= HospitalMapPanel.ALERT_ZOOM);
        Point2D seen = panel.cameraFor(panel.getWidth(), panel.getHeight()).orElseThrow()
                .toScreen(center.getX(), center.getY());
        assertTrue(seen.getX() >= 0 && seen.getX() <= panel.getWidth() && seen.getY() >= 0
                && seen.getY() <= panel.getHeight(), "el lugar del aviso queda a la vista");
        assertThrows(IllegalArgumentException.class, () -> panel.showAlerts(null));
        assertThrows(IllegalArgumentException.class, () -> panel.flyTo(null));
    }

    @Test
    void theFloatingPanelAppearsOnlyWhenItIsEnabledAndOverAPlace() {
        fullSizePanel();
        Position bed = standard.positionOf("H1-C1").orElseThrow();
        PlaceInfo info = new PlaceInfo("Hospitalización · H1-C1", List.of());
        panel.setPlaceInfo(cell -> cell.equals(bed) ? Optional.of(info) : Optional.empty());
        Point2D center = renderer.centerOf(bed);
        Point2D screen = panel.cameraFor(panel.getWidth(), panel.getHeight()).orElseThrow()
                .toScreen(center.getX(), center.getY());

        panel.hoverAt((int) screen.getX(), (int) screen.getY());
        assertTrue(panel.getHoverInfo().isEmpty(), "sin pantalla completa no se ve");

        panel.setHoverEnabled(true);
        panel.hoverAt((int) screen.getX(), (int) screen.getY());
        assertEquals(info, panel.getHoverInfo().orElseThrow());
        assertEquals(bed, panel.cellAt((int) screen.getX(), (int) screen.getY()).orElseThrow());

        panel.hoverAt(1, 1);
        assertTrue(panel.getHoverInfo().isEmpty());
        panel.hoverAt((int) screen.getX(), (int) screen.getY());
        panel.setHoverEnabled(false);
        assertTrue(panel.getHoverInfo().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> panel.setPlaceInfo(null));
    }

    @Test
    void inThe25DViewTheCellUnderThePointerIsTheOneOfTheFloor() {
        panel.setSize(900, 600);
        panel.setIsometric(true);
        panel.setView(70, 50);
        Position bed = standard.positionOf("UCI-C2").orElseThrow();
        IsometricRenderer iso = new IsometricRenderer(renderer);
        iso.setView(70, 50);
        Point2D floor = iso.projectionOf(standard).at(bed.col() + 0.5, bed.row() + 0.5, 0);
        Point2D screen = panel.cameraFor(900, 600).orElseThrow().toScreen(floor.getX(), floor.getY());

        assertEquals(bed, panel.cellAt((int) Math.round(screen.getX()), (int) Math.round(screen.getY()))
                .orElseThrow());
    }

    @Test
    void theBannerCanBeSetAndRemoved() {
        panel.setBanner("Repetición");
        assertEquals("Repetición", panel.getBanner().orElseThrow());

        panel.setBanner(null);
        assertTrue(panel.getBanner().isEmpty());
    }

    // ---------- animación ----------

    @Test
    void withAnimationTheChangeBetweenViewsTakesItsTime() {
        panel.setSize(900, 600);
        panel.setAnimated(true);

        panel.setIsometric(true);

        assertTrue(panel.isIsometric());
        assertTrue(panel.isInTransition());
        assertEquals(900, panel.toImage(900, 600).getWidth());
        panel.setAnimated(false);
        assertFalse(panel.isInTransition());
    }

    @Test
    void withoutAnimationTheChangeIsInstant() {
        panel.setIsometric(true);

        assertFalse(panel.isAnimated());
        assertFalse(panel.isInTransition());
    }

    @Test
    void theAnimatedCameraCatchesUpLittleByLittle() {
        panel.setSize(900, 600);
        panel.setAnimated(true);
        showOnePatientOnTheFirstSeat();
        panel.select("P001");
        BufferedImage canvas = new BufferedImage(900, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        panel.paint(graphics);
        Camera before = panel.cameraFor(900, 600).orElseThrow();

        panel.setFollowing(true);
        panel.paint(graphics);

        Camera target = panel.cameraFor(900, 600).orElseThrow();
        assertNotEquals(before.zoom(), target.zoom());
        Point2D center = panel.cameraFor(900, 600).orElseThrow().toWorld(450, 300);
        assertTrue(panel.staffAt(450, 300).isEmpty());
        assertTrue(center.getX() > 0);
        graphics.dispose();
        panel.setAnimated(false);
    }
}
