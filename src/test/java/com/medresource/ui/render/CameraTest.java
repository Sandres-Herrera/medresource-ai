package com.medresource.ui.render;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la cámara de la vista.
 */
class CameraTest {

    @Test
    void convertsWorldPointsToTheScreen() {
        Camera camera = new Camera(2, 10, 20);

        Point2D point = camera.toScreen(5, 7);

        assertEquals(20, point.getX(), 0.0);
        assertEquals(34, point.getY(), 0.0);
    }

    @Test
    void convertsScreenPointsBackToTheWorld() {
        Camera camera = new Camera(2, 10, 20);

        Point2D point = camera.toWorld(20, 34);

        assertEquals(5, point.getX(), 0.0);
        assertEquals(7, point.getY(), 0.0);
    }

    @Test
    void acceptsTheLimitsOfTheZoom() {
        assertEquals(Camera.MIN_ZOOM, new Camera(Camera.MIN_ZOOM, 0, 0).zoom(), 0.0);
        assertEquals(Camera.MAX_ZOOM, new Camera(Camera.MAX_ZOOM, 0, 0).zoom(), 0.0);
    }

    @Test
    void rejectsAZoomOutsideTheLimitsOrNotANumber() {
        assertThrows(IllegalArgumentException.class, () -> new Camera(Camera.MIN_ZOOM / 2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Camera(Camera.MAX_ZOOM * 2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Camera(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Camera(1, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Camera(1, 0, Double.NaN));
    }

    @Test
    void fitKeepsRealSizeWhenTheWorldJustFitsWithItsMargins() {
        Camera camera = Camera.fit(1000, 500, 1020, 520, 10);

        assertEquals(1.0, camera.zoom(), 0.0);
        assertEquals(10, camera.offsetX(), 0.0);
        assertEquals(10, camera.offsetY(), 0.0);
    }

    @Test
    void fitShrinksTheWorldToTheMostRestrictedSide() {
        Camera camera = Camera.fit(1000, 500, 500, 500, 0);

        assertEquals(0.5, camera.zoom(), 0.0);
    }

    @Test
    void fitCentersTheWorldOnTheFreeSide() {
        Camera camera = Camera.fit(1000, 500, 500, 500, 0);

        assertEquals(0, camera.offsetX(), 0.0);
        assertEquals(125, camera.offsetY(), 0.0);
    }

    @Test
    void fitEnlargesASmallWorld() {
        Camera camera = Camera.fit(100, 100, 400, 400, 0);

        assertEquals(4.0, camera.zoom(), 0.0);
    }

    @Test
    void fitDoesNotPassTheZoomLimits() {
        assertEquals(Camera.MAX_ZOOM, Camera.fit(10, 10, 1000, 1000, 0).zoom(), 0.0);
        assertEquals(Camera.MIN_ZOOM, Camera.fit(10_000, 10_000, 10, 10, 0).zoom(), 0.0);
    }

    @Test
    void fitRejectsAnEmptyWorldOrAScreenSmallerThanTheMargins() {
        assertThrows(IllegalArgumentException.class, () -> Camera.fit(0, 10, 100, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> Camera.fit(10, 0, 100, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> Camera.fit(10, 10, 20, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> Camera.fit(10, 10, 100, 20, 10));
        assertThrows(IllegalArgumentException.class, () -> Camera.fit(10, 10, 100, 100, -1));
    }

    @Test
    void drawsInWorldCoordinatesOnceApplied() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        new Camera(2, 10, 10).applyTo(graphics);
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 5, 5);
        graphics.dispose();

        assertEquals(Color.RED.getRGB(), image.getRGB(15, 15));
        assertEquals(Color.BLACK.getRGB(), image.getRGB(25, 25));
        assertEquals(Color.BLACK.getRGB(), image.getRGB(5, 5));
    }

    @Test
    void doesNotApplyItselfToNoSurface() {
        assertThrows(IllegalArgumentException.class, () -> new Camera(1, 0, 0).applyTo(null));
    }
}