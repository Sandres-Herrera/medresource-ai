package com.medresource.ui.view;

import com.medresource.model.GeneratedReport;
import com.medresource.model.ReportSection;
import com.medresource.ui.view.ReportFigures.ChartImage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las imágenes de las gráficas del reporte.
 */
class ReportFiguresTest {

    @Test
    void everySectionWithAChartBecomesAnImageOnWhite() throws IOException {
        GeneratedReport generated = ReportViewFixtures.generated();

        for (ReportSection section : ReportSection.values()) {
            boolean hasChart = ReportCharts.chartFor(section, generated).isPresent();
            assertEquals(hasChart, ReportFigures.imageFor(section, generated).isPresent(), section.name());
        }
        ChartImage image = ReportFigures.imageFor(ReportSection.TRIAGE, generated).orElseThrow();
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(image.png()));

        assertEquals(image.width(), read.getWidth());
        assertEquals(image.height(), read.getHeight());
        assertEquals(Color.WHITE.getRGB(), read.getRGB(1, 1));
    }

    @Test
    void theChartIsDrawnAtDoubleSizeWithPaperColors() {
        BarChart chart = new BarChart(List.of(new BarChart.Bar("Triage I", 2, "2 min", HomeTheme.RED)), 0);

        ChartImage image = ReportFigures.render(chart);

        assertEquals(ChartColors.PAPER, chart.getColors());
        assertTrue(image.width() >= ReportFigures.WIDTH * ReportFigures.SCALE);
        assertTrue(image.height() >= chart.getPreferredSize().height * ReportFigures.SCALE);
    }

    @Test
    void wrongDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ReportFigures.render(null));
        assertThrows(IllegalArgumentException.class,
                () -> ReportFigures.imageFor(null, ReportViewFixtures.generated()));
        assertThrows(IllegalArgumentException.class, () -> new ChartImage(new byte[0], 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ChartImage(new byte[]{1}, 0, 1));
    }
}
