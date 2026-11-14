package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.Door;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.ZoneType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * El edificio de la vista 2.5D: el terreno, los pisos, los muros de vidrio, las puertas y la etiqueta de cada sala.
 *
 * <p>Los muros no se dibujan a mano: hay un muro en cada borde entre dos casillas de áreas distintas (o entre un área
 * y el exterior), salvo entre dos pasillos y donde hay una {@linkplain Door puerta}. Cada muro se arma con tramos de
 * un metro centrados en la línea del borde y un poste en cada esquina, así que las esquinas cierran limpias con
 * cualquier tamaño de hospital y ningún borde sobresale de su sala. Los muros de la fachada que da hacia quien mira
 * (abajo a la izquierda y abajo a la derecha) son bajos, para que se vea el interior.</p>
 *
 * <p>Cada sala tiene un borde de neón y una etiqueta flotante de su color ({@link IsoPalette#accent}); los pasillos no
 * llevan ni borde ni etiqueta. La entrada lleva una acera con una flecha verde.</p>
 */
public final class IsoStructure {

    /** Alto de los muros, en metros. */
    public static final double WALL_HEIGHT = 0.95;
    /** Alto de los muros de la fachada que da hacia quien mira, en metros. */
    public static final double LOW_WALL_HEIGHT = 0.3;
    /** Grosor de los muros de vidrio, en metros. */
    public static final double WALL_THICKNESS = 0.08;
    /** Cuánto se mete el borde de neón dentro de su sala, en metros. */
    public static final double NEON_INSET = 0.07;
    /** Altura de las etiquetas de las salas sobre el piso, en metros. */
    public static final double TAG_HEIGHT = 1.5;

    /** Borde entre dos casillas: horizontal (arriba de la casilla) o vertical (a su izquierda). */
    record Edge(boolean horizontal, int col, int row) {
    }

    private final HospitalLayout layout;
    private final IsoProjection projection;
    private final Map<Edge, Double> walls = new LinkedHashMap<>();
    private final Set<Edge> doors = new HashSet<>();

    /**
     * Arma el edificio de un plano.
     *
     * @param layout     plano del hospital
     * @param projection proyección de la vista
     * @throws IllegalArgumentException si falta alguno
     */
    public IsoStructure(HospitalLayout layout, IsoProjection projection) {
        if (layout == null || projection == null) {
            throw new IllegalArgumentException("El plano y la proyección son obligatorios.");
        }
        this.layout = layout;
        this.projection = projection;
        for (Door door : layout.getDoors()) {
            doors.add(edgeBetween(door.inside(), door.outside()));
        }
        Position entrance = layout.getEntrance();
        if (areaCovering(entrance.col() - 1, entrance.row()).isEmpty()) {
            doors.add(new Edge(false, entrance.col(), entrance.row()));
        } else if (areaCovering(entrance.col(), entrance.row() - 1).isEmpty()) {
            doors.add(new Edge(true, entrance.col(), entrance.row()));
        }
        findWalls();
    }

    /** Borde que comparten dos casillas vecinas. */
    static Edge edgeBetween(Position a, Position b) {
        if (a.col() == b.col()) {
            return new Edge(true, a.col(), Math.max(a.row(), b.row()));
        }
        return new Edge(false, Math.max(a.col(), b.col()), a.row());
    }

    private void findWalls() {
        for (int row = 0; row <= layout.getRows(); row++) {
            for (int col = 0; col < layout.getCols(); col++) {
                Edge edge = new Edge(true, col, row);
                if (separates(areaCovering(col, row - 1), areaCovering(col, row), edge)) {
                    boolean front = areaCovering(col, row).isEmpty();
                    walls.put(edge, front ? LOW_WALL_HEIGHT : WALL_HEIGHT);
                }
            }
        }
        for (int col = 0; col <= layout.getCols(); col++) {
            for (int row = 0; row < layout.getRows(); row++) {
                Edge edge = new Edge(false, col, row);
                if (separates(areaCovering(col - 1, row), areaCovering(col, row), edge)) {
                    boolean front = areaCovering(col, row).isEmpty();
                    walls.put(edge, front ? LOW_WALL_HEIGHT : WALL_HEIGHT);
                }
            }
        }
    }

    /** Indica si en el borde entre dos casillas vecinas, de las áreas dadas, hay un muro. */
    private boolean separates(Optional<Area> first, Optional<Area> second, Edge edge) {
        if (first.isEmpty() && second.isEmpty()) {
            return false;
        }
        if (first.isPresent() && second.isPresent() && (first.get().equals(second.get())
                || first.get().zone() == ZoneType.CORRIDOR && second.get().zone() == ZoneType.CORRIDOR)) {
            return false;
        }
        return !doors.contains(edge);
    }

    /**
     * Área que cubre una casilla, también en la fila de su título.
     *
     * @param col columna, que puede estar fuera del plano (incluso ser negativa)
     * @param row fila, que puede estar fuera del plano
     * @return el área, o vacío si la casilla está fuera de todas
     */
    public Optional<Area> areaCovering(int col, int row) {
        if (col < 0 || row < 0) {
            return Optional.empty();
        }
        Position position = new Position(col, row);
        return layout.getAreas().stream().filter(area -> area.covers(position)).findFirst();
    }

    /** Cantidad de tramos de muro de un metro. */
    public int wallCount() {
        return walls.size();
    }

    /**
     * Indica si hay un tramo de muro en un borde.
     *
     * @param horizontal {@code true} para el borde de arriba de la casilla; {@code false} para el de su izquierda
     * @param col        columna de la casilla
     * @param row        fila de la casilla
     * @return {@code true} si en ese borde hay muro
     */
    public boolean hasWall(boolean horizontal, int col, int row) {
        return walls.containsKey(new Edge(horizontal, col, row));
    }

    /**
     * Alto del muro de un borde.
     *
     * @param horizontal {@code true} para el borde de arriba de la casilla; {@code false} para el de su izquierda
     * @param col        columna de la casilla
     * @param row        fila de la casilla
     * @return el alto en metros, o 0 si en ese borde no hay muro
     */
    public double wallHeight(boolean horizontal, int col, int row) {
        return walls.getOrDefault(new Edge(horizontal, col, row), 0.0);
    }

    /**
     * Dibuja lo que va pegado al piso, antes que todo lo demás: el terreno, la acera de la entrada, el piso de cada
     * área con sus baldosas y su borde de neón, los umbrales de las puertas y las marcas de la fila del triage.
     *
     * @param graphics superficie de dibujo
     */
    public void paintGround(Graphics2D graphics) {
        paintSite(graphics);
        for (Area area : layout.getAreas()) {
            paintFloor(graphics, area);
        }
        for (Edge door : doors) {
            paintThreshold(graphics, door);
        }
        graphics.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] {3f, 3f}, 0f));
        graphics.setColor(IsoPalette.QUEUE_MARK);
        for (Position spot : layout.getTriageQueueSpots()) {
            graphics.draw(IsoBrush.quad(projection, spot.col() + 0.2, spot.row() + 0.2, spot.col() + 0.8,
                    spot.row() + 0.8, 0));
        }
        paintEntrance(graphics);
    }

    private void paintSite(Graphics2D graphics) {
        double margin = IsoProjection.SITE_MARGIN;
        double x0 = -margin;
        double y0 = -margin;
        double x1 = layout.getCols() + margin;
        double y1 = layout.getRows() + margin;
        double depth = -0.35;
        graphics.setColor(IsoPalette.SITE_LEFT);
        graphics.fill(IsoBrush.polygon(projection, new double[][] {{x0, y1, 0}, {x1, y1, 0}, {x1, y1, depth},
                {x0, y1, depth}}));
        graphics.setColor(IsoPalette.SITE_RIGHT);
        graphics.fill(IsoBrush.polygon(projection, new double[][] {{x1, y0, 0}, {x1, y1, 0}, {x1, y1, depth},
                {x1, y0, depth}}));
        Path2D top = IsoBrush.quad(projection, x0, y0, x1, y1, 0);
        graphics.setColor(IsoPalette.SITE);
        graphics.fill(top);
        graphics.setStroke(new BasicStroke(1f));
        graphics.setColor(IsoPalette.SITE_EDGE);
        graphics.draw(top);
    }

    private void paintFloor(Graphics2D graphics, Area area) {
        double x0 = area.col();
        double y0 = area.row();
        double x1 = area.right();
        double y1 = area.bottom();
        graphics.setColor(IsoPalette.floor(area.zone()));
        graphics.fill(IsoBrush.quad(projection, x0, y0, x1, y1, 0));
        Path2D.Double tiles = new Path2D.Double();
        for (int col = area.col() + 1; col < area.right(); col++) {
            line(tiles, col, y0, col, y1);
        }
        for (int row = area.row() + 1; row < area.bottom(); row++) {
            line(tiles, x0, row, x1, row);
        }
        graphics.setStroke(new BasicStroke(0.7f));
        graphics.setColor(IsoPalette.TILE_LINE);
        graphics.draw(tiles);
        if (area.zone() == ZoneType.CORRIDOR) {
            return;
        }
        Color accent = IsoPalette.accent(area.zone());
        Path2D neon = IsoBrush.quad(projection, x0 + NEON_INSET, y0 + NEON_INSET, x1 - NEON_INSET,
                y1 - NEON_INSET, 0);
        graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 14));
        graphics.fill(neon);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 215));
        graphics.draw(neon);
    }

    private void line(Path2D path, double x0, double y0, double x1, double y1) {
        Point2D from = projection.at(x0, y0, 0);
        Point2D to = projection.at(x1, y1, 0);
        path.moveTo(from.getX(), from.getY());
        path.lineTo(to.getX(), to.getY());
    }

    private void paintThreshold(Graphics2D graphics, Edge door) {
        double half = 0.09;
        double x = door.col();
        double y = door.row();
        Path2D strip = door.horizontal()
                ? IsoBrush.quad(projection, x + 0.1, y - half, x + 0.9, y + half, 0)
                : IsoBrush.quad(projection, x - half, y + 0.1, x + half, y + 0.9, 0);
        graphics.setColor(IsoPalette.THRESHOLD);
        graphics.fill(strip);
    }

    /** Acera frente a la entrada con una flecha verde que apunta hacia adentro. */
    private void paintEntrance(Graphics2D graphics) {
        Position entrance = layout.getEntrance();
        boolean fromLeft = areaCovering(entrance.col() - 1, entrance.row()).isEmpty();
        double gx = entrance.col();
        double gy = entrance.row();
        double reach = IsoProjection.SITE_MARGIN * 0.9;
        Path2D walk = fromLeft ? IsoBrush.quad(projection, gx - reach, gy + 0.05, gx, gy + 0.95, 0)
                : IsoBrush.quad(projection, gx + 0.05, gy - reach, gx + 0.95, gy, 0);
        graphics.setColor(IsoPalette.SIDEWALK);
        graphics.fill(walk);
        double[][] arrow = fromLeft
                ? new double[][] {{gx - 0.75, gy + 0.38, 0}, {gx - 0.3, gy + 0.38, 0}, {gx - 0.3, gy + 0.25, 0},
                        {gx - 0.08, gy + 0.5, 0}, {gx - 0.3, gy + 0.75, 0}, {gx - 0.3, gy + 0.62, 0},
                        {gx - 0.75, gy + 0.62, 0}}
                : new double[][] {{gx + 0.38, gy - 0.75, 0}, {gx + 0.38, gy - 0.3, 0}, {gx + 0.25, gy - 0.3, 0},
                        {gx + 0.5, gy - 0.08, 0}, {gx + 0.75, gy - 0.3, 0}, {gx + 0.62, gy - 0.3, 0},
                        {gx + 0.62, gy - 0.75, 0}};
        graphics.setColor(IsoPalette.ENTRANCE);
        graphics.fill(IsoBrush.polygon(projection, arrow));
        Point2D label = fromLeft ? projection.at(gx - 0.55, gy - 0.15, 0) : projection.at(gx - 0.15, gy - 0.55, 0);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        int width = graphics.getFontMetrics().stringWidth("ENTRADA");
        graphics.drawString("ENTRADA", (float) (label.getX() - width / 2.0), (float) label.getY());
    }

    /**
     * Tramos de muro y postes, para dibujarlos en su turno junto con los muebles y las personas.
     *
     * @return un elemento por tramo de muro y por poste
     */
    public List<IsoItem> items() {
        List<IsoItem> items = new ArrayList<>();
        double t = WALL_THICKNESS;
        Map<Position, Double> posts = new LinkedHashMap<>();
        walls.forEach((edge, height) -> {
            double x = edge.horizontal() ? edge.col() + t / 2 : edge.col() - t / 2;
            double y = edge.horizontal() ? edge.row() - t / 2 : edge.row() + t / 2;
            double w = edge.horizontal() ? 1 - t : t;
            double d = edge.horizontal() ? t : 1 - t;
            items.add(new IsoItem(x + w / 2 + y + d / 2, IsoItem.WALL,
                    graphics -> IsoBrush.glass(graphics, projection, x, y, 0, w, d, height)));
            List<Position> ends = edge.horizontal()
                    ? List.of(new Position(edge.col(), edge.row()), new Position(edge.col() + 1, edge.row()))
                    : List.of(new Position(edge.col(), edge.row()), new Position(edge.col(), edge.row() + 1));
            for (Position end : ends) {
                posts.merge(end, height, Math::max);
            }
        });
        posts.forEach((vertex, height) -> {
            double x = vertex.col() - t / 2;
            double y = vertex.row() - t / 2;
            items.add(new IsoItem(vertex.col() + vertex.row(), IsoItem.WALL,
                    graphics -> IsoBrush.glass(graphics, projection, x, y, 0, t, t, height)));
        });
        return items;
    }

    /**
     * Dibuja las etiquetas de las salas, al final, encima de todo: una píldora del color del área con su título,
     * flotando sobre su esquina del fondo. Los pasillos no llevan etiqueta.
     *
     * @param graphics superficie de dibujo
     */
    public void paintTags(Graphics2D graphics) {
        for (Area area : layout.getAreas()) {
            if (area.zone() == ZoneType.CORRIDOR) {
                continue;
            }
            double gx = area.col() + 0.4;
            double gy = area.row() + 0.4;
            IsoBrush.tag(graphics, projection.at(gx, gy, 0), projection.at(gx, gy, TAG_HEIGHT), area.title(),
                    IsoPalette.accent(area.zone()), IsoPalette.TAG_TEXT);
        }
    }
}
