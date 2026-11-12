package com.medresource.ui.view;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Tarjeta del tema oscuro: un panel con el fondo de tarjeta, las esquinas redondeadas y un borde fino, que se
 * puede {@linkplain #setHighlighted(boolean) resaltar} con un borde de acento más grueso.
 *
 * <p>La usan las secciones del formulario de una simulación nueva y las de la ventana de la simulación. Lo que
 * va adentro se acomoda con el administrador de diseño que se le pase; {@link #heading} arma el título de una
 * sección con su icono.</p>
 */
public class ThemedCard extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Radio de las esquinas. */
    public static final int ARC = 14;

    private boolean highlighted;

    /**
     * Crea la tarjeta.
     *
     * @param layout  cómo se acomoda lo que va adentro
     * @param padding margen interior en píxeles, el mismo en los cuatro lados
     * @throws IllegalArgumentException si el administrador es nulo o el margen es negativo
     */
    public ThemedCard(LayoutManager layout, int padding) {
        super(layout);
        if (layout == null || padding < 0) {
            throw new IllegalArgumentException("La tarjeta necesita un diseño y un margen no negativo.");
        }
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    /**
     * Resalta la tarjeta con un borde de acento, por ejemplo cuando muestra al paciente seleccionado.
     *
     * @param highlighted {@code true} para resaltarla
     */
    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
        repaint();
    }

    /** Color del borde ahora: acento si está resaltada, el borde de tarjeta si no. */
    public Color borderColor() {
        return highlighted ? HomeTheme.ACCENT : HomeTheme.CARD_BORDER;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double inset = highlighted ? 1.0 : 0.5;
        RoundRectangle2D box = new RoundRectangle2D.Double(inset, inset, getWidth() - 2 * inset,
                getHeight() - 2 * inset, ARC, ARC);
        surface.setColor(HomeTheme.CARD);
        surface.fill(box);
        surface.setColor(borderColor());
        surface.setStroke(new BasicStroke(highlighted ? 2f : 1f));
        surface.draw(box);
        surface.dispose();
    }

    /**
     * Arma el título de una sección: su icono y el texto en negrita.
     *
     * @param icon      icono
     * @param title     título
     * @param iconColor color del icono
     * @return la fila con el icono y el título
     * @throws IllegalArgumentException si falta algún dato
     */
    public static JComponent heading(HomeIcon icon, String title, Color iconColor) {
        if (icon == null || title == null || title.isBlank() || iconColor == null) {
            throw new IllegalArgumentException("El icono, el título y su color son obligatorios.");
        }
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.add(new IconLabel(icon, 18, iconColor));
        JLabel label = new JLabel(title);
        label.setFont(HomeTheme.font(Font.BOLD, 14f));
        label.setForeground(HomeTheme.TEXT);
        row.add(label);
        return row;
    }
}
