package com.medresource.ui.view;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.ui.render.Renderer2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new MapFrame(null, "estándar"));
        assertThrows(IllegalArgumentException.class, () -> new MapFrame(map(), " "));
    }
}
