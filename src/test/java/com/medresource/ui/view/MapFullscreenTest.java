package com.medresource.ui.view;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.ui.render.Renderer2D;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.BorderLayout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Pruebas del contenido de la pantalla completa del plano, sin ventana (la ventana en sí necesita pantalla).
 */
class MapFullscreenTest {

    private static HospitalMapPanel map() {
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
        return new HospitalMapPanel(layout, new Renderer2D());
    }

    @Test
    void theBarGoesOnTopAndThePlanFillsTheRest() {
        FullscreenBar bar = new FullscreenBar();
        HospitalMapPanel map = map();

        JPanel content = MapFullscreen.content(bar, map);
        BorderLayout layout = (BorderLayout) content.getLayout();

        assertSame(bar, layout.getLayoutComponent(BorderLayout.NORTH));
        assertSame(map, layout.getLayoutComponent(BorderLayout.CENTER));
    }

    @Test
    void thePlanLeavesItsFrameWhileItIsInFullScreen() {
        HospitalMapPanel map = map();
        MapFrame frame = new MapFrame(map, "estándar");

        MapFullscreen.content(new FullscreenBar(), map);

        assertFalse(frame.hasMap());

        frame.restoreMap();

        assertSame(frame, map.getParent());
    }
}
