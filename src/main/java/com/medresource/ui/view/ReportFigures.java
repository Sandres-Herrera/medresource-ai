package com.medresource.ui.view;

import com.medresource.model.GeneratedReport;
import com.medresource.model.ReportSection;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Optional;

/**
 * Convierte la gráfica de cada sección del reporte en una imagen PNG para el documento descargado.
 *
 * <p>Usa las mismas gráficas de la ventana ({@link ReportCharts}), pero con los colores de papel
 * ({@link ChartColors#PAPER}) sobre fondo blanco. Se dibujan al doble de tamaño para que se vean nítidas al
 * imprimir. No necesita mostrar ninguna ventana.</p>
 */
public final class ReportFigures {

    /** Ancho de la gráfica en puntos de pantalla, antes de duplicarlo. */
    public static final int WIDTH = 660;
    /** Cuántos píxeles de la imagen hay por cada punto de la gráfica. */
    public static final int SCALE = 2;

    /** Margen blanco alrededor de la gráfica, en puntos. */
    private static final int MARGIN = 8;

    /**
     * Imagen de una gráfica.
     *
     * @param png    bytes de la imagen PNG
     * @param width  ancho en píxeles
     * @param height alto en píxeles
     */
    public record ChartImage(byte[] png, int width, int height) {

        /**
         * Valida la imagen y guarda una copia.
         *
         * @throws IllegalArgumentException si está vacía o el tamaño no es positivo
         */
        public ChartImage {
            if (png == null || png.length == 0 || width < 1 || height < 1) {
                throw new IllegalArgumentException("La imagen y su tamaño son obligatorios.");
            }
            png = png.clone();
        }

        @Override
        public byte[] png() {
            return png.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ChartImage image && width == image.width && height == image.height
                    && Arrays.equals(png, image.png);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * width + height) + Arrays.hashCode(png);
        }

        @Override
        public String toString() {
            return "ChartImage[" + width + "x" + height + "]";
        }
    }

    private ReportFigures() {
    }

    /**
     * Dibuja la gráfica de una sección como imagen.
     *
     * @param section   sección del reporte
     * @param generated reporte terminado
     * @return la imagen; vacío si la sección no lleva gráfica
     * @throws IllegalArgumentException si falta la sección o el reporte
     */
    public static Optional<ChartImage> imageFor(ReportSection section, GeneratedReport generated) {
        return ReportCharts.chartFor(section, generated).map(ReportFigures::render);
    }

    /**
     * Dibuja una gráfica sobre fondo blanco, con los colores de papel si los admite.
     *
     * @param chart gráfica
     * @return imagen PNG
     * @throws IllegalArgumentException si la gráfica es nula
     * @throws UncheckedIOException     si no se pudo escribir el PNG
     */
    public static ChartImage render(JComponent chart) {
        if (chart == null) {
            throw new IllegalArgumentException("La gráfica es obligatoria.");
        }
        if (chart instanceof BarChart bars) {
            bars.setColors(ChartColors.PAPER);
        } else if (chart instanceof TimelineStrip strip) {
            strip.setColors(ChartColors.PAPER);
        }
        int height = chart.getPreferredSize().height;
        chart.setSize(WIDTH, height);
        int imageWidth = (WIDTH + 2 * MARGIN) * SCALE;
        int imageHeight = (height + 2 * MARGIN) * SCALE;
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D surface = image.createGraphics();
        surface.setColor(Color.WHITE);
        surface.fillRect(0, 0, imageWidth, imageHeight);
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        surface.scale(SCALE, SCALE);
        surface.translate(MARGIN, MARGIN);
        chart.paint(surface);
        surface.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bytes);
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo convertir la gráfica en imagen.", error);
        }
        return new ChartImage(bytes.toByteArray(), imageWidth, imageHeight);
    }
}
