package com.medresource.ui.view;

import com.medresource.ui.render.IsoTheme;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 * Selector del tema de la vista 2.5D: dos pastillas, «Claro» y «Oscuro», dentro de una caja redondeada con el tema
 * oscuro de la ventana.
 *
 * <p>Empieza en el tema claro. Al pulsar una pastilla queda marcada y se avisa a quien escucha con {@link #onChange};
 * pulsar la que ya está marcada no avisa.</p>
 */
public final class ThemeToggle extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Pill light = new Pill(IsoTheme.LIGHT.getDisplayName());
    private final Pill dark = new Pill(IsoTheme.DARK.getDisplayName());
    private transient Consumer<IsoTheme> changeAction = value -> { };

    /** Crea el selector con el tema claro marcado. */
    public ThemeToggle() {
        super(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        light.setToolTipText("Hospital de día: baldosas blancas, muros azules y césped");
        dark.setToolTipText("Hospital de noche: muros azul marino y luz de las lámparas");
        light.addActionListener(event -> choose(IsoTheme.LIGHT));
        dark.addActionListener(event -> choose(IsoTheme.DARK));
        add(light);
        add(dark);
        setTheme(IsoTheme.LIGHT);
    }

    private void choose(IsoTheme theme) {
        if (theme != getTheme()) {
            setTheme(theme);
            changeAction.accept(theme);
        }
    }

    /**
     * Cambia lo que se hace al elegir un tema.
     *
     * @param action recibe el tema elegido
     * @throws IllegalArgumentException si es nula
     */
    public void onChange(Consumer<IsoTheme> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción del selector de tema es obligatoria.");
        }
        this.changeAction = action;
    }

    /**
     * Marca un tema sin avisar a nadie.
     *
     * @param theme tema
     * @throws IllegalArgumentException si es nulo
     */
    public void setTheme(IsoTheme theme) {
        if (theme == null) {
            throw new IllegalArgumentException("El tema es obligatorio.");
        }
        light.setSelected(theme == IsoTheme.LIGHT);
        dark.setSelected(theme == IsoTheme.DARK);
    }

    /** Tema marcado. */
    public IsoTheme getTheme() {
        return dark.isSelected() ? IsoTheme.DARK : IsoTheme.LIGHT;
    }

    /** Pastilla del tema claro. */
    public Pill lightPill() {
        return light;
    }

    /** Pastilla del tema oscuro. */
    public Pill darkPill() {
        return dark;
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
