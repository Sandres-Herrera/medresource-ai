package com.medresource.ui.render;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;

import java.awt.Dimension;
import java.awt.geom.Point2D;

/**
 * Proyección de la vista 2.5D: convierte un punto del plano en el punto donde se dibuja en la vista isométrica.
 *
 * <p>La vista 2.5D no cambia las coordenadas del mundo: los pacientes y el personal siguen caminando sobre el plano
 * 2D (los puntos que da {@link SceneRenderer#centerOf}), y esta proyección solo dice dónde se ven. Cada casilla
 * del plano mide {@code cellWidth} × {@code cellHeight} píxeles del plano y en la vista 2.5D es un rombo de
 * {@value #TILE_W} × {@value #TILE_H} píxeles: el eje de las columnas baja hacia la derecha y el de las filas
 * hacia la izquierda. Cada metro de altura sube {@value #Z_SCALE} píxeles.</p>
 *
 * <p>Es una transformación afín, así que también se puede deshacer: {@link #toPlan} devuelve el punto del piso
 * que se ve en un punto de la vista.</p>
 *
 * @param cols       columnas del plano
 * @param rows       filas del plano
 * @param cellWidth  ancho de una casilla en el plano 2D, en píxeles
 * @param cellHeight alto de una casilla en el plano 2D, en píxeles
 */
public record IsoProjection(int cols, int rows, double cellWidth, double cellHeight) {

    /** Ancho del rombo de una casilla, en píxeles de la vista. */
    public static final int TILE_W = 64;
    /** Alto del rombo de una casilla, en píxeles de la vista. */
    public static final int TILE_H = 32;
    /** Píxeles que sube un metro de altura. */
    public static final int Z_SCALE = 40;
    /** Casillas de terreno que se dejan alrededor del edificio: la acera, el césped, los árboles y la vía. */
    public static final double SITE_MARGIN = 2.2;
    /** Espacio libre arriba del dibujo, para los muros y las etiquetas de las salas, en píxeles. */
    public static final int TOP_SPACE = 90;
    /** Espacio libre abajo del dibujo, para el borde del terreno, en píxeles. */
    public static final int BOTTOM_SPACE = 30;

    /**
     * Valida la proyección.
     *
     * @throws IllegalArgumentException si el plano no tiene casillas o una casilla no tiene tamaño
     */
    public IsoProjection {
        if (cols < 1 || rows < 1) {
            throw new IllegalArgumentException("El plano debe tener al menos una columna y una fila.");
        }
        if (!(cellWidth > 0) || !(cellHeight > 0)) {
            throw new IllegalArgumentException("Las casillas deben tener ancho y alto.");
        }
    }

    /**
     * Crea la proyección de un plano, midiendo las casillas con el renderizador 2D que ubica a las personas.
     *
     * @param layout plano del hospital
     * @param flat   renderizador del plano 2D
     * @return la proyección
     * @throws IllegalArgumentException si falta alguno
     */
    public static IsoProjection of(HospitalLayout layout, SceneRenderer flat) {
        if (layout == null || flat == null) {
            throw new IllegalArgumentException("El plano y el renderizador 2D son obligatorios.");
        }
        Point2D origin = flat.centerOf(new Position(0, 0));
        double width = flat.centerOf(new Position(1, 0)).getX() - origin.getX();
        double height = flat.centerOf(new Position(0, 1)).getY() - origin.getY();
        return new IsoProjection(layout.getCols(), layout.getRows(), width, height);
    }

    /** Tamaño del dibujo completo de la vista 2.5D, en píxeles. */
    public Dimension size() {
        double span = cols + rows + 4 * SITE_MARGIN;
        return new Dimension((int) Math.ceil(span * TILE_W / 2.0),
                (int) Math.ceil(span * TILE_H / 2.0 + TOP_SPACE + BOTTOM_SPACE));
    }

    /**
     * Punto de la vista donde se ve un punto de la cuadrícula a cierta altura.
     *
     * @param gx columna, con decimales (0 es el borde izquierdo de la primera columna)
     * @param gy fila, con decimales
     * @param z  altura sobre el piso, en metros
     * @return punto de la vista
     */
    public Point2D at(double gx, double gy, double z) {
        return new Point2D.Double(screenX(gx, gy), screenY(gx, gy, z));
    }

    /** Coordenada x de la vista de un punto de la cuadrícula. */
    public double screenX(double gx, double gy) {
        return (gx - gy + rows + 2 * SITE_MARGIN) * TILE_W / 2.0;
    }

    /** Coordenada y de la vista de un punto de la cuadrícula a cierta altura. */
    public double screenY(double gx, double gy, double z) {
        return (gx + gy + 2 * SITE_MARGIN) * TILE_H / 2.0 + TOP_SPACE - z * Z_SCALE;
    }

    /**
     * Punto de la vista donde se ve un punto del plano 2D a cierta altura.
     *
     * @param planX coordenada x en el plano 2D, en píxeles
     * @param planY coordenada y en el plano 2D, en píxeles
     * @param z     altura sobre el piso, en metros
     * @return punto de la vista
     */
    public Point2D fromPlan(double planX, double planY, double z) {
        return at(planX / cellWidth, planY / cellHeight, z);
    }

    /**
     * Punto del piso del plano 2D que se ve en un punto de la vista: lo contrario de {@link #fromPlan} con altura 0.
     *
     * @param viewX coordenada x en la vista, en píxeles
     * @param viewY coordenada y en la vista, en píxeles
     * @return punto del plano 2D, en píxeles
     */
    public Point2D toPlan(double viewX, double viewY) {
        double difference = viewX / (TILE_W / 2.0) - rows - 2 * SITE_MARGIN;
        double sum = (viewY - TOP_SPACE) / (TILE_H / 2.0) - 2 * SITE_MARGIN;
        double gx = (sum + difference) / 2;
        double gy = (sum - difference) / 2;
        return new Point2D.Double(gx * cellWidth, gy * cellHeight);
    }

    /** Columna, con decimales, de un punto del plano 2D. */
    public double gridX(double planX) {
        return planX / cellWidth;
    }

    /** Fila, con decimales, de un punto del plano 2D. */
    public double gridY(double planY) {
        return planY / cellHeight;
    }
}
