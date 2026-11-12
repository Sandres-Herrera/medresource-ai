package com.medresource.ui.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * Botón pequeño y cuadrado que solo muestra un icono: los «−» y «+» de los campos de números, el dado de la
 * semilla y la equis de cerrar del formulario de una simulación nueva.
 *
 * <p>En reposo el icono es gris; con el ratón encima se ve un fondo suave y el icono blanco; con el foco del
 * teclado lleva un borde de acento; deshabilitado, el icono queda tenue. Su nombre es el que leen los lectores de
 * pantalla y el que sale como ayuda al pasar el ratón. Se pulsa como un botón ({@link Clickable}).</p>
 *
 * <p>Tiene dos variantes para las barras de herramientas: {@linkplain #setFilled(boolean) rellena} con el color
 * de acento (la acción principal, como reproducir o pausar) y {@linkplain #setOutlined(boolean) con borde}
 * sobre el fondo de tarjeta.</p>
 */
public final class IconButton extends Clickable {

    private static final long serialVersionUID = 1L;

    private static final int ARC = 8;

    private HomeIcon icon;
    private final int size;
    private boolean filled;
    private boolean outlined;

    /**
     * Crea el botón.
     *
     * @param icon icono
     * @param name nombre accesible y ayuda, por ejemplo «Aumentar camas»
     * @param size lado del botón en píxeles, al menos 12
     * @throws IllegalArgumentException si falta el icono o el nombre, o el tamaño es menor que 12
     */
    public IconButton(HomeIcon icon, String name, int size) {
        super(name);
        if (icon == null) {
            throw new IllegalArgumentException("El icono es obligatorio.");
        }
        if (size < 12) {
            throw new IllegalArgumentException("El botón debe medir al menos 12 píxeles.");
        }
        this.icon = icon;
        this.size = size;
        setToolTipText(name);
    }

    public HomeIcon getIcon() {
        return icon;
    }

    /**
     * Cambia el icono, por ejemplo de reproducir a pausar.
     *
     * @param icon icono nuevo
     * @throws IllegalArgumentException si es nulo
     */
    public void setIcon(HomeIcon icon) {
        if (icon == null) {
            throw new IllegalArgumentException("El icono es obligatorio.");
        }
        this.icon = icon;
        repaint();
    }

    public boolean isFilled() {
        return filled;
    }

    /**
     * Rellena el botón con el color de acento y dibuja el icono oscuro encima.
     *
     * @param filled {@code true} para rellenarlo
     */
    public void setFilled(boolean filled) {
        this.filled = filled;
        repaint();
    }

    public boolean isOutlined() {
        return outlined;
    }

    /**
     * Pone el botón sobre el fondo de tarjeta con un borde fino.
     *
     * @param outlined {@code true} para ponerle borde
     */
    public void setOutlined(boolean outlined) {
        this.outlined = outlined;
        repaint();
    }

    /**
     * Color con el que se dibuja el icono ahora: tenue si está deshabilitado, oscuro si el botón está relleno y
     * blanco con el ratón encima o con borde.
     */
    public Color iconColor() {
        if (!isEnabled()) {
            return HomeTheme.FAINT;
        }
        if (filled) {
            return HomeTheme.ACCENT_TEXT;
        }
        return isHover() || isFocusOwner() || outlined ? HomeTheme.TEXT : HomeTheme.MUTED;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(size, size);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            setHover(false);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, ARC, ARC);
        if (filled) {
            surface.setColor(!isEnabled() ? HomeTheme.CARD_BORDER : isPressed() ? HomeTheme.ACCENT.darker()
                    : isHover() ? HomeTheme.ACCENT_HOVER : HomeTheme.ACCENT);
            surface.fill(box);
        } else if (outlined) {
            surface.setColor(isPressed() ? HomeTheme.CARD_BORDER : isHover() ? HomeTheme.CARD_HOVER : HomeTheme.CARD);
            surface.fill(box);
            surface.setColor(HomeTheme.CARD_BORDER);
            surface.draw(box);
        } else if (isEnabled() && (isHover() || isPressed())) {
            surface.setColor(isPressed() ? HomeTheme.CARD_BORDER : HomeTheme.CARD_HOVER);
            surface.fill(new RoundRectangle2D.Double(1, 1, getWidth() - 2.0, getHeight() - 2.0, ARC, ARC));
        }
        if (isFocusOwner()) {
            surface.setColor(HomeTheme.ACCENT);
            surface.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, ARC, ARC));
        }
        double iconSize = Math.round(getHeight() * 0.55);
        icon.paint(surface, (getWidth() - iconSize) / 2, (getHeight() - iconSize) / 2, iconSize, iconColor());
        surface.dispose();
    }
}
