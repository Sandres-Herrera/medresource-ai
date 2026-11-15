package com.medresource.ui.view;

import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la gráfica de barras del reporte, sin ventana.
 */
class BarChartTest {

    private static BarChart.Bar bar(String label, double value) {
        return new BarChart.Bar(label, value, ReportFormat.decimal(value) + " min", HomeTheme.ACCENT);
    }

    @Test
    void withoutAScaleTheLongestBarFillsItsRow() {
        BarChart chart = new BarChart(List.of(bar("I", 2), bar("II", 8), bar("III", 4)), 0);

        assertEquals(8, chart.getScale(), 1e-9);
        assertEquals(0.25, chart.fractionOf(0), 1e-9);
        assertEquals(1.0, chart.fractionOf(1), 1e-9);
        assertEquals(0.5, chart.fractionOf(2), 1e-9);
    }

    @Test
    void withAScaleEveryBarIsComparedWithIt() {
        BarChart chart = new BarChart(List.of(bar("Camas", 0.62), bar("UCI", 1.2)), 1.0);

        assertEquals(0.62, chart.fractionOf(0), 1e-9);
        assertEquals(1.0, chart.fractionOf(1), 1e-9);
    }

    @Test
    void ifEverythingIsZeroNoBarIsFilled() {
        BarChart chart = new BarChart(List.of(bar("A", 0), bar("B", 0)), 0);

        assertEquals(0.0, chart.fractionOf(0), 1e-9);
    }

    @Test
    void hasOneRowPerBar() {
        BarChart chart = new BarChart(List.of(bar("A", 1), bar("B", 2), bar("C", 3)), 0);

        assertEquals(3 * BarChart.ROW_HEIGHT, chart.getPreferredSize().height);
        assertEquals(3, chart.getBars().size());
    }

    @Test
    void canBePaintedEvenWithLongTexts() {
        BarChart chart = new BarChart(List.of(bar("Un nombre muy largo que no cabe en la columna de nombres", 3),
                bar("B", 0)), 0);
        chart.setSize(700, chart.getPreferredSize().height);
        BufferedImage image = new BufferedImage(700, chart.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D surface = image.createGraphics();

        assertDoesNotThrow(() -> chart.paint(surface));
        surface.dispose();
    }

    @Test
    void startsWithTheScreenColorsAndCanBeDrawnForPaper() {
        BarChart chart = new BarChart(List.of(bar("Sin espera", 0), bar("Triage II", 1)), 0);
        chart.setSize(700, chart.getPreferredSize().height);

        assertEquals(ChartColors.SCREEN, chart.getColors());
        assertTrue(brightness(paintOnWhite(chart), BarChart.LABEL_WIDTH + 30, BarChart.ROW_HEIGHT / 2) < 80);

        chart.setColors(ChartColors.PAPER);
        BufferedImage paper = paintOnWhite(chart);

        assertEquals(ChartColors.PAPER, chart.getColors());
        assertTrue(brightness(paper, BarChart.LABEL_WIDTH + 30, BarChart.ROW_HEIGHT / 2) > 200);
        assertTrue(darkPixels(paper) > 20);
        assertThrows(IllegalArgumentException.class, () -> chart.setColors(null));
    }

    private static BufferedImage paintOnWhite(JComponent component) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D surface = image.createGraphics();
        surface.setColor(Color.WHITE);
        surface.fillRect(0, 0, image.getWidth(), image.getHeight());
        component.paint(surface);
        surface.dispose();
        return image;
    }

    private static int brightness(BufferedImage image, int x, int y) {
        Color pixel = new Color(image.getRGB(x, y));
        return (pixel.getRed() + pixel.getGreen() + pixel.getBlue()) / 3;
    }

    /** Cuenta los píxeles oscuros: los textos que se leen sobre el papel. */
    private static int darkPixels(BufferedImage image) {
        int dark = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (brightness(image, x, y) < 110) {
                    dark++;
                }
            }
        }
        return dark;
    }

    @Test
    void wrongDataIsRejected() {
        List<BarChart.Bar> withNull = new ArrayList<>();
        withNull.add(null);

        assertThrows(IllegalArgumentException.class, () -> new BarChart(List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> new BarChart(null, 0));
        assertThrows(IllegalArgumentException.class, () -> new BarChart(withNull, 0));
        assertThrows(IllegalArgumentException.class, () -> new BarChart(List.of(bar("A", 1)), -1));
        assertThrows(IllegalArgumentException.class, () -> new BarChart.Bar(" ", 1, "1", Color.RED));
        assertThrows(IllegalArgumentException.class, () -> new BarChart.Bar("A", -1, "1", Color.RED));
        assertThrows(IllegalArgumentException.class, () -> new BarChart.Bar("A", Double.NaN, "1", Color.RED));
        assertThrows(IllegalArgumentException.class, () -> new BarChart.Bar("A", 1, null, Color.RED));
        assertThrows(IllegalArgumentException.class, () -> new BarChart.Bar("A", 1, "1", null));
    }
}
