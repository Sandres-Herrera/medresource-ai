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

/**
 * Etiqueta corta en forma de pastilla, sin acción: el triage del paciente, lo que está haciendo, cada síntoma o
 * el aviso «Siguiendo a P003».
 *
 * <p>Se pinta con un fondo, un color de texto y, si se pide, un borde. {@link #tinted(String, Color)} arma la
 * forma más común: texto de un color sobre un fondo hecho con ese mismo color muy transparente.</p>
 */
public final class Chip extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Alto de la etiqueta. */
    public static final int HEIGHT = 22;
    /** Opacidad del fondo de una etiqueta teñida, de 0 a 255. */
    public static final int TINT_ALPHA = 38;

    private String text;
    private Color foreground;
    private Color background;
    private Color border;

    /**
     * Crea la etiqueta.
     *
     * @param text       texto
     * @param foreground color del texto
     * @param background color del fondo
     * @param border     color del borde, o nulo si no lleva
     * @throws IllegalArgumentException si falta el texto o alguno de los dos primeros colores
     */
    public Chip(String text, Color foreground, Color background, Color border) {
        setText(text);
        setColors(foreground, background, border);
    }

    /**
     * Crea una etiqueta teñida: el texto de un color sobre un fondo de ese color casi transparente.
     *
     * @param text  texto
     * @param color color del texto y del tinte
     * @return la etiqueta
     */
    public static Chip tinted(String text, Color color) {
        if (color == null) {
            throw new IllegalArgumentException("El color es obligatorio.");
        }
        return new Chip(text, color, tint(color), null);
    }

    /** El mismo color con la opacidad del fondo de las etiquetas teñidas. */
    public static Color tint(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), TINT_ALPHA);
    }

    public String getText() {
        return text;
    }

    /**
     * Cambia el texto.
     *
     * @param text texto nuevo
     * @throws IllegalArgumentException si falta
     */
    public void setText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("El texto de la etiqueta es obligatorio.");
        }
        this.text = text;
        revalidate();
        repaint();
    }

    /**
     * Cambia los colores.
     *
     * @param foreground color del texto
     * @param background color del fondo
     * @param border     color del borde, o nulo si no lleva
     * @throws IllegalArgumentException si falta alguno de los dos primeros
     */
    public void setColors(Color foreground, Color background, Color border) {
        if (foreground == null || background == null) {
            throw new IllegalArgumentException("El color del texto y el del fondo son obligatorios.");
        }
        this.foreground = foreground;
        this.background = background;
        this.border = border;
        repaint();
    }

    public Color textColor() {
        return foreground;
    }

    public Color fillColor() {
        return background;
    }

    public Color borderColor() {
        return border;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(font());
        return new Dimension(metrics.stringWidth(text) + 20, HEIGHT);
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
        surface.setColor(background);
        surface.fill(shape);
        if (border != null) {
            surface.setColor(border);
            surface.draw(shape);
        }
        surface.setFont(font());
        FontMetrics metrics = surface.getFontMetrics();
        surface.setColor(foreground);
        surface.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2,
                (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
        surface.dispose();
    }

    private static Font font() {
        return HomeTheme.font(Font.PLAIN, 12f);
    }
}
