package com.medresource.ui.view;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
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
}
