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
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final Color ICE = new Color(0xEAF8FF);
    private static final Color WHITE = new Color(0xFFFFFF);

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
            for (Area area : layout.getAreas()) paintDecoration(g, area);
            for (Door door : layout.getDoors()) paintDoor(g, door);
            paintResources(g, layout);
            paintStaffTables(g, layout);
            paintConsultDesks(g, layout);
            for (Position spot : layout.getMorgueSpots()) paintMorgueDrawer(g, spot);
            paintWaitingGroups(g, layout.getWaitingSpots());
            for (Position spot : layout.getTriageSpots()) {
                paintTriageStation(g, spot);
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

    /** Accesorios escenográficos, todos decorativos. No cambian puestos ni rutas del motor. */
    private void paintDecoration(Graphics2D g, Area area) {
        int x = area.col() * CELL_W, y = area.row() * CELL_H;
        int w = area.cols() * CELL_W, h = area.rows() * CELL_H;
        if (area.zone() == ZoneType.CORRIDOR) {
            for (int dx = 4 * CELL_W; dx + 120 < w; dx += 6 * CELL_W) {
                paintBench(g, x + dx, y + 17);
                paintFireExtinguisher(g, x + dx - 46, y + 11);
            }
            for (int dx = 2 * CELL_W; dx + 64 < w; dx += 8 * CELL_W) {
                paintFloorMarker(g, x + dx, y + h / 2);
            }
            if (w > 10 * CELL_W) paintTrash(g, x + w - 53, y + 17);
            return;
        }
        switch (area.zone()) {
            case TRIAGE -> {
                paintPlant(g, x + 25, y + h - 23, 1.0);
                paintPlant(g, x + w - 25, y + h - 23, 1.0);
                // Primer cubículo despejado: el letrero de entrada ocupa la zona izquierda.
                paintMiniCabinet(g, x + 13, y + 108, 34, 39);
                paintTriageConsole(g, x + 37, y + 184);
                paintPoster(g, x + 23, y + 62, false);
            }
            case WAITING_ROOM -> {
                // Plantas de pared fuera de los tres módulos centrales de butacas.
                paintPlant(g, x + 15, y + 57, .80);
                paintPlant(g, x + w - 15, y + 57, .80);
                // Con seis filas de sillas no existe espacio libre para una recepción: se
                // dibuja únicamente cuando la configuración dispone de espacio sobrante.
                if (area.rows() >= 9) {
                    paintPlant(g, x + 26, y + h - 22, .85);
                    paintPlant(g, x + w - 26, y + h - 22, .85);
                    paintInfoDesk(g, x + w / 2, y + h - 69);
                }
            }
            case HOSPITALIZATION -> {
                paintWallMonitor(g, x + w - 40, y + 12);
                paintPlant(g, x + 25, y + h - 22, .85);
                paintTrash(g, x + w - 22, y + h - 29);
            }
            case ICU -> {
                paintWallMonitor(g, x + w - 42, y + 12);
                paintCabinet(g, x + 15, y + h - 64, 34);
                paintMonitoringTower(g, x + w - 27, y + h - 62);
            }
            case OPERATING_ROOM -> {
                // Cada sala ya recibe un foco articulado propio sobre la camilla.
                paintInstrumentCart(g, x + w - 41, y + h - 39);
                paintSurgicalTray(g, x + 16, y + h - 40);
            }
            case BLOOD_BANK -> {
                paintFridge(g, x + w - 39, y + 14);
                paintBloodDrop(g, x + 45, y + 22);
            }
            case MORGUE -> {
                paintMortuaryPanel(g, x + w - 42, y + 13);
            }
            case DISCHARGE -> {
                paintInfoDesk(g, x + w / 2, y + h / 2 - 16);
                paintBench(g, x + w / 2 - 51, y + h - 45);
                paintPlant(g, x + 25, y + h - 22, .95);
                paintPlant(g, x + w - 25, y + h - 22, .95);
            }
            case STAFF_ROOM -> {
                // Los muebles se reparten entre las casillas DESOCUPADAS de la última fila.
                paintLockers(g, x + w - 284, y + h - 40);
                paintPlant(g, x + w - 191, y + h - 20, .68);
                paintMeetingTable(g, x + w - 145, y + h - 34);
                paintWaterCooler(g, x + w - 37, y + h - 47);
            }
            case CONSULTATION -> {
                paintPlant(g, x + 19, y + h - 18, .72);
                paintPoster(g, x + w - 36, y + 10, true);
            }
            case CORRIDOR -> { }
        }
    }

    private static void paintPlant(Graphics2D g, double x, double y, double scale) {
        Graphics2D p = (Graphics2D) g.create();
        try {
            p.translate(x, y); p.scale(scale, scale);
            p.setColor(new Color(0, 0, 0, 35)); p.fillOval(-14, 14, 28, 7);
            p.setColor(new Color(0x895B36)); p.fillRoundRect(-10, 2, 20, 16, 4, 4);
            p.setColor(new Color(0xCC9E68)); p.fillRect(-9, 4, 18, 4);
            p.setColor(new Color(0x276C38));
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * i / 3;
                int px = (int) (Math.sin(a) * 10);
                int py = (int) (Math.cos(a) * 5);
                p.fillOval(px - 6, py - 13, 13, 15);
            }
            p.setColor(new Color(0x61B94C));
            p.fillOval(-7, -17, 11, 12); p.fillOval(2, -12, 10, 11);
        } finally { p.dispose(); }
    }

    private static void paintBench(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 39)); g.fillRoundRect(x + 4, y + 26, 100, 5, 3, 3);
        g.setColor(new Color(0x284C72));
        g.fillRoundRect(x + 2, y + 1, 104, 22, 7, 7);
        g.setColor(new Color(0x327FB7));
        for (int i = 0; i < 3; i++) {
            g.setPaint(new GradientPaint(x, y + 2, new Color(0x7DD5F7), x, y + 22, new Color(0x308BBF)));
            g.fillRoundRect(x + 6 + 33 * i, y + 3, 30, 17, 5, 5);
        }
        g.setColor(new Color(0x173D5F));
        g.fillRect(x + 9, y + 23, 5, 5); g.fillRect(x + 94, y + 23, 5, 5);
    }

    private static void paintFireExtinguisher(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x9B2332)); g.fillRoundRect(x, y + 7, 14, 23, 4, 4);
        g.setColor(new Color(0xFA343F)); g.fillRoundRect(x + 2, y + 9, 9, 17, 3, 3);
        g.setColor(WHITE); g.fillRect(x + 4, y + 17, 5, 4);
        g.setColor(INK); g.setStroke(new BasicStroke(2));
        g.drawLine(x + 7, y + 6, x + 7, y + 2); g.drawLine(x + 4, y + 3, x + 12, y + 3);
    }

    private static void paintTrash(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x354F65)); g.fillRoundRect(x, y + 2, 16, 22, 3, 3);
        g.setColor(new Color(0xB0CBD7)); g.fillRoundRect(x + 2, y + 5, 12, 16, 2, 2);
        g.setColor(new Color(0x273A4A)); g.fillRoundRect(x - 2, y, 20, 6, 3, 3);
    }

    private static void paintPoster(Graphics2D g, int x, int y, boolean cross) {
        g.setColor(FRAME); g.fillRoundRect(x, y, 29, 34, 3, 3);
        g.setColor(ICE); g.fillRoundRect(x + 3, y + 3, 23, 28, 2, 2);
        g.setColor(new Color(0x67BCE2)); g.fillRoundRect(x + 6, y + 6, 17, 6, 2, 2);
        g.setColor(new Color(0x5E9EA9));
        if (cross) { g.fillRect(x + 13, y + 13, 4, 12); g.fillRect(x + 9, y + 17, 12, 4); }
        else {
            g.fillRect(x + 6, y + 16, 16, 2);
            g.fillRect(x + 6, y + 21, 12, 2);
        }
    }


    private static void paintMiniCabinet(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0x466176)); g.fillRoundRect(x, y, w, h, 4, 4);
        g.setPaint(new GradientPaint(x, y + 1, new Color(0xF6FAFD), x, y + h - 2, new Color(0xA8C0D0)));
        g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 3, 3);
        g.setColor(new Color(0x7491A4)); g.drawLine(x + w / 2, y + 4, x + w / 2, y + h - 4);
        g.fillRect(x + w / 2 - 4, y + h / 2, 2, 5); g.fillRect(x + w / 2 + 2, y + h / 2, 2, 5);
    }

    private static void paintMonitoringTower(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x466981)); g.fillRoundRect(x, y, 18, 40, 3, 3);
        g.setPaint(new GradientPaint(x + 1, y + 1, new Color(0xE8F8FF), x + 1, y + 18, new Color(0x81C8D8)));
        g.fillRoundRect(x + 2, y + 2, 14, 16, 2, 2);
        g.setColor(new Color(0x64A7B5)); g.fillRect(x + 4, y + 24, 10, 2); g.fillRect(x + 4, y + 29, 10, 2);
        g.setColor(new Color(0x2B4C5E)); g.fillOval(x + 4, y + 35, 4, 4); g.fillOval(x + 10, y + 35, 4, 4);
    }

    private static void paintSurgicalTray(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x5C8592)); g.fillRoundRect(x, y + 8, 21, 4, 2, 2);
        g.fillRect(x + 9, y + 12, 3, 11);
        g.setColor(new Color(0xD6FBF3)); g.fillRoundRect(x + 2, y + 2, 17, 8, 3, 3);
        g.setColor(new Color(0x3A6F82)); g.drawLine(x + 5, y + 5, x + 15, y + 5);
        g.drawLine(x + 8, y + 3, x + 8, y + 9);
    }

    private static void paintMeetingTable(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 24)); g.fillOval(x - 4, y + 18, 56, 12);
        g.setColor(new Color(0x7297A8)); g.fillOval(x, y, 48, 24);
        g.setColor(new Color(0xE8F4F7)); g.fillOval(x + 3, y + 3, 42, 18);
        g.setColor(new Color(0x557B8A)); g.fillRect(x + 22, y + 21, 4, 8);
        for (int i = 0; i < 4; i++) {
            int sx = x - 8 + i * 18;
            g.setColor(new Color(0x3D6A86)); g.fillRoundRect(sx, y + 7 + (i % 2) * 10, 10, 7, 3, 3);
            g.setColor(new Color(0x8AD3F0)); g.fillRoundRect(sx + 1, y + 8 + (i % 2) * 10, 8, 5, 2, 2);
        }
    }

    private static void paintFloorMarker(Graphics2D g, int x, int cy) {
        g.setColor(new Color(0xC8D7E3)); g.fillRoundRect(x, cy - 4, 26, 8, 4, 4);
        g.setColor(new Color(0x6C93A7)); g.fillRoundRect(x + 3, cy - 2, 20, 4, 3, 3);
        g.setColor(WHITE); g.fillPolygon(new int[]{x + 8, x + 8, x + 15}, new int[]{cy - 1, cy + 3, cy + 1}, 3);
    }

    private static void paintTriageConsole(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x3B586B)); g.fillRoundRect(x - 26, y + 24, 48, 5, 2, 2);
        g.setColor(new Color(0xF0F6FB)); g.fillRoundRect(x - 25, y + 7, 44, 20, 4, 4);
        g.setColor(FRAME); g.drawRoundRect(x - 25, y + 7, 44, 20, 4, 4);
        g.setColor(new Color(0x507D96)); g.fillRoundRect(x - 15, y - 7, 22, 16, 2, 2);
        g.setColor(new Color(0xB9EFFF)); g.fillRoundRect(x - 13, y - 5, 18, 11, 2, 2);
        g.setColor(new Color(0x3E738F)); g.fillRect(x - 5, y + 9, 3, 7);
    }

    private static void paintWallMonitor(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x1A3852)); g.fillRoundRect(x, y, 31, 24, 4, 4);
        g.setColor(new Color(0x48B2BC)); g.fillRoundRect(x + 3, y + 3, 25, 16, 2, 2);
        g.setColor(new Color(0xC2FEEE)); g.setStroke(new BasicStroke(1.4f));
        g.drawPolyline(new int[]{x + 5, x + 10, x + 12, x + 15, x + 18, x + 20, x + 26},
                new int[]{y + 11, y + 11, y + 7, y + 16, y + 8, y + 11, y + 11}, 7);
        g.setColor(new Color(0x7893AA)); g.fillRect(x + 12, y + 22, 8, 3);
    }

    private static void paintCabinet(Graphics2D g, int x, int y, int height) {
        g.setColor(new Color(0x244460)); g.fillRoundRect(x, y, 24, height, 4, 4);
        g.setColor(new Color(0xC9E1EE)); g.fillRoundRect(x + 2, y + 2, 20, height - 4, 3, 3);
        g.setColor(new Color(0x88A5B7)); g.drawLine(x + 12, y + 2, x + 12, y + height - 2);
        g.setColor(new Color(0x4B7691)); g.fillRect(x + 9, y + height / 2, 2, 5);
    }

    private static void paintInstrumentCart(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x557D8B)); g.fillRoundRect(x, y, 28, 5, 2, 2);
        g.drawLine(x + 3, y + 5, x + 3, y + 24);
        g.drawLine(x + 25, y + 5, x + 25, y + 24);
        g.setColor(new Color(0xBDF2EC)); g.fillRect(x + 4, y + 13, 20, 3);
        g.setColor(new Color(0x24475A)); g.fillOval(x + 1, y + 23, 5, 5); g.fillOval(x + 23, y + 23, 5, 5);
    }

    private static void paintBloodDrop(Graphics2D g, int x, int y) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y - 10);
        p.curveTo(x + 15, y + 4, x + 8, y + 11, x, y + 11);
        p.curveTo(x - 10, y + 10, x - 13, y + 3, x, y - 10);
        p.closePath();
        g.setColor(new Color(0xA92339)); g.fill(p);
        g.setColor(new Color(255, 201, 207, 190)); g.fillOval(x - 4, y - 1, 4, 6);
    }

    private static void paintFridge(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x47677C)); g.fillRoundRect(x, y, 27, 34, 3, 3);
        g.setPaint(new GradientPaint(x, y, WHITE, x + 26, y + 30, new Color(0x90B5C6)));
        g.fillRoundRect(x + 2, y + 2, 23, 30, 2, 2);
        g.setColor(new Color(0x4E788B)); g.drawLine(x + 3, y + 18, x + 25, y + 18);
        g.fillRect(x + 19, y + 10, 2, 6); g.fillRect(x + 19, y + 23, 2, 6);
    }

    private static void paintMortuaryPanel(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x314F6A)); g.fillRoundRect(x, y, 32, 25, 3, 3);
        g.setColor(new Color(0xDCE8F1)); g.fillRoundRect(x + 3, y + 3, 26, 19, 2, 2);
        g.setColor(new Color(0x79B8D8)); g.fillRect(x + 6, y + 6, 20, 4);
        g.setColor(new Color(0x7A92A0)); g.fillRect(x + 6, y + 12, 8, 7);
        g.fillRect(x + 17, y + 12, 9, 7);
    }

    private static void paintLockers(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x597687)); g.fillRoundRect(x, y, 72, 28, 4, 4);
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(0xC9DEDD));
            g.fillRoundRect(x + 3 + i * 17, y + 2, 15, 24, 2, 2);
            g.setColor(new Color(0x668A93));
            g.fillRect(x + 6 + i * 17, y + 6, 9, 2);
            g.fillRect(x + 6 + i * 17, y + 10, 9, 2);
            g.fillOval(x + 14 + i * 17, y + 17, 2, 2);
        }
    }

    private static void paintWaterCooler(Graphics2D g, int x, int y) {
        g.setColor(new Color(0x529BC0)); g.fillRoundRect(x + 5, y, 15, 16, 5, 5);
        g.setColor(new Color(0xC4E8F7)); g.fillRoundRect(x + 8, y + 1, 9, 12, 3, 3);
        g.setColor(new Color(0xF2F8FC)); g.fillRoundRect(x, y + 15, 25, 24, 4, 4);
        g.setColor(new Color(0x657F9D)); g.drawRoundRect(x, y + 15, 25, 24, 4, 4);
        g.fillRect(x + 7, y + 20, 11, 3);
    }

    private static void paintInfoDesk(Graphics2D g, int centerX, int y) {
        int x = centerX - 61;
        g.setColor(new Color(0, 0, 0, 28)); g.fillRoundRect(x + 4, y + 36, 125, 7, 5, 5);
        g.setColor(new Color(0x57788F)); g.fillRoundRect(x, y + 9, 124, 28, 6, 6);
        g.setPaint(new GradientPaint(x, y + 9, new Color(0xD8E6F1), x, y + 34, new Color(0x7894B3)));
        g.fillRoundRect(x + 4, y + 11, 116, 24, 5, 5);
        g.setColor(new Color(0x3E5870)); g.fillRoundRect(x - 5, y + 4, 134, 8, 3, 3);
        g.setColor(new Color(0x254960)); g.fillRoundRect(centerX - 8, y + 14, 16, 16, 9, 9);
        g.setColor(new Color(0x324B61)); g.fillRoundRect(centerX - 39, y + 2, 10, 8, 2, 2);
        g.fillRoundRect(centerX + 29, y + 2, 10, 8, 2, 2);
        g.setColor(new Color(0xAEE5F6)); g.fillRoundRect(centerX - 37, y + 4, 6, 4, 2, 2);
        g.fillRoundRect(centerX + 31, y + 4, 6, 4, 2, 2);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(WHITE); g.drawString("i", centerX - 2, y + 26);
    }

    /** Camas con almohada, manta, ruedas, dos monitores y biombo, sin mover su casilla. */
    private void paintResources(Graphics2D g, HospitalLayout layout) {
        Map<String, Position> ordered = new TreeMap<>();
        for (String code : layout.getResourceCodes()) {
            ordered.put(code, layout.positionOf(code).orElseThrow());
        }
        for (Map.Entry<String, Position> item : ordered.entrySet()) {
            Position spot = item.getValue();
            ZoneType zone = layout.areaAt(spot).orElseThrow().zone();
            if (zone == ZoneType.HOSPITALIZATION || zone == ZoneType.ICU) {
                paintBed(g, spot, item.getKey(), zone == ZoneType.ICU);
            } else if (zone == ZoneType.OPERATING_ROOM) {
                paintOperatingTable(g, spot, item.getKey());
            } else {
                // El modelo puede contener otros tipos de recurso en algunas configuraciones.
                paintFallbackCard(g, spot, item.getKey());
            }
        }
    }

    /**
     * Cama vista desde arriba con profundidad propia: somier con cantos, cabecera, almohada, manta
     * (azul en hospitalización, rosada en la UCI) y dos ruedas, con sus monitores y su biombo. Todo
     * queda dentro de la fila real del plano y no mueve la casilla donde se ubica el paciente.
     */
    private static void paintBed(Graphics2D g, Position spot, String code, boolean icu) {
        int x = spot.col() * CELL_W, y = spot.row() * CELL_H;
        int bx = x + 29, by = y + 3, bw = 45, bh = 35;
        g.setColor(new Color(26, 57, 78, 30));
        g.fillOval(bx - 2, by + bh, bw + 14, 8);
        g.setColor(new Color(0x344E63));
        g.fillRoundRect(bx - 3, by + 3, bw + 7, bh - 1, 8, 8);
        // Canto lateral del somier: la perspectiva aparece en las dos caras.
        g.setColor(new Color(0x67899B));
        g.fillPolygon(new int[]{bx + bw - 1, bx + bw + 5, bx + bw + 5, bx + bw - 1},
                new int[]{by + 6, by + 10, by + bh + 1, by + bh - 4}, 4);
        g.setPaint(new GradientPaint(bx + 3, by + 5, WHITE, bx + bw - 3, by + bh, new Color(0xB9D4E2)));
        g.fillRoundRect(bx + 1, by + 5, bw - 2, bh - 5, 6, 6);
        // Cabecera con borde oscuro y almohada levantada.
        g.setColor(new Color(0x2B4C62));
        g.fillRoundRect(bx - 3, by - 2, bw + 5, 12, 5, 5);
        g.setPaint(new GradientPaint(bx, by - 1, WHITE, bx, by + 9, new Color(0xB6CCD8)));
        g.fillRoundRect(bx, by, bw - 1, 9, 5, 5);
        g.setColor(new Color(0x8DADBC));
        g.drawRoundRect(bx + 4, by + 1, bw - 10, 6, 3, 3);
        g.setColor(new Color(0xD3E5F0));
        g.fillRoundRect(bx + 9, by + 11, bw - 17, 9, 4, 4);
        g.setColor(WHITE);
        g.fillRoundRect(bx + 9, by + 10, bw - 19, 8, 4, 4);
        // Manta con borde doblado, sombra inferior y luz que recorre su longitud.
        g.setPaint(new GradientPaint(bx + 2, by + 19,
                icu ? new Color(0xFFE4EC) : new Color(0xB3E8FC),
                bx + bw - 3, by + 34,
                icu ? new Color(0xE58EA8) : new Color(0x368FC9)));
        g.fillRoundRect(bx + 4, by + 19, bw - 9, 16, 4, 4);
        g.setColor(new Color(255, 255, 255, 167));
        g.drawLine(bx + 7, by + 21, bx + bw - 8, by + 21);
        g.drawLine(bx + 7, by + 23, bx + 7, by + 31);
        g.setColor(new Color(0x6E96AE));
        g.fillRoundRect(bx - 5, by + 17, 3, 17, 2, 2);
        g.fillRoundRect(bx + bw + 1, by + 17, 3, 17, 2, 2);
        g.setColor(new Color(0xD8ECF2));
        g.drawLine(bx - 4, by + 19, bx - 4, by + 32);
        g.drawLine(bx + bw + 2, by + 19, bx + bw + 2, by + 32);
        g.setColor(new Color(0x536B7E));
        g.fillRect(bx, by + bh - 1, 5, 5);
        g.fillRect(bx + bw - 5, by + bh - 1, 5, 5);
        g.setColor(new Color(0x2C4051));
        g.fillOval(bx - 1, by + bh + 3, 7, 4);
        g.fillOval(bx + bw - 6, by + bh + 3, 7, 4);

        if (icu) {
            paintBedsideMonitor(g, x + 78, y + 11, true);
            paintIVStand(g, x + 7, y + 4);
            paintCurtain(g, x + 130, y + 4, 14, 35);
        } else {
            paintBedsideMonitor(g, x + 3, y + 11, false);
            paintBedsideMonitor(g, x + 78, y + 11, false);
            paintCurtain(g, x + 130, y + 4, 14, 35);
        }
        paintCodePill(g, code, bx + bw / 2, y + 41, 9);
    }

    /** Equipo con pantalla, ECG, soporte rodante y bandeja lateral (no recurso del motor). */
    private static void paintBedsideMonitor(Graphics2D g, int x, int y, boolean icu) {
        g.setColor(new Color(18, 55, 68, 30));
        g.fillOval(x + 1, y + 23, 22, 5);
        g.setColor(new Color(0x456F80));
        g.fillRect(x + 10, y + 13, 3, 11);
        g.setColor(new Color(0x789AAA));
        g.fillRoundRect(x + 2, y + 23, 19, 3, 2, 2);
        g.setColor(new Color(0x314453));
        g.fillRoundRect(x + 2, y, 21, 17, 3, 3);
        g.setColor(new Color(0x113941));
        g.fillRoundRect(x + 4, y + 2, 17, 12, 2, 2);
        g.setColor(new Color(0x7AF7B2));
        g.setStroke(new BasicStroke(1.3f));
        g.drawPolyline(new int[]{x + 5, x + 8, x + 9, x + 11, x + 14, x + 16, x + 20},
                new int[]{y + 8, y + 8, y + 5, y + 11, y + 7, y + 8, y + 8}, 7);
        g.setColor(new Color(0xD9EBEF));
        g.fillRect(x + 7, y + 16, 10, 3);
        if (icu) {
            g.setColor(new Color(0xC5DFE6));
            g.fillRoundRect(x + 7, y + 20, 11, 4, 2, 2);
        }
    }

    private static void paintIVStand(Graphics2D g, int x, int y) {
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(new Color(0x466B85));
        g.drawLine(x + 8, y + 2, x + 8, y + 33);
        g.drawLine(x + 2, y + 34, x + 15, y + 34);
        g.drawLine(x + 8, y + 1, x + 15, y + 1);
        g.drawLine(x + 15, y + 1, x + 15, y + 5);
        g.setColor(new Color(0xDAF8FB));
        g.fillRoundRect(x + 11, y + 5, 9, 11, 3, 3);
        g.setColor(new Color(0x62A4C1));
        g.drawRoundRect(x + 11, y + 5, 9, 11, 3, 3);
        g.setColor(new Color(0x354F61));
        g.fillOval(x + 1, y + 33, 5, 3);
        g.fillOval(x + 12, y + 33, 5, 3);
    }

    /** Biombo de vidrio esmerilado con marco, reflejos, pliegues y base de apoyo. */
    private static void paintCurtain(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(19, 49, 72, 40));
        g.fillRoundRect(x + 4, y + 5, w, h + 2, 4, 4);
        g.setColor(new Color(0x245777));
        g.fillRoundRect(x, y, w, h, 3, 3);
        g.setPaint(new GradientPaint(x + 2, y + 2, new Color(0xD3F8FF),
                x + w - 2, y + h - 2, new Color(0x248ABE)));
        g.fillRoundRect(x + 2, y + 3, w - 4, h - 7, 2, 2);
        g.setColor(new Color(0x1B6E9D));
        g.fillRect(x, y + h - 6, w, 5);
        g.setColor(new Color(255, 255, 255, 178));
        for (int i = 4; i < w - 2; i += 5) {
            g.drawLine(x + i, y + 6, x + i, y + h - 9);
        }
        g.setColor(new Color(0x80E3F7));
        g.fillRect(x + 2, y + 4, 2, h - 12);
        g.setColor(new Color(0x42677C));
        g.fillRect(x - 3, y - 2, w + 6, 3);
        g.fillRect(x + w / 2 - 1, y + h, 3, 5);
        g.fillRoundRect(x + w / 2 - 6, y + h + 4, 12, 3, 2, 2);
    }

    /** Lámpara quirúrgica suspendida y despejada del tablero, dejando libre el espacio para el paciente. */
    private static void paintOverheadSurgicalLamp(Graphics2D g, int x, int y) {
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0x4A7488));
        // brazo suspendido desde la zona superior derecha del quirófano
        g.drawLine(x + 18, y - 16, x + 10, y - 4);
        g.drawLine(x + 10, y - 4, x - 2, y + 2);
        g.drawLine(x - 2, y + 2, x - 8, y + 12);

        g.setColor(new Color(0xA2C4CB));
        g.fillOval(x + 7, y - 8, 7, 7);
        g.fillOval(x - 3, y - 1, 7, 7);

        // dos cabezales compactos claramente suspendidos sobre la mesa, sin invadir su superficie
        paintLampHead(g, x - 20, y + 10, 18, 10);
        paintLampHead(g, x + 1, y + 8, 18, 10);
    }

    private static void paintLampHead(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0x2F6675));
        g.fillOval(x, y, w, h + 3);
        g.setColor(new Color(0xD7FBF4));
        g.fillOval(x + 2, y, w - 3, h);
        g.setColor(new Color(0x77C2C4));
        g.fillOval(x + 4, y + 2, w - 7, h - 4);
        g.setColor(new Color(0xF9FFF2));
        g.fillOval(x + (w / 2) - 4, y + 3, 8, 4);
    }

    /** Mesa quirúrgica con apoyacabezas, base elevadora, cantos y lámpara articulada superpuesta. */
    private static void paintOperatingTable(Graphics2D g, Position spot, String code) {
        int x = spot.col() * CELL_W, y = spot.row() * CELL_H;
        // La mesa utiliza visualmente ambas columnas del quirófano, pero su ancla (slot) sigue igual.
        int bx = x + 44, by = y + 11;
        g.setColor(new Color(23, 60, 63, 37));
        g.fillOval(bx - 7, by + 26, 82, 10);

        // Plataforma de elevación y ruedas en la zona sombreada, bajo el tablero.
        g.setColor(new Color(0x3D7383));
        g.fillRect(bx + 29, by + 17, 8, 18);
        g.setColor(new Color(0x6F9DA8));
        g.fillRoundRect(bx + 15, by + 32, 41, 5, 3, 3);
        g.setColor(new Color(0x365769));
        g.fillOval(bx + 17, by + 35, 7, 4);
        g.fillOval(bx + 47, by + 35, 7, 4);

        // Borde lateral oscuro y frente con volumen (no una barra plana).
        g.setColor(new Color(0x2B6979));
        Path2D side = new Path2D.Double();
        side.moveTo(bx - 2, by + 9);
        side.lineTo(bx + 65, by + 9);
        side.lineTo(bx + 69, by + 16);
        side.lineTo(bx + 64, by + 23);
        side.lineTo(bx, by + 23);
        side.closePath();
        g.fill(side);
        g.setPaint(new GradientPaint(bx, by, new Color(0xE4FFF7), bx + 59, by + 17, new Color(0x67CAC8)));
        g.fillRoundRect(bx, by + 6, 64, 13, 6, 6);
        g.setColor(new Color(0x74AFBA));
        g.drawRoundRect(bx, by + 6, 64, 13, 6, 6);
        g.setColor(new Color(255, 255, 255, 188));
        g.drawLine(bx + 5, by + 8, bx + 58, by + 8);
        g.setColor(new Color(0xC5F1E7));
        g.fillRoundRect(bx - 6, by + 7, 13, 11, 4, 4);
        g.setColor(new Color(0x4D97A0));
        g.drawRoundRect(bx - 6, by + 7, 13, 11, 4, 4);
        g.setColor(new Color(0xAEE8DA));
        g.fillRoundRect(bx + 54, by + 8, 14, 10, 4, 4);

        // La articulación y el foco se dibujan en ÚLTIMO LUGAR para verse suspendidos SOBRE la mesa.
        paintOverheadSurgicalLamp(g, bx + 50, by - 16);
        paintCodePill(g, code, bx + 32, y + 41, 9);
    }

    private static void paintFallbackCard(Graphics2D g, Position spot, String code) {
        int x = spot.col() * CELL_W, y = spot.row() * CELL_H;
        g.setColor(FRAME); g.fillRoundRect(x + 6, y + 7, 72, 33, 5, 5);
        g.setColor(ICE); g.fillRoundRect(x + 9, y + 9, 66, 28, 4, 4);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, CODE_SIZE));
        g.setColor(INK); g.drawString(code, x + 13, y + 27);
    }

    private static void paintCodePill(Graphics2D g, String code, int cx, int cy, int size) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        int width = Math.max(28, g.getFontMetrics().stringWidth(code) + 11);
        g.setColor(new Color(255, 255, 255, 229));
        g.fillRoundRect(cx - width / 2, cy - size + 1, width, size + 4, 5, 5);
        g.setColor(new Color(45, 88, 116, 100));
        g.drawRoundRect(cx - width / 2, cy - size + 1, width, size + 4, 5, 5);
        g.setColor(INK); g.drawString(code, cx - g.getFontMetrics().stringWidth(code) / 2, cy + 1);
    }

    /**
     * Cada par de sillas contiguas de la sala de espera se dibuja como un módulo de tres butacas; una silla
     * sin pareja, como una butaca sola. Las casillas de las sillas no cambian.
     */
    private static void paintWaitingGroups(Graphics2D g, List<Position> spots) {
        Map<Integer, List<Position>> rows = new TreeMap<>();
        for (Position spot : spots) {
            rows.computeIfAbsent(spot.row(), unused -> new ArrayList<>()).add(spot);
        }
        for (List<Position> row : rows.values()) {
            row.sort(Comparator.comparingInt(Position::col));
            for (int i = 0; i < row.size();) {
                Position first = row.get(i);
                boolean paired = i + 1 < row.size() && row.get(i + 1).col() == first.col() + 1;
                int width = paired ? 112 : 53;
                int x = first.col() * CELL_W + (paired ? (2 * CELL_W - width) / 2 : (CELL_W - width) / 2);
                int y = first.row() * CELL_H + 8;
                int count = paired ? 3 : 1;
                int seatW = (width - 10) / count;
                g.setColor(new Color(13, 43, 70, 30));
                g.fillOval(x + 1, y + 33, width, 8);
                g.setColor(new Color(0x284761));
                g.fillRoundRect(x, y + 4, width, 33, 7, 7);
                g.setColor(new Color(0x18394F));
                g.fillRect(x + 5, y + 34, 5, 6);
                g.fillRect(x + width - 10, y + 34, 5, 6);
                for (int k = 0; k < count; k++) {
                    int sx = x + 5 + k * seatW;
                    int cw = seatW - 3;
                    g.setColor(new Color(0x214C77));
                    g.fillRoundRect(sx, y + 4, cw, 26, 7, 7);
                    g.setPaint(new GradientPaint(sx, y + 6, new Color(0x79C5F0),
                            sx, y + 29, new Color(0x2776AE)));
                    g.fillRoundRect(sx + 2, y + 6, cw - 4, 21, 5, 5);
                    g.setColor(new Color(255, 255, 255, 106));
                    g.drawLine(sx + 5, y + 8, sx + cw - 6, y + 8);
                    g.setColor(new Color(0x15507F));
                    g.fillRoundRect(sx + 1, y + 26, cw - 2, 7, 3, 3);
                    g.setColor(new Color(0x5AA5D4));
                    g.drawLine(sx + 4, y + 28, sx + cw - 5, y + 28);
                }
            i += paired ? 2 : 1;
            }
        }
    }

    /** Cada cajón corresponde exactamente a un puesto real de la morgue. */
    private static void paintMorgueDrawer(Graphics2D g, Position spot) {
        int x = spot.col() * CELL_W + 8, y = spot.row() * CELL_H + 5;
        g.setColor(new Color(25, 46, 66, 38));
        g.fillRoundRect(x + 5, y + 8, 72, 35, 4, 4);
        g.setColor(new Color(0x40546A));
        g.fillRoundRect(x, y, 74, 33, 5, 5);
        g.setPaint(new GradientPaint(x + 2, y + 1, new Color(0xF6FAFF),
                x + 2, y + 30, new Color(0x90A7B9)));
        g.fillRoundRect(x + 3, y + 3, 68, 26, 4, 4);
        g.setColor(new Color(0x6D8192));
        g.drawRoundRect(x + 3, y + 3, 68, 26, 4, 4);
        g.setColor(WHITE);
        g.drawLine(x + 8, y + 6, x + 65, y + 6);
        g.setColor(new Color(0x607587));
        g.fillRoundRect(x + 28, y + 19, 18, 4, 2, 2);
        g.setColor(new Color(0xD9E5EF));
        g.drawLine(x + 30, y + 19, x + 42, y + 19);
    }

    /** Los puestos son muebles VACÍOS: solo StaffPainter dibuja personas en tiempo real. */
    private static void paintStaffTables(Graphics2D g, HospitalLayout layout) {
        Set<Position> tables = new TreeSet<>(Comparator.comparingInt(Position::row)
                .thenComparingInt(Position::col));
        for (String code : layout.getStaffCodes()) {
            tables.add(layout.staffSeatOf(code).orElseThrow().table());
        }
        for (Position table : tables) {
            int x = table.col() * CELL_W, y = table.row() * CELL_H;
            g.setColor(new Color(42, 80, 81, 25));
            g.fillOval(x + 5, y + 37, CELL_W - 10, 7);
            g.setColor(new Color(0x6B9DA2));
            g.fillRoundRect(x + 4, y + 7, CELL_W - 8, CELL_H - 13, 8, 8);
            g.setPaint(new GradientPaint(x + 5, y + 8, new Color(0xFCFFFA),
                    x + 5, y + 38, new Color(0xD8EAD7)));
            g.fillRoundRect(x + 7, y + 8, CELL_W - 14, CELL_H - 17, 6, 6);
            g.setColor(new Color(0xA3C3BE));
            g.drawLine(x + CELL_W/2, y + 11, x + CELL_W/2, y + 34);
            // Carpetas o tarjetas en reposo, sin dibujar siluetas humanas.
            for (int side = 0; side < StaffSeat.SEATS_PER_TABLE; side++) {
                int cx = (int) Math.round(table.col() * CELL_W + CELL_W / 2.0 +
                        (side == 0 ? -SEAT_OFFSET : SEAT_OFFSET));
                int cy = (int) Math.round(table.row() * CELL_H + CELL_H / 2.0);
                g.setColor(new Color(0xE5F4F8));
                g.fillRoundRect(cx - 9, cy - 10, 18, 12, 3, 3);
                g.setColor(new Color(0x95B6C4));
                g.drawRoundRect(cx - 9, cy - 10, 18, 12, 3, 3);
                g.setColor(new Color(0x7CA9B4));
                g.fillRect(cx - 5, cy - 7, 10, 2);
                g.setColor(new Color(0x7EA4A0));
                g.fillRoundRect(cx - 10, cy + 5, 20, 9, 4, 4);
            }
        }
    }

    private static void paintConsultDesks(Graphics2D g, HospitalLayout layout) {
        Map<String, Position> desks = new TreeMap<>();
        for (String code : layout.getStaffCodes()) {
            layout.consultDeskOf(code).ifPresent(desk -> desks.put(code, desk));
        }
        for (Map.Entry<String, Position> item : desks.entrySet()) {
            int x = item.getValue().col() * CELL_W, y = item.getValue().row() * CELL_H;
            g.setColor(new Color(14, 38, 69, 27));
            g.fillRoundRect(x + 6, y + 10, 77, 36, 6, 6);
            g.setColor(new Color(0x455E86));
            g.fillRoundRect(x + 2, y + 5, 80, 38, 6, 6);
            g.setPaint(new GradientPaint(x + 4, y + 6, new Color(0xFDFEFF),
                    x + 5, y + 38, new Color(0xC2D1EA)));
            g.fillRoundRect(x + 5, y + 7, 74, 33, 5, 5);
            // Tablero diagonal, silla y pantalla.
            g.setColor(new Color(0xA1B7CF));
            g.drawArc(x + 10, y + 21, 25, 23, 0, 185);
            g.setColor(new Color(0x7B9EB5));
            g.fillOval(x + 9, y + 31, 6, 5);
            g.setColor(new Color(0x405F79));
            g.fillRoundRect(x + 53, y + 9, 21, 16, 3, 3);
            g.setColor(new Color(0x8CD4E3));
            g.fillRoundRect(x + 55, y + 11, 17, 11, 2, 2);
            g.setColor(WHITE);
            g.drawLine(x + 58, y + 14, x + 67, y + 14);
            g.setColor(new Color(0x6E889B));
            g.fillRect(x + 61, y + 25, 5, 4);
            paintCodePill(g, item.getKey(), x + 40, y + 38, 9);
        }
    }

    private static void paintTriageStation(Graphics2D g, Position spot) {
        int x = spot.col() * CELL_W, y = spot.row() * CELL_H;
        // Mesa de exploración naranja: estructura y ruedas bajo el colchón, con caída de luz.
        g.setColor(new Color(0,0,0,35));
        g.fillOval(x + 9, y + 34, 72, 8);
        g.setColor(new Color(0x526F82));
        g.fillRoundRect(x + 8, y + 13, 72, 24, 6, 6);
        g.setPaint(new GradientPaint(x + 15,y + 15,new Color(0xFFDAB5),
                x + 15,y + 36,new Color(0xE78135)));
        g.fillRoundRect(x + 12, y + 16, 65, 17, 4, 4);
        g.setColor(new Color(0xBA6227));
        g.fillRoundRect(x + 14, y + 32, 63, 3, 2, 2);
        g.setColor(new Color(0xF9F6F0));
        g.fillRoundRect(x + 16, y + 12, 22, 11, 5, 5);
        g.setColor(new Color(0xC8D9E1));
        g.drawRoundRect(x + 16, y + 12, 22, 11, 5, 5);
        g.setColor(new Color(0x4D7186));
        g.fillRect(x + 16, y + 35, 5, 4); g.fillRect(x + 69, y + 35, 5, 4);
        g.setColor(new Color(0x253F51));
        g.fillOval(x + 15, y + 38, 9, 4); g.fillOval(x + 67, y + 38, 9, 4);
        // Consola de constantes: un equipo visible junto a la cabeza de la camilla.
        g.setColor(new Color(0x41647D));
        g.fillRoundRect(x + 47, y + 2, 29, 16, 3, 3);
        g.setColor(new Color(0xCEEFFF));
        g.fillRoundRect(x + 49, y + 4, 25, 12, 2, 2);
        g.setColor(new Color(0x4D9FB6));
        g.drawPolyline(new int[]{x+51,x+55,x+57,x+61,x+63,x+70},
                new int[]{y+11,y+11,y+7,y+14,y+10,y+10},6);
    }

    /** Enfermera decorativa original en el mismo lugar: no es una entidad del motor. */
    private static void paintTriageNurse(Graphics2D g, Position spot) {
        double cx = spot.col() * CELL_W + TRIAGE_NURSE_X;
        double cy = spot.row() * CELL_H + CELL_H / 2.0 - 2;
        g.setColor(new Color(0, 0, 0, 45)); g.fill(new Ellipse2D.Double(cx - 10, cy + 10, 20, 4));
        g.setStroke(new BasicStroke(Palette.OUTLINE_WIDTH));
        outlined(g, new RoundRectangle2D.Double(cx - 5, cy - 3, 10, 14, 4, 4), Palette.TRIAGE_NURSE);
        outlined(g, new Ellipse2D.Double(cx - 5, cy - 14, 10, 10), Palette.PATIENT_HEAD);
        outlined(g, new RoundRectangle2D.Double(cx - 4, cy - 16, 8, 3, 2, 2), Palette.TRIAGE_NURSE.darker());
    }

    private static void outlined(Graphics2D g, Shape shape, Color color) {
        g.setColor(color); g.fill(shape);
        g.setColor(Palette.PATIENT_OUTLINE); g.draw(shape);
    }

    private static void paintBlood(Graphics2D g, List<Position> spots) {
        BloodType[] types = BloodType.values();
        for (int i = 0; i < spots.size() && i < types.length; i++) {
            Position spot = spots.get(i);
            int x = spot.col() * CELL_W, y = spot.row() * CELL_H;
            g.setColor(new Color(0, 0, 0, 39)); g.fillRoundRect(x + 5, y + 35, 75, 5, 3, 3);
            g.setColor(new Color(0x841C2F)); g.fillRoundRect(x + 5, y + 7, 74, 29, 5, 5);
            g.setPaint(new GradientPaint(x, y + 9, new Color(0xF14D58), x, y + 34,
                    new Color(0xB51C30)));
            g.fillRoundRect(x + 8, y + 9, 68, 24, 4, 4);
            g.setColor(new Color(255,255,255,100)); g.drawLine(x + 13, y + 12, x + 70, y + 12);
            g.setColor(new Color(0xF5DEE2));
            g.fillRoundRect(x + 12, y + 16, 10, 11, 3, 3);
            g.setColor(new Color(0xB01D32)); g.fillRect(x + 16, y + 18, 3, 7);
            g.fillRect(x + 14, y + 20, 7, 3);
            String label = types[i].getLabel();
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(WHITE); g.drawString(label, x + 32, y + 27);
        }
    }

    /** La puerta continúa en el borde de sus casillas original; solo cambia su apariencia. */
    private static void paintDoor(Graphics2D g, Door door) {
        Position inside = door.inside(), outside = door.outside();
        boolean horizontal = inside.col() == outside.col();
        int x, y, w, h;
        if (horizontal) {
            int edge = Math.max(inside.row(), outside.row()) * CELL_H;
            x = inside.col() * CELL_W + CELL_W / 4;
            y = edge - DOOR_THICKNESS / 2;
            w = CELL_W / 2;
            h = DOOR_THICKNESS;
        } else {
            int edge = Math.max(inside.col(), outside.col()) * CELL_W;
            x = edge - DOOR_THICKNESS / 2;
            y = inside.row() * CELL_H + CELL_H / 4;
            w = DOOR_THICKNESS;
            h = CELL_H / 2;
        }
        g.setColor(new Color(0, 0, 0, 45));
        g.fillRoundRect(x - 3, y - 2, w + 6, h + 5, 2, 2);
        g.setColor(new Color(0x37657C)); g.fillRect(x - 1, y - 1, w + 2, h + 2);
        g.setColor(new Color(0xD5F6F5)); g.fillRect(x + 1, y + 1, w - 2, h - 2);
        g.setColor(new Color(0x77BFAF)); g.fillRect(x + 2, y + 2, w - 4, h - 4);
        g.setColor(new Color(0xEFFDFD));
        if (horizontal) {
            g.drawLine(x + w / 2, y + 1, x + w / 2, y + h - 2);
            g.drawLine(x + 4, y + 2, x + w - 4, y + 2);
        } else {
            g.drawLine(x + 1, y + h / 2, x + w - 2, y + h / 2);
        }
    }

    private static void paintEntrance(Graphics2D g, Position entrance) {
        int x = entrance.col() * CELL_W + 8, y = entrance.row() * CELL_H;
        int cy = y + CELL_H / 2;
        g.setColor(new Color(0x1D6E43));
        g.fillRoundRect(x - 2, cy - 11, 64, 22, 4, 4);
        g.setColor(new Color(0x35B97E));
        g.fillRoundRect(x, cy - 9, 60, 18, 3, 3);
        g.setColor(WHITE);
        g.fillPolygon(new int[]{x + 5, x + 5, x + 17},
                new int[]{cy - 5, cy + 5, cy}, 3);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        g.drawString("ENTRADA", x + 18, cy + 4);
    }

    private static void requireLayout(HospitalLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("El plano es obligatorio.");
        }
    }
}
