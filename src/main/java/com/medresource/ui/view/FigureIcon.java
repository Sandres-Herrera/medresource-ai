package com.medresource.ui.view;

import com.medresource.ui.render.Palette;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Figura pequeña de una persona, como se ve en el plano: el cuerpo del color que dice quién es, la cabeza y, si es
 * del personal, el gorro. La usa la leyenda «Quién es quién».
 */
public final class FigureIcon extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Ancho de la figura, en píxeles. */
    public static final int WIDTH = 12;
    /** Alto de la figura, en píxeles. */
    public static final int HEIGHT = 20;

    private final Color body;
    private final Color cap;

    /**
     * Crea la figura.
     *
     * @param body color del cuerpo
     * @param cap  color del gorro, o nulo si no lleva (los pacientes)
     * @throws IllegalArgumentException si falta el color del cuerpo
     */
    public FigureIcon(Color body, Color cap) {
        if (body == null) {
            throw new IllegalArgumentException("El color del cuerpo es obligatorio.");
        }
        this.body = body;
        this.cap = cap;
    }

    public Color getBody() {
        return body;
    }

    /** Color del gorro, o nulo si no lleva. */
    public Color getCap() {
        return cap;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double cx = getWidth() / 2.0;
        double top = (getHeight() - HEIGHT) / 2.0;
        surface.setStroke(new BasicStroke(1.1f));
        RoundRectangle2D trunk = new RoundRectangle2D.Double(cx - 5, top + 8.5, 10, 11, 5, 5);
        surface.setColor(body);
        surface.fill(trunk);
        surface.setColor(Palette.PATIENT_OUTLINE);
        surface.draw(trunk);
        Ellipse2D head = new Ellipse2D.Double(cx - 4.5, top + 0.5, 9, 9);
        surface.setColor(Palette.PATIENT_HEAD);
        surface.fill(head);
        if (cap != null) {
            surface.setColor(cap);
            surface.fill(new Arc2D.Double(cx - 4.5, top + 0.5, 9, 9, 0, 180, Arc2D.CHORD));
        }
        surface.setColor(Palette.PATIENT_OUTLINE);
        surface.draw(head);
        surface.dispose();
    }
}
