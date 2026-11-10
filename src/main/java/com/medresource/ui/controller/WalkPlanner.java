package com.medresource.ui.controller;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.Route;
import com.medresource.services.simulation.PathFinder;
import com.medresource.ui.render.SceneRenderer;
import com.medresource.ui.render.Walker.Leg;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma los tramos que camina una persona para ir de un punto del plano a otro por los pasillos.
 *
 * <p>Busca la ruta más corta entre la casilla donde está y la de destino con {@link PathFinder}, y la
 * convierte en tramos para un {@link com.medresource.ui.render.Walker}: primero hasta el centro de su casilla,
 * después de centro en centro por cada casilla de la ruta, y al final hasta el punto exacto de destino (una
 * silla, el lado de una cama). Si la persona ya iba camino a la segunda casilla de la ruta, sigue de frente
 * sin volver atrás; dentro de una misma casilla va en línea recta. Si no hubiera ruta, iría en línea recta
 * hasta el destino. Cada tramo mide lo que recorre en metros: un paso entre centros vecinos mide
 * {@value HospitalLayout#METERS_PER_CELL} m.</p>
 */
public final class WalkPlanner {

    /** Lo que mide un tramo tan corto que no hace falta caminarlo, en metros. */
    private static final double NOTHING = 1e-9;

    private final PathFinder finder;
    private final SceneRenderer renderer;
    private final double cellWidth;
    private final double cellHeight;

    /**
     * Prepara los recorridos sobre un plano.
     *
     * @param layout   plano del hospital
     * @param renderer renderizador que da el centro de cada casilla en el mundo
     * @throws IllegalArgumentException si falta el plano o el renderizador
     */
    public WalkPlanner(HospitalLayout layout, SceneRenderer renderer) {
        if (layout == null || renderer == null) {
            throw new IllegalArgumentException("El plano y el renderizador son obligatorios.");
        }
        this.finder = new PathFinder(layout);
        this.renderer = renderer;
        Point2D origin = renderer.centerOf(new Position(0, 0));
        this.cellWidth = renderer.centerOf(new Position(1, 0)).getX() - origin.getX();
        this.cellHeight = renderer.centerOf(new Position(0, 1)).getY() - origin.getY();
    }

    /**
     * Arma los tramos de un recorrido.
     *
     * @param fromCell  casilla donde está la persona (la del último punto al que llegó)
     * @param fromPoint punto del mundo donde está ahora
     * @param toCell    casilla de destino
     * @param toPoint   punto del mundo de destino, dentro de esa casilla
     * @return tramos en orden; vacío si ya está en el destino
     * @throws IllegalArgumentException si falta algún dato o si alguna casilla no se puede pisar
     */
    public List<Leg> legs(Position fromCell, Point2D fromPoint, Position toCell, Point2D toPoint) {
        if (fromPoint == null || toPoint == null) {
            throw new IllegalArgumentException("Los puntos de salida y de llegada son obligatorios.");
        }
        List<Position> cells = finder.find(fromCell, toCell).map(Route::cells).orElse(List.of(toCell));
        List<Leg> legs = new ArrayList<>();
        Point2D at = fromPoint;
        if (cells.size() > 1) {
            int first = between(fromPoint, renderer.centerOf(cells.get(0)), renderer.centerOf(cells.get(1))) ? 1 : 0;
            for (int index = first; index < cells.size(); index++) {
                at = addLeg(legs, at, cells.get(index), renderer.centerOf(cells.get(index)));
            }
        }
        addLeg(legs, at, toCell, toPoint);
        return legs;
    }

    /** Agrega un tramo hasta un punto, salvo que no haga falta caminarlo; devuelve dónde queda la persona. */
    private Point2D addLeg(List<Leg> legs, Point2D from, Position cell, Point2D to) {
        double meters = metersBetween(from, to);
        if (meters > NOTHING) {
            legs.add(new Leg(cell, to, meters));
        }
        return to;
    }

    /** Distancia en metros entre dos puntos del mundo, midiendo cada eje con el tamaño de una casilla. */
    private double metersBetween(Point2D from, Point2D to) {
        return Math.hypot((to.getX() - from.getX()) / cellWidth, (to.getY() - from.getY()) / cellHeight)
                * HospitalLayout.METERS_PER_CELL;
    }

    /** Indica si un punto está sobre el segmento recto entre dos centros de casillas vecinas. */
    private static boolean between(Point2D point, Point2D a, Point2D b) {
        boolean sameX = point.getX() == a.getX() && a.getX() == b.getX();
        boolean sameY = point.getY() == a.getY() && a.getY() == b.getY();
        return sameX && within(point.getY(), a.getY(), b.getY()) || sameY && within(point.getX(), a.getX(), b.getX());
    }

    private static boolean within(double value, double a, double b) {
        return value >= Math.min(a, b) && value <= Math.max(a, b);
    }
}
