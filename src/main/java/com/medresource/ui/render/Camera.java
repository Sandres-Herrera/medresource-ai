package com.medresource.ui.render;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;

/**
 * Cámara de la vista: decide qué parte del mundo se ve y con cuánto aumento.
 *
 * <p>El mundo son los píxeles en que el renderizador dibuja el plano; la pantalla son los píxeles del
 * panel. Un punto del mundo (x, y) se ve en la pantalla en ({@code x * zoom + offsetX},
 * {@code y * zoom + offsetY}). Es inmutable: cada cambio produce otra cámara.</p>
 *
 * @param zoom    aumento; 1 es tamaño real
 * @param offsetX desplazamiento horizontal en pantalla, en píxeles
 * @param offsetY desplazamiento vertical en pantalla, en píxeles
 */
public record Camera(double zoom, double offsetX, double offsetY) {

    /** Aumento mínimo permitido. */
    public static final double MIN_ZOOM = 0.05;
    /** Aumento máximo permitido. */
    public static final double MAX_ZOOM = 8.0;

    /**
     * Valida la cámara.
     *
     * @throws IllegalArgumentException si el aumento no está entre {@link #MIN_ZOOM} y {@link #MAX_ZOOM}
     *                                  o algún valor no es un número
     */
    public Camera {
        if (Double.isNaN(offsetX) || Double.isNaN(offsetY) || Double.isNaN(zoom)
                || zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            throw new IllegalArgumentException("El aumento debe estar entre " + MIN_ZOOM + " y " + MAX_ZOOM + ".");
        }
    }

    /**
     * Calcula la cámara que deja todo el mundo visible y centrado, con un margen alrededor.
     *
     * @param worldWidth  ancho del mundo en píxeles
     * @param worldHeight alto del mundo en píxeles
     * @param viewWidth   ancho de la pantalla en píxeles
     * @param viewHeight  alto de la pantalla en píxeles
     * @param margin      margen que se deja en cada lado, en píxeles
     * @return cámara que muestra el mundo completo, con el mayor aumento posible dentro de los límites
     * @throws IllegalArgumentException si el mundo no tiene tamaño, la pantalla es menor que el margen o el
     *                                  margen es negativo
     */
    public static Camera fit(double worldWidth, double worldHeight, double viewWidth, double viewHeight,
                             double margin) {
        if (worldWidth <= 0 || worldHeight <= 0) {
            throw new IllegalArgumentException("El mundo debe tener ancho y alto.");
        }
        if (margin < 0 || viewWidth <= 2 * margin || viewHeight <= 2 * margin) {
            throw new IllegalArgumentException("La pantalla debe ser mayor que los márgenes.");
        }
        double zoom = Math.min((viewWidth - 2 * margin) / worldWidth, (viewHeight - 2 * margin) / worldHeight);
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        return new Camera(zoom, (viewWidth - worldWidth * zoom) / 2, (viewHeight - worldHeight * zoom) / 2);
    }

    /**
     * Convierte un punto del mundo a la pantalla.
     *
     * @param worldX coordenada x del mundo
     * @param worldY coordenada y del mundo
     * @return punto en la pantalla
     */
    public Point2D toScreen(double worldX, double worldY) {
        return new Point2D.Double(worldX * zoom + offsetX, worldY * zoom + offsetY);
    }

    /**
     * Convierte un punto de la pantalla al mundo.
     *
     * @param screenX coordenada x de la pantalla
     * @param screenY coordenada y de la pantalla
     * @return punto en el mundo
     */
    public Point2D toWorld(double screenX, double screenY) {
        return new Point2D.Double((screenX - offsetX) / zoom, (screenY - offsetY) / zoom);
    }

    /**
     * Prepara un {@code Graphics2D} para dibujar en coordenadas del mundo: después de llamarlo, lo que se
     * dibuje se ve desplazado y aumentado según esta cámara.
     *
     * @param graphics superficie de dibujo
     * @throws IllegalArgumentException si la superficie es nula
     */
    public void applyTo(Graphics2D graphics) {
        if (graphics == null) {
            throw new IllegalArgumentException("La superficie de dibujo es obligatoria.");
        }
        graphics.translate(offsetX, offsetY);
        graphics.scale(zoom, zoom);
    }
}