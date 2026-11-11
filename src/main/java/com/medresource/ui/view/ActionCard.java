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
 * Tarjeta de una opción de la pantalla de inicio: un icono en su cuadro de color, un título, una línea con los
 * datos y, a la derecha, una flecha o una etiqueta (por ejemplo «DEMO»).
 *
 * <p>Con el ratón encima, la tarjeta se aclara y su borde y su flecha toman el color de acento; con el foco del
 * teclado, también lleva el borde de acento. Se pulsa como un botón ({@link Clickable}).</p>
 */
public final class ActionCard extends Clickable {

    private static final long serialVersionUID = 1L;

    /** Ancho preferido de la tarjeta. */
    public static final int WIDTH = 460;
    /** Alto de la tarjeta. */
    public static final int HEIGHT = 68;

    private static final int ARC = 14;
    private static final int TILE = 40;
    private static final int PADDING = 16;

    private final HomeIcon icon;
    private final Color iconColor;
    private final Color tileColor;
    private final String title;
    private final String detail;
    private final String badge;

    /**
     * Crea la tarjeta.
     *
     * @param icon      icono de la opción
     * @param iconColor color del icono
     * @param tileColor color del cuadro del icono
     * @param title     título de la opción
     * @param detail    línea con los datos de la opción
     * @param badge     etiqueta de la derecha, en lugar de la flecha; nula si no lleva
     * @throws IllegalArgumentException si falta el icono, algún color, el título o la línea de datos
     */
    public ActionCard(HomeIcon icon, Color iconColor, Color tileColor, String title, String detail, String badge) {
        super(title);
        if (icon == null || iconColor == null || tileColor == null) {
            throw new IllegalArgumentException("El icono y sus colores son obligatorios.");
        }
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("La línea de datos es obligatoria.");
        }
        this.icon = icon;
        this.iconColor = iconColor;
        this.tileColor = tileColor;
        this.title = title;
        this.detail = detail;
        this.badge = badge == null || badge.isBlank() ? null : badge;
        getAccessibleContext().setAccessibleDescription(detail);
        setToolTipText(detail);
    }

    /** Título de la opción. */
    public String getTitle() {
        return title;
    }

    /** Línea con los datos de la opción. */
    public String getDetail() {
        return detail;
    }

    /** Etiqueta de la derecha, o {@code null} si lleva la flecha. */
    public String getBadge() {
        return badge;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        boolean lit = isHover() || isFocusOwner();
        RoundRectangle2D card = new RoundRectangle2D.Double(0.5, 0.5, width - 1.0, height - 1.0, ARC, ARC);
        surface.setColor(isHover() ? HomeTheme.CARD_HOVER : HomeTheme.CARD);
        surface.fill(card);
        surface.setStroke(new BasicStroke(1f));
        surface.setColor(lit ? HomeTheme.ACCENT : HomeTheme.CARD_BORDER);
        surface.draw(card);

        int tileY = (height - TILE) / 2;
        surface.setColor(tileColor);
        surface.fill(new RoundRectangle2D.Double(PADDING, tileY, TILE, TILE, 10, 10));
        icon.paint(surface, PADDING + 10, tileY + 10, TILE - 20, iconColor);

        int textX = PADDING + TILE + 14;
        surface.setFont(HomeTheme.font(Font.BOLD, 15f));
        surface.setColor(HomeTheme.TEXT);
        surface.drawString(title, textX, height / 2 - 3);
        surface.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        surface.setColor(HomeTheme.MUTED);
        surface.drawString(detail, textX, height / 2 + 15);

        if (badge != null) {
            paintBadge(surface, width, height);
        } else {
            HomeIcon.CHEVRON.paint(surface, width - PADDING - 18, (height - 18) / 2.0, 18,
                    lit ? HomeTheme.ACCENT : HomeTheme.MUTED);
        }
        surface.dispose();
    }

    /** Etiqueta en forma de píldora, roja, a la derecha de la tarjeta. */
    private void paintBadge(Graphics2D surface, int width, int height) {
        surface.setFont(HomeTheme.font(Font.BOLD, 11f));
        FontMetrics metrics = surface.getFontMetrics();
        int pillWidth = metrics.stringWidth(badge) + 18;
        int pillHeight = 20;
        int x = width - PADDING - pillWidth;
        int y = (height - pillHeight) / 2;
        surface.setColor(HomeTheme.DANGER_TILE);
        surface.fill(new RoundRectangle2D.Double(x, y, pillWidth, pillHeight, pillHeight, pillHeight));
        surface.setColor(HomeTheme.DANGER);
        surface.drawString(badge, x + 9, y + (pillHeight + metrics.getAscent() - metrics.getDescent()) / 2);
    }
}
