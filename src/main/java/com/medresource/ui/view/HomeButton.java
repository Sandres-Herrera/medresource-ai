package com.medresource.ui.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Botón de la pantalla de inicio y del formulario de una simulación nueva, en uno de tres estilos.
 *
 * <ul>
 *     <li>{@linkplain Style#PRIMARY Principal}: ancho, relleno con el color de acento, con un icono opcional a la
 *     izquierda del texto. Con el ratón encima se aclara y al presionarlo se oscurece un poco.</li>
 *     <li>{@linkplain Style#COMPACT Compacto}: igual que el principal pero del ancho de su texto y más bajo, para
 *     el pie de un formulario.</li>
 *     <li>{@linkplain Style#LINK Enlace}: solo el texto, en gris; con el ratón encima se pone blanco y se
 *     subraya.</li>
 * </ul>
 *
 * <p>Con el foco del teclado, los dos llevan un anillo de acento alrededor. Se pulsa como un botón
 * ({@link Clickable}).</p>
 */
public final class HomeButton extends Clickable {

    private static final long serialVersionUID = 1L;

    /** Estilo del botón. */
    public enum Style {
        /** Botón ancho relleno con el color de acento: la acción principal. */
        PRIMARY,
        /** Relleno con el color de acento, del ancho de su texto: la acción principal de un formulario. */
        COMPACT,
        /** Solo texto, como un enlace: una acción secundaria. */
        LINK
    }

    /** Alto del botón principal. */
    public static final int PRIMARY_HEIGHT = 52;
    /** Ancho del botón principal: el de las tarjetas más el espacio del anillo de foco a cada lado. */
    public static final int PRIMARY_WIDTH = ActionCard.WIDTH + 6;
    /** Alto del botón compacto. */
    public static final int COMPACT_HEIGHT = 40;

    private static final int ICON_SIZE = 18;
    private static final int ARC = 14;

    private final Style style;
    private final String text;
    private final HomeIcon icon;

    /**
     * Crea el botón.
     *
     * @param style estilo del botón
     * @param text  texto del botón
     * @param icon  icono a la izquierda del texto (solo en los estilos con relleno); nulo si no lleva
     * @throws IllegalArgumentException si falta el estilo o el texto
     */
    public HomeButton(Style style, String text, HomeIcon icon) {
        super(text);
        if (style == null) {
            throw new IllegalArgumentException("El estilo del botón es obligatorio.");
        }
        this.style = style;
        this.text = text;
        this.icon = style == Style.LINK ? null : icon;
    }

    /** Estilo del botón. */
    public Style getStyle() {
        return style;
    }

    /** Texto del botón. */
    public String getText() {
        return text;
    }

    @Override
    public Dimension getPreferredSize() {
        if (style == Style.PRIMARY) {
            return new Dimension(PRIMARY_WIDTH, PRIMARY_HEIGHT);
        }
        FontMetrics metrics = getFontMetrics(font());
        if (style == Style.COMPACT) {
            int gap = icon == null ? 0 : ICON_SIZE + 8;
            return new Dimension(metrics.stringWidth(text) + gap + 40, COMPACT_HEIGHT);
        }
        return new Dimension(metrics.stringWidth(text) + 16, metrics.getHeight() + 10);
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
        surface.setFont(font());
        if (style != Style.LINK) {
            paintPrimary(surface);
        } else {
            paintLink(surface);
        }
        surface.dispose();
    }

    private Font font() {
        return switch (style) {
            case PRIMARY -> HomeTheme.font(Font.BOLD, 15f);
            case COMPACT -> HomeTheme.font(Font.BOLD, 13.5f);
            case LINK -> HomeTheme.font(Font.PLAIN, 13f);
        };
    }

    private void paintPrimary(Graphics2D surface) {
        int width = getWidth();
        int height = getHeight();
        if (isFocusOwner()) {
            surface.setColor(HomeTheme.ACCENT);
            surface.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1.0, height - 1.0, ARC + 4, ARC + 4));
        }
        Color fill = isPressed() ? HomeTheme.ACCENT.darker() : isHover() ? HomeTheme.ACCENT_HOVER : HomeTheme.ACCENT;
        surface.setColor(fill);
        surface.fill(new RoundRectangle2D.Double(3, 3, width - 6.0, height - 6.0, ARC, ARC));
        FontMetrics metrics = surface.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int gap = icon == null ? 0 : ICON_SIZE + 8;
        int x = (width - textWidth - gap) / 2;
        if (icon != null) {
            icon.paint(surface, x, (height - ICON_SIZE) / 2.0, ICON_SIZE, HomeTheme.ACCENT_TEXT);
        }
        surface.setColor(HomeTheme.ACCENT_TEXT);
        surface.drawString(text, x + gap, (height + metrics.getAscent() - metrics.getDescent()) / 2);
    }

    private void paintLink(Graphics2D surface) {
        FontMetrics metrics = surface.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int x = (getWidth() - textWidth) / 2;
        int baseline = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
        surface.setColor(isHover() || isFocusOwner() ? HomeTheme.TEXT : HomeTheme.MUTED);
        surface.drawString(text, x, baseline);
        if (isHover() || isFocusOwner()) {
            surface.draw(new Line2D.Double(x, baseline + 3, x + textWidth, baseline + 3));
        }
    }
}
