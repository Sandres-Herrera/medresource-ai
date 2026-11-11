package com.medresource.ui.view;

import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * Ajusta el tamaño de las ventanas al espacio libre de la pantalla, para que se vean completas también en un
 * portátil de {@link #SMALL_SCREEN} píxeles (la pantalla más pequeña que se espera en la feria y en clase).
 *
 * <p>El espacio libre es la pantalla sin la barra de tareas; en Swing lo da
 * {@code GraphicsEnvironment.getMaximumWindowBounds()}.</p>
 */
public final class ScreenFit {

    /** Pantalla más pequeña en la que las ventanas deben caber completas: 1366 × 768. */
    public static final Dimension SMALL_SCREEN = new Dimension(1366, 768);

    private ScreenFit() {
    }

    /**
     * Tamaño que cabe en el espacio libre: el pedido, recortado a lo ancho o a lo alto si no cabe.
     *
     * @param wanted tamaño que quiere la ventana
     * @param usable espacio libre de la pantalla
     * @return el tamaño pedido, sin pasarse del espacio libre
     * @throws IllegalArgumentException si alguno es nulo o no tiene tamaño
     */
    public static Dimension fit(Dimension wanted, Rectangle usable) {
        if (wanted == null || usable == null || wanted.width <= 0 || wanted.height <= 0
                || usable.width <= 0 || usable.height <= 0) {
            throw new IllegalArgumentException("El tamaño pedido y el espacio libre deben tener ancho y alto.");
        }
        return new Dimension(Math.min(wanted.width, usable.width), Math.min(wanted.height, usable.height));
    }

    /**
     * Lugar de la ventana: con el tamaño que cabe ({@link #fit}) y centrada en el espacio libre.
     *
     * @param wanted tamaño que quiere la ventana
     * @param usable espacio libre de la pantalla
     * @return posición y tamaño de la ventana, siempre dentro del espacio libre
     * @throws IllegalArgumentException si alguno es nulo o no tiene tamaño
     */
    public static Rectangle centered(Dimension wanted, Rectangle usable) {
        Dimension size = fit(wanted, usable);
        return new Rectangle(usable.x + (usable.width - size.width) / 2, usable.y + (usable.height - size.height) / 2,
                size.width, size.height);
    }
}
