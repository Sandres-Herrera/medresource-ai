package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.BloodType;
import com.medresource.model.Door;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.StaffSeat;
import com.medresource.model.ZoneType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Dibuja el plano como un tablero de salas: cada área es una caja con su título y cada lugar es una
 * tarjeta de {@value #CELL_W} × {@value #CELL_H} píxeles.
 *
 * <p>Las camas y los quirófanos se dibujan como tarjetas con su código; la sala del personal, como mesas
 * con dos sillas (una por persona); los tipos de sangre del banco de sangre, como tarjetas rojas con su
 * etiqueta; los lugares de la morgue, los puestos de triage y las sillas de la sala de espera, como tarjetas
 * más pequeñas sin texto; cada escritorio de los consultorios, como una tarjeta con el código de su médico.
 * La entrada lleva una flecha verde y el rótulo "ENTRADA". El color de cada tarjeta depende del área donde
 * está, no de su código.</p>
 *
 * <p>Los pasillos se dibujan como franjas grises sin título que llegan hasta el borde de sus casillas, y cada
 * puerta como una barra café sobre el borde entre su sala y el pasillo.</p>
 */
public class Renderer2D implements SceneRenderer {

    /** Ancho de una casilla, en píxeles del mundo. */
    public static final int CELL_W = 84;
    /** Alto de una casilla, en píxeles del mundo. */
    public static final int CELL_H = 48;

    private static final int AREA_INSET = 2;
    private static final int CARD_INSET = 5;
    private static final int SMALL_INSET = 14;
    private static final int ARC = 12;
    private static final int TITLE_SIZE = 13;
    private static final int CODE_SIZE = 11;
    /** Cuánto se separa cada puesto del personal del centro de su mesa: un cuarto de casilla. */
    private static final double SEAT_OFFSET = CELL_W / 4.0;
    private static final int CHAIR_SIZE = 18;
    /** Grosor de la barra que marca una puerta, en píxeles. */
    public static final int DOOR_THICKNESS = 8;

    @Override
    public Dimension worldSize(HospitalLayout layout) {
        requireLayout(layout);
        return new Dimension(layout.getCols() * CELL_W, layout.getRows() * CELL_H);
    }

    @Override
    public Point2D centerOf(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("La casilla es obligatoria.");
        }
        return new Point2D.Double(position.col() * CELL_W + CELL_W / 2.0, position.row() * CELL_H + CELL_H / 2.0);
    }

    @Override
    public Point2D seatOf(StaffSeat seat) {
        if (seat == null) {
            throw new IllegalArgumentException("El puesto es obligatorio.");
        }
        Point2D table = centerOf(seat.table());
        double side = seat.side() == 0 ? -SEAT_OFFSET : SEAT_OFFSET;
        return new Point2D.Double(table.getX() + side, table.getY());
    }

    @Override
    public void render(Graphics2D graphics, HospitalLayout layout) {
        if (graphics == null) {
            throw new IllegalArgumentException("La superficie de dibujo es obligatoria.");
        }
        requireLayout(layout);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (Area area : layout.getAreas()) {
            paintArea(graphics, area);
        }
        for (Door door : layout.getDoors()) {
            paintDoor(graphics, door);
        }
        paintResources(graphics, layout);
        paintStaffTables(graphics, layout);
        paintConsultDesks(graphics, layout);
        for (Position spot : layout.getMorgueSpots()) {
            card(graphics, spot, SMALL_INSET, Palette.MORGUE_SLAB);
        }
        for (Position spot : layout.getWaitingSpots()) {
            card(graphics, spot, SMALL_INSET, Palette.WAITING_SEAT);
        }
        for (Position spot : layout.getTriageSpots()) {
            card(graphics, spot, SMALL_INSET, Palette.TRIAGE_STATION);
        }
        paintBlood(graphics, layout.getBloodSpots());
        paintEntrance(graphics, layout.getEntrance());
    }

    private void paintArea(Graphics2D graphics, Area area) {
        if (!area.hasTitleRow()) {
            graphics.setColor(Palette.zone(area.zone()));
            graphics.fillRect(area.col() * CELL_W, area.row() * CELL_H, area.cols() * CELL_W, area.rows() * CELL_H);
            return;
        }
        int x = area.col() * CELL_W + AREA_INSET;
        int y = area.row() * CELL_H + AREA_INSET;
        int width = area.cols() * CELL_W - 2 * AREA_INSET;
        int height = area.rows() * CELL_H - 2 * AREA_INSET;
        RoundRectangle2D box = new RoundRectangle2D.Double(x, y, width, height, ARC, ARC);
        graphics.setColor(Palette.zone(area.zone()));
        graphics.fill(box);
        graphics.setColor(Palette.CARD_BORDER);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.draw(box);
        graphics.setColor(Palette.LABEL);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, TITLE_SIZE));
        graphics.drawString(area.title(), x + 8, y + TITLE_SIZE + 8);
    }

    /** Dibuja la tarjeta de cada recurso con su código, en orden de código para que el dibujo sea estable. */
    private void paintResources(Graphics2D graphics, HospitalLayout layout) {
        Map<String, Position> ordered = new TreeMap<>();
        for (String code : layout.getResourceCodes()) {
            ordered.put(code, layout.positionOf(code).orElseThrow());
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, CODE_SIZE));
        for (Map.Entry<String, Position> resource : ordered.entrySet()) {
            Position spot = resource.getValue();
            Color color = layout.areaAt(spot).orElseThrow().zone() == ZoneType.OPERATING_ROOM
                    ? Palette.OPERATING_TABLE
                    : Palette.CARD;
            card(graphics, spot, CARD_INSET, color);
            graphics.setColor(Palette.LABEL);
            graphics.drawString(resource.getKey(), spot.col() * CELL_W + CARD_INSET + 5,
                    spot.row() * CELL_H + CELL_H / 2 + CODE_SIZE / 2 - 1);
        }
    }

    /** Dibuja cada mesa de la sala del personal con una silla en cada puesto. */
    private void paintStaffTables(Graphics2D graphics, HospitalLayout layout) {
        Set<Position> tables = new TreeSet<>(
                Comparator.comparingInt(Position::row).thenComparingInt(Position::col));
        Set<StaffSeat> seats = new HashSet<>();
        for (String code : layout.getStaffCodes()) {
            StaffSeat seat = layout.staffSeatOf(code).orElseThrow();
            tables.add(seat.table());
            seats.add(seat);
        }
        for (Position table : tables) {
            card(graphics, table, CARD_INSET, Palette.STAFF_TABLE);
            for (int side = 0; side < StaffSeat.SEATS_PER_TABLE; side++) {
                StaffSeat seat = new StaffSeat(table, side);
                if (seats.contains(seat)) {
                    Point2D at = seatOf(seat);
                    RoundRectangle2D chair = new RoundRectangle2D.Double(at.getX() - CHAIR_SIZE / 2.0,
                            at.getY() - CHAIR_SIZE / 2.0, CHAIR_SIZE, CHAIR_SIZE, 6, 6);
                    graphics.setColor(Palette.STAFF_SEAT);
                    graphics.fill(chair);
                    graphics.setColor(Palette.CARD_BORDER);
                    graphics.draw(chair);
                }
            }
        }
    }

    /**
     * Dibuja una puerta doble sobre el borde que comparten su sala y el pasillo: una sombra suave alrededor, la
     * barra café con un marco más oscuro y una línea en el centro que la divide en dos hojas.
     */
    private void paintDoor(Graphics2D graphics, Door door) {
        Position inside = door.inside();
        Position outside = door.outside();
        boolean across = inside.col() == outside.col();
        int x;
        int y;
        int width;
        int height;
        if (across) {
            int edge = Math.max(inside.row(), outside.row()) * CELL_H;
            x = inside.col() * CELL_W + CELL_W / 4;
            y = edge - DOOR_THICKNESS / 2;
            width = CELL_W / 2;
            height = DOOR_THICKNESS;
        } else {
            int edge = Math.max(inside.col(), outside.col()) * CELL_W;
            x = edge - DOOR_THICKNESS / 2;
            y = inside.row() * CELL_H + CELL_H / 4;
            width = DOOR_THICKNESS;
            height = CELL_H / 2;
        }
        graphics.setColor(Palette.DOOR_SHADOW);
        graphics.fillRect(x - 1, y - 1, width + 2, height + 2);
        graphics.setColor(Palette.DOOR);
        graphics.fillRect(x, y, width, height);
        graphics.setColor(Palette.DOOR_FRAME);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawRect(x, y, width - 1, height - 1);
        if (across) {
            graphics.drawLine(x + width / 2, y, x + width / 2, y + height - 1);
        } else {
            graphics.drawLine(x, y + height / 2, x + width - 1, y + height / 2);
        }
    }

    /** Dibuja el escritorio de cada médico en los consultorios, con su código. */
    private void paintConsultDesks(Graphics2D graphics, HospitalLayout layout) {
        Map<String, Position> desks = new TreeMap<>();
        for (String code : layout.getStaffCodes()) {
            layout.consultDeskOf(code).ifPresent(desk -> desks.put(code, desk));
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, CODE_SIZE));
        for (Map.Entry<String, Position> desk : desks.entrySet()) {
            Position spot = desk.getValue();
            card(graphics, spot, CARD_INSET, Palette.CONSULT_DESK);
            graphics.setColor(Palette.LABEL);
            graphics.drawString(desk.getKey(), spot.col() * CELL_W + CARD_INSET + 5,
                    spot.row() * CELL_H + CELL_H / 2 + CODE_SIZE / 2 - 1);
        }
    }

    /** Dibuja una tarjeta roja con el tipo de sangre en cada lugar del banco de sangre. */
    private void paintBlood(Graphics2D graphics, List<Position> spots) {
        BloodType[] types = BloodType.values();
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, CODE_SIZE));
        for (int index = 0; index < spots.size() && index < types.length; index++) {
            Position spot = spots.get(index);
            card(graphics, spot, CARD_INSET, Palette.BLOOD_UNIT);
            graphics.setColor(Palette.CARD);
            graphics.drawString(types[index].getLabel(), spot.col() * CELL_W + CARD_INSET + 8,
                    spot.row() * CELL_H + CELL_H / 2 + CODE_SIZE / 2 - 1);
        }
    }

    private void card(Graphics2D graphics, Position spot, int inset, Color color) {
        RoundRectangle2D card = new RoundRectangle2D.Double(spot.col() * CELL_W + inset,
                spot.row() * CELL_H + inset, CELL_W - 2 * inset, CELL_H - 2 * inset, 8, 8);
        graphics.setColor(color);
        graphics.fill(card);
        graphics.setColor(Palette.CARD_BORDER);
        graphics.setStroke(new BasicStroke(1f));
        graphics.draw(card);
    }

    /** Marca la entrada con una flecha verde hacia la derecha y el rótulo "ENTRADA". */
    private void paintEntrance(Graphics2D graphics, Position entrance) {
        int left = entrance.col() * CELL_W + 8;
        int top = entrance.row() * CELL_H;
        int[] xs = {left, left, left + 12};
        int[] ys = {top + CELL_H / 2 - 8, top + CELL_H / 2 + 8, top + CELL_H / 2};
        graphics.setColor(Palette.ENTRANCE);
        graphics.fillPolygon(xs, ys, 3);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, CODE_SIZE));
        graphics.drawString("ENTRADA", left + 16, top + CELL_H / 2 + CODE_SIZE / 2 - 1);
    }

    private static void requireLayout(HospitalLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("El plano es obligatorio.");
        }
    }
}