package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.BloodType;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.ZoneType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Dibuja el plano como un tablero de salas: cada área es una caja con su título y cada lugar es una
 * tarjeta de {@value #CELL_W} × {@value #CELL_H} píxeles.
 *
 * <p>Las camas, los quirófanos, los médicos y los enfermeros se dibujan como tarjetas con su código; los
 * tipos de sangre del banco de sangre, como tarjetas rojas con su etiqueta; los lugares de la morgue, los
 * puestos de triage y las sillas de la sala de espera, como tarjetas más pequeñas sin texto. La entrada
 * lleva una flecha verde y el rótulo "ENTRADA". El color de cada tarjeta depende del área donde está, no de
 * su código.</p>
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
        paintResources(graphics, layout);
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
            Color color = switch (layout.areaAt(spot).orElseThrow().zone()) {
                case OPERATING_ROOM -> Palette.OPERATING_TABLE;
                case STAFF_ROOM -> Palette.STAFF_SEAT;
                default -> Palette.CARD;
            };
            card(graphics, spot, CARD_INSET, color);
            graphics.setColor(color == Palette.STAFF_SEAT ? Palette.CARD : Palette.LABEL);
            graphics.drawString(resource.getKey(), spot.col() * CELL_W + CARD_INSET + 5,
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