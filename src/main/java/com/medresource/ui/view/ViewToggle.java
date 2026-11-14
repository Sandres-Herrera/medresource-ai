package com.medresource.ui.view;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 * Selector de la vista del plano: dos pastillas, «2D» y «2.5D», dentro de una caja redondeada con el tema oscuro.
 *
 * <p>Empieza en 2D. Al pulsar una pastilla queda marcada y se avisa a quien escucha con {@link #onChange}; pulsar la
 * que ya está marcada no avisa.</p>
 */
public final class ViewToggle extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Texto de la pastilla de la vista 2D. */
    public static final String FLAT = "2D";
    /** Texto de la pastilla de la vista 2.5D. */
    public static final String ISOMETRIC = "2.5D";

    private final Pill flat = new Pill(FLAT);
    private final Pill isometric = new Pill(ISOMETRIC);
    private transient Consumer<Boolean> changeAction = value -> { };

    /** Crea el selector con la vista 2D marcada. */
    public ViewToggle() {
        super(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        flat.setToolTipText("Ver el plano desde arriba");
        isometric.setToolTipText("Ver el plano en 2.5D, con muros, muebles y personas con volumen");
        flat.addActionListener(event -> choose(false));
        isometric.addActionListener(event -> choose(true));
        add(flat);
        add(isometric);
        setIsometric(false);
    }

    private void choose(boolean value) {
        if (value != isIsometric()) {
            setIsometric(value);
            changeAction.accept(value);
        }
    }

    /**
     * Cambia lo que se hace al elegir una vista.
     *
     * @param action recibe {@code true} si se eligió la 2.5D y {@code false} si se eligió la 2D
     * @throws IllegalArgumentException si es nula
     */
    public void onChange(Consumer<Boolean> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción del selector de vista es obligatoria.");
        }
        this.changeAction = action;
    }

    /**
     * Marca una vista sin avisar a nadie.
     *
     * @param value {@code true} para marcar la 2.5D; {@code false} para la 2D
     */
    public void setIsometric(boolean value) {
        flat.setSelected(!value);
        isometric.setSelected(value);
    }

    /** Indica si está marcada la vista 2.5D. */
    public boolean isIsometric() {
        return isometric.isSelected();
    }

    /** Pastilla de la vista 2D. */
    public Pill flatPill() {
        return flat;
    }

    /** Pastilla de la vista 2.5D. */
    public Pill isometricPill() {
        return isometric;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, 10, 10);
        surface.setColor(HomeTheme.CARD);
        surface.fill(box);
        surface.setColor(HomeTheme.CARD_BORDER);
        surface.draw(box);
        surface.dispose();
    }
}
