package com.medresource.ui.render;

import com.medresource.model.Sex;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Pincel de la vista 2.5D: dibuja pisos, cajas, muros de vidrio y personas sobre una {@link IsoProjection}.
 *
 * <p>Todo se mide en metros sobre la cuadrícula del plano: {@code x} crece con las columnas, {@code y} con las filas y
 * {@code z} hacia arriba. Una caja muestra sus tres caras visibles: la de arriba (la más clara), la del frente
 * izquierdo (la cara de las filas mayores) y la del frente derecho (la de las columnas mayores, la más oscura).</p>
 *
 * <p>Una persona es un cilindro con cabeza: el cuerpo lleva el color que dice quién es, con un degradado de luz de
 * izquierda a derecha, y el personal lleva además un gorro más oscuro. Las mujeres llevan vestido (el cilindro se
 * abre hacia abajo). Todas tienen un contorno casi negro y una sombra en el piso.</p>
 *
 * <p>No guarda estado: cada método dibuja de una vez sobre la superficie que recibe.</p>
 */
public final class IsoBrush {

    /** Radio del cuerpo de un adulto, en metros. */
    public static final double BODY_RADIUS = 0.13;
    /** Alto del cuerpo de un adulto de pie, en metros (sin la cabeza). */
    public static final double BODY_HEIGHT = 0.44;
    /** Alto del cuerpo de un adulto sentado, en metros. */
    public static final double SEATED_BODY_HEIGHT = 0.3;
    /** Radio de la cabeza de un adulto, en metros. */
    public static final double HEAD_RADIUS = 0.115;
    /** Grosor del contorno de las personas, en píxeles. */
    public static final float OUTLINE_WIDTH = 1.1f;
    /** Ancho de la barra de salud, en píxeles. */
    public static final int BAR_WIDTH = 18;
    /** Alto de las barras, en píxeles. */
    public static final int BAR_HEIGHT = 3;

    private static final double SQRT2 = Math.sqrt(2);
    private static final double FACE_EAST = 0.76;
    private static final double FACE_TOP = 0.18;
    private static final double EDGE = 0.8;

    private IsoBrush() {
    }

    /**
     * Cómo se ve una persona.
     *
     * @param body   color del cuerpo
     * @param cap    color del gorro, o nulo si no lleva (los pacientes)
     * @param sex    sexo: las mujeres llevan vestido
     * @param scale  tamaño (1 para un adulto)
     * @param seated {@code true} si está sentada
     */
    public record Person(Color body, Color cap, Sex sex, double scale, boolean seated) {

        /**
         * Valida la persona.
         *
         * @throws IllegalArgumentException si falta el color o el sexo, o el tamaño no es positivo
         */
        public Person {
            if (body == null || sex == null) {
                throw new IllegalArgumentException("El color y el sexo de la persona son obligatorios.");
            }
            if (!(scale > 0)) {
                throw new IllegalArgumentException("El tamaño de la persona debe ser positivo.");
            }
        }
    }

    /**
     * Rombo del piso entre dos esquinas de la cuadrícula, a cierta altura.
     *
     * @param projection proyección de la vista
     * @param x0         columna de la esquina superior
     * @param y0         fila de la esquina superior
     * @param x1         columna de la esquina opuesta
     * @param y1         fila de la esquina opuesta
     * @param z          altura, en metros
     * @return la figura del rombo
     */
    public static Path2D quad(IsoProjection projection, double x0, double y0, double x1, double y1, double z) {
        return polygon(projection, new double[][] {{x0, y0, z}, {x1, y0, z}, {x1, y1, z}, {x0, y1, z}});
    }

    /**
     * Polígono por puntos de la cuadrícula.
     *
     * @param projection proyección de la vista
     * @param points     puntos {x, y, z} en orden
     * @return el polígono cerrado
     */
    public static Path2D polygon(IsoProjection projection, double[][] points) {
        Path2D.Double path = new Path2D.Double();
        for (int index = 0; index < points.length; index++) {
            Point2D at = projection.at(points[index][0], points[index][1], points[index][2]);
            if (index == 0) {
                path.moveTo(at.getX(), at.getY());
            } else {
                path.lineTo(at.getX(), at.getY());
            }
        }
        path.closePath();
        return path;
    }

    /**
     * Caja sólida de un color: la cara de arriba más clara, la izquierda de ese color y la derecha más oscura.
     *
     * @param graphics   superficie de dibujo
     * @param projection proyección de la vista
     * @param x          columna de la esquina superior
     * @param y          fila de la esquina superior
     * @param z          altura de la base
     * @param w          ancho a lo largo de las columnas
     * @param d          fondo a lo largo de las filas
     * @param h          alto
     * @param color      color de la caja
     */
    public static void box(Graphics2D graphics, IsoProjection projection, double x, double y, double z, double w,
                           double d, double h, Color color) {
        box(graphics, projection, x, y, z, w, d, h, IsoPalette.lighter(color, FACE_TOP), color,
                IsoPalette.darker(color, FACE_EAST));
    }

    /**
     * Caja sólida con un color por cara y un borde un poco más oscuro que cada cara.
     *
     * @param graphics   superficie de dibujo
     * @param projection proyección de la vista
     * @param x          columna de la esquina superior
     * @param y          fila de la esquina superior
     * @param z          altura de la base
     * @param w          ancho
     * @param d          fondo
     * @param h          alto
     * @param top        color de la cara de arriba
     * @param left       color de la cara izquierda (la de las filas mayores)
     * @param right      color de la cara derecha (la de las columnas mayores)
     */
    public static void box(Graphics2D graphics, IsoProjection projection, double x, double y, double z, double w,
                           double d, double h, Color top, Color left, Color right) {
        graphics.setStroke(new BasicStroke(0.5f));
        face(graphics, leftFace(projection, x, y, z, w, d, h), left, IsoPalette.darker(left, EDGE));
        face(graphics, rightFace(projection, x, y, z, w, d, h), right, IsoPalette.darker(right, EDGE));
        face(graphics, quad(projection, x, y, x + w, y + d, z + h), top, IsoPalette.darker(top, EDGE));
    }

    /**
     * Caja de vidrio: caras casi transparentes con bordes celestes y el borde de arriba brillante.
     *
     * @param graphics   superficie de dibujo
     * @param projection proyección de la vista
     * @param x          columna de la esquina superior
     * @param y          fila de la esquina superior
     * @param z          altura de la base
     * @param w          ancho
     * @param d          fondo
     * @param h          alto
     */
    public static void glass(Graphics2D graphics, IsoProjection projection, double x, double y, double z, double w,
                             double d, double h) {
        graphics.setStroke(new BasicStroke(0.8f));
        face(graphics, leftFace(projection, x, y, z, w, d, h), IsoPalette.GLASS, IsoPalette.GLASS_EDGE);
        face(graphics, rightFace(projection, x, y, z, w, d, h), IsoPalette.GLASS, IsoPalette.GLASS_EDGE);
        graphics.setStroke(new BasicStroke(1.1f));
        face(graphics, quad(projection, x, y, x + w, y + d, z + h), IsoPalette.GLASS, IsoPalette.GLASS_TOP);
    }

    /** Cara izquierda de una caja: la de las filas mayores. */
    static Path2D leftFace(IsoProjection projection, double x, double y, double z, double w, double d, double h) {
        return polygon(projection, new double[][] {{x, y + d, z}, {x + w, y + d, z}, {x + w, y + d, z + h},
                {x, y + d, z + h}});
    }

    /** Cara derecha de una caja: la de las columnas mayores. */
    static Path2D rightFace(IsoProjection projection, double x, double y, double z, double w, double d, double h) {
        return polygon(projection, new double[][] {{x + w, y, z}, {x + w, y + d, z}, {x + w, y + d, z + h},
                {x + w, y, z + h}});
    }

    private static void face(Graphics2D graphics, Shape shape, Color fill, Color edge) {
        graphics.setColor(fill);
        graphics.fill(shape);
        graphics.setColor(edge);
        graphics.draw(shape);
    }

    /** Medio ancho en pantalla de un círculo del piso de ese radio, en píxeles. */
    public static double screenRadius(double meters) {
        return meters * SQRT2 * IsoProjection.TILE_W / 2.0;
    }

    /**
     * Sombra ovalada en el piso bajo una persona o una camilla.
     *
     * @param graphics   superficie de dibujo
     * @param projection proyección de la vista
     * @param gx         columna del centro
     * @param gy         fila del centro
     * @param radius     radio en metros
     */
    public static void shadow(Graphics2D graphics, IsoProjection projection, double gx, double gy, double radius) {
        Point2D at = projection.at(gx, gy, 0);
        double rx = screenRadius(radius);
        double ry = rx * IsoProjection.TILE_H / IsoProjection.TILE_W;
        graphics.setColor(IsoPalette.SHADOW);
        graphics.fill(new Ellipse2D.Double(at.getX() - rx + 1, at.getY() - ry + 0.5, 2 * rx, 2 * ry));
    }

    /**
     * Anillo en el piso, por ejemplo el del paciente seleccionado.
     *
     * @param graphics   superficie de dibujo
     * @param projection proyección de la vista
     * @param gx         columna del centro
     * @param gy         fila del centro
     * @param radius     radio en metros
     * @param color      color del anillo
     */
    public static void ring(Graphics2D graphics, IsoProjection projection, double gx, double gy, double radius,
                            Color color) {
        Point2D at = projection.at(gx, gy, 0);
        double rx = screenRadius(radius);
        double ry = rx * IsoProjection.TILE_H / IsoProjection.TILE_W;
        graphics.setStroke(new BasicStroke(2.2f));
        graphics.setColor(color);
        graphics.draw(new Ellipse2D.Double(at.getX() - rx, at.getY() - ry, 2 * rx, 2 * ry));
    }

    /**
     * Dibuja una persona de pie o sentada con los pies en un punto de la cuadrícula.
     *
     * @param graphics   superficie de dibujo
     * @param projection proyección de la vista
     * @param gx         columna de los pies
     * @param gy         fila de los pies
     * @param z          altura de la base del cuerpo (0 de pie; la del asiento, sentada)
     * @param lift       cuánto sube o baja la figura en pantalla por el movimiento de reposo, en píxeles
     * @param person     cómo se ve
     * @return punto de la pantalla justo encima de la cabeza, donde van las barras
     */
    public static Point2D person(Graphics2D graphics, IsoProjection projection, double gx, double gy, double z,
                                 double lift, Person person) {
        double scale = person.scale();
        double radius = screenRadius(BODY_RADIUS * scale);
        double ratio = (double) IsoProjection.TILE_H / IsoProjection.TILE_W;
        double height = (person.seated() ? SEATED_BODY_HEIGHT : BODY_HEIGHT) * scale * IsoProjection.Z_SCALE;
        Point2D base = projection.at(gx, gy, z);
        double cx = base.getX();
        double bottom = base.getY() + lift;
        double top = bottom - height;
        boolean dress = person.sex() == Sex.FEMALE;
        double bottomRadius = dress ? radius * 1.25 : radius;
        double topRadius = dress ? radius * 0.78 : radius;
        Path2D.Double body = new Path2D.Double();
        body.moveTo(cx - topRadius, top);
        body.lineTo(cx - bottomRadius, bottom);
        body.append(new Arc2D.Double(cx - bottomRadius, bottom - bottomRadius * ratio, 2 * bottomRadius,
                2 * bottomRadius * ratio, 180, 180, Arc2D.OPEN), true);
        body.lineTo(cx + topRadius, top);
        body.append(new Arc2D.Double(cx - topRadius, top - topRadius * ratio, 2 * topRadius, 2 * topRadius * ratio,
                0, 180, Arc2D.OPEN), true);
        body.closePath();
        Color color = person.body();
        graphics.setPaint(new GradientPaint((float) (cx - bottomRadius), 0f, IsoPalette.lighter(color, 0.28),
                (float) (cx + bottomRadius), 0f, IsoPalette.darker(color, 0.72)));
        graphics.fill(body);
        graphics.setStroke(new BasicStroke(OUTLINE_WIDTH));
        graphics.setColor(IsoPalette.OUTLINE);
        graphics.draw(body);
        graphics.setColor(IsoPalette.lighter(color, 0.3));
        graphics.fill(new Ellipse2D.Double(cx - topRadius + 0.6, top - topRadius * ratio + 0.3,
                2 * topRadius - 1.2, 2 * topRadius * ratio - 0.6));
        double head = screenRadius(HEAD_RADIUS * scale);
        double headY = top - head * 0.92;
        head(graphics, cx, headY, head, person.cap());
        return new Point2D.Double(cx, headY - head);
    }

    /**
     * Cabeza: una esfera color piel con contorno y, si lleva, el gorro en la mitad de arriba.
     *
     * @param graphics superficie de dibujo
     * @param cx       centro horizontal en pantalla
     * @param cy       centro vertical en pantalla
     * @param radius   radio en píxeles
     * @param cap      color del gorro, o nulo
     */
    public static void head(Graphics2D graphics, double cx, double cy, double radius, Color cap) {
        Ellipse2D ball = new Ellipse2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius);
        graphics.setPaint(new GradientPaint((float) (cx - radius), 0f, IsoPalette.lighter(Palette.PATIENT_HEAD, 0.25),
                (float) (cx + radius), 0f, IsoPalette.darker(Palette.PATIENT_HEAD, 0.8)));
        graphics.fill(ball);
        if (cap != null) {
            Path2D.Double hat = new Path2D.Double();
            hat.append(new Arc2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius, 0, 180, Arc2D.OPEN), false);
            hat.quadTo(cx, cy + radius * 0.25, cx + radius, cy);
            hat.closePath();
            graphics.setColor(cap);
            graphics.fill(hat);
        }
        graphics.setStroke(new BasicStroke(OUTLINE_WIDTH));
        graphics.setColor(IsoPalette.OUTLINE);
        graphics.draw(ball);
    }

    /**
     * Barra horizontal centrada: el fondo oscuro y, encima, la parte llena desde la izquierda.
     *
     * @param graphics superficie de dibujo
     * @param cx       centro horizontal en pantalla
     * @param top      borde de arriba en pantalla
     * @param width    ancho en píxeles
     * @param share    parte llena, de 0 a 1
     * @param fill     color de la parte llena
     */
    public static void bar(Graphics2D graphics, double cx, double top, double width, double share, Color fill) {
        double left = cx - width / 2;
        graphics.setColor(Palette.BAR_TRACK);
        graphics.fill(new RoundRectangle2D.Double(left - 0.6, top - 0.6, width + 1.2, BAR_HEIGHT + 1.2, 3, 3));
        double filled = width * Math.max(0.0, Math.min(1.0, share));
        if (filled > 0) {
            graphics.setColor(fill);
            graphics.fill(new RoundRectangle2D.Double(left, top, filled, BAR_HEIGHT, 2, 2));
        }
    }

    /**
     * Etiqueta flotante: una píldora de color con su texto, unida al piso por una línea punteada.
     *
     * @param graphics superficie de dibujo
     * @param floor    punto del piso al que apunta
     * @param at       punto de la pantalla donde va el borde de abajo de la píldora
     * @param text     texto
     * @param fill     color de la píldora y de la línea
     * @param textColor color del texto
     */
    public static void tag(Graphics2D graphics, Point2D floor, Point2D at, String text, Color fill, Color textColor) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        FontMetrics metrics = graphics.getFontMetrics();
        double width = metrics.stringWidth(text) + 14.0;
        double height = 16.0;
        graphics.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 150));
        graphics.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] {2f, 3f}, 0f));
        graphics.draw(new Line2D.Double(floor, at));
        graphics.setColor(fill);
        graphics.fill(new Ellipse2D.Double(floor.getX() - 2.2, floor.getY() - 2.2, 4.4, 4.4));
        graphics.fill(new RoundRectangle2D.Double(at.getX() - width / 2, at.getY() - height, width, height, height,
                height));
        graphics.setColor(textColor);
        graphics.drawString(text, (float) (at.getX() - metrics.stringWidth(text) / 2.0),
                (float) (at.getY() - height / 2 + metrics.getAscent() / 2.0 - 1));
    }
}
