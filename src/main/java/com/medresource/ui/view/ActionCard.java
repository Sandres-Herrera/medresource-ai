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
import java.util.Arrays;
import java.util.List;

/**
 * Tarjeta de una opción de la pantalla de inicio: un icono en su cuadro de color, un título, una línea con los
 * datos y, a la derecha, una flecha, con una etiqueta antes si la lleva (por ejemplo «DEMO»).
 *
 * <p>La línea de datos se escribe separada por « · ». Con {@link #setDetailIcons(HomeIcon...)} cada parte lleva su
 * icono delante (personas, reloj, velocímetro) en lugar de los puntos.</p>
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
    private static final int DETAIL_ICON = 14;

    private final HomeIcon icon;
    private final Color iconColor;
    private final Color tileColor;
    private final String title;
    private final String detail;
    private final String badge;
    private HomeIcon[] detailIcons = new HomeIcon[0];

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

    /** Etiqueta antes de la flecha, o {@code null} si no lleva. */
    public String getBadge() {
        return badge;
    }

    /**
     * Pone un icono delante de cada parte de la línea de datos, en orden.
     *
     * @param icons un icono por cada parte separada por « · »
     * @throws IllegalArgumentException si falta alguno o no hay uno por parte
     */
    public void setDetailIcons(HomeIcon... icons) {
        if (icons == null || icons.length != detailParts().length || Arrays.asList(icons).contains(null)) {
            throw new IllegalArgumentException("Hace falta un icono por cada parte de la línea de datos.");
        }
        detailIcons = icons.clone();
        repaint();
    }

    /** Iconos delante de cada parte de la línea de datos; vacío si se escribe con puntos. */
    public List<HomeIcon> getDetailIcons() {
        return List.of(detailIcons);
    }

    private String[] detailParts() {
        return detail.split(" · ");
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
        paintDetail(surface, textX, height / 2 + 15);

        HomeIcon.CHEVRON.paint(surface, width - PADDING - 18, (height - 18) / 2.0, 18,
                lit ? HomeTheme.ACCENT : HomeTheme.MUTED);
        if (badge != null) {
            paintBadge(surface, width - PADDING - 18 - 10, height);
        }
        surface.dispose();
    }

    /** Línea de datos: con puntos, o con un icono delante de cada parte. */
    private void paintDetail(Graphics2D surface, int x, int baseline) {
        if (detailIcons.length == 0) {
            surface.drawString(detail, x, baseline);
            return;
        }
        FontMetrics metrics = surface.getFontMetrics();
        String[] parts = detailParts();
        int left = x;
        for (int index = 0; index < parts.length; index++) {
            detailIcons[index].paint(surface, left, baseline - DETAIL_ICON + 2, DETAIL_ICON, HomeTheme.MUTED);
            left += DETAIL_ICON + 5;
            surface.drawString(parts[index], left, baseline);
            left += metrics.stringWidth(parts[index]) + 16;
        }
    }

    /** Etiqueta en forma de píldora, roja, a la altura del título, que termina en {@code right}, antes de la flecha. */
    private void paintBadge(Graphics2D surface, int right, int height) {
        surface.setFont(HomeTheme.font(Font.BOLD, 11f));
        FontMetrics metrics = surface.getFontMetrics();
        int pillWidth = metrics.stringWidth(badge) + 18;
        int pillHeight = 20;
        int x = right - pillWidth;
        int y = height / 2 - pillHeight - 2;
        surface.setColor(HomeTheme.DANGER_TILE);
        surface.fill(new RoundRectangle2D.Double(x, y, pillWidth, pillHeight, pillHeight, pillHeight));
        surface.setColor(HomeTheme.DANGER);
        surface.drawString(badge, x + 9, y + (pillHeight + metrics.getAscent() - metrics.getDescent()) / 2);
    }
}
