package com.medresource.ui.view;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;

/**
 * Punto de color de 8 píxeles, como el de cada evento del recorrido en la ficha del paciente.
 */
public final class Dot extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Diámetro del punto. */
    public static final int SIZE = 8;

    private Color color;

    /**
     * Crea el punto.
     *
     * @param color color
     * @throws IllegalArgumentException si es nulo
     */
    public Dot(Color color) {
        setColor(color);
    }

    public Color getColor() {
        return color;
    }

    /**
     * Cambia el color.
     *
     * @param color color nuevo
     * @throws IllegalArgumentException si es nulo
     */
    public void setColor(Color color) {
        if (color == null) {
            throw new IllegalArgumentException("El color del punto es obligatorio.");
        }
        this.color = color;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SIZE, SIZE);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        surface.setColor(color);
        surface.fill(new Ellipse2D.Double((getWidth() - SIZE) / 2.0, (getHeight() - SIZE) / 2.0, SIZE, SIZE));
        surface.dispose();
    }
}
