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
 * Opción que se elige entre varias en el formulario de una simulación nueva, como los escenarios o las
 * estrategias: un icono de su color, un título y una línea que la explica.
 *
 * <p>La elegida se ve con el fondo teñido de su color y el borde de ese color; las demás, con el fondo de los
 * campos y un borde fino que se aclara con el ratón encima. La línea de abajo se puede cambiar mientras el
 * formulario está abierto (por ejemplo, cuando cambia el tiempo de llegadas). Elegir una y soltar las demás le
 * toca a quien las agrupa. Se pulsa como un botón ({@link Clickable}).</p>
 */
public final class ChoiceTile extends Clickable {

    private static final long serialVersionUID = 1L;

    /** Alto de la opción. */
    public static final int HEIGHT = 50;
    /** Opacidad del tinte del fondo de la opción elegida, de 0 a 255. */
    public static final int TINT_ALPHA = 34;

    private static final int ARC = 12;
    private static final int ICON = 20;
    private static final int PADDING = 12;

    private final HomeIcon icon;
    private final Color color;
    private final String title;
    private String detail;
    private boolean selected;

    /**
     * Crea la opción.
     *
     * @param icon   icono
     * @param color  color del icono y, cuando está elegida, del borde y del tinte del fondo
     * @param title  título
     * @param detail línea que la explica
     * @throws IllegalArgumentException si falta algún dato
     */
    public ChoiceTile(HomeIcon icon, Color color, String title, String detail) {
        super(title);
        if (icon == null || color == null) {
            throw new IllegalArgumentException("El icono y su color son obligatorios.");
        }
        this.icon = icon;
        this.color = color;
        this.title = title;
        setDetail(detail);
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    /**
     * Cambia la línea que explica la opción.
     *
     * @param detail línea nueva
     * @throws IllegalArgumentException si falta
     */
    public void setDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("La línea que explica la opción es obligatoria.");
        }
        this.detail = detail;
        setToolTipText(title + ": " + detail);
        getAccessibleContext().setAccessibleDescription(detail);
        repaint();
    }

    public boolean isSelected() {
        return selected;
    }

    /**
     * Marca la opción como elegida o no y vuelve a dibujarla.
     *
     * @param selected {@code true} si es la elegida
     */
    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            repaint();
        }
    }

    /** Fondo de la opción ahora: teñido de su color si está elegida, el de los campos si no. */
    public Color background() {
        if (!selected) {
            return isHover() ? HomeTheme.CARD_HOVER : HomeTheme.FIELD;
        }
        return blend(HomeTheme.FIELD, color, TINT_ALPHA);
    }

    /** Borde de la opción ahora: su color si está elegida o tiene el foco, más claro con el ratón encima. */
    public Color border() {
        if (selected || isFocusOwner()) {
            return color;
        }
        return isHover() ? HomeTheme.MUTED.darker() : HomeTheme.CARD_BORDER;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics titleMetrics = getFontMetrics(HomeTheme.font(Font.BOLD, 13f));
        FontMetrics detailMetrics = getFontMetrics(HomeTheme.font(Font.PLAIN, 11.5f));
        int text = Math.max(titleMetrics.stringWidth(title), detailMetrics.stringWidth(detail));
        return new Dimension(PADDING + ICON + 10 + text + PADDING, HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        surface.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, ARC, ARC);
        surface.setColor(background());
        surface.fill(box);
        surface.setColor(border());
        surface.draw(box);
        int height = getHeight();
        icon.paint(surface, PADDING, (height - ICON) / 2.0, ICON, color);
        int textX = PADDING + ICON + 10;
        surface.setFont(HomeTheme.font(Font.BOLD, 13f));
        surface.setColor(HomeTheme.TEXT);
        surface.drawString(title, textX, height / 2 - 2);
        surface.setFont(HomeTheme.font(Font.PLAIN, 11.5f));
        surface.setColor(HomeTheme.MUTED);
        surface.drawString(detail, textX, height / 2 + 13);
        surface.dispose();
    }

    /** Mezcla un color de fondo con otro encima, con la opacidad dada (0 a 255). */
    private static Color blend(Color base, Color over, int alpha) {
        double share = alpha / 255.0;
        return new Color((int) Math.round(base.getRed() + (over.getRed() - base.getRed()) * share),
                (int) Math.round(base.getGreen() + (over.getGreen() - base.getGreen()) * share),
                (int) Math.round(base.getBlue() + (over.getBlue() - base.getBlue()) * share));
    }
}
