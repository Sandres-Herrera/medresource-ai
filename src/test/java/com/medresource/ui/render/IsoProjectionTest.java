package com.medresource.ui.render;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la proyección de la vista 2.5D.
 */
class IsoProjectionTest {

    private final IsoProjection projection = new IsoProjection(20, 16, Renderer2D.CELL_W, Renderer2D.CELL_H);

    @Test
    void measuresTheCellsWithTheFlatRenderer() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));

        IsoProjection measured = IsoProjection.of(layout, new Renderer2D());

        assertEquals(new IsoProjection(layout.getCols(), layout.getRows(), Renderer2D.CELL_W, Renderer2D.CELL_H),
                measured);
    }

    @Test
    void theColumnsGoDownToTheRightAndTheRowsDownToTheLeft() {
        Point2D origin = projection.at(0, 0, 0);
        Point2D nextColumn = projection.at(1, 0, 0);
        Point2D nextRow = projection.at(0, 1, 0);

        assertEquals(IsoProjection.TILE_W / 2.0, nextColumn.getX() - origin.getX(), 1e-9);
        assertEquals(IsoProjection.TILE_H / 2.0, nextColumn.getY() - origin.getY(), 1e-9);
        assertEquals(-IsoProjection.TILE_W / 2.0, nextRow.getX() - origin.getX(), 1e-9);
        assertEquals(IsoProjection.TILE_H / 2.0, nextRow.getY() - origin.getY(), 1e-9);
    }

    @Test
    void eachMeterOfHeightGoesUp() {
        Point2D floor = projection.at(3, 4, 0);
        Point2D raised = projection.at(3, 4, 1);

        assertEquals(floor.getX(), raised.getX(), 1e-9);
        assertEquals(-IsoProjection.Z_SCALE, raised.getY() - floor.getY(), 1e-9);
    }

    @Test
    void aPointOfThePlanIsSeenWhereItsCellIs() {
        Point2D center = new Renderer2D().centerOf(new Position(5, 3));

        assertEquals(projection.at(5.5, 3.5, 0), projection.fromPlan(center.getX(), center.getY(), 0));
        assertEquals(5.5, projection.gridX(center.getX()), 1e-9);
        assertEquals(3.5, projection.gridY(center.getY()), 1e-9);
    }

    @Test
    void goingBackToThePlanUndoesTheProjection() {
        Point2D seen = projection.fromPlan(321.5, 456.25, 0);

        Point2D back = projection.toPlan(seen.getX(), seen.getY());

        assertEquals(321.5, back.getX(), 1e-9);
        assertEquals(456.25, back.getY(), 1e-9);
    }

    @Test
    void theWholeBuildingAndItsSiteFitInTheDrawing() {
        Dimension size = projection.size();
        double margin = IsoProjection.SITE_MARGIN;

        for (double[] corner : new double[][] {{-margin, -margin}, {20 + margin, -margin}, {-margin, 16 + margin},
                {20 + margin, 16 + margin}}) {
            Point2D floor = projection.at(corner[0], corner[1], 0);
            Point2D top = projection.at(corner[0], corner[1], 1.5);
            assertTrue(floor.getX() >= 0 && floor.getX() <= size.width, "x de " + floor);
            assertTrue(floor.getY() <= size.height && top.getY() >= 0, "y de " + floor);
        }
    }

    @Test
    void theDefaultViewHasNoTurnAndTheUsualElevation() {
        assertTrue(projection.isDefaultView());
        assertEquals(0.0, projection.yaw(), 1e-12);
        assertEquals(IsoProjection.DEFAULT_ELEVATION, projection.elevation(), 1e-12);
        assertEquals(projection, projection.withView(360, IsoProjection.DEFAULT_ELEVATION));
        assertEquals(1.0, projection.groundStretch(), 1e-12);
        assertEquals(IsoProjection.Z_SCALE, projection.heightScale(), 1e-12);
        assertEquals(0.5, projection.groundRatio(), 1e-12);
    }

    @Test
    void theTurnIsKeptWithinOneRound() {
        assertEquals(270.0, projection.withView(-90, 40).yaw(), 1e-9);
        assertEquals(45.0, projection.withView(765, 40).yaw(), 1e-9);
        assertEquals(40.0, projection.withView(-90, 40).elevation(), 1e-12);
        assertFalse(projection.withView(10, IsoProjection.DEFAULT_ELEVATION).isDefaultView());
    }

    @Test
    void turningAQuarterMakesTheColumnsGoWhereTheRowsWent() {
        IsoProjection turned = projection.withView(90, IsoProjection.DEFAULT_ELEVATION);

        Point2D column = turned.step(1, 0);
        Point2D row = projection.step(0, 1);

        assertEquals(row.getX(), column.getX(), 1e-9);
        assertEquals(row.getY(), column.getY(), 1e-9);
    }

    @Test
    void seenFromAboveEachCellIsSquareAndHeightDoesNotRise() {
        IsoProjection above = projection.withView(-45, IsoProjection.MAX_ELEVATION);

        Point2D column = above.step(1, 0);
        Point2D row = above.step(0, 1);

        assertEquals(Math.hypot(column.getX(), column.getY()), Math.hypot(row.getX(), row.getY()), 1e-9);
        assertEquals(0.0, column.getY(), 1e-9);
        assertEquals(0.0, row.getX(), 1e-9);
        assertEquals(0.0, above.heightScale(), 1e-9);
        assertEquals(2.0, above.groundStretch(), 1e-9);
    }

    @Test
    void goingBackToThePlanUndoesAnyView() {
        for (double yaw = 0; yaw < 360; yaw += 37) {
            for (double elevation : new double[] {30, 55, 90}) {
                IsoProjection view = projection.withView(yaw, elevation);
                Point2D seen = view.fromPlan(321.5, 456.25, 0);

                Point2D back = view.toPlan(seen.getX(), seen.getY());

                assertEquals(321.5, back.getX(), 1e-6, "giro " + yaw + ", elevación " + elevation);
                assertEquals(456.25, back.getY(), 1e-6, "giro " + yaw + ", elevación " + elevation);
            }
        }
    }

    @Test
    void theSiteFitsInTheDrawingFromAnyAngle() {
        double margin = IsoProjection.SITE_MARGIN;
        for (double yaw = 0; yaw < 360; yaw += 15) {
            IsoProjection view = projection.withView(yaw, 50);
            Dimension size = view.size();
            for (double[] corner : new double[][] {{-margin, -margin}, {20 + margin, -margin},
                    {-margin, 16 + margin}, {20 + margin, 16 + margin}}) {
                Point2D floor = view.at(corner[0], corner[1], 0);
                assertTrue(floor.getX() >= -1e-6 && floor.getX() <= size.width + 1e-6, "x con giro " + yaw);
                assertTrue(floor.getY() >= 0 && floor.getY() <= size.height, "y con giro " + yaw);
            }
        }
    }

    @Test
    void theDepthGrowsTowardsWhoeverLooks() {
        assertEquals(7.0, projection.depth(3, 4), 1e-12);
        IsoProjection back = projection.withView(180, IsoProjection.DEFAULT_ELEVATION);

        assertTrue(back.depth(0, 0) > back.depth(20, 16));
        assertTrue(projection.depth(0, 0) < projection.depth(20, 16));
    }

    @Test
    void onlyTheFacesTurnedTowardsWhoeverLooksAreSeen() {
        assertTrue(projection.faces(1, 0));
        assertTrue(projection.faces(0, 1));
        assertFalse(projection.faces(-1, 0));
        assertFalse(projection.faces(0, -1));
        IsoProjection back = projection.withView(180, IsoProjection.DEFAULT_ELEVATION);
        assertTrue(back.faces(-1, 0));
        assertTrue(back.faces(0, -1));
        assertFalse(back.faces(1, 0));
    }

    @Test
    void theFacesOfTheColumnsLookRightAndThoseOfTheRowsLookLeft() {
        assertEquals(1.0, projection.rightness(1, 0), 1e-9);
        assertEquals(0.0, projection.rightness(0, 1), 1e-9);
        assertEquals(0.5, projection.withView(45, IsoProjection.DEFAULT_ELEVATION).rightness(1, 0), 1e-9);
    }

    @Test
    void rejectsAViewOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> projection.withView(Double.NaN, 40));
        assertThrows(IllegalArgumentException.class, () -> projection.withView(0, 29));
        assertThrows(IllegalArgumentException.class, () -> projection.withView(0, 91));
    }

    @Test
    void rejectsAPlanWithoutCellsOrSize() {
        assertThrows(IllegalArgumentException.class, () -> new IsoProjection(0, 16, 84, 48));
        assertThrows(IllegalArgumentException.class, () -> new IsoProjection(20, 0, 84, 48));
        assertThrows(IllegalArgumentException.class, () -> new IsoProjection(20, 16, 0, 48));
        assertThrows(IllegalArgumentException.class, () -> new IsoProjection(20, 16, 84, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> IsoProjection.of(null, new Renderer2D()));
    }
}
