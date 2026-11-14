package com.medresource.ui.view;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.ui.render.IsoTheme;
import com.medresource.ui.render.Renderer2D;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del marco del plano, sin ventana.
 */
class MapFrameTest {

    private static HospitalMapPanel map() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        return new HospitalMapPanel(layout, new Renderer2D());
    }

    @Test
    void hasTheAccentBorderAndTheSizeOfThePlan() {
        MapFrame frame = new MapFrame(map(), "estándar · 20 × 16 m · clic en un paciente");

        assertTrue(frame.isHighlighted());
        assertEquals("estándar · 20 × 16 m · clic en un paciente", frame.sizeText());
        assertEquals("", frame.followText());
    }

    @Test
    void theViewToggleChangesThePlanBetween2dAnd25d() {
        HospitalMapPanel map = map();
        MapFrame frame = new MapFrame(map, "estándar · 20 × 16 m");

        frame.viewToggle().isometricPill().doClick();

        assertTrue(map.isIsometric());

        frame.viewToggle().flatPill().doClick();

        assertFalse(map.isIsometric());
    }

    @Test
    void theThemeToggleOnlyShowsIn25dAndChangesTheThemeOfThePlan() {
        HospitalMapPanel map = map();
        MapFrame frame = new MapFrame(map, "estándar · 20 × 16 m");

        assertFalse(frame.themeToggle().isVisible(), "en 2D no hay tema que elegir");

        frame.viewToggle().isometricPill().doClick();
        frame.themeToggle().darkPill().doClick();

        assertTrue(frame.themeToggle().isVisible());
        assertEquals(IsoTheme.DARK, map.getIsoTheme());

        frame.viewToggle().flatPill().doClick();

        assertFalse(frame.themeToggle().isVisible());
        assertEquals(IsoTheme.DARK, map.getIsoTheme(), "el tema se recuerda al volver a 2.5D");
    }

    @Test
    void saysWhoTheCameraIsFollowing() {
        MapFrame frame = new MapFrame(map(), "estándar · 20 × 16 m");

        frame.showFollowing("P003");

        assertEquals("Siguiendo a P003", frame.followText());

        frame.showFollowing(null);

        assertEquals("", frame.followText());
    }

    @Test
    void theFullScreenButtonRunsItsAction() {
        MapFrame frame = new MapFrame(map(), "estándar");
        List<String> heard = new ArrayList<>();
        frame.onFullscreen(() -> heard.add("completa"));

        frame.fullscreenButton().doClick();

        assertEquals(List.of("completa"), heard);
        assertEquals("Pantalla completa", frame.fullscreenButton().getAccessibleContext().getAccessibleName());
    }

    @Test
    void takesThePlanBackAfterItWasShownElsewhere() {
        HospitalMapPanel map = map();
        MapFrame frame = new MapFrame(map, "estándar");
        assertTrue(frame.hasMap());

        new JPanel().add(map);

        assertFalse(frame.hasMap());

        frame.restoreMap();

        assertTrue(frame.hasMap());
        assertSame(frame, map.getParent());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new MapFrame(null, "estándar"));
        assertThrows(IllegalArgumentException.class, () -> new MapFrame(map(), " "));
        assertThrows(IllegalArgumentException.class, () -> new MapFrame(map(), "estándar").onFullscreen(null));
    }

    @Test
    void theInitialViewButtonOnlyShowsIn25dAndUndoesTheTurn() {
        HospitalMapPanel map = map();
        MapFrame frame = new MapFrame(map, "estándar");
        assertFalse(frame.resetViewButton().isVisible());

        frame.viewToggle().isometricPill().doClick();
        map.setView(90, 60);
        frame.resetViewButton().doClick();

        assertTrue(frame.resetViewButton().isVisible());
        assertEquals(0.0, map.getYaw(), 0.0);
    }
}
