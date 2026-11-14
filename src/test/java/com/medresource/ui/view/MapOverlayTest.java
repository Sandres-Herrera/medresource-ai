package com.medresource.ui.view;

import com.medresource.model.Position;
import com.medresource.model.TriageLevel;
import com.medresource.ui.controller.PlaceInfo;
import com.medresource.ui.render.PlanAlert;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de lo que se dibuja encima del plano.
 */
class MapOverlayTest {

    @Test
    void theDurationsAreInSecondsUnderAMinuteAndInMinutesAfter() {
        assertEquals("40 s", MapOverlay.duration(39.2));
        assertEquals("1 min", MapOverlay.duration(60));
        assertEquals("13 min", MapOverlay.duration(12 * 60 + 5));
    }

    @Test
    void aFreePlaceSaysFreeAndABusyOneSaysWhoAndForHowLong() {
        assertEquals("Libre", MapOverlay.placeRows(new PlaceInfo("Q1", List.of())).get(0)[1]);

        List<String[]> rows = MapOverlay.placeRows(new PlaceInfo("Q1", List.of(
                new PlaceInfo.Occupant("P012", TriageLevel.II, "En tratamiento", 600, 300))));

        assertEquals("P012 · Triage II", rows.get(0)[1]);
        assertEquals("En tratamiento · lleva 10 min · faltan 5 min", rows.get(1)[1]);
    }

    @Test
    void withoutTimesOnlyTheActivityIsShown() {
        List<String[]> rows = MapOverlay.placeRows(new PlaceInfo("UCI-C1", List.of(
                new PlaceInfo.Occupant("P003", null, "Fallecido", Double.NaN, Double.NaN))));

        assertEquals("P003 · sin triage", rows.get(0)[1]);
        assertEquals("Fallecido", rows.get(1)[1]);
    }

    @Test
    void anAlertIsHitNearItsCenterOnly() {
        Point2D at = new Point2D.Double(100, 100);

        assertTrue(MapOverlay.hitsAlert(at, 105, 108));
        assertFalse(MapOverlay.hitsAlert(at, 100 + MapOverlay.ALERT_HIT + 1, 100));
    }

    @Test
    void theAlertTheCompassTheBannerAndTheCardAreDrawn() {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        int before = image.getRGB(100, 100);

        MapOverlay.paintAlert(graphics, new PlanAlert(PlanAlert.Kind.BLOOD_SHORTAGE, new Position(1, 1),
                "Sin sangre O-"), new Point2D.Double(100, 100), 0);
        MapOverlay.paintCompass(graphics, new Point2D.Double(370, 30), new Point2D.Double(1, -1));
        MapOverlay.paintBanner(graphics, 400, "Repetición");
        MapOverlay.paintPlaceCard(graphics, 380, 280, new PlaceInfo("Q1", List.of()), 400, 300);
        graphics.dispose();

        assertNotEquals(before, image.getRGB(100, 100));
        assertNotEquals(before, image.getRGB(370, 30));
        assertNotEquals(before, image.getRGB(200, 24));
    }
}
