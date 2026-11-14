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
    void rejectsAPlanWithoutCellsOrSize() {
        assertThrows(IllegalArgumentException.class, () -> new IsoProjection(0, 16, 84, 48));
        assertThrows(IllegalArgumentException.class, () -> new IsoProjection(20, 0, 84, 48));
        assertThrows(IllegalArgumentException.class, () -> new IsoProjection(20, 16, 0, 48));
        assertThrows(IllegalArgumentException.class, () -> new IsoProjection(20, 16, 84, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> IsoProjection.of(null, new Renderer2D()));
    }
}
