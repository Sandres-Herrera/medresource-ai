package com.medresource.ui.view;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Botón de los accesos rápidos de la pantalla de inicio: un icono de acento y un texto, centrados, en una caja
 * redondeada con un borde verde agua tenue.
 *
 * <p>Con el ratón encima, la caja se aclara y el borde toma el color de acento; con el foco del teclado también
 * lleva el borde de acento. Todos tienen el mismo ancho, para que la fila quede pareja. Se pulsa como un botón
 * ({@link Clickable}).</p>
 */
public final class QuickAccessButton extends Clickable {

    private static final long serialVersionUID = 1L;

    /** Ancho del botón. */
    public static final int WIDTH = 204;
    /** Alto del botón. */
    public static final int HEIGHT = 46;
    /** Borde del botón en reposo: el acento, apagado. */
    static final Color BORDER = new Color(HomeTheme.ACCENT.getRed(), HomeTheme.ACCENT.getGreen(),
            HomeTheme.ACCENT.getBlue(), 90);

    private static final int ARC = 14;
    private static final int ICON = 20;

    private final HomeIcon icon;
    private final String text;

    /**
     * Crea el botón.
     *
     * @param icon icono a la izquierda del texto
     * @param text texto del botón
     * @throws IllegalArgumentException si falta el icono o el texto
     */
    public QuickAccessButton(HomeIcon icon, String text) {
        super(text);
        if (icon == null) {
            throw new IllegalArgumentException("El icono del acceso rápido es obligatorio.");
        }
        this.icon = icon;
        this.text = text;
    }

    /** Icono del botón. */
    public HomeIcon getIcon() {
        return icon;
    }

    /** Texto del botón. */
    public String getText() {
        return text;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        boolean lit = isHover() || isFocusOwner();
        RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, width - 1.0, height - 1.0, ARC, ARC);
        surface.setColor(isPressed() ? HomeTheme.CARD_BORDER : isHover() ? HomeTheme.CARD_HOVER : HomeTheme.CARD);
        surface.fill(box);
        surface.setStroke(new BasicStroke(1.2f));
        surface.setColor(lit ? HomeTheme.ACCENT : BORDER);
        surface.draw(box);
        surface.setFont(HomeTheme.font(Font.PLAIN, 13.5f));
        FontMetrics metrics = surface.getFontMetrics();
        int gap = ICON + 12;
        int x = (width - metrics.stringWidth(text) - gap) / 2;
        icon.paint(surface, x, (height - ICON) / 2.0, ICON, HomeTheme.ACCENT);
        surface.setColor(HomeTheme.TEXT);
        surface.drawString(text, x + gap, (height + metrics.getAscent() - metrics.getDescent()) / 2);
        surface.dispose();
    }
}
