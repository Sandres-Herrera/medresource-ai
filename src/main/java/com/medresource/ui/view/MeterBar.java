package com.medresource.ui.view;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Barra fina y redondeada que muestra una parte de un total: la salud del paciente, el avance de su tratamiento,
 * los recursos libres o el progreso del caso.
 *
 * <p>Se llena de izquierda a derecha con su color sobre una pista oscura. Ocupa todo el ancho que le den.</p>
 */
public final class MeterBar extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Alto de la barra. */
    public static final int HEIGHT = 6;
    /** Color de la parte vacía. */
    public static final Color TRACK = new Color(0x1B, 0x27, 0x40);

    private double fraction;
    private Color color;

    /**
     * Crea la barra vacía.
     *
     * @param color color de la parte llena
     * @throws IllegalArgumentException si el color es nulo
     */
    public MeterBar(Color color) {
        setColor(color);
    }

    public double getFraction() {
        return fraction;
    }

    /**
     * Cambia cuánto está llena; lo que se salga de 0 a 1 se ajusta.
     *
     * @param fraction parte llena, de 0 a 1
     */
    public void setFraction(double fraction) {
        double clamped = Double.isNaN(fraction) ? 0.0 : Math.max(0.0, Math.min(1.0, fraction));
        if (clamped != this.fraction) {
            this.fraction = clamped;
            repaint();
        }
    }

    public Color getColor() {
        return color;
    }

    /**
     * Cambia el color de la parte llena.
     *
     * @param color color nuevo
     * @throws IllegalArgumentException si es nulo
     */
    public void setColor(Color color) {
        if (color == null) {
            throw new IllegalArgumentException("El color de la barra es obligatorio.");
        }
        this.color = color;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(60, HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int height = Math.min(HEIGHT, getHeight());
        double y = (getHeight() - height) / 2.0;
        surface.setColor(TRACK);
        surface.fill(new RoundRectangle2D.Double(0, y, getWidth(), height, height, height));
        if (fraction > 0) {
            surface.setColor(color);
            surface.fill(new RoundRectangle2D.Double(0, y, Math.max(height, getWidth() * fraction), height, height,
                    height));
        }
        surface.dispose();
    }
}
