package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Scenario;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la pantalla de inicio, sin ventana: se pulsan sus piezas y se dibuja en una imagen en memoria.
 */
class HomePanelTest {

    private static final CaseConfig QUICK = new CaseConfig(1L, Scenario.NORMAL, 60, 120);
    private static final CaseConfig DEMO = new CaseConfig(7L, Scenario.CATASTROPHE, 100, 120);

    private final List<String> done = new ArrayList<>();
    private final HomePanel panel = new HomePanel(HospitalConfig.standard(), QUICK, DEMO, 10,
            new HomePanel.Actions(() -> done.add("nueva"), () -> done.add("rápida"), () -> done.add("demo"),
                    () -> done.add("salir")));

    /** Acomoda el panel y todo lo que tiene dentro a un tamaño, como lo haría una ventana. */
    private static void layOut(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container inner) {
                layOut(inner);
            }
        }
    }

    @Test
    void eachOptionDoesItsOwnAction() {
        panel.newSimulationButton().doClick();
        panel.quickCard().doClick();
        panel.demoCard().doClick();
        panel.exitLink().doClick();

        assertEquals(List.of("nueva", "rápida", "demo", "salir"), done);
    }

    @Test
    void theCardsSayWhatIsSimulated() {
        assertEquals("Simulación rápida", panel.quickCard().getTitle());
        assertEquals("60 pacientes · 2 horas · velocidad ×10", panel.quickCard().getDetail());
        assertEquals("Demostración: catástrofe", panel.demoCard().getTitle());
        assertEquals("100 pacientes · 2 horas · velocidad ×10", panel.demoCard().getDetail());
        assertEquals("DEMO", panel.demoCard().getBadge());
    }

    @Test
    void showsTheDataOfTheStandardHospital() {
        assertEquals(List.of("30 camas", "6 UCI", "4 quirófanos", "16 médicos", "24 enfermeros"), panel.chipTexts());
    }

    @Test
    void describesTheDurationInHoursOrMinutes() {
        assertEquals("100 pacientes · 8 horas · velocidad ×1",
                HomePanel.describe(new CaseConfig(1L, Scenario.NORMAL, 100, 480), 1));
        assertEquals("20 pacientes · 1 hora · velocidad ×5",
                HomePanel.describe(new CaseConfig(1L, Scenario.NORMAL, 20, 60), 5));
        assertEquals("20 pacientes · 90 min · velocidad ×2",
                HomePanel.describe(new CaseConfig(1L, Scenario.NORMAL, 20, 90), 2));
    }

    @Test
    void fitsInASmallLaptopScreen() {
        Dimension size = panel.getPreferredSize();

        assertTrue(size.width <= 640, "ancho " + size.width);
        assertTrue(size.height <= 700, "alto " + size.height);
    }

    @Test
    void paintsTheDarkBackgroundWithTheAccentLineOnTop() {
        Dimension size = panel.getPreferredSize();
        panel.setSize(size);
        layOut(panel);
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();

        assertEquals(HomeTheme.BACKGROUND.getRGB(), image.getRGB(2, size.height - 2));
        Color top = new Color(image.getRGB(size.width / 2, 0));
        assertTrue(Math.abs(top.getGreen() - HomeTheme.ACCENT.getGreen()) < 12
                && Math.abs(top.getBlue() - HomeTheme.ACCENT.getBlue()) < 12, "línea de acento: " + top);
    }

    @Test
    void rejectsMissingData() {
        HomePanel.Actions actions = new HomePanel.Actions(() -> { }, () -> { }, () -> { }, () -> { });

        assertThrows(IllegalArgumentException.class, () -> new HomePanel(null, QUICK, DEMO, 10, actions));
        assertThrows(IllegalArgumentException.class,
                () -> new HomePanel(HospitalConfig.standard(), QUICK, DEMO, 0, actions));
        assertThrows(IllegalArgumentException.class,
                () -> new HomePanel(HospitalConfig.standard(), QUICK, DEMO, 10, null));
        assertThrows(IllegalArgumentException.class, () -> new HomePanel.Actions(null, () -> { }, () -> { },
                () -> { }));
        assertThrows(IllegalArgumentException.class, () -> HomePanel.describe(null, 1));
    }

    @Test
    void inABiggerWindowTheContentStaysCentered() {
        Dimension size = panel.getPreferredSize();
        panel.setSize(size.width + 400, size.height + 300);
        layOut(panel);

        int left = panel.quickCard().getX();
        int right = panel.getWidth() - left - panel.quickCard().getWidth();
        assertTrue(Math.abs(left - right) <= 3, "centrada a lo ancho: " + left + " y " + right);
        int top = panel.newSimulationButton().getY();
        assertTrue(top > 150 + 150, "bajó con el espacio de arriba: " + top);
        Component first = panel.getComponent(1);
        Component last = panel.getComponent(panel.getComponentCount() - 2);
        int above = first.getY() - panel.getInsets().top;
        int below = panel.getHeight() - panel.getInsets().bottom - last.getY() - last.getHeight();
        assertTrue(Math.abs(above - below) <= 2, "centrada a lo alto: " + above + " y " + below);
    }
}
