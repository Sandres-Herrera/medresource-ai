package com.medresource.ui.view;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Objects;

/**
 * Gráfica de barras horizontales del reporte, con el tema oscuro: una fila por dato con su nombre a la izquierda,
 * la barra en el medio y el valor escrito a la derecha.
 *
 * <p>Todas las barras usan la misma escala, así se comparan a simple vista. La escala la da quien arma la
 * gráfica (por ejemplo, 1 para porcentajes); si no la da, es el valor más grande.</p>
 */
public final class BarChart extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Alto de cada fila. */
    public static final int ROW_HEIGHT = 26;
    /** Ancho de la columna de los nombres. */
    public static final int LABEL_WIDTH = 220;
    /** Ancho de la columna de los valores. */
    public static final int VALUE_WIDTH = 250;

    private static final int BAR_HEIGHT = 10;
    private static final int GAP = 10;
    private static final int MIN_BAR_WIDTH = 120;

    /**
     * Un dato de la gráfica.
     *
     * @param label     nombre del dato
     * @param value     valor, no negativo
     * @param valueText valor como se escribe a la derecha de la barra
     * @param color     color de la barra
     */
    public record Bar(String label, double value, String valueText, Color color) {

        /**
         * Valida el dato.
         *
         * @throws IllegalArgumentException si falta el nombre, el texto o el color, o si el valor es negativo o
         *                                  no es un número
         */
        public Bar {
            if (label == null || label.isBlank() || valueText == null || color == null) {
                throw new IllegalArgumentException("El nombre, el texto y el color de la barra son obligatorios.");
            }
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
                throw new IllegalArgumentException("El valor de la barra debe ser un número no negativo.");
            }
        }
    }

    private final transient List<Bar> bars;
    private final double scale;

    /**
     * Crea la gráfica.
     *
     * @param bars  datos, en el orden en que se muestran
     * @param scale valor que llena toda la barra; 0 para usar el valor más grande
     * @throws IllegalArgumentException si no hay datos o si la escala es negativa
     */
    public BarChart(List<Bar> bars, double scale) {
        if (bars == null || bars.isEmpty() || bars.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("La gráfica necesita al menos un dato.");
        }
        if (Double.isNaN(scale) || scale < 0) {
            throw new IllegalArgumentException("La escala no puede ser negativa.");
        }
        this.bars = List.copyOf(bars);
        this.scale = scale > 0 ? scale : bars.stream().mapToDouble(Bar::value).max().orElse(0);
        setOpaque(false);
    }

    public List<Bar> getBars() {
        return bars;
    }

    public double getScale() {
        return scale;
    }

    /**
     * Calcula qué parte de su fila llena una barra.
     *
     * @param index posición del dato
     * @return de 0 a 1; 0 si la escala es 0
     * @throws IndexOutOfBoundsException si no existe ese dato
     */
    public double fractionOf(int index) {
        double value = bars.get(index).value();
        return scale == 0 ? 0 : Math.min(1.0, value / scale);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(LABEL_WIDTH + MIN_BAR_WIDTH * 2 + VALUE_WIDTH + 2 * GAP, bars.size() * ROW_HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font labelFont = HomeTheme.font(Font.PLAIN, 12.5f);
        Font valueFont = HomeTheme.font(Font.BOLD, 12.5f);
        int barX = LABEL_WIDTH + GAP;
        int barWidth = Math.max(MIN_BAR_WIDTH, getWidth() - LABEL_WIDTH - VALUE_WIDTH - 2 * GAP);
        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            int top = i * ROW_HEIGHT;
            int middle = top + ROW_HEIGHT / 2;
            surface.setFont(labelFont);
            FontMetrics labelMetrics = surface.getFontMetrics();
            surface.setColor(HomeTheme.MUTED);
            surface.drawString(fit(bar.label(), labelMetrics, LABEL_WIDTH), 0,
                    middle + labelMetrics.getAscent() / 2 - 2);
            double y = middle - BAR_HEIGHT / 2.0;
            surface.setColor(MeterBar.TRACK);
            surface.fill(new RoundRectangle2D.Double(barX, y, barWidth, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT));
            double filled = barWidth * fractionOf(i);
            if (filled > 0) {
                surface.setColor(bar.color());
                surface.fill(new RoundRectangle2D.Double(barX, y, Math.max(BAR_HEIGHT, filled), BAR_HEIGHT,
                        BAR_HEIGHT, BAR_HEIGHT));
            }
            surface.setFont(valueFont);
            FontMetrics valueMetrics = surface.getFontMetrics();
            surface.setColor(HomeTheme.TEXT);
            surface.drawString(fit(bar.valueText(), valueMetrics, VALUE_WIDTH), barX + barWidth + GAP,
                    middle + valueMetrics.getAscent() / 2 - 2);
        }
        surface.dispose();
    }

    /** Corta el texto con puntos suspensivos si no cabe en el ancho. */
    private static String fit(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && metrics.stringWidth(cut + "…") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
