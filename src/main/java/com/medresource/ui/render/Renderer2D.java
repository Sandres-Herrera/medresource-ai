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
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Ilustración vectorial de la vista 2D del hospital. Mantiene exactamente la geometría que utilizan
 * el motor de simulación, la cámara, los clics y la proyección de la vista 2.5D.
 *
 * <p>Todos los muebles, iconos y elementos decorativos se dibujan en Swing/Java2D. No son nuevos
 * recursos del simulador: la cantidad y las posiciones provienen exclusivamente de HospitalLayout.
 * Esta clase no modifica el estado del hospital ni los objetos del plano.</p>
 */
public class Renderer2D implements SceneRenderer {

    // Estos valores forman parte del contrato espacial con el motor, la cámara y la vista 2.5D.
    /** Ancho de una casilla, en píxeles del mundo. */
    public static final int CELL_W = 84;
    /** Alto de una casilla, en píxeles del mundo. */
    public static final int CELL_H = 48;
    /** Dónde se para la enfermera de triage dentro de la casilla de su puesto, desde el borde izquierdo. */
    public static final int TRIAGE_NURSE_X = 18;
    /** Grosor de la barra que marca una puerta, en píxeles. */
    public static final int DOOR_THICKNESS = 8;

    private static final int TITLE_SIZE = 20;
    private static final int CODE_SIZE = 11;
    private static final double SEAT_OFFSET = CELL_W / 4.0;
    private static final Color INK = new Color(0x15283E);
    private static final Color FRAME = new Color(0x263F65);
    private static final int CARD_INSET = 5;
    private static final int SMALL_INSET = 14;
    private static final int CHAIR_SIZE = 18;

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
        return new Point2D.Double(position.col() * CELL_W + CELL_W / 2.0,
                position.row() * CELL_H + CELL_H / 2.0);
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
        // Crear una superficie hija evita propagar clips, fuentes, strokes y degradados al panel
        // que dibuja a los pacientes encima (y a cualquier otra vista).
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for (Area area : layout.getAreas()) paintArea(g, area);
            for (Door door : layout.getDoors()) paintDoor(g, door);
            paintResources(g, layout);
            paintStaffTables(g, layout);
            paintConsultDesks(g, layout);
            for (Position spot : layout.getMorgueSpots()) card(g, spot, SMALL_INSET, Palette.MORGUE_SLAB);
            for (Position spot : layout.getWaitingSpots()) card(g, spot, SMALL_INSET, Palette.WAITING_SEAT);
            for (Position spot : layout.getTriageSpots()) {
                card(g, spot, SMALL_INSET, Palette.TRIAGE_STATION);
                paintTriageNurse(g, spot);
            }
            paintBlood(g, layout.getBloodSpots());
            paintEntrance(g, layout.getEntrance());
        } finally {
            g.dispose();
        }
    }

    private static Color roomColor(ZoneType zone) {
        return switch (zone) {
            case TRIAGE -> new Color(0xFFD2AC);
            case WAITING_ROOM -> new Color(0xFFF0CD);
            case HOSPITALIZATION -> new Color(0xB8E3FF);
            case ICU -> new Color(0xFFC2D1);
            case OPERATING_ROOM -> new Color(0xA7F5EA);
            case BLOOD_BANK -> new Color(0xFFD2DE);
            case MORGUE -> new Color(0xD4CFF4);
            case DISCHARGE -> new Color(0xD4F5CB);
            case STAFF_ROOM -> new Color(0xE0F0C8);
            case CONSULTATION -> new Color(0xD9D7FD);
            case CORRIDOR -> new Color(0xF0F6FB);
        };
    }

    private static Color lighter(Color color, double fraction) {
        return new Color((int) (color.getRed() + (255 - color.getRed()) * fraction),
                (int) (color.getGreen() + (255 - color.getGreen()) * fraction),
                (int) (color.getBlue() + (255 - color.getBlue()) * fraction));
    }

    private void paintArea(Graphics2D g, Area area) {
        int x = area.col() * CELL_W, y = area.row() * CELL_H;
        int w = area.cols() * CELL_W, h = area.rows() * CELL_H;
        if (!area.hasTitleRow()) {
            g.setColor(new Color(0xE5EDF5));
            g.fillRect(x, y, w, h);
            g.setColor(new Color(0xFAFCFF));
            g.fillRect(x, y + 2, w, h - 4);
            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(0xD8E2EB));
            for (int xx = x; xx <= x + w; xx += 36) g.drawLine(xx, y, xx, y + h);
            for (int yy = y; yy <= y + h; yy += 36) g.drawLine(x, yy, x + w, yy);
            g.setColor(new Color(0xA7BBD0));
            g.fillRect(x, y, w, 3);
            g.fillRect(x, y + h - 3, w, 3);
            return;
        }

        int margin = 4;
        // El borde oscuro y su reflejo crean el aspecto de plano de videojuego de la referencia.
        g.setColor(new Color(0x14243D));
        g.fillRoundRect(x + margin - 1, y + margin + 4, w - 2 * margin + 2,
                h - 2 * margin, 15, 15);
        Shape room = new RoundRectangle2D.Double(x + margin, y + margin,
                w - 2.0 * margin, h - 2.0 * margin, 15, 15);
        Color base = roomColor(area.zone());
        g.setPaint(new GradientPaint(x, y + 4, lighter(base, .67), x, y + h - 8, base));
        g.fill(room);

        Graphics2D tiled = (Graphics2D) g.create();
        try {
            tiled.clip(room);
            tiled.setStroke(new BasicStroke(.7f));
            tiled.setColor(new Color(255, 255, 255, 79));
            for (int xx = x + 12; xx < x + w; xx += 36) tiled.drawLine(xx, y + 42, xx, y + h);
            for (int yy = y + 42; yy < y + h; yy += 36) tiled.drawLine(x, yy, x + w, yy);
            tiled.setColor(new Color(30, 72, 119, 14));
            for (int yy = y + 59; yy < y + h; yy += 72) {
                for (int xx = x + 14; xx < x + w; xx += 72) {
                    tiled.fillOval(xx, yy, 2, 2);
                }
            }
            tiled.setPaint(new GradientPaint(x, y + 4, new Color(255, 255, 255, 95),
                    x, y + 46, new Color(255, 255, 255, 0)));
            tiled.fillRect(x + 5, y + 4, w - 10, 47);
        } finally {
            tiled.dispose();
        }
        g.setColor(FRAME);
        g.setStroke(new BasicStroke(4f));
        g.draw(room);
        g.setColor(new Color(192, 228, 247, 180));
        g.setStroke(new BasicStroke(1.15f));
        g.drawRoundRect(x + 7, y + 7, w - 14, h - 14, 12, 12);

        // Cornisa interior satinada que dota al muro de grosor.
        g.setColor(new Color(255, 255, 255, 118));
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(x + 14, y + 10, x + w - 14, y + 10);
        paintRoomTitle(g, area, x, y, w);
    }

    private void paintRoomTitle(Graphics2D g, Area area, int x, int y, int w) {
        String title = area.title().toUpperCase(Locale.ROOT);
        int size = TITLE_SIZE;
        int reserve = area.zone() == ZoneType.OPERATING_ROOM ? 29 : 41;
        while (size > 10) {
            Font f = new Font(Font.SANS_SERIF, Font.BOLD, size);
            if (g.getFontMetrics(f).stringWidth(title) <= w - reserve - 16) break;
            size--;
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        int textX = x + (w - fm.stringWidth(title)) / 2;
        textX = Math.max(textX, x + (area.zone() == ZoneType.OPERATING_ROOM ? 26 : 34));
        g.setColor(new Color(255, 255, 255, 155));
        g.drawString(title, textX + 1, y + 32);
        g.setColor(INK);
        g.drawString(title, textX, y + 31);
        paintAreaIcon(g, area.zone(), x + 15, y + 15);
    }

    private void paintAreaIcon(Graphics2D g, ZoneType zone, int x, int y) {
        Graphics2D icon = (Graphics2D) g.create();
        try {
            icon.translate(x, y);
            icon.setColor(new Color(0x1F4863));
            icon.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (zone) {
                case TRIAGE -> {
                    icon.drawRoundRect(3, 3, 21, 24, 3, 3);
                    icon.drawLine(10, 3, 10, 0); icon.drawLine(10, 0, 17, 0);
                    icon.drawLine(17, 0, 17, 3);
                    icon.drawLine(13, 9, 13, 21); icon.drawLine(7, 15, 19, 15);
                }
                case WAITING_ROOM, STAFF_ROOM -> {
                    for (int i = 0; i < 3; i++) {
                        icon.drawOval(2 + i * 8, 2 + (i % 2) * 3, 6, 6);
                        icon.fillRoundRect(1 + i * 8, 13 + (i % 2) * 3, 8, 9, 3, 3);
                    }
                }
                case HOSPITALIZATION -> {
                    icon.drawRoundRect(3, 12, 25, 9, 2, 2);
                    icon.fillRoundRect(4, 8, 8, 6, 3, 3);
                    icon.drawLine(3, 21, 3, 25); icon.drawLine(27, 21, 27, 25);
                }
                case ICU -> {
                    icon.drawLine(1, 14, 7, 14);
                    icon.drawLine(7, 14, 10, 7);
                    icon.drawLine(10, 7, 15, 23);
                    icon.drawLine(15, 23, 19, 11);
                    icon.drawLine(19, 11, 23, 14);
                    icon.drawLine(23, 14, 30, 14);
                }
                case OPERATING_ROOM -> {
                    icon.drawRoundRect(3, 13, 26, 8, 4, 4);
                    icon.drawLine(16, 11, 16, 4);
                    icon.drawOval(13, 1, 6, 5);
                    icon.drawLine(16, 21, 16, 25);
                }
                case BLOOD_BANK -> {
                    Path2D drop = new Path2D.Double();
                    drop.moveTo(15, 1); drop.curveTo(24, 14, 26, 18, 22, 24);
                    drop.curveTo(16, 31, 4, 26, 7, 18); drop.closePath();
                    icon.fill(drop);
                }
                case MORGUE -> {
                    icon.drawOval(7, 3, 16, 18);
                    icon.drawOval(9, 5, 12, 14);
                    icon.fillOval(11, 11, 3, 3); icon.fillOval(17, 11, 3, 3);
                }
                case DISCHARGE -> {
                    icon.drawRoundRect(2, 5, 24, 21, 3, 3);
                    icon.drawLine(8, 16, 27, 16);
                    icon.drawLine(21, 10, 27, 16); icon.drawLine(21, 22, 27, 16);
                }
                case CONSULTATION -> {
                    icon.drawOval(8, 1, 11, 9);
                    icon.drawLine(13, 10, 13, 18);
                    icon.drawArc(4, 14, 18, 14, 170, 200);
                    icon.fillOval(22, 18, 4, 4);
                }
                case CORRIDOR -> { }
            }
        } finally {
            icon.dispose();
        }
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

    /**
     * Dibuja a la enfermera de triage de un puesto, a la izquierda de su tarjeta: uniforme lila y gorro. No es
     * personal del motor; siempre está en su puesto.
     */
    private void paintTriageNurse(Graphics2D graphics, Position spot) {
        double cx = spot.col() * CELL_W + TRIAGE_NURSE_X;
        double cy = spot.row() * CELL_H + CELL_H / 2.0 - 2;
        PatientPainter.paintShadow(graphics, cx, cy, 1.0);
        graphics.setStroke(new BasicStroke(Palette.OUTLINE_WIDTH));
        outlined(graphics, new RoundRectangle2D.Double(cx - 5, cy - 3, 10, 14, 4, 4), Palette.TRIAGE_NURSE);
        outlined(graphics, new Ellipse2D.Double(cx - 5, cy - 14, 10, 10), Palette.PATIENT_HEAD);
        outlined(graphics, new RoundRectangle2D.Double(cx - 4, cy - 16, 8, 3, 2, 2),
                Palette.TRIAGE_NURSE.darker());
    }

    private static void outlined(Graphics2D graphics, Shape shape, Color color) {
        graphics.setColor(color);
        graphics.fill(shape);
        graphics.setColor(Palette.PATIENT_OUTLINE);
        graphics.draw(shape);
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

    private void card(Graphics2D graphics, Position spot, int inset, Color color) {
        RoundRectangle2D card = new RoundRectangle2D.Double(spot.col() * CELL_W + inset,
                spot.row() * CELL_H + inset, CELL_W - 2 * inset, CELL_H - 2 * inset, 8, 8);
        graphics.setColor(color);
        graphics.fill(card);
        graphics.setColor(Palette.CARD_BORDER);
        graphics.setStroke(new BasicStroke(1f));
        graphics.draw(card);
    }

    private static void requireLayout(HospitalLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("El plano es obligatorio.");
        }
    }
}
