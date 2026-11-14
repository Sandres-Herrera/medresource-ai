package com.medresource.ui.render;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las personas de la vista 2.5D sobre el hospital estándar.
 */
class IsoFiguresTest {

    private final Renderer2D flat = new Renderer2D();
    private final HospitalLayout layout =
            new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    private final IsoProjection projection = IsoProjection.of(layout, flat);
    private final IsoFigures figures = new IsoFigures(projection, new IsoFurniture(layout, projection, flat));

    private PatientMarker at(Position cell, boolean stretcher, boolean moving, boolean deceased) {
        Point2D point = flat.centerOf(cell);
        return new PatientMarker("P001", TriageLevel.III, Sex.FEMALE, false, deceased, point.getX(), point.getY(),
                deceased ? 0 : 80, PatientMarker.NO_PROGRESS, stretcher, moving, 0);
    }

    @Test
    void aPatientStillInTheMiddleOfABedLiesInIt() {
        Position bed = layout.positionOf("H1-C1").orElseThrow();

        assertEquals(IsoFigures.Pose.IN_BED, figures.poseOf(at(bed, false, false, false)));
        assertEquals(IsoFigures.Pose.IN_BED, figures.poseOf(at(bed, true, false, false)),
                "quien llega en camilla pasa a la cama");
        assertEquals(IsoFigures.Pose.STANDING, figures.poseOf(at(bed, false, true, false)),
                "mientras camina por encima de la casilla no se acuesta");
    }

    @Test
    void theOtherFurnitureAlsoDecidesThePose() {
        assertEquals(IsoFigures.Pose.ON_TABLE,
                figures.poseOf(at(layout.positionOf("Q1").orElseThrow(), false, false, false)));
        assertEquals(IsoFigures.Pose.ON_SLAB, figures.poseOf(at(layout.getMorgueSpots().get(0), true, false, true)));
        assertEquals(IsoFigures.Pose.SEATED, figures.poseOf(at(layout.getWaitingSpots().get(0), false, false, false)));
        assertEquals(IsoFigures.Pose.ON_STRETCHER,
                figures.poseOf(at(layout.getWaitingSpots().get(0), true, false, false)),
                "quien no puede caminar espera en su camilla, no en la silla");
        assertEquals(IsoFigures.Pose.ON_STRETCHER, figures.poseOf(at(layout.getEntrance(), true, true, false)));
        assertEquals(IsoFigures.Pose.STANDING, figures.poseOf(at(layout.getEntrance(), false, false, false)));
    }

    @Test
    void theFocusOfAStandingPatientIsOverHisFeet() {
        PatientMarker marker = at(layout.getEntrance(), false, false, false);

        Point2D focus = figures.focusOf(marker);

        assertEquals(projection.fromPlan(marker.x(), marker.y(), IsoFigures.STANDING_FOCUS), focus);
    }

    @Test
    void thereIsOneItemPerPersonAndTheSelectedPatientHasHisRing() {
        Position spot = layout.getEntrance();
        PatientMarker patient = at(spot, false, false, false);
        Point2D desk = flat.centerOf(layout.positionOf("H1-C2").orElseThrow());
        List<StaffMarker> staff = List.of(new StaffMarker("D01", Specialty.SURGERY, Sex.MALE, false, desk.getX(),
                desk.getY()));
        List<PorterMarker> porters = List.of(new PorterMarker("P002", desk.getX() + 36, desk.getY()));

        List<IsoItem> items = figures.items(List.of(patient), staff, porters, "P001");

        assertEquals(3, items.size());
        BufferedImage image = new BufferedImage(projection.size().width, projection.size().height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        items.forEach(item -> item.paint(graphics));
        Point2D feet = projection.fromPlan(patient.x(), patient.y(), 0);
        double rx = IsoBrush.screenRadius(IsoFigures.SELECTION_RADIUS);
        boolean ring = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                ring |= image.getRGB((int) Math.round(feet.getX() - rx) + dx, (int) Math.round(feet.getY()) + dy)
                        == Palette.SELECTION.getRGB();
            }
        }
        assertTrue(ring, "el seleccionado lleva su anillo en el piso");
    }

    @Test
    void aPatientInBedIsDrawnRightAfterHisBed() {
        PatientMarker patient = at(layout.positionOf("H1-C1").orElseThrow(), false, false, false);
        Position bed = layout.positionOf("H1-C1").orElseThrow();

        IsoItem item = figures.items(List.of(patient), List.of(), List.of(), null).get(0);

        assertEquals(bed.col() + bed.row() + 1, item.depth(), 1e-9);
        assertEquals(IsoItem.PERSON, item.layer());
        assertTrue(IsoItem.BACK_TO_FRONT.compare(new IsoItem(item.depth(), IsoItem.FURNITURE, g -> { }), item) < 0);
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new IsoFigures(null, null));
        assertThrows(IllegalArgumentException.class, () -> figures.poseOf(null));
        assertThrows(IllegalArgumentException.class, () -> figures.items(null, List.of(), List.of(), null));
    }
}
