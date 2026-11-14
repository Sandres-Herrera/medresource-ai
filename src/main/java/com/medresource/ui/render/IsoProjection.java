package com.medresource.ui.render;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * Proyección de la vista 2.5D: convierte un punto del plano en el punto donde se dibuja en la vista isométrica.
 *
 * <p>La vista 2.5D no cambia las coordenadas del mundo: los pacientes y el personal siguen caminando sobre el plano
 * 2D (los puntos que da {@link SceneRenderer#centerOf}), y esta proyección solo dice dónde se ven. Cada casilla
 * del plano mide {@code cellWidth} × {@code cellHeight} píxeles del plano y, en la vista de siempre, es un rombo de
 * {@value #TILE_W} × {@value #TILE_H} píxeles: el eje de las columnas baja hacia la derecha y el de las filas
 * hacia la izquierda. Cada metro de altura sube {@value #Z_SCALE} píxeles.</p>
 *
 * <p>La cámara puede girar el hospital alrededor de su centro ({@code yaw}, en grados, una vuelta completa) y
 * levantar la mirada desde la diagonal de siempre ({@value #DEFAULT_ELEVATION}°) hasta verlo desde arriba
 * ({@value #MAX_ELEVATION}°). Con {@link #withView} se obtiene la misma proyección vista desde otro ángulo; en la vista
 * de siempre (giro 0 y elevación {@value #DEFAULT_ELEVATION}°) los puntos son exactamente los de antes. El dibujo
 * completo ({@link #size}) es siempre el rectángulo más pequeño que contiene el terreno con ese ángulo.</p>
 *
 * <p>Es una transformación afín, así que también se puede deshacer: {@link #toPlan} devuelve el punto del piso
 * que se ve en un punto de la vista.</p>
 *
 * <p>Es inmutable: dos proyecciones son iguales si tienen el mismo plano, las mismas casillas y el mismo ángulo.</p>
 */
public final class IsoProjection {

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
    /** Elevación de la vista de siempre, en grados: la de un rombo el doble de ancho que de alto. */
    public static final double DEFAULT_ELEVATION = 30.0;
    /** Elevación de la vista desde arriba, en grados. */
    public static final double MAX_ELEVATION = 90.0;

    private static final double SIN_DEFAULT = Math.sin(Math.toRadians(DEFAULT_ELEVATION));
    private static final double COS_DEFAULT = Math.cos(Math.toRadians(DEFAULT_ELEVATION));
    private static final double FACING_TOLERANCE = 1e-9;

    private final int cols;
    private final int rows;
    private final double cellWidth;
    private final double cellHeight;
    private final double yaw;
    private final double elevation;
    private final boolean defaultView;
    private final double cos;
    private final double sin;
    private final double stretch;
    private final double lift;
    private final double minDifference;
    private final double maxDifference;
    private final double minSum;
    private final double maxSum;

    /**
     * Crea la proyección con un ángulo de vista; el giro se lleva a una vuelta, entre 0 y 360 grados.
     *
     * @param cols       columnas del plano
     * @param rows       filas del plano
     * @param cellWidth  ancho de una casilla en el plano 2D, en píxeles
     * @param cellHeight alto de una casilla en el plano 2D, en píxeles
     * @param yaw        giro del hospital alrededor de su centro, en grados
     * @param elevation  ángulo de la mirada sobre el piso, en grados, de {@value #DEFAULT_ELEVATION} a
     *                   {@value #MAX_ELEVATION}
     * @throws IllegalArgumentException si el plano no tiene casillas, una casilla no tiene tamaño, el giro no es un
     *                                  número o la elevación está fuera de su rango
     */
    public IsoProjection(int cols, int rows, double cellWidth, double cellHeight, double yaw, double elevation) {
        if (cols < 1 || rows < 1) {
            throw new IllegalArgumentException("El plano debe tener al menos una columna y una fila.");
        }
        if (!(cellWidth > 0) || !(cellHeight > 0)) {
            throw new IllegalArgumentException("Las casillas deben tener ancho y alto.");
        }
        if (!Double.isFinite(yaw)) {
            throw new IllegalArgumentException("El giro de la vista debe ser un número.");
        }
        if (!(elevation >= DEFAULT_ELEVATION && elevation <= MAX_ELEVATION)) {
            throw new IllegalArgumentException("La elevación de la vista debe estar entre " + DEFAULT_ELEVATION
                    + " y " + MAX_ELEVATION + " grados.");
        }
        this.cols = cols;
        this.rows = rows;
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.yaw = normalize(yaw);
        this.elevation = elevation;
        this.defaultView = this.yaw == 0.0 && elevation == DEFAULT_ELEVATION;
        double radians = Math.toRadians(this.yaw);
        this.cos = this.yaw == 0.0 ? 1.0 : Math.cos(radians);
        this.sin = this.yaw == 0.0 ? 0.0 : Math.sin(radians);
        this.stretch = elevation == DEFAULT_ELEVATION ? 1.0 : Math.sin(Math.toRadians(elevation)) / SIN_DEFAULT;
        this.lift = elevation == DEFAULT_ELEVATION ? Z_SCALE
                : Math.max(0.0, Z_SCALE * Math.cos(Math.toRadians(elevation)) / COS_DEFAULT);
        double[] bounds = siteBounds();
        this.minDifference = bounds[0];
        this.maxDifference = bounds[1];
        this.minSum = bounds[2];
        this.maxSum = bounds[3];
    }

    /**
     * Crea la proyección de siempre: sin giro y con la elevación de siempre.
     *
     * @param cols       columnas del plano
     * @param rows       filas del plano
     * @param cellWidth  ancho de una casilla en el plano 2D, en píxeles
     * @param cellHeight alto de una casilla en el plano 2D, en píxeles
     * @throws IllegalArgumentException si el plano no tiene casillas o una casilla no tiene tamaño
     */
    public IsoProjection(int cols, int rows, double cellWidth, double cellHeight) {
        this(cols, rows, cellWidth, cellHeight, 0.0, DEFAULT_ELEVATION);
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

    /**
     * La misma proyección vista desde otro ángulo.
     *
     * @param yaw       giro, en grados (cualquier número: se lleva a una vuelta)
     * @param elevation elevación, en grados
     * @return la proyección con ese ángulo
     * @throws IllegalArgumentException si el giro no es un número o la elevación está fuera de su rango
     */
    public IsoProjection withView(double yaw, double elevation) {
        return new IsoProjection(cols, rows, cellWidth, cellHeight, yaw, elevation);
    }

    /** Indica si es la vista de siempre: sin giro y con la elevación de siempre. */
    public boolean isDefaultView() {
        return defaultView;
    }

    /** Columnas del plano. */
    public int cols() {
        return cols;
    }

    /** Filas del plano. */
    public int rows() {
        return rows;
    }

    /** Ancho de una casilla en el plano 2D, en píxeles. */
    public double cellWidth() {
        return cellWidth;
    }

    /** Alto de una casilla en el plano 2D, en píxeles. */
    public double cellHeight() {
        return cellHeight;
    }

    /** Giro del hospital alrededor de su centro, en grados, de 0 a 360 (sin incluir). */
    public double yaw() {
        return yaw;
    }

    /** Ángulo de la mirada sobre el piso, en grados. */
    public double elevation() {
        return elevation;
    }

    /** Tamaño del dibujo completo de la vista 2.5D, en píxeles. */
    public Dimension size() {
        if (defaultView) {
            double span = cols + rows + 4 * SITE_MARGIN;
            return new Dimension((int) Math.ceil(span * TILE_W / 2.0),
                    (int) Math.ceil(span * TILE_H / 2.0 + TOP_SPACE + BOTTOM_SPACE));
        }
        return new Dimension((int) Math.ceil((maxDifference - minDifference) * TILE_W / 2.0),
                (int) Math.ceil((maxSum - minSum) * TILE_H / 2.0 * stretch + TOP_SPACE + BOTTOM_SPACE));
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
        if (defaultView) {
            return (gx - gy + rows + 2 * SITE_MARGIN) * TILE_W / 2.0;
        }
        double x = gx - cols / 2.0;
        double y = gy - rows / 2.0;
        return (x * cos - y * sin - x * sin - y * cos - minDifference) * TILE_W / 2.0;
    }

    /** Coordenada y de la vista de un punto de la cuadrícula a cierta altura. */
    public double screenY(double gx, double gy, double z) {
        if (defaultView) {
            return (gx + gy + 2 * SITE_MARGIN) * TILE_H / 2.0 + TOP_SPACE - z * Z_SCALE;
        }
        double x = gx - cols / 2.0;
        double y = gy - rows / 2.0;
        return (x * cos - y * sin + x * sin + y * cos - minSum) * TILE_H / 2.0 * stretch + TOP_SPACE - z * lift;
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
        if (defaultView) {
            double difference = viewX / (TILE_W / 2.0) - rows - 2 * SITE_MARGIN;
            double sum = (viewY - TOP_SPACE) / (TILE_H / 2.0) - 2 * SITE_MARGIN;
            double gx = (sum + difference) / 2;
            double gy = (sum - difference) / 2;
            return new Point2D.Double(gx * cellWidth, gy * cellHeight);
        }
        double difference = viewX / (TILE_W / 2.0) + minDifference;
        double sum = (viewY - TOP_SPACE) / (TILE_H / 2.0 * stretch) + minSum;
        double qx = (sum + difference) / 2;
        double qy = (sum - difference) / 2;
        double gx = qx * cos + qy * sin + cols / 2.0;
        double gy = -qx * sin + qy * cos + rows / 2.0;
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

    /**
     * Profundidad de un punto de la cuadrícula: crece hacia quien mira, y sirve para dibujar de atrás hacia adelante.
     * En la vista de siempre es la columna más la fila.
     *
     * @param gx columna, con decimales
     * @param gy fila, con decimales
     * @return la profundidad, en casillas
     */
    public double depth(double gx, double gy) {
        if (defaultView) {
            return gx + gy;
        }
        double[] turned = turn(gx, gy);
        return turned[0] + turned[1] + cols / 2.0 + rows / 2.0;
    }

    /**
     * Indica si una cara vertical, con su frente hacia el lado dado de la cuadrícula, mira hacia quien mira; si no,
     * queda de espaldas y no se dibuja.
     *
     * @param normalX hacia dónde mira la cara a lo largo de las columnas (1, 0 o -1)
     * @param normalY hacia dónde mira la cara a lo largo de las filas (1, 0 o -1)
     * @return {@code true} si se ve de frente
     */
    public boolean faces(double normalX, double normalY) {
        double[] turned = rotate(normalX, normalY);
        return turned[0] + turned[1] > FACING_TOLERANCE;
    }

    /**
     * Cuánto mira una cara hacia la derecha de la pantalla, de 0 (hacia la izquierda, como la cara de las filas
     * mayores en la vista de siempre) a 1 (hacia la derecha, como la de las columnas mayores): sirve para darle la
     * sombra de su lado.
     *
     * @param normalX hacia dónde mira la cara a lo largo de las columnas
     * @param normalY hacia dónde mira la cara a lo largo de las filas
     * @return de 0 a 1
     */
    public double rightness(double normalX, double normalY) {
        double[] turned = rotate(normalX, normalY);
        double length = Math.hypot(turned[0], turned[1]);
        if (length == 0) {
            return 0.5;
        }
        double side = (turned[0] - turned[1]) / (length * Math.sqrt(2));
        double half = Math.sqrt(0.5);
        return Math.max(0.0, Math.min(1.0, (side + half) / (2 * half)));
    }

    /**
     * Cuánto se estira el piso hacia abajo en la pantalla respecto a la vista de siempre: 1 en la vista de siempre y
     * 2 vista desde arriba, donde cada casilla se ve cuadrada.
     */
    public double groundStretch() {
        return stretch;
    }

    /** Píxeles que sube un metro de altura con esta elevación: {@value #Z_SCALE} en la vista de siempre, 0 desde arriba. */
    public double heightScale() {
        return lift;
    }

    /** Alto en pantalla de un círculo del piso dividido por su ancho: 0,5 en la vista de siempre. */
    public double groundRatio() {
        return TILE_H / (double) TILE_W * groundStretch();
    }

    /**
     * Cuánto se mueve en la pantalla un paso de un metro por el piso en una dirección de la cuadrícula.
     *
     * @param dx paso a lo largo de las columnas
     * @param dy paso a lo largo de las filas
     * @return el desplazamiento en la pantalla, en píxeles
     */
    public Point2D step(double dx, double dy) {
        Point2D from = at(0, 0, 0);
        Point2D to = at(dx, dy, 0);
        return new Point2D.Double(to.getX() - from.getX(), to.getY() - from.getY());
    }

    /** Punto de la cuadrícula girado alrededor del centro del plano. */
    private double[] turn(double gx, double gy) {
        return rotate(gx - cols / 2.0, gy - rows / 2.0);
    }

    private double[] rotate(double x, double y) {
        return new double[] {x * cos - y * sin, x * sin + y * cos};
    }

    /**
     * Límites del terreno girado: lo menor y lo mayor de (columna − fila) y de (columna + fila) de sus cuatro esquinas,
     * en ese orden.
     */
    private double[] siteBounds() {
        double margin = SITE_MARGIN;
        double[][] corners = {{-margin, -margin}, {cols + margin, -margin}, {-margin, rows + margin},
            {cols + margin, rows + margin}};
        double[] bounds = {Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE};
        for (double[] corner : corners) {
            double[] turned = turn(corner[0], corner[1]);
            double difference = turned[0] - turned[1];
            double sum = turned[0] + turned[1];
            bounds[0] = Math.min(bounds[0], difference);
            bounds[1] = Math.max(bounds[1], difference);
            bounds[2] = Math.min(bounds[2], sum);
            bounds[3] = Math.max(bounds[3], sum);
        }
        return bounds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IsoProjection that)) {
            return false;
        }
        return cols == that.cols && rows == that.rows && Double.compare(cellWidth, that.cellWidth) == 0
                && Double.compare(cellHeight, that.cellHeight) == 0 && Double.compare(yaw, that.yaw) == 0
                && Double.compare(elevation, that.elevation) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cols, rows, cellWidth, cellHeight, yaw, elevation);
    }

    @Override
    public String toString() {
        return "IsoProjection[cols=" + cols + ", rows=" + rows + ", cellWidth=" + cellWidth + ", cellHeight="
                + cellHeight + ", yaw=" + yaw + ", elevation=" + elevation + "]";
    }

    private static double normalize(double degrees) {
        double turned = degrees % 360.0;
        if (turned < 0) {
            turned += 360.0;
        }
        return turned == 360.0 || turned == -0.0 ? 0.0 : turned;
    }
}
