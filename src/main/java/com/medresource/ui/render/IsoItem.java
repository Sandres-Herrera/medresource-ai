package com.medresource.ui.render;

import java.awt.Graphics2D;
import java.util.Comparator;
import java.util.function.Consumer;

/**
 * Algo que se dibuja en la vista 2.5D en su turno: un tramo de muro, un mueble o una persona.
 *
 * <p>La vista dibuja de atrás hacia adelante: primero lo que está más al fondo (menor columna + fila) y al final lo
 * que está más cerca. Así un mueble tapa a quien está detrás de él, y nadie se ve encima de una pared que tiene
 * delante. A igual profundidad se dibuja primero la capa menor (los muebles antes que las personas).</p>
 *
 * @param depth   profundidad: columna + fila del punto de apoyo, en casillas
 * @param layer   capa, para desempatar a igual profundidad
 * @param painter lo que dibuja, sobre la superficie que recibe
 */
public record IsoItem(double depth, int layer, Consumer<Graphics2D> painter) {

    /** Capa de los muros. */
    public static final int WALL = 0;
    /** Capa de los muebles. */
    public static final int FURNITURE = 1;
    /** Capa de las personas. */
    public static final int PERSON = 2;

    /** Orden en que se dibujan: de atrás hacia adelante y, a igual profundidad, por capa. */
    public static final Comparator<IsoItem> BACK_TO_FRONT = Comparator.comparingDouble(IsoItem::depth)
            .thenComparingInt(IsoItem::layer);

    /**
     * Valida el elemento.
     *
     * @throws IllegalArgumentException si la profundidad no es un número o falta lo que dibuja
     */
    public IsoItem {
        if (Double.isNaN(depth) || painter == null) {
            throw new IllegalArgumentException("La profundidad y lo que se dibuja son obligatorios.");
        }
    }

    /**
     * Dibuja el elemento.
     *
     * @param graphics superficie de dibujo
     */
    public void paint(Graphics2D graphics) {
        painter.accept(graphics);
    }
}
