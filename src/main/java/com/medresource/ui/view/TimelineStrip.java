package com.medresource.ui.view;

import com.medresource.model.CollapsePeriod;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Objects;

/**
 * Línea de tiempo del reporte: toda la simulación de izquierda a derecha, con los periodos de colapso en rojo y
 * una marca donde terminaron las llegadas.
 *
 * <p>Debajo lleva la hora del reloj cada hora (o cada dos o cuatro horas, si la simulación es larga), así se ve
 * de un vistazo cuándo colapsó el hospital y cuándo se recuperó.</p>
 */
public final class TimelineStrip extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Alto de la línea con sus horas. */
    public static final int HEIGHT = 64;
    /** Color de los periodos de colapso. */
    public static final Color COLLAPSE = new Color(HomeTheme.RED.getRed(), HomeTheme.RED.getGreen(),
            HomeTheme.RED.getBlue(), 200);
    /** Texto de la marca del final de las llegadas. */
    public static final String ARRIVALS_END = "Fin de las llegadas";

    private static final int TRACK_TOP = 18;
    private static final int TRACK_HEIGHT = 14;
    private static final int MINUTES_PER_HOUR = 60;
    private static final int MAX_TICKS = 12;

    private final int totalMinutes;
    private final int arrivalsEndMinute;
    private final transient List<CollapsePeriod> collapses;

    /**
     * Crea la línea de tiempo.
     *
     * @param totalMinutes      minutos que duró toda la simulación, al menos 1
     * @param arrivalsEndMinute minuto en que terminaron las llegadas
     * @param collapses         periodos de colapso, en orden
     * @throws IllegalArgumentException si la duración no es positiva, si el final de las llegadas es negativo o
     *                                  si falta la lista de colapsos
     */
    public TimelineStrip(int totalMinutes, int arrivalsEndMinute, List<CollapsePeriod> collapses) {
        if (totalMinutes < 1 || arrivalsEndMinute < 0) {
            throw new IllegalArgumentException("La duración debe ser positiva y el final de las llegadas no negativo.");
        }
        if (collapses == null || collapses.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("La lista de colapsos es obligatoria.");
        }
        this.totalMinutes = totalMinutes;
        this.arrivalsEndMinute = arrivalsEndMinute;
        this.collapses = List.copyOf(collapses);
        setOpaque(false);
    }

    public int getTotalMinutes() {
        return totalMinutes;
    }

    public List<CollapsePeriod> getCollapses() {
        return collapses;
    }

    /**
     * Calcula en qué parte del ancho cae un minuto.
     *
     * @param minute minuto de la simulación
     * @return de 0 a 1; lo que se sale se ajusta
     */
    public double fractionAt(int minute) {
        return Math.max(0.0, Math.min(1.0, minute / (double) totalMinutes));
    }

    /**
     * Elige cada cuánto se escribe la hora debajo de la línea: cada hora, o el doble, hasta que no pasen de
     * {@value #MAX_TICKS} marcas.
     *
     * @return minutos entre una marca y la siguiente
     */
    public int tickMinutes() {
        int step = MINUTES_PER_HOUR;
        while (totalMinutes / step > MAX_TICKS) {
            step *= 2;
        }
        return step;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(640, HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        surface.setFont(HomeTheme.font(Font.PLAIN, 11.5f));
        FontMetrics metrics = surface.getFontMetrics();
        int margin = metrics.stringWidth("00:00") / 2 + 2;
        double width = Math.max(1, getWidth() - 2.0 * margin);
        surface.setColor(MeterBar.TRACK);
        surface.fill(new RoundRectangle2D.Double(margin, TRACK_TOP, width, TRACK_HEIGHT, 6, 6));
        surface.setColor(COLLAPSE);
        for (CollapsePeriod collapse : collapses) {
            double from = margin + width * fractionAt(collapse.startMinute());
            double to = margin + width * fractionAt(collapse.endMinute() + 1);
            surface.fill(new RoundRectangle2D.Double(from, TRACK_TOP, Math.max(3, to - from), TRACK_HEIGHT, 4, 4));
        }
        double end = margin + width * fractionAt(arrivalsEndMinute);
        surface.setColor(HomeTheme.AMBER);
        surface.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[]{4f, 3f}, 0f));
        surface.draw(new Line2D.Double(end, 4, end, TRACK_TOP + TRACK_HEIGHT + 4));
        int labelX = (int) Math.min(end + 4, getWidth() - metrics.stringWidth(ARRIVALS_END));
        surface.drawString(ARRIVALS_END, Math.max(0, labelX), metrics.getAscent() - 1);
        surface.setStroke(new BasicStroke(1f));
        int labelY = TRACK_TOP + TRACK_HEIGHT + 8 + metrics.getAscent();
        int step = tickMinutes();
        for (int minute = 0; minute <= totalMinutes; minute += step) {
            drawTick(surface, metrics, margin + width * fractionAt(minute), labelY, minute);
        }
        if (totalMinutes % step != 0) {
            drawTick(surface, metrics, margin + width, labelY, totalMinutes);
        }
        surface.dispose();
    }

    private static void drawTick(Graphics2D surface, FontMetrics metrics, double x, int labelY, int minute) {
        surface.setColor(HomeTheme.FAINT);
        surface.draw(new Line2D.Double(x, TRACK_TOP + TRACK_HEIGHT + 1, x, TRACK_TOP + TRACK_HEIGHT + 5));
        String text = ReportFormat.clock(minute);
        surface.setColor(HomeTheme.MUTED);
        surface.drawString(text, (float) (x - metrics.stringWidth(text) / 2.0), labelY);
    }
}
