package com.medresource.ui.render;

import com.medresource.model.Sex;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Pincel de la vista 2.5D: dibuja pisos, cajas, paneles de vidrio y personas sobre una {@link IsoProjection}.
 *
 * <p>Todo se mide en metros sobre la cuadrícula del plano: {@code x} crece con las columnas, {@code y} con las filas y
 * {@code z} hacia arriba. Una caja muestra la cara de arriba (la más clara) y las caras de los lados que miran hacia
 * quien mira: en la vista de siempre, la del frente izquierdo (la cara de las filas mayores) y la del frente derecho
 * (la de las columnas mayores, la más oscura). Si la cámara gira, cada cara toma el color del lado de la pantalla al
 * que mira, así que la luz parece venir siempre del mismo lado de la pantalla.</p>
 *
 * <p>Una persona tiene piernas, torso con brazos y cabeza con pelo: la ropa lleva el color que dice quién es y el
 * personal lleva además un gorro más oscuro. Las mujeres llevan vestido. Todas tienen un contorno casi negro, que
 * las separa del piso en el tema claro y en el oscuro, y una sombra en el piso.</p>
 *
 * <p>No guarda estado: cada método dibuja de una vez sobre la superficie que recibe.</p>
 */
public final class IsoBrush {

    /** Radio que ocupa en el piso un adulto, en metros (para su sombra y para no chocar con los muebles). */
    public static final double BODY_RADIUS = 0.13;
    /** Alto de las piernas de un adulto, en metros. */
    public static final double LEG_HEIGHT = 0.34;
    /** Alto del torso de un adulto, en metros. */
    public static final double TORSO_HEIGHT = 0.3;
    /** Alto del cuerpo de un adulto de pie, en metros (sin la cabeza). */
    public static final double BODY_HEIGHT = LEG_HEIGHT + TORSO_HEIGHT;
    /** Alto del cuerpo de un adulto sentado, desde el asiento, en metros. */
    public static final double SEATED_BODY_HEIGHT = 0.35;
    /** Radio de la cabeza de un adulto, en metros. */
    public static final double HEAD_RADIUS = 0.1;
    /** Medio ancho de los hombros de un adulto, en metros. */
    public static final double TORSO_HALF_WIDTH = 0.12;
    /** Grosor del contorno de las personas, en píxeles. */
    public static final float OUTLINE_WIDTH = 1.1f;
    /** Ancho de la barra de salud, en píxeles. */
    public static final int BAR_WIDTH = 18;
    /** Alto de las barras, en píxeles. */
    public static final int BAR_HEIGHT = 3;

    private static final double SQRT2 = Math.sqrt(2);
    private static final double PANTS = 0.72;
    private static final double SLEEVE = 0.86;
    private static final float PERSON_STROKE = 0.9f;
    private static final Color SHOES = new Color(0x1F, 0x24, 0x30);
    private static final double FACE_EAST = 0.76;
    private static final double FACE_TOP = 0.18;
    private static final double EDGE = 0.8;
    /** Caras de los lados de una caja: hacia dónde mira cada una, en columnas y filas. */
    private static final int[][] SIDES = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    /** Alto mínimo en pantalla, en píxeles, para que valga la pena dibujar las caras de los lados. */
    private static final double MIN_SIDE_PIXELS = 0.05;

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
     * @param hair   color del pelo, o nulo para el castaño oscuro de siempre (con gorro no se ve)
     */
    public record Person(Color body, Color cap, Sex sex, double scale, boolean seated, Color hair) {

        /** Castaño oscuro: el pelo de quien no dice otro. */
        public static final Color DEFAULT_HAIR = new Color(0x3B, 0x2A, 0x1E);

        /**
         * Crea una persona con el pelo castaño oscuro.
         *
         * @param body   color del cuerpo
         * @param cap    color del gorro, o nulo
         * @param sex    sexo
         * @param scale  tamaño
         * @param seated {@code true} si está sentada
         */
        public Person(Color body, Color cap, Sex sex, double scale, boolean seated) {
            this(body, cap, sex, scale, seated, DEFAULT_HAIR);
        }

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
            if (hair == null) {
                hair = DEFAULT_HAIR;
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
     * Caja sólida con un color por cara y un borde un poco más oscuro que cada cara. Se dibujan las caras de los lados
     * que miran hacia quien mira, cada una con el color de la izquierda, el de la derecha o una mezcla de los dos según
     * hacia qué lado de la pantalla mira, y al final la de arriba.
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
     * @param left       color de las caras que miran a la izquierda (en la vista de siempre, la de las filas mayores)
     * @param right      color de las caras que miran a la derecha (en la vista de siempre, la de las columnas mayores)
     */
    public static void box(Graphics2D graphics, IsoProjection projection, double x, double y, double z, double w,
                           double d, double h, Color top, Color left, Color right) {
        graphics.setStroke(new BasicStroke(0.5f));
        if (h * projection.heightScale() >= MIN_SIDE_PIXELS) {
            for (int[] side : SIDES) {
                if (projection.faces(side[0], side[1])) {
                    Color color = mix(left, right, projection.rightness(side[0], side[1]));
                    face(graphics, sideFace(projection, side, x, y, z, w, d, h), color,
                            IsoPalette.darker(color, EDGE));
                }
            }
        }
        face(graphics, quad(projection, x, y, x + w, y + d, z + h), top, IsoPalette.darker(top, EDGE));
    }

    /**
     * Panel de vidrio: las caras con el relleno transparente que se le da y los bordes del marco; el borde de
     * arriba, más grueso. Como la caja, solo muestra los lados que miran hacia quien mira.
     *
     * @param graphics   superficie de dibujo
     * @param projection proyección de la vista
     * @param x          columna de la esquina superior
     * @param y          fila de la esquina superior
     * @param z          altura de la base
     * @param w          ancho
     * @param d          fondo
     * @param h          alto
     * @param fill       relleno del vidrio, con transparencia
     * @param frame      color del marco
     */
    public static void pane(Graphics2D graphics, IsoProjection projection, double x, double y, double z, double w,
                            double d, double h, Color fill, Color frame) {
        graphics.setStroke(new BasicStroke(0.8f));
        if (h * projection.heightScale() >= MIN_SIDE_PIXELS) {
            for (int[] side : SIDES) {
                if (projection.faces(side[0], side[1])) {
                    face(graphics, sideFace(projection, side, x, y, z, w, d, h), fill, frame);
                }
            }
        }
        graphics.setStroke(new BasicStroke(1.2f));
        face(graphics, quad(projection, x, y, x + w, y + d, z + h), fill, frame);
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

    /** Cara de un lado de una caja, la que mira hacia el lado dado. */
    private static Path2D sideFace(IsoProjection projection, int[] side, double x, double y, double z, double w,
                                   double d, double h) {
        if (side[1] > 0) {
            return leftFace(projection, x, y, z, w, d, h);
        }
        if (side[0] > 0) {
            return rightFace(projection, x, y, z, w, d, h);
        }
        if (side[1] < 0) {
            return polygon(projection, new double[][] {{x, y, z}, {x + w, y, z}, {x + w, y, z + h}, {x, y, z + h}});
        }
        return polygon(projection, new double[][] {{x, y, z}, {x, y + d, z}, {x, y + d, z + h}, {x, y, z + h}});
    }

    /**
     * Mezcla dos colores.
     *
     * @param from color con parte 0
     * @param to   color con parte 1
     * @param share parte del segundo, de 0 a 1
     * @return la mezcla; con 0 o 1, el color de ese extremo tal cual
     */
    static Color mix(Color from, Color to, double share) {
        if (share <= 0) {
            return from;
        }
        if (share >= 1) {
            return to;
        }
        return new Color(channel(from.getRed(), to.getRed(), share), channel(from.getGreen(), to.getGreen(), share),
                channel(from.getBlue(), to.getBlue(), share), channel(from.getAlpha(), to.getAlpha(), share));
    }

    private static int channel(int from, int to, double share) {
        return (int) Math.round(from + (to - from) * share);
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
        double ry = rx * projection.groundRatio();
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
        double ry = rx * projection.groundRatio();
        graphics.setStroke(new BasicStroke(2.2f));
        graphics.setColor(color);
        graphics.draw(new Ellipse2D.Double(at.getX() - rx, at.getY() - ry, 2 * rx, 2 * ry));
    }

    /**
     * Dibuja una persona de pie o sentada con los pies en un punto de la cuadrícula: piernas (o vestido), torso con
     * brazos y manos, cuello y cabeza con pelo o con gorro.
     *
     * <p>La ropa lleva el color que dice quién es; el pantalón es el mismo color más oscuro. De pie, las piernas
     * miden {@value #LEG_HEIGHT} m y el torso {@value #TORSO_HEIGHT} m; sentada, los muslos van hacia el frente
     * sobre el asiento y las piernas bajan hasta el piso.</p>
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
        double unit = IsoProjection.Z_SCALE * scale;
        Color clothes = person.body();
        Color pants = IsoPalette.darker(clothes, PANTS);
        Color sleeve = IsoPalette.darker(clothes, SLEEVE);
        Point2D base = projection.at(gx, gy, z);
        double cx = base.getX();
        double feet = base.getY() + lift;
        double halfWidth = TORSO_HALF_WIDTH * unit;
        graphics.setStroke(new BasicStroke(PERSON_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double hip;
        if (person.seated()) {
            double thigh = 0.2 * scale;
            box(graphics, projection, gx - 0.09 * scale, gy - 0.02, z, 0.18 * scale, thigh, 0.07 * scale, pants);
            box(graphics, projection, gx - 0.09 * scale, gy + thigh - 0.06, 0, 0.18 * scale, 0.05, z, pants);
            hip = feet - 0.05 * unit;
        } else {
            double legs = LEG_HEIGHT * unit;
            hip = feet - legs;
            if (person.sex() == Sex.FEMALE) {
                outlined(graphics, trapezoid(cx, hip, halfWidth * 0.9, feet - 0.13 * unit, halfWidth * 1.25),
                        IsoPalette.lighter(clothes, 0.08));
                outlined(graphics, new Rectangle2D.Double(cx - halfWidth * 0.75, feet - 0.13 * unit,
                        halfWidth * 0.55, 0.13 * unit), pants);
                outlined(graphics, new Rectangle2D.Double(cx + halfWidth * 0.2, feet - 0.13 * unit,
                        halfWidth * 0.55, 0.13 * unit), pants);
            } else {
                outlined(graphics, trapezoid(cx, hip, halfWidth * 0.9, feet, halfWidth * 0.82), pants);
                graphics.setColor(IsoPalette.darker(pants, 0.7));
                graphics.draw(new Line2D.Double(cx + 0.6, hip + 2, cx + 0.6, feet));
            }
            graphics.setColor(SHOES);
            graphics.fill(new RoundRectangle2D.Double(cx - halfWidth * 0.95, feet - 1.6, halfWidth * 0.85, 2.6, 1.5,
                    1.5));
            graphics.fill(new RoundRectangle2D.Double(cx + halfWidth * 0.12, feet - 1.6, halfWidth * 0.85, 2.6, 1.5,
                    1.5));
        }
        double shoulders = hip - TORSO_HEIGHT * unit;
        outlined(graphics, trapezoid(cx, shoulders, halfWidth, hip + 1, halfWidth * 0.86), clothes);
        graphics.setColor(IsoPalette.darker(clothes, 0.82));
        graphics.draw(new Line2D.Double(cx, shoulders + 3, cx, hip));
        for (int side = -1; side <= 1; side += 2) {
            double sx = cx + side * (halfWidth + 0.6);
            Path2D.Double arm = new Path2D.Double();
            arm.moveTo(sx, shoulders + 2);
            arm.quadTo(sx + side * 1.4, (shoulders + hip) / 2, sx + side * 0.5, hip - 1);
            graphics.setStroke(new BasicStroke((float) (0.07 * unit), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(IsoPalette.OUTLINE);
            graphics.draw(arm);
            graphics.setStroke(new BasicStroke((float) (0.07 * unit - 1.6), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            graphics.setColor(sleeve);
            graphics.draw(arm);
            graphics.setColor(Palette.PATIENT_HEAD);
            graphics.fill(new Ellipse2D.Double(sx + side * 0.5 - 1.7, hip - 2.6, 3.4, 3.4));
        }
        graphics.setStroke(new BasicStroke(PERSON_STROKE));
        double head = HEAD_RADIUS * unit;
        double headY = shoulders - head * 0.85;
        graphics.setColor(Palette.PATIENT_HEAD);
        graphics.fill(new Rectangle2D.Double(cx - head * 0.35, shoulders - head * 0.4, head * 0.7, head * 0.6));
        head(graphics, cx, headY, head, person.cap(), person.hair());
        return new Point2D.Double(cx, headY - head);
    }

    /**
     * Altura del pecho de una persona, donde el médico lleva el distintivo de su especialidad.
     *
     * @param seated {@code true} si está sentada (la altura se mide desde el asiento)
     * @return la altura en metros
     */
    public static double chestHeight(boolean seated) {
        return (seated ? 0.05 : LEG_HEIGHT) + TORSO_HEIGHT * 0.62;
    }

    private static Path2D trapezoid(double cx, double top, double topHalf, double bottom, double bottomHalf) {
        Path2D.Double shape = new Path2D.Double();
        shape.moveTo(cx - topHalf, top);
        shape.lineTo(cx + topHalf, top);
        shape.lineTo(cx + bottomHalf, bottom);
        shape.lineTo(cx - bottomHalf, bottom);
        shape.closePath();
        return shape;
    }

    private static void outlined(Graphics2D graphics, Shape shape, Color fill) {
        graphics.setColor(fill);
        graphics.fill(shape);
        graphics.setStroke(new BasicStroke(PERSON_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(IsoPalette.OUTLINE);
        graphics.draw(shape);
    }

    /**
     * Cabeza sin pelo: una esfera color piel con contorno y, si lleva, el gorro en la mitad de arriba.
     *
     * @param graphics superficie de dibujo
     * @param cx       centro horizontal en pantalla
     * @param cy       centro vertical en pantalla
     * @param radius   radio en píxeles
     * @param cap      color del gorro, o nulo
     */
    public static void head(Graphics2D graphics, double cx, double cy, double radius, Color cap) {
        head(graphics, cx, cy, radius, cap, null);
    }

    /**
     * Cabeza: una esfera color piel con contorno y, encima, el gorro si lleva o si no el pelo.
     *
     * @param graphics superficie de dibujo
     * @param cx       centro horizontal en pantalla
     * @param cy       centro vertical en pantalla
     * @param radius   radio en píxeles
     * @param cap      color del gorro, o nulo
     * @param hair     color del pelo, o nulo para no dibujarlo
     */
    public static void head(Graphics2D graphics, double cx, double cy, double radius, Color cap, Color hair) {
        Ellipse2D ball = new Ellipse2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius);
        graphics.setPaint(new GradientPaint((float) (cx - radius), 0f, IsoPalette.lighter(Palette.PATIENT_HEAD, 0.25),
                (float) (cx + radius), 0f, IsoPalette.darker(Palette.PATIENT_HEAD, 0.8)));
        graphics.fill(ball);
        Color top = cap != null ? cap : hair;
        if (top != null) {
            Path2D.Double hat = new Path2D.Double();
            hat.append(new Arc2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius, 0, 180, Arc2D.OPEN), false);
            hat.quadTo(cx, cy + radius * (cap != null ? 0.25 : -0.35), cx + radius, cy);
            hat.closePath();
            graphics.setColor(top);
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
}
