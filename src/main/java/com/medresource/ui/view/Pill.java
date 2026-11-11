package com.medresource.ui.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Pastilla de texto que se elige entre varias puestas en fila, como el tamaño del hospital (pequeño, estándar,
 * grande) o la velocidad inicial (×1, ×2, ×5, ×10).
 *
 * <p>La elegida tiene el borde y el texto de acento sobre un fondo verde muy oscuro; las demás, borde fino y
 * texto gris, que se pone blanco con el ratón encima. Elegir una y soltar las demás le toca a quien las agrupa.
 * Se pulsa como un botón ({@link Clickable}).</p>
 */
public final class Pill extends Clickable {

    private static final long serialVersionUID = 1L;

    /** Alto de la pastilla. */
    public static final int HEIGHT = 26;

    private final String text;
    private boolean selected;

    /**
     * Crea la pastilla.
     *
     * @param text texto
     * @throws IllegalArgumentException si falta el texto
     */
    public Pill(String text) {
        super(text);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public boolean isSelected() {
        return selected;
    }

    /**
     * Marca la pastilla como elegida o no y vuelve a dibujarla.
     *
     * @param selected {@code true} si es la elegida
     */
    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            repaint();
        }
    }

    /** Color del texto ahora: acento si está elegida, blanco con el ratón encima y gris si no. */
    public Color textColor() {
        if (selected) {
            return HomeTheme.ACCENT;
        }
        return isHover() || isFocusOwner() ? HomeTheme.TEXT : HomeTheme.MUTED;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(font());
        return new Dimension(metrics.stringWidth(text) + 26, HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double arc = getHeight() - 1.0;
        RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, arc, arc);
        surface.setColor(selected ? HomeTheme.SELECTED : isHover() ? HomeTheme.CARD_HOVER : HomeTheme.FIELD);
        surface.fill(shape);
        surface.setColor(selected || isFocusOwner() ? HomeTheme.ACCENT : HomeTheme.CARD_BORDER);
        surface.draw(shape);
        surface.setFont(font());
        FontMetrics metrics = surface.getFontMetrics();
        surface.setColor(textColor());
        surface.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2,
                (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
        surface.dispose();
    }

    private Font font() {
        return HomeTheme.font(selected ? Font.BOLD : Font.PLAIN, 12.5f);
    }
}
