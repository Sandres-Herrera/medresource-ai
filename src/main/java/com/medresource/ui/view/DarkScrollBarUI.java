package com.medresource.ui.view;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Barra de desplazamiento delgada para el tema oscuro: sin flechas, con la pista del color del fondo y el
 * deslizador redondeado del color del borde de las tarjetas (más claro con el ratón encima).
 */
public final class DarkScrollBarUI extends BasicScrollBarUI {

    /** Grosor de la barra. */
    public static final int THICKNESS = 8;

    /**
     * Pone la barra oscura a las dos barras de un panel con desplazamiento y las hace delgadas.
     *
     * @param scroll panel con desplazamiento
     * @throws IllegalArgumentException si es nulo
     */
    public static void apply(JScrollPane scroll) {
        if (scroll == null) {
            throw new IllegalArgumentException("El panel con desplazamiento es obligatorio.");
        }
        for (JScrollBar bar : new JScrollBar[]{scroll.getVerticalScrollBar(), scroll.getHorizontalScrollBar()}) {
            bar.setUI(new DarkScrollBarUI());
            bar.setOpaque(false);
            bar.setPreferredSize(new Dimension(THICKNESS, THICKNESS));
        }
    }

    @Override
    protected void configureScrollBarColors() {
        thumbColor = HomeTheme.CARD_BORDER;
        trackColor = HomeTheme.BACKGROUND;
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return emptyButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return emptyButton();
    }

    @Override
    protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
        // La pista queda transparente: se ve el fondo del panel.
    }

    @Override
    protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
        if (bounds.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        surface.setColor(isThumbRollover() ? HomeTheme.FAINT : thumbColor);
        surface.fill(new RoundRectangle2D.Double(bounds.x + 1, bounds.y + 1, bounds.width - 2.0, bounds.height - 2.0,
                THICKNESS, THICKNESS));
        surface.dispose();
    }

    private static JButton emptyButton() {
        JButton button = new JButton();
        Dimension none = new Dimension(0, 0);
        button.setPreferredSize(none);
        button.setMinimumSize(none);
        button.setMaximumSize(none);
        return button;
    }
}
