package com.medresource.ui.view;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * Un icono solo, sin acción, del tamaño y el color dados: el de los títulos de las secciones o el de la línea de
 * información.
 */
public final class IconLabel extends JComponent {

    private static final long serialVersionUID = 1L;

    private final HomeIcon icon;
    private final int size;
    private Color color;

    /**
     * Crea el icono.
     *
     * @param icon  icono
     * @param size  lado en píxeles, positivo
     * @param color color
     * @throws IllegalArgumentException si falta el icono o el color, o el tamaño no es positivo
     */
    public IconLabel(HomeIcon icon, int size, Color color) {
        if (icon == null || color == null || size <= 0) {
            throw new IllegalArgumentException("El icono, su tamaño y su color son obligatorios.");
        }
        this.icon = icon;
        this.size = size;
        this.color = color;
    }

    public HomeIcon getIcon() {
        return icon;
    }

    public Color getColor() {
        return color;
    }

    /**
     * Cambia el color del icono.
     *
     * @param color color nuevo
     * @throws IllegalArgumentException si es nulo
     */
    public void setColor(Color color) {
        if (color == null) {
            throw new IllegalArgumentException("El color es obligatorio.");
        }
        this.color = color;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(size, size);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        icon.paint((Graphics2D) graphics, 0, 0, size, color);
    }
}
