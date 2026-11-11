package com.medresource.ui.view;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Logo de MedResource AI: un cuadro verde agua oscuro con la línea de un electrocardiograma que late.
 *
 * <p>La línea se dibuja tenue y, sobre ella, un pulso brillante la recorre de izquierda a derecha dejando una
 * estela, como en el monitor de un paciente. El latido avanza solo mientras el logo está en pantalla: empieza al
 * mostrarse y se detiene al cerrarse la ventana, así no gasta nada escondido. {@link #image(int)} da el mismo logo,
 * quieto, para el icono de la ventana.</p>
 */
public final class LogoBadge extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Lado del logo, en píxeles. */
    public static final int SIZE = 72;
    /** Milisegundos entre dos cuadros de la animación. */
    public static final int FRAME_MILLIS = 30;
    /** Parte del recorrido que avanza el pulso en cada cuadro: una vuelta cada 2,4 s. */
    public static final double STEP = FRAME_MILLIS / 2400.0;

    /** Parte de la línea, detrás del pulso, que todavía brilla. */
    private static final double TRAIL = 0.35;
    /** Puntos de la línea del electrocardiograma en un cuadro de 24 × 24. */
    private static final double[] PULSE = {2, 12, 7, 12, 9.5, 7, 13, 17, 15.5, 10, 17, 12, 22, 12};

    private final transient Timer timer = new Timer(FRAME_MILLIS, event -> advance());
    private double phase = 0.6;

    /** Crea el logo, quieto hasta que se muestre. */
    public LogoBadge() {
        setOpaque(false);
        setToolTipText("MedResource AI");
    }

    /**
     * Dibuja el logo, quieto, en una imagen cuadrada; sirve de icono de la ventana.
     *
     * @param size lado de la imagen, en píxeles
     * @return imagen del logo con fondo transparente
     * @throws IllegalArgumentException si el lado no es positivo
     */
    public static BufferedImage image(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("El lado de la imagen debe ser positivo.");
        }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        paintLogo(graphics, size, 1.0);
        graphics.dispose();
        return image;
    }

    /** Parte del recorrido donde va el pulso, de 0 (izquierda) a casi 1 (derecha). */
    public double getPhase() {
        return phase;
    }

    /**
     * Pone el pulso en una parte del recorrido y vuelve a dibujar.
     *
     * @param phase parte del recorrido, desde 0 y menor que 1
     * @throws IllegalArgumentException si está fuera de ese rango
     */
    public void setPhase(double phase) {
        if (!(phase >= 0.0 && phase < 1.0)) {
            throw new IllegalArgumentException("La fase del pulso debe estar entre 0 y 1.");
        }
        this.phase = phase;
        repaint();
    }

    /** Avanza el pulso un cuadro; al llegar al final vuelve a empezar. */
    public void advance() {
        setPhase((phase + STEP) % 1.0);
    }

    /** Indica si el latido está corriendo: solo mientras el logo está en pantalla. */
    public boolean isAnimating() {
        return timer.isRunning();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SIZE, SIZE);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        int side = Math.min(getWidth(), getHeight());
        surface.translate((getWidth() - side) / 2.0, (getHeight() - side) / 2.0);
        paintLogo(surface, side, phase);
        surface.dispose();
    }

    /**
     * Dibuja el logo en un cuadro de {@code side} píxeles: el fondo, la línea tenue y el pulso con su estela. Con
     * fase 1 la línea completa brilla (logo quieto).
     */
    private static void paintLogo(Graphics2D graphics, int side, double phase) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        surface.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double arc = side * 0.28;
        surface.setColor(HomeTheme.LOGO_TILE);
        surface.fill(new RoundRectangle2D.Double(0, 0, side, side, arc, arc));
        surface.setColor(withAlpha(HomeTheme.ACCENT, 70));
        surface.setStroke(new BasicStroke(Math.max(1f, side / 72f)));
        surface.draw(new RoundRectangle2D.Double(0.5, 0.5, side - 1.0, side - 1.0, arc, arc));

        double inset = side * 0.22;
        double scale = (side - 2 * inset) / 24.0;
        surface.translate(inset, inset);
        surface.scale(scale, scale);
        float width = (float) (Math.max(1.5, side / 26.0) / scale);
        surface.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        surface.setColor(phase >= 1.0 ? HomeTheme.ACCENT : withAlpha(HomeTheme.ACCENT, 110));
        surface.draw(polyline(0.0, 1.0));
        if (phase < 1.0) {
            int steps = 12;
            for (int step = 0; step < steps; step++) {
                double from = Math.max(0.0, phase - TRAIL * (steps - step) / steps);
                double to = Math.max(0.0, phase - TRAIL * (steps - step - 1) / steps);
                if (to > from) {
                    surface.setColor(withAlpha(HomeTheme.ACCENT, 60 + 195 * (step + 1) / steps));
                    surface.draw(polyline(from, to));
                }
            }
            Point2D head = pointAt(phase);
            double dot = width * 1.6;
            surface.setColor(HomeTheme.ACCENT_HOVER);
            surface.fill(new Ellipse2D.Double(head.getX() - dot / 2, head.getY() - dot / 2, dot, dot));
        }
        surface.dispose();
    }

    /** Tramo de la línea entre dos partes del recorrido (de 0 a 1, medidas a lo largo de la línea). */
    private static Shape polyline(double from, double to) {
        Path2D.Double path = new Path2D.Double();
        Point2D start = pointAt(from);
        path.moveTo(start.getX(), start.getY());
        double walked = 0;
        double total = length();
        for (int index = 2; index < PULSE.length; index += 2) {
            double segment = Point2D.distance(PULSE[index - 2], PULSE[index - 1], PULSE[index], PULSE[index + 1]);
            double end = (walked + segment) / total;
            if (end > from && walked / total < to) {
                Point2D point = end <= to ? new Point2D.Double(PULSE[index], PULSE[index + 1]) : pointAt(to);
                path.lineTo(point.getX(), point.getY());
            }
            walked += segment;
        }
        return path;
    }

    /** Punto de la línea en una parte del recorrido, medida a lo largo de la línea. */
    private static Point2D pointAt(double share) {
        double target = Math.max(0.0, Math.min(1.0, share)) * length();
        double walked = 0;
        for (int index = 2; index < PULSE.length; index += 2) {
            double x0 = PULSE[index - 2];
            double y0 = PULSE[index - 1];
            double segment = Point2D.distance(x0, y0, PULSE[index], PULSE[index + 1]);
            if (walked + segment >= target) {
                double part = segment == 0 ? 0 : (target - walked) / segment;
                return new Point2D.Double(x0 + (PULSE[index] - x0) * part, y0 + (PULSE[index + 1] - y0) * part);
            }
            walked += segment;
        }
        return new Point2D.Double(PULSE[PULSE.length - 2], PULSE[PULSE.length - 1]);
    }

    private static double length() {
        double total = 0;
        for (int index = 2; index < PULSE.length; index += 2) {
            total += Point2D.distance(PULSE[index - 2], PULSE[index - 1], PULSE[index], PULSE[index + 1]);
        }
        return total;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
