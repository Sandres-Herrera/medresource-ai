package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.Door;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.ZoneType;
import com.medresource.ui.render.IsoTheme.Part;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El edificio de la vista 2.5D: el terreno con césped, árboles y la vía de urgencias; los pisos de baldosa; los muros
 * cortados a media altura, con ventanales hacia los pasillos; las puertas corredizas de vidrio y el letrero de cada
 * sala.
 *
 * <p>Los muros no se dibujan a mano: hay un muro en cada borde entre dos casillas de áreas distintas (o entre un área
 * y el exterior), salvo entre dos pasillos y donde hay una {@linkplain Door puerta}. Cada muro se arma con tramos de
 * un metro centrados en la línea del borde y un poste en cada esquina, así que las esquinas cierran limpias con
 * cualquier tamaño de hospital y ningún borde sobresale de su sala. Los muros de la fachada que da hacia quien mira
 * (abajo a la izquierda y abajo a la derecha) son bajos, para que se vea el interior. Los que dan a un pasillo son
 * ventanales: antepecho, vidrio con parteluz y cabezal. Los demás llevan enchape de baldosa en la mitad de abajo de
 * su cara visible.</p>
 *
 * <p>Cada sala lleva su letrero pintado en la cara de adentro de su muro del fondo, sobre la fila del título: el
 * número si es una de varias salas iguales (por ejemplo «2» en el quirófano 2) o si no un pictograma, y el nombre.
 * La sala de espera tiene además la pantalla de turnos en la esquina de su muro del fondo.
 * Los colores salen del {@link IsoTheme}: cambiar de tema no cambia el edificio.</p>
 */
public final class IsoStructure {

    /** Alto de los muros, en metros. */
    public static final double WALL_HEIGHT = 1.0;
    /** Alto de los muros de la fachada que da hacia quien mira, en metros. */
    public static final double LOW_WALL_HEIGHT = 0.3;
    /** Grosor de los muros, en metros. */
    public static final double WALL_THICKNESS = 0.2;
    /** Alto del zócalo, en metros. */
    public static final double BASEBOARD_HEIGHT = 0.1;
    /** Alto del antepecho de los ventanales, en metros. */
    public static final double SILL_HEIGHT = 0.32;
    /** Alto del enchape de baldosa de las paredes, en metros. */
    public static final double WAINSCOT_HEIGHT = 0.5;
    /** Ancho de la acera alrededor del edificio, en metros. */
    public static final double SIDEWALK = 0.5;
    /** Ancho del borde azul del pasillo junto a los muros, en metros. */
    public static final double CORRIDOR_BAND = 0.18;
    /** Tramos de muro que ocupa, como mucho, el letrero de una sala. */
    public static final int SIGN_SEGMENTS = 4;

    /** Píxeles de letra por metro del muro donde se pinta un letrero. */
    private static final double TEXT_UNIT = 40.0;
    private static final Pattern NUMBER = Pattern.compile("^(.*?)\\s+(\\d+)$");
    private static final Color AMBULANCE = new Color(0xF4, 0xF6, 0xFA);
    private static final Color AMBULANCE_RED = new Color(0xE5, 0x48, 0x4D);
    private static final Color SIREN_BLUE = new Color(0x3B, 0x82, 0xF6);
    private static final Color WINDSHIELD = new Color(0x2B, 0x3A, 0x55);
    private static final Color TV_FRAME = new Color(0x11, 0x15, 0x1C);
    private static final Color TV_SCREEN = new Color(0x1D, 0x4E, 0xD8);

    /** Borde entre dos casillas: horizontal (arriba de la casilla) o vertical (a su izquierda). */
    record Edge(boolean horizontal, int col, int row) {
    }

    /** Letrero de una sala: el número o el pictograma, el nombre y los tramos de muro donde va. */
    record Sign(Area area, String number, String name, int col, int row, int segments) {
    }

    private final HospitalLayout layout;
    private final IsoProjection projection;
    private final IsoTheme theme;
    private final Map<Edge, Double> walls = new LinkedHashMap<>();
    private final Set<Edge> doors = new HashSet<>();
    private final List<Sign> signs = new ArrayList<>();
    private final Set<Edge> signEdges = new HashSet<>();
    private final boolean entranceFromLeft;

    /**
     * Arma el edificio de un plano con el tema claro.
     *
     * @param layout     plano del hospital
     * @param projection proyección de la vista
     * @throws IllegalArgumentException si falta alguno
     */
    public IsoStructure(HospitalLayout layout, IsoProjection projection) {
        this(layout, projection, IsoTheme.LIGHT);
    }

    /**
     * Arma el edificio de un plano.
     *
     * @param layout     plano del hospital
     * @param projection proyección de la vista
     * @param theme      tema de colores
     * @throws IllegalArgumentException si falta alguno
     */
    public IsoStructure(HospitalLayout layout, IsoProjection projection, IsoTheme theme) {
        if (layout == null || projection == null || theme == null) {
            throw new IllegalArgumentException("El plano, la proyección y el tema son obligatorios.");
        }
        this.layout = layout;
        this.projection = projection;
        this.theme = theme;
        for (Door door : layout.getDoors()) {
            doors.add(edgeBetween(door.inside(), door.outside()));
        }
        Position entrance = layout.getEntrance();
        entranceFromLeft = areaCovering(entrance.col() - 1, entrance.row()).isEmpty();
        if (entranceFromLeft) {
            doors.add(new Edge(false, entrance.col(), entrance.row()));
        } else if (areaCovering(entrance.col(), entrance.row() - 1).isEmpty()) {
            doors.add(new Edge(true, entrance.col(), entrance.row()));
        }
        findWalls();
        findSigns();
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

    /** Busca, en el muro del fondo de cada sala, el tramo más largo sin puertas desde su esquina izquierda. */
    private void findSigns() {
        for (Area area : layout.getAreas()) {
            if (area.zone() == ZoneType.CORRIDOR) {
                continue;
            }
            int start = area.col();
            while (start < area.right() && !hasWall(true, start, area.row())) {
                start++;
            }
            int length = 0;
            while (start + length < area.right() && length < SIGN_SEGMENTS
                    && hasWall(true, start + length, area.row())) {
                length++;
            }
            if (length == 0) {
                continue;
            }
            String title = area.title().replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
            Matcher matcher = NUMBER.matcher(title);
            String number = matcher.matches() ? matcher.group(2) : null;
            String name = (matcher.matches() ? matcher.group(1) : title).toUpperCase(Locale.ROOT);
            signs.add(new Sign(area, number, name, start, area.row(), length));
            for (int index = 0; index < length; index++) {
                signEdges.add(new Edge(true, start + index, area.row()));
            }
        }
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

    /** Tema de colores del edificio. */
    public IsoTheme getTheme() {
        return theme;
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
     * Indica si el tramo de muro de un borde es un ventanal: da a un pasillo, es alto y no lleva letrero.
     *
     * @param horizontal {@code true} para el borde de arriba de la casilla; {@code false} para el de su izquierda
     * @param col        columna de la casilla
     * @param row        fila de la casilla
     * @return {@code true} si es un ventanal
     */
    public boolean isWindow(boolean horizontal, int col, int row) {
        Edge edge = new Edge(horizontal, col, row);
        if (walls.getOrDefault(edge, 0.0) < WALL_HEIGHT || signEdges.contains(edge)) {
            return false;
        }
        Optional<Area> first = horizontal ? areaCovering(col, row - 1) : areaCovering(col - 1, row);
        Optional<Area> second = areaCovering(col, row);
        return first.isPresent() && second.isPresent() && (first.get().zone() == ZoneType.CORRIDOR
                || second.get().zone() == ZoneType.CORRIDOR);
    }

    /**
     * Texto del letrero de un área: el número (si es una de varias salas iguales) o vacío, y el nombre.
     *
     * @param area área
     * @return «2 QUIRÓFANO», «UCI»… o vacío si el área no tiene letrero
     */
    public Optional<String> signOf(Area area) {
        return signs.stream().filter(sign -> sign.area().equals(area)).findFirst()
                .map(sign -> sign.number() == null ? sign.name() : sign.number() + " " + sign.name());
    }

    // ================================================================ lo que va pegado al piso

    /**
     * Dibuja lo que va pegado al piso, antes que todo lo demás: el terreno con el césped, la acera y la vía de
     * urgencias; el piso de baldosa de cada área, el borde del pasillo y la sombra al pie de los muros; los tapetes de
     * las puertas, las marcas de la fila del triage y, en el tema oscuro, la luz de las lámparas.
     *
     * @param graphics superficie de dibujo
     */
    public void paintGround(Graphics2D graphics) {
        paintSite(graphics);
        for (Area area : layout.getAreas()) {
            paintFloor(graphics, area);
        }
        paintCorridorBands(graphics);
        paintOcclusion(graphics);
        for (Edge door : doors) {
            paintMat(graphics, door);
        }
        graphics.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                new float[] {3f, 3f}, 0f));
        graphics.setColor(theme.color(Part.DOOR_FRAME));
        for (Position spot : layout.getTriageQueueSpots()) {
            graphics.draw(IsoBrush.quad(projection, spot.col() + 0.2, spot.row() + 0.2, spot.col() + 0.8,
                    spot.row() + 0.8, 0));
        }
        paintEntrance(graphics);
        if (theme.isDark()) {
            paintCeilingLights(graphics);
        }
    }

    private void paintSite(Graphics2D graphics) {
        double margin = IsoProjection.SITE_MARGIN;
        double x0 = -margin;
        double y0 = -margin;
        double x1 = layout.getCols() + margin;
        double y1 = layout.getRows() + margin;
        double depth = -0.4;
        graphics.setColor(theme.color(Part.SOIL_LEFT));
        graphics.fill(IsoBrush.polygon(projection, new double[][] {{x0, y1, 0}, {x1, y1, 0}, {x1, y1, depth},
                {x0, y1, depth}}));
        graphics.setColor(theme.color(Part.SOIL_RIGHT));
        graphics.fill(IsoBrush.polygon(projection, new double[][] {{x1, y0, 0}, {x1, y1, 0}, {x1, y1, depth},
                {x1, y0, depth}}));
        graphics.setColor(theme.color(Part.GRASS));
        graphics.fill(IsoBrush.quad(projection, x0, y0, x1, y1, 0));
        Random random = new Random(3);
        graphics.setColor(theme.color(Part.GRASS_SHADE));
        int tufts = (int) ((x1 - x0) * (y1 - y0) * 0.6);
        for (int index = 0; index < tufts; index++) {
            double gx = x0 + 0.2 + random.nextDouble() * (x1 - x0 - 0.4);
            double gy = y0 + 0.2 + random.nextDouble() * (y1 - y0 - 0.4);
            if (gx > -SIDEWALK && gx < layout.getCols() + SIDEWALK && gy > -SIDEWALK
                    && gy < layout.getRows() + SIDEWALK) {
                continue;
            }
            Point2D at = projection.at(gx, gy, 0);
            graphics.fill(new Ellipse2D.Double(at.getX() - 2.5, at.getY() - 1.2, 5, 2.4));
        }
        graphics.setColor(theme.color(Part.SIDEWALK));
        graphics.fill(IsoBrush.quad(projection, -SIDEWALK, -SIDEWALK, layout.getCols() + SIDEWALK,
                layout.getRows() + SIDEWALK, 0));
        Position entrance = layout.getEntrance();
        double gx = entrance.col();
        double gy = entrance.row();
        graphics.setColor(theme.color(Part.ROAD));
        graphics.fill(entranceFromLeft ? IsoBrush.quad(projection, x0, gy - 0.9, -SIDEWALK, gy + 1.9, 0)
                : IsoBrush.quad(projection, gx - 0.9, y0, gx + 1.9, -SIDEWALK, 0));
        graphics.setColor(theme.color(Part.ROAD_MARK));
        for (int stripe = 0; stripe < 5; stripe++) {
            double offset = -0.75 + stripe * 0.55;
            graphics.fill(entranceFromLeft
                    ? IsoBrush.quad(projection, -SIDEWALK - 0.75, gy + offset, -SIDEWALK - 0.1, gy + offset + 0.28, 0)
                    : IsoBrush.quad(projection, gx + offset, -SIDEWALK - 0.75, gx + offset + 0.28, -SIDEWALK - 0.1,
                    0));
        }
        floorText(graphics, entranceFromLeft ? x0 + 0.15 : gx + 1.7, entranceFromLeft ? gy + 1.7 : y0 + 0.15,
                "URGENCIAS", 9, theme.color(Part.ROAD_MARK), entranceFromLeft);
    }

    private void paintFloor(Graphics2D graphics, Area area) {
        Color base = theme.floor(area.zone());
        Color alternate = IsoPalette.darker(base, 0.965);
        for (int row = area.row() * 2; row < area.bottom() * 2; row++) {
            for (int col = area.col() * 2; col < area.right() * 2; col++) {
                graphics.setColor((row + col) % 2 == 0 ? base : alternate);
                graphics.fill(IsoBrush.quad(projection, col / 2.0, row / 2.0, (col + 1) / 2.0, (row + 1) / 2.0, 0));
            }
        }
        Path2D.Double grout = new Path2D.Double();
        for (int col = area.col() * 2 + 1; col < area.right() * 2; col++) {
            line(grout, col / 2.0, area.row(), col / 2.0, area.bottom());
        }
        for (int row = area.row() * 2 + 1; row < area.bottom() * 2; row++) {
            line(grout, area.col(), row / 2.0, area.right(), row / 2.0);
        }
        graphics.setStroke(new BasicStroke(0.6f));
        graphics.setColor(theme.color(Part.GROUT));
        graphics.draw(grout);
    }

    /** Borde de color en el piso del pasillo, al pie de cada muro que da a un pasillo. */
    private void paintCorridorBands(Graphics2D graphics) {
        graphics.setColor(theme.color(Part.CORRIDOR_BAND));
        double t = WALL_THICKNESS / 2;
        walls.forEach((edge, height) -> {
            int col = edge.col();
            int row = edge.row();
            if (edge.horizontal()) {
                if (isCorridor(col, row - 1)) {
                    graphics.fill(IsoBrush.quad(projection, col, row - t - CORRIDOR_BAND, col + 1, row - t, 0));
                }
                if (isCorridor(col, row)) {
                    graphics.fill(IsoBrush.quad(projection, col, row + t, col + 1, row + t + CORRIDOR_BAND, 0));
                }
            } else {
                if (isCorridor(col - 1, row)) {
                    graphics.fill(IsoBrush.quad(projection, col - t - CORRIDOR_BAND, row, col - t, row + 1, 0));
                }
                if (isCorridor(col, row)) {
                    graphics.fill(IsoBrush.quad(projection, col + t, row, col + t + CORRIDOR_BAND, row + 1, 0));
                }
            }
        });
    }

    private boolean isCorridor(int col, int row) {
        return areaCovering(col, row).map(area -> area.zone() == ZoneType.CORRIDOR).orElse(false);
    }

    /** Sombra suave al pie de los muros, del lado donde cae la luz que viene del fondo. */
    private void paintOcclusion(Graphics2D graphics) {
        graphics.setColor(theme.color(Part.OCCLUSION));
        double t = WALL_THICKNESS / 2;
        walls.forEach((edge, height) -> {
            if (height < WALL_HEIGHT) {
                return;
            }
            if (edge.horizontal()) {
                graphics.fill(IsoBrush.quad(projection, edge.col(), edge.row() + t, edge.col() + 1,
                        edge.row() + t + 0.28, 0));
            } else {
                graphics.fill(IsoBrush.quad(projection, edge.col() + t, edge.row(), edge.col() + t + 0.28,
                        edge.row() + 1, 0));
            }
        });
    }

    private void line(Path2D path, double x0, double y0, double x1, double y1) {
        Point2D from = projection.at(x0, y0, 0);
        Point2D to = projection.at(x1, y1, 0);
        path.moveTo(from.getX(), from.getY());
        path.lineTo(to.getX(), to.getY());
    }

    private void paintMat(Graphics2D graphics, Edge door) {
        double half = 0.2;
        double x = door.col();
        double y = door.row();
        graphics.setColor(theme.color(Part.DOOR_MAT));
        graphics.fill(door.horizontal()
                ? IsoBrush.quad(projection, x + 0.12, y - half, x + 0.88, y + half, 0)
                : IsoBrush.quad(projection, x - half, y + 0.12, x + half, y + 0.88, 0));
    }

    /** Flecha verde en la acera de la entrada, que apunta hacia adentro. */
    private void paintEntrance(Graphics2D graphics) {
        Position entrance = layout.getEntrance();
        double gx = entrance.col();
        double gy = entrance.row();
        double[][] arrow = entranceFromLeft
                ? new double[][] {{gx - 0.48, gy + 0.42, 0}, {gx - 0.26, gy + 0.42, 0}, {gx - 0.26, gy + 0.3, 0},
                        {gx - 0.06, gy + 0.5, 0}, {gx - 0.26, gy + 0.7, 0}, {gx - 0.26, gy + 0.58, 0},
                        {gx - 0.48, gy + 0.58, 0}}
                : new double[][] {{gx + 0.42, gy - 0.48, 0}, {gx + 0.42, gy - 0.26, 0}, {gx + 0.3, gy - 0.26, 0},
                        {gx + 0.5, gy - 0.06, 0}, {gx + 0.7, gy - 0.26, 0}, {gx + 0.58, gy - 0.26, 0},
                        {gx + 0.58, gy - 0.48, 0}};
        graphics.setColor(IsoPalette.ENTRANCE);
        graphics.fill(IsoBrush.polygon(projection, arrow));
    }

    /** Luz de las lámparas del techo en el piso de cada área, una cada cuatro metros más o menos. */
    private void paintCeilingLights(Graphics2D graphics) {
        Color light = theme.color(Part.LAMP_LIGHT);
        Color[] stops = {new Color(light.getRed(), light.getGreen(), light.getBlue(), 46),
            new Color(light.getRed(), light.getGreen(), light.getBlue(), 0)};
        for (Area area : layout.getAreas()) {
            int across = Math.max(1, Math.round(area.cols() / 4f));
            int down = Math.max(1, Math.round(area.rows() / 4f));
            for (int i = 0; i < across; i++) {
                for (int j = 0; j < down; j++) {
                    double gx = area.col() + area.cols() * (i + 0.5) / across;
                    double gy = area.row() + area.rows() * (j + 0.5) / down;
                    glow(graphics, projection.at(gx, gy, 0), 70, stops);
                }
            }
        }
    }

    private static void glow(Graphics2D graphics, Point2D at, double radius, Color[] stops) {
        Graphics2D flat = (Graphics2D) graphics.create();
        flat.translate(at.getX(), at.getY());
        flat.scale(1, 0.5);
        flat.setPaint(new RadialGradientPaint(0f, 0f, (float) radius, new float[] {0f, 1f}, stops));
        flat.fill(new Ellipse2D.Double(-radius, -radius, 2 * radius, 2 * radius));
        flat.dispose();
    }

    /** Texto pintado en el piso, a lo largo de las columnas (o de las filas, con {@code alongRows}). */
    private void floorText(Graphics2D graphics, double gx, double gy, String text, float size, Color color,
                           boolean alongRows) {
        Point2D at = projection.at(gx, gy, 0);
        double unit = 1 / TEXT_UNIT;
        double ax = IsoProjection.TILE_W / 2.0 * unit;
        double ay = IsoProjection.TILE_H / 2.0 * unit;
        AffineTransform transform = alongRows
                ? new AffineTransform(ax, -ay, ax, ay, at.getX(), at.getY())
                : new AffineTransform(ax, ay, -ax, ay, at.getX(), at.getY());
        Graphics2D text2 = (Graphics2D) graphics.create();
        text2.transform(transform);
        text2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size)));
        text2.setColor(color);
        text2.drawString(text, 0, 0);
        text2.dispose();
    }

    // ================================================================ muros, puertas, letreros y exterior

    /**
     * Tramos de muro, postes, puertas, letreros y lo que hay afuera (árboles, arbustos, faroles y la ambulancia),
     * para dibujarlos en su turno junto con los muebles y las personas.
     *
     * @return los elementos del edificio
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
            boolean window = isWindow(edge.horizontal(), edge.col(), edge.row());
            items.add(new IsoItem(x + w / 2 + y + d / 2, IsoItem.WALL,
                    graphics -> wall(graphics, edge, x, y, w, d, height, window)));
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
                    graphics -> solid(graphics, x, y, t, t, height)));
        });
        for (Edge door : doors) {
            items.add(new IsoItem(door.col() + door.row() + 0.5 + 0.01, IsoItem.WALL,
                    graphics -> door(graphics, door)));
        }
        for (Sign sign : signs) {
            items.add(new IsoItem(sign.col() + sign.segments() + sign.row() + 0.6, IsoItem.WALL,
                    graphics -> sign(graphics, sign)));
        }
        for (Area area : layout.areasOf(ZoneType.WAITING_ROOM)) {
            int col = area.right() - 1;
            Edge edge = new Edge(true, col, area.row());
            if (walls.containsKey(edge) && !signEdges.contains(edge)) {
                items.add(new IsoItem(col + 1 + area.row() + 0.6, IsoItem.WALL,
                        graphics -> turnScreen(graphics, col, area.row())));
            }
        }
        addOutdoors(items);
        return items;
    }

    /** Pantalla de turnos colgada en el muro del fondo de la sala de espera. */
    private void turnScreen(Graphics2D graphics, int col, int row) {
        double plane = row + WALL_THICKNESS / 2 + 0.004;
        double x0 = col + 0.14;
        double x1 = col + 0.86;
        graphics.setColor(TV_FRAME);
        graphics.fill(IsoBrush.polygon(projection, new double[][] {{x0, plane, 0.5}, {x1, plane, 0.5},
            {x1, plane, 0.9}, {x0, plane, 0.9}}));
        graphics.setColor(TV_SCREEN);
        graphics.fill(IsoBrush.polygon(projection, new double[][] {{x0 + 0.03, plane + 0.002, 0.53},
            {x1 - 0.03, plane + 0.002, 0.53}, {x1 - 0.03, plane + 0.002, 0.87}, {x0 + 0.03, plane + 0.002, 0.87}}));
        Point2D origin = projection.at(x0 + 0.07, plane + 0.004, 0.64);
        double unit = 1 / TEXT_UNIT;
        Graphics2D screen = (Graphics2D) graphics.create();
        screen.transform(new AffineTransform(IsoProjection.TILE_W / 2.0 * unit, IsoProjection.TILE_H / 2.0 * unit, 0,
                IsoProjection.Z_SCALE * unit, origin.getX(), origin.getY()));
        screen.setColor(Color.WHITE);
        screen.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 7));
        screen.drawString("TURNO", 0f, -6f);
        screen.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
        screen.drawString("A-17", 0f, 4f);
        screen.dispose();
    }

    private void solid(Graphics2D graphics, double x, double y, double w, double d, double h) {
        Color left = theme.color(Part.WALL_LEFT);
        Color right = theme.color(Part.WALL_RIGHT);
        Color top = theme.color(Part.WALL_TOP);
        Color base = theme.color(Part.BASEBOARD);
        IsoBrush.box(graphics, projection, x, y, 0, w, d, Math.min(BASEBOARD_HEIGHT, h), top, base,
                IsoPalette.darker(base, 0.85));
        if (h > BASEBOARD_HEIGHT) {
            IsoBrush.box(graphics, projection, x, y, BASEBOARD_HEIGHT, w, d, h - BASEBOARD_HEIGHT, top, left, right);
        }
    }

    private void wall(Graphics2D graphics, Edge edge, double x, double y, double w, double d, double h,
                      boolean window) {
        if (!window) {
            solid(graphics, x, y, w, d, h);
            if (h >= WALL_HEIGHT) {
                wainscot(graphics, edge, x, y, w, d);
            }
            return;
        }
        Color left = theme.color(Part.WALL_LEFT);
        Color right = theme.color(Part.WALL_RIGHT);
        Color top = theme.color(Part.WALL_TOP);
        solid(graphics, x, y, w, d, SILL_HEIGHT);
        double glassTop = h - 0.1;
        IsoBrush.pane(graphics, projection, x + 0.02, y + 0.02, SILL_HEIGHT, w - 0.04, d - 0.04,
                glassTop - SILL_HEIGHT, theme.color(Part.WINDOW), theme.color(Part.WINDOW_FRAME));
        if (edge.horizontal()) {
            IsoBrush.box(graphics, projection, edge.col() + 0.48, y + 0.03, SILL_HEIGHT, 0.04, d - 0.06,
                    glassTop - SILL_HEIGHT, theme.color(Part.WINDOW_FRAME));
        } else {
            IsoBrush.box(graphics, projection, x + 0.03, edge.row() + 0.48, SILL_HEIGHT, w - 0.06, 0.04,
                    glassTop - SILL_HEIGHT, theme.color(Part.WINDOW_FRAME));
        }
        IsoBrush.box(graphics, projection, x, y, glassTop, w, d, h - glassTop, top, left, right);
    }

    /** Enchape de baldosa en la mitad de abajo de la cara visible de un muro. */
    private void wainscot(Graphics2D graphics, Edge edge, double x, double y, double w, double d) {
        Path2D face = edge.horizontal()
                ? IsoBrush.polygon(projection, new double[][] {{x, y + d + 0.003, BASEBOARD_HEIGHT},
                        {x + w, y + d + 0.003, BASEBOARD_HEIGHT}, {x + w, y + d + 0.003, WAINSCOT_HEIGHT},
                        {x, y + d + 0.003, WAINSCOT_HEIGHT}})
                : IsoBrush.polygon(projection, new double[][] {{x + w + 0.003, y, BASEBOARD_HEIGHT},
                        {x + w + 0.003, y + d, BASEBOARD_HEIGHT}, {x + w + 0.003, y + d, WAINSCOT_HEIGHT},
                        {x + w + 0.003, y, WAINSCOT_HEIGHT}});
        Color tile = theme.color(Part.WALL_TILE);
        graphics.setColor(edge.horizontal() ? tile : IsoPalette.darker(tile, 0.9));
        graphics.fill(face);
        Path2D.Double lines = new Path2D.Double();
        double plane = edge.horizontal() ? y + d + 0.003 : x + w + 0.003;
        for (double z = BASEBOARD_HEIGHT + 0.1; z <= WAINSCOT_HEIGHT + 1e-9; z += 0.1) {
            Point2D from = edge.horizontal() ? projection.at(x, plane, z) : projection.at(plane, y, z);
            Point2D to = edge.horizontal() ? projection.at(x + w, plane, z) : projection.at(plane, y + d, z);
            lines.append(new Line2D.Double(from, to), false);
        }
        double length = edge.horizontal() ? w : d;
        for (int step = 1; step < 4; step++) {
            double along = step * length / 4;
            Point2D from = edge.horizontal() ? projection.at(x + along, plane, BASEBOARD_HEIGHT)
                    : projection.at(plane, y + along, BASEBOARD_HEIGHT);
            Point2D to = edge.horizontal() ? projection.at(x + along, plane, WAINSCOT_HEIGHT)
                    : projection.at(plane, y + along, WAINSCOT_HEIGHT);
            lines.append(new Line2D.Double(from, to), false);
        }
        graphics.setStroke(new BasicStroke(0.6f));
        graphics.setColor(theme.color(Part.WALL_TILE_GROUT));
        graphics.draw(lines);
    }

    /** Puerta corrediza de vidrio: el dintel arriba y dos hojas entreabiertas junto a los marcos. */
    private void door(Graphics2D graphics, Edge door) {
        double t = WALL_THICKNESS;
        boolean front = door.horizontal() ? areaCovering(door.col(), door.row()).isEmpty()
                : areaCovering(door.col(), door.row()).isEmpty();
        double h = front ? LOW_WALL_HEIGHT : WALL_HEIGHT;
        Color glass = theme.color(Part.DOOR_GLASS);
        Color frame = theme.color(Part.DOOR_FRAME);
        double leaf = 0.24;
        if (door.horizontal()) {
            double y = door.row() - 0.03;
            for (double x : new double[] {door.col() + t / 2, door.col() + 1 - t / 2 - leaf}) {
                IsoBrush.pane(graphics, projection, x, y, 0.02, leaf, 0.06, h - 0.14, glass, frame);
            }
            if (!front) {
                IsoBrush.box(graphics, projection, door.col() + t / 2, door.row() - t / 2, h - 0.12, 1 - t, t, 0.12,
                        theme.color(Part.WALL_TOP), theme.color(Part.WALL_LEFT), theme.color(Part.WALL_RIGHT));
            }
        } else {
            double x = door.col() - 0.03;
            for (double y : new double[] {door.row() + t / 2, door.row() + 1 - t / 2 - leaf}) {
                IsoBrush.pane(graphics, projection, x, y, 0.02, 0.06, leaf, h - 0.14, glass, frame);
            }
            if (!front) {
                IsoBrush.box(graphics, projection, door.col() - t / 2, door.row() + t / 2, h - 0.12, t, 1 - t, 0.12,
                        theme.color(Part.WALL_TOP), theme.color(Part.WALL_LEFT), theme.color(Part.WALL_RIGHT));
            }
        }
    }

    /**
     * Letrero pintado en la cara de adentro del muro del fondo de una sala: el número grande o el pictograma, y el
     * nombre al lado.
     */
    private void sign(Graphics2D graphics, Sign sign) {
        double plane = sign.row() + WALL_THICKNESS / 2 + 0.004;
        double x0 = sign.col() + 0.18;
        Point2D origin = projection.at(x0, plane, 0.36);
        double unit = 1 / TEXT_UNIT;
        Graphics2D wall = (Graphics2D) graphics.create();
        wall.transform(new AffineTransform(IsoProjection.TILE_W / 2.0 * unit, IsoProjection.TILE_H / 2.0 * unit, 0,
                IsoProjection.Z_SCALE * unit, origin.getX(), origin.getY()));
        wall.setColor(theme.color(Part.SIGN));
        double room = sign.segments() * TEXT_UNIT - 10;
        double nameX;
        if (sign.number() != null) {
            wall.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            wall.drawString(sign.number(), 0f, 0f);
            nameX = wall.getFontMetrics().stringWidth(sign.number()) + 5;
        } else {
            pictogram(wall, sign.area().zone(), 9, -9, 9);
            nameX = 23;
        }
        int size = 9;
        wall.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        while (size > 5 && nameX + wall.getFontMetrics().stringWidth(sign.name()) > room) {
            size--;
            wall.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        }
        wall.drawString(sign.name(), (float) nameX, -3f);
        wall.dispose();
    }

    /** Pictograma de un área, dibujado en el espacio del letrero alrededor de un centro, con el radio dado. */
    static void pictogram(Graphics2D graphics, ZoneType zone, double cx, double cy, double radius) {
        double r = radius;
        graphics.setStroke(new BasicStroke((float) (r * 0.28), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double shape = new Path2D.Double();
        switch (zone) {
            case ICU -> {
                shape.moveTo(cx, cy + r * 0.85);
                shape.curveTo(cx - r * 1.3, cy - r * 0.1, cx - r * 0.6, cy - r * 1.1, cx, cy - r * 0.35);
                shape.curveTo(cx + r * 0.6, cy - r * 1.1, cx + r * 1.3, cy - r * 0.1, cx, cy + r * 0.85);
                graphics.fill(shape);
            }
            case BLOOD_BANK -> {
                shape.moveTo(cx, cy - r);
                shape.curveTo(cx + r * 0.9, cy, cx + r * 0.8, cy + r, cx, cy + r);
                shape.curveTo(cx - r * 0.8, cy + r, cx - r * 0.9, cy, cx, cy - r);
                graphics.fill(shape);
            }
            case HOSPITALIZATION, MORGUE -> {
                graphics.draw(new Line2D.Double(cx - r, cy + r * 0.6, cx + r, cy + r * 0.6));
                graphics.draw(new Line2D.Double(cx - r, cy + r * 0.6, cx - r, cy - r * 0.6));
                graphics.fill(new Rectangle2D.Double(cx - r * 0.6, cy - r * 0.2, r * 1.6, r * 0.6));
            }
            case WAITING_ROOM, STAFF_ROOM -> {
                graphics.draw(new Line2D.Double(cx - r * 0.5, cy - r, cx - r * 0.5, cy + r));
                graphics.draw(new Line2D.Double(cx - r * 0.5, cy + r * 0.1, cx + r * 0.6, cy + r * 0.1));
                graphics.draw(new Line2D.Double(cx + r * 0.6, cy + r * 0.1, cx + r * 0.6, cy + r));
            }
            case DISCHARGE -> {
                graphics.draw(new Line2D.Double(cx - r, cy, cx + r * 0.8, cy));
                graphics.draw(new Line2D.Double(cx + r * 0.2, cy - r * 0.6, cx + r * 0.8, cy));
                graphics.draw(new Line2D.Double(cx + r * 0.2, cy + r * 0.6, cx + r * 0.8, cy));
            }
            default -> {
                graphics.fill(new Rectangle2D.Double(cx - r * 0.3, cy - r, r * 0.6, 2 * r));
                graphics.fill(new Rectangle2D.Double(cx - r, cy - r * 0.3, 2 * r, r * 0.6));
            }
        }
    }

    /** Árboles detrás del edificio, arbustos al frente, faroles en la acera y la ambulancia en la vía. */
    private void addOutdoors(List<IsoItem> items) {
        int cols = layout.getCols();
        int rows = layout.getRows();
        double margin = IsoProjection.SITE_MARGIN;
        Position entrance = layout.getEntrance();
        double road0 = entranceFromLeft ? entrance.row() - 1.5 : entrance.col() - 1.5;
        double road1 = road0 + 4;
        for (double x = 1.5; x < cols; x += 4.5) {
            double gx = x;
            double gy = -margin + 0.7;
            if (entranceFromLeft || gx < road0 || gx > road1) {
                items.add(new IsoItem(gx + gy, IsoItem.FURNITURE, graphics -> tree(graphics, gx, gy, 0.8)));
            }
        }
        for (double y = 1.5; y < rows; y += 4.5) {
            double gx = -margin + 0.7;
            double gy = y;
            if (!entranceFromLeft || gy < road0 || gy > road1) {
                items.add(new IsoItem(gx + gy, IsoItem.FURNITURE, graphics -> tree(graphics, gx, gy, 0.8)));
            }
        }
        for (double x = 2; x < cols; x += 5) {
            double gx = x;
            double gy = rows + margin - 0.7;
            items.add(new IsoItem(gx + gy, IsoItem.FURNITURE, graphics -> bush(graphics, gx, gy)));
        }
        for (double y = 2; y < rows; y += 5) {
            double gx = cols + margin - 0.7;
            double gy = y;
            items.add(new IsoItem(gx + gy, IsoItem.FURNITURE, graphics -> bush(graphics, gx, gy)));
        }
        double lampA = entranceFromLeft ? entrance.row() - 1.2 : entrance.col() - 1.2;
        double lampB = entranceFromLeft ? entrance.row() + 2.2 : entrance.col() + 2.2;
        for (double along : new double[] {lampA, lampB}) {
            double gx = entranceFromLeft ? -SIDEWALK - 0.25 : along;
            double gy = entranceFromLeft ? along : -SIDEWALK - 0.25;
            items.add(new IsoItem(gx + gy, IsoItem.FURNITURE, graphics -> streetLamp(graphics, gx, gy)));
        }
        double ax = entranceFromLeft ? -margin + 0.15 : entrance.col() - 0.75;
        double ay = entranceFromLeft ? entrance.row() - 0.75 : -margin + 0.15;
        items.add(new IsoItem(ax + ay + 1.2, IsoItem.FURNITURE, graphics -> ambulance(graphics, ax, ay)));
    }

    private void tree(Graphics2D graphics, double gx, double gy, double size) {
        IsoBrush.shadow(graphics, projection, gx + 0.25, gy + 0.1, 0.5 * size);
        IsoBrush.box(graphics, projection, gx - 0.07, gy - 0.07, 0, 0.14, 0.14, 1.1, theme.color(Part.TRUNK));
        Point2D crown = projection.at(gx, gy, 1.55);
        double r = 34 * size;
        Color leaf = theme.color(Part.LEAF);
        double[][] blobs = {{-0.45, 0.15, 0.72, 0.82}, {0.42, 0.2, 0.7, 0.88}, {0, -0.25, 0.8, 1.0},
            {-0.15, 0.35, 0.62, 1.06}, {0.25, -0.05, 0.55, 1.14}};
        graphics.setStroke(new BasicStroke(1f));
        for (double[] blob : blobs) {
            double radius = r * blob[2];
            double cx = crown.getX() + blob[0] * r;
            double cy = crown.getY() + blob[1] * r;
            Ellipse2D ball = new Ellipse2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius);
            graphics.setColor(blob[3] >= 1 ? IsoPalette.lighter(leaf, blob[3] - 1) : IsoPalette.darker(leaf, blob[3]));
            graphics.fill(ball);
            graphics.setColor(IsoPalette.darker(leaf, 0.7));
            graphics.draw(ball);
        }
    }

    private void bush(Graphics2D graphics, double gx, double gy) {
        Point2D at = projection.at(gx, gy, 0.22);
        Color leaf = theme.color(Part.LEAF);
        double[][] blobs = {{-8, 2, 9, 0.85}, {7, 3, 8, 0.92}, {0, -3, 10, 1.05}};
        for (double[] blob : blobs) {
            graphics.setColor(blob[3] >= 1 ? IsoPalette.lighter(leaf, blob[3] - 1) : IsoPalette.darker(leaf, blob[3]));
            graphics.fill(new Ellipse2D.Double(at.getX() + blob[0] - blob[2], at.getY() + blob[1] - blob[2],
                    2 * blob[2], 2 * blob[2]));
        }
    }

    private void streetLamp(Graphics2D graphics, double gx, double gy) {
        Color light = theme.color(Part.LAMP_LIGHT);
        if (theme.isDark()) {
            glow(graphics, projection.at(gx, gy, 0), 60, new Color[] {
                new Color(light.getRed(), light.getGreen(), light.getBlue(), 70),
                new Color(light.getRed(), light.getGreen(), light.getBlue(), 0)});
        }
        IsoBrush.box(graphics, projection, gx - 0.035, gy - 0.035, 0, 0.07, 0.07, 1.5, theme.color(Part.LAMP_POST));
        Point2D top = projection.at(gx, gy, 1.55);
        graphics.setColor(light);
        graphics.fill(new Ellipse2D.Double(top.getX() - 6, top.getY() - 3.5, 12, 7));
    }

    private void ambulance(Graphics2D graphics, double gx, double gy) {
        boolean alongCols = entranceFromLeft;
        double length = 1.7;
        double width = 0.95;
        double w = alongCols ? length : width;
        double d = alongCols ? width : length;
        IsoBrush.shadow(graphics, projection, gx + w / 2 + 0.2, gy + d / 2 + 0.1, 0.9);
        for (double[] wheel : alongCols ? new double[][] {{gx + 0.35, gy + d}, {gx + length - 0.35, gy + d}}
                : new double[][] {{gx + w, gy + 0.35}, {gx + w, gy + length - 0.35}}) {
            Point2D at = projection.at(wheel[0], wheel[1], 0.12);
            graphics.setColor(IsoPalette.OUTLINE);
            graphics.fill(new Ellipse2D.Double(at.getX() - 5, at.getY() - 4, 10, 8));
        }
        IsoBrush.box(graphics, projection, gx, gy, 0.12, w, d, 0.9, IsoPalette.lighter(AMBULANCE, 0.5), AMBULANCE,
                IsoPalette.darker(AMBULANCE, 0.86));
        double cab = 0.55;
        if (alongCols) {
            IsoBrush.box(graphics, projection, gx + length, gy + 0.05, 0.12, cab, d - 0.1, 0.6,
                    IsoPalette.lighter(AMBULANCE, 0.5), AMBULANCE, IsoPalette.darker(AMBULANCE, 0.86));
            graphics.setColor(WINDSHIELD);
            graphics.fill(IsoBrush.polygon(projection, new double[][] {{gx + length + cab + 0.002, gy + 0.15, 0.45},
                    {gx + length + cab + 0.002, gy + d - 0.15, 0.45}, {gx + length + cab + 0.002, gy + d - 0.15, 0.68},
                    {gx + length + cab + 0.002, gy + 0.15, 0.68}}));
            graphics.setColor(AMBULANCE_RED);
            graphics.fill(IsoBrush.polygon(projection, new double[][] {{gx, gy + d + 0.002, 0.42},
                    {gx + length, gy + d + 0.002, 0.42}, {gx + length, gy + d + 0.002, 0.54},
                    {gx, gy + d + 0.002, 0.54}}));
        } else {
            IsoBrush.box(graphics, projection, gx + 0.05, gy + length, 0.12, w - 0.1, cab, 0.6,
                    IsoPalette.lighter(AMBULANCE, 0.5), AMBULANCE, IsoPalette.darker(AMBULANCE, 0.86));
            graphics.setColor(AMBULANCE_RED);
            graphics.fill(IsoBrush.polygon(projection, new double[][] {{gx + w + 0.002, gy, 0.42},
                    {gx + w + 0.002, gy + length, 0.42}, {gx + w + 0.002, gy + length, 0.54},
                    {gx + w + 0.002, gy, 0.54}}));
        }
        IsoBrush.box(graphics, projection, gx + 0.25, gy + 0.3, 1.02, 0.22, 0.3, 0.08, SIREN_BLUE);
        IsoBrush.box(graphics, projection, gx + w - 0.5, gy + 0.3, 1.02, 0.22, 0.3, 0.08, AMBULANCE_RED);
        Point2D cross = alongCols ? projection.at(gx + length / 2, gy + d + 0.003, 0.78)
                : projection.at(gx + w + 0.003, gy + length / 2, 0.78);
        graphics.setColor(AMBULANCE_RED);
        graphics.fill(new Rectangle2D.Double(cross.getX() - 6, cross.getY() - 2, 12, 4));
        graphics.fill(new Rectangle2D.Double(cross.getX() - 2, cross.getY() - 6, 4, 12));
    }
}
