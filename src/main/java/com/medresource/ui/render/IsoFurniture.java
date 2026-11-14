package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.BloodType;
import com.medresource.model.Door;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.Sex;
import com.medresource.model.StaffSeat;
import com.medresource.model.ZoneType;
import com.medresource.ui.render.IsoTheme.Part;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Los muebles de la vista 2.5D, uno por cada lugar del plano: camas, camas de UCI con su monitor, mesas de
 * operaciones con su lámpara, sillas de la sala de espera, puestos de triage con su enfermera, neveras del banco de
 * sangre, mesas de la morgue, escritorios de los consultorios y mesas de la sala del personal.
 *
 * <p>Contra el muro del fondo de cada sala, en la fila del título, van además objetos según la sala: lavamanos,
 * vitrinas con insumos, contenedores de cortopunzantes, el carro de paro de la UCI, la máquina expendedora de la sala
 * de espera, estanterías, casilleros y la cafetera del personal. Los colores salen del {@link IsoTheme}.</p>
 *
 * <p>Cada mueble cabe dentro de su casilla de un metro y deja libre el lado izquierdo, donde se para el personal que
 * atiende al paciente (a la izquierda del centro de la casilla, como en la vista 2D). Las medidas de las camas, de la
 * mesa de operaciones y de la mesa de la morgue son públicas porque {@link IsoFigures} acuesta a los pacientes
 * encima.</p>
 */
public final class IsoFurniture {

    /** Borde izquierdo de una cama dentro de su casilla, en metros. */
    public static final double BED_LEFT = 0.5;
    /** Borde derecho de una cama dentro de su casilla. */
    public static final double BED_RIGHT = 0.94;
    /** Borde de la cabecera de una cama (al fondo) dentro de su casilla. */
    public static final double BED_TOP = 0.1;
    /** Borde de los pies de una cama dentro de su casilla. */
    public static final double BED_BOTTOM = 0.9;
    /** Altura del colchón de una cama, en metros. */
    public static final double MATTRESS_TOP = 0.38;
    /** Altura de la superficie de la mesa de operaciones. */
    public static final double OPERATING_TOP = 0.49;
    /** Altura de la superficie de la mesa de la morgue. */
    public static final double SLAB_TOP = 0.42;
    /** Casillas de la fila del título que quedan libres debajo del letrero de la sala. */
    public static final int SIGN_CELLS = IsoStructure.SIGN_SEGMENTS;
    /** Altura del asiento de las sillas. */
    public static final double SEAT_TOP = 0.24;
    /** Lado de las sillas, en metros. */
    public static final double CHAIR_SIZE = 0.4;

    private static final Color BASIN = new Color(0xCB, 0xD5, 0xE1);
    private static final Color CABINET_GLASS = new Color(0xBF, 0xE3, 0xF5, 150);
    private static final Color SHARPS = new Color(0xFA, 0xCC, 0x15);
    private static final Color ALARM_RED = new Color(0xDC, 0x26, 0x26);
    private static final Color WATER = new Color(0x7D, 0xD3, 0xFC, 200);
    private static final Color LEAVES = new Color(0x4D, 0x9A, 0x3F);
    private static final Color LOCKER = new Color(0x8D, 0xA2, 0xC0);
    private static final Color CUP = new Color(0xF8, 0xFA, 0xFC);
    private static final Color[] SUPPLIES = {new Color(0xF8, 0x71, 0x71), new Color(0x60, 0xA5, 0xFA),
        new Color(0xFB, 0xBF, 0x24), new Color(0x34, 0xD3, 0x99), new Color(0xF1, 0xF5, 0xF9)};

    private final HospitalLayout layout;
    private final IsoProjection projection;
    private final SceneRenderer flat;
    private final IsoTheme theme;

    /**
     * Prepara los muebles de un plano con el tema claro.
     *
     * @param layout     plano del hospital
     * @param projection proyección de la vista
     * @param flat       renderizador 2D, que da el punto de cada puesto de la sala del personal
     * @throws IllegalArgumentException si falta alguno
     */
    public IsoFurniture(HospitalLayout layout, IsoProjection projection, SceneRenderer flat) {
        this(layout, projection, flat, IsoTheme.LIGHT);
    }

    /**
     * Prepara los muebles de un plano.
     *
     * @param layout     plano del hospital
     * @param projection proyección de la vista
     * @param flat       renderizador 2D, que da el punto de cada puesto de la sala del personal
     * @param theme      tema de colores
     * @throws IllegalArgumentException si falta alguno
     */
    public IsoFurniture(HospitalLayout layout, IsoProjection projection, SceneRenderer flat, IsoTheme theme) {
        if (layout == null || projection == null || flat == null || theme == null) {
            throw new IllegalArgumentException("El plano, la proyección, el renderizador 2D y el tema son "
                    + "obligatorios.");
        }
        this.layout = layout;
        this.projection = projection;
        this.flat = flat;
        this.theme = theme;
    }

    private Color color(Part part) {
        return theme.color(part);
    }

    /**
     * Arma un elemento por mueble, para dibujarlos en su turno junto con los muros y las personas.
     *
     * @return los muebles del plano
     */
    public List<IsoItem> items() {
        List<IsoItem> items = new ArrayList<>();
        for (String code : new TreeSet<>(layout.getResourceCodes())) {
            Position spot = layout.positionOf(code).orElseThrow();
            ZoneType zone = layout.areaAt(spot).map(Area::zone).orElse(ZoneType.HOSPITALIZATION);
            switch (zone) {
                case ICU -> {
                    add(items, spot, graphics -> bed(graphics, spot));
                    add(items, spot.col() + 0.25, spot.row() + 0.22, graphics -> monitor(graphics, spot));
                }
                case OPERATING_ROOM -> {
                    add(items, spot, graphics -> operatingTable(graphics, spot));
                    add(items, spot.col() + 0.2, spot.row() + 0.2, graphics -> anesthesia(graphics, spot));
                    items.add(new IsoItem(spot.col() + spot.row() + 1.2, IsoItem.PERSON + 1,
                            graphics -> lamp(graphics, spot)));
                }
                default -> add(items, spot, graphics -> bed(graphics, spot));
            }
        }
        for (Position seat : layout.getWaitingSpots()) {
            add(items, seat, graphics -> waitingChair(graphics, seat));
        }
        for (Position station : layout.getTriageSpots()) {
            add(items, station.col() + 0.5, station.row() + 0.06, graphics -> triageNurse(graphics, station));
            add(items, station.col() + 0.5, station.row() + 0.21, graphics -> triageDesk(graphics, station));
        }
        List<Position> blood = layout.getBloodSpots();
        BloodType[] types = BloodType.values();
        for (int index = 0; index < blood.size(); index++) {
            Position spot = blood.get(index);
            String label = index < types.length ? types[index].getLabel() : "";
            add(items, spot, graphics -> fridge(graphics, spot, label));
        }
        for (Position spot : layout.getMorgueSpots()) {
            add(items, spot, graphics -> slab(graphics, spot));
        }
        TreeMap<String, Position> desks = new TreeMap<>();
        for (String code : layout.getStaffCodes()) {
            layout.consultDeskOf(code).ifPresent(desk -> desks.put(code, desk));
        }
        desks.values().forEach(desk -> add(items, desk, graphics -> consultDesk(graphics, desk)));
        addStaffTables(items);
        addProps(items);
        return items;
    }

    /**
     * Objetos de cada sala contra su muro del fondo, en la fila del título (que no tiene lugares): lavamanos,
     * vitrinas, carro de paro, máquina expendedora, estantes, casilleros… según la sala. Se salta las casillas que dan
     * a una puerta y las que están debajo del letrero de la sala.
     */
    private void addProps(List<IsoItem> items) {
        Set<Position> taken = takenCells();
        for (Area area : layout.getAreas()) {
            Prop[] props = propsOf(area.zone());
            if (props.length == 0) {
                continue;
            }
            int placed = 0;
            for (int col = area.col() + Math.min(SIGN_CELLS, area.cols() - 1); col < area.right(); col++) {
                Position cell = new Position(col, area.row());
                if (taken.contains(cell) || nearDoor(cell) || (col - area.col()) % 2 == 1 && area.cols() > 4) {
                    continue;
                }
                Prop prop = props[placed % props.length];
                double gx = col;
                double gy = area.row();
                add(items, gx + 0.5, gy + 0.35, graphics -> prop(graphics, prop, gx, gy));
                placed++;
            }
        }
    }

    /**
     * Cantidad de objetos contra los muros del fondo de las salas (lavamanos, vitrinas, casilleros…).
     *
     * @return cuántos hay en este plano
     */
    public int propCount() {
        List<IsoItem> props = new ArrayList<>();
        addProps(props);
        return props.size();
    }

    /** Casillas que ya tienen un lugar del plano (camas, sillas, puestos, mesas, la fila del triage, la entrada). */
    private Set<Position> takenCells() {
        Set<Position> taken = new HashSet<>();
        for (String code : layout.getResourceCodes()) {
            layout.positionOf(code).ifPresent(taken::add);
        }
        taken.addAll(layout.getWaitingSpots());
        taken.addAll(layout.getTriageSpots());
        taken.addAll(layout.getTriageQueueSpots());
        taken.addAll(layout.getBloodSpots());
        taken.addAll(layout.getMorgueSpots());
        taken.add(layout.getEntrance());
        for (String code : layout.getStaffCodes()) {
            layout.consultDeskOf(code).ifPresent(taken::add);
            layout.staffSeatOf(code).ifPresent(seat -> taken.add(seat.table()));
        }
        return taken;
    }

    /** Indica si una casilla queda junto a una puerta: ahí no se pone nada, para no tapar el paso. */
    private boolean nearDoor(Position cell) {
        for (Door door : layout.getDoors()) {
            for (Position side : new Position[] {door.inside(), door.outside()}) {
                if (Math.abs(side.col() - cell.col()) + Math.abs(side.row() - cell.row()) <= 1) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Objetos que lleva cada tipo de sala, en el orden en que se reparten. */
    private static Prop[] propsOf(ZoneType zone) {
        return switch (zone) {
            case TRIAGE -> new Prop[] {Prop.SINK, Prop.SHARPS_BIN, Prop.GLASS_CABINET};
            case WAITING_ROOM -> new Prop[] {Prop.VENDING, Prop.WATER_COOLER, Prop.PLANT};
            case HOSPITALIZATION -> new Prop[] {Prop.GLASS_CABINET, Prop.SINK, Prop.PLANT, Prop.SHARPS_BIN};
            case ICU -> new Prop[] {Prop.CRASH_CART, Prop.SINK, Prop.GLASS_CABINET};
            case OPERATING_ROOM -> new Prop[] {Prop.INSTRUMENT_TABLE, Prop.GLASS_CABINET};
            case BLOOD_BANK, MORGUE -> new Prop[] {Prop.SHELF, Prop.SINK};
            case DISCHARGE -> new Prop[] {Prop.PLANT, Prop.WATER_COOLER};
            case CONSULTATION -> new Prop[] {Prop.GLASS_CABINET, Prop.SINK, Prop.PLANT};
            case STAFF_ROOM -> new Prop[] {Prop.LOCKERS, Prop.COFFEE, Prop.PLANT, Prop.WATER_COOLER};
            case CORRIDOR -> new Prop[0];
        };
    }

    /** Objetos que se ponen contra el muro del fondo de una sala. */
    enum Prop {
        /** Lavamanos con su mueble. */
        SINK,
        /** Vitrina con insumos. */
        GLASS_CABINET,
        /** Contenedor amarillo de cortopunzantes. */
        SHARPS_BIN,
        /** Carro de paro rojo con el desfibrilador. */
        CRASH_CART,
        /** Máquina expendedora. */
        VENDING,
        /** Dispensador de agua. */
        WATER_COOLER,
        /** Matera con una planta. */
        PLANT,
        /** Estantería metálica con cajas. */
        SHELF,
        /** Mesa de instrumentos del quirófano. */
        INSTRUMENT_TABLE,
        /** Casilleros del personal. */
        LOCKERS,
        /** Mueble con la cafetera. */
        COFFEE
    }

    private void prop(Graphics2D graphics, Prop prop, double col, double row) {
        double x = col + 0.15;
        double y = row + 0.14;
        switch (prop) {
            case SINK -> {
                IsoBrush.box(graphics, projection, x, y, 0, 0.7, 0.4, 0.6, color(Part.COUNTER_TOP),
                        color(Part.CABINET), IsoPalette.darker(color(Part.CABINET), 0.8));
                IsoBrush.box(graphics, projection, x + 0.18, y + 0.08, 0.6, 0.34, 0.24, 0.02, BASIN, BASIN,
                        IsoPalette.darker(BASIN, 0.8));
                IsoBrush.box(graphics, projection, x + 0.33, y + 0.03, 0.6, 0.04, 0.05, 0.14, color(Part.METAL));
            }
            case GLASS_CABINET -> {
                IsoBrush.box(graphics, projection, x, y, 0, 0.7, 0.34, 0.95, color(Part.COUNTER_TOP),
                        color(Part.CABINET), IsoPalette.darker(color(Part.CABINET), 0.8));
                double face = y + 0.344;
                graphics.setColor(CABINET_GLASS);
                graphics.fill(IsoBrush.polygon(projection, new double[][] {{x + 0.05, face, 0.08},
                        {x + 0.65, face, 0.08}, {x + 0.65, face, 0.88}, {x + 0.05, face, 0.88}}));
                for (int shelf = 0; shelf < 3; shelf++) {
                    double z = 0.14 + shelf * 0.25;
                    for (int box = 0; box < 4; box++) {
                        double left = x + 0.08 + box * 0.14;
                        graphics.setColor(SUPPLIES[(shelf + box) % SUPPLIES.length]);
                        graphics.fill(IsoBrush.polygon(projection, new double[][] {{left, face, z},
                                {left + 0.1, face, z}, {left + 0.1, face, z + 0.12 + 0.03 * (box % 2)},
                                {left, face, z + 0.12 + 0.03 * (box % 2)}}));
                    }
                }
            }
            case SHARPS_BIN -> {
                IsoBrush.box(graphics, projection, x + 0.2, y + 0.04, 0, 0.26, 0.22, 0.34, SHARPS);
                IsoBrush.box(graphics, projection, x + 0.22, y + 0.06, 0.34, 0.22, 0.18, 0.04, ALARM_RED);
            }
            case CRASH_CART -> {
                IsoBrush.box(graphics, projection, x + 0.1, y, 0.06, 0.5, 0.4, 0.72, ALARM_RED);
                for (int drawer = 0; drawer < 4; drawer++) {
                    double z = 0.14 + drawer * 0.15;
                    graphics.setColor(IsoPalette.darker(ALARM_RED, 0.75));
                    graphics.fill(IsoBrush.polygon(projection, new double[][] {{x + 0.14, y + 0.404, z},
                            {x + 0.56, y + 0.404, z}, {x + 0.56, y + 0.404, z + 0.11}, {x + 0.14, y + 0.404, z + 0.11}}));
                }
                IsoBrush.box(graphics, projection, x + 0.18, y + 0.08, 0.78, 0.3, 0.22, 0.1, color(Part.DEVICE));
            }
            case VENDING -> {
                IsoBrush.box(graphics, projection, x, y, 0, 0.6, 0.45, 1.1, ALARM_RED);
                double face = y + 0.454;
                graphics.setColor(color(Part.DEVICE));
                graphics.fill(IsoBrush.polygon(projection, new double[][] {{x + 0.05, face, 0.3},
                        {x + 0.4, face, 0.3}, {x + 0.4, face, 1.02}, {x + 0.05, face, 1.02}}));
                for (int r = 0; r < 4; r++) {
                    for (int c = 0; c < 3; c++) {
                        double left = x + 0.08 + c * 0.11;
                        double z = 0.38 + r * 0.16;
                        graphics.setColor(SUPPLIES[(r + c) % SUPPLIES.length]);
                        graphics.fill(IsoBrush.polygon(projection, new double[][] {{left, face, z},
                                {left + 0.07, face, z}, {left + 0.07, face, z + 0.1}, {left, face, z + 0.1}}));
                    }
                }
            }
            case WATER_COOLER -> {
                IsoBrush.box(graphics, projection, x + 0.2, y + 0.05, 0, 0.3, 0.3, 0.8, color(Part.CABINET));
                IsoBrush.box(graphics, projection, x + 0.25, y + 0.1, 0.8, 0.2, 0.2, 0.3, WATER);
            }
            case PLANT -> {
                IsoBrush.box(graphics, projection, x + 0.21, y + 0.06, 0, 0.28, 0.28, 0.26, color(Part.POT));
                Point2D leaves = projection.at(x + 0.35, y + 0.2, 0.5);
                graphics.setColor(IsoPalette.darker(LEAVES, 0.8));
                graphics.fill(new Ellipse2D.Double(leaves.getX() - 11, leaves.getY() - 8, 22, 17));
                graphics.setColor(LEAVES);
                graphics.fill(new Ellipse2D.Double(leaves.getX() - 8, leaves.getY() - 11, 15, 13));
            }
            case SHELF -> {
                for (double[] post : new double[][] {{x, y}, {x + 0.66, y}, {x, y + 0.32}, {x + 0.66, y + 0.32}}) {
                    IsoBrush.box(graphics, projection, post[0], post[1], 0, 0.04, 0.04, 1.0, color(Part.METAL));
                }
                for (int shelf = 0; shelf < 4; shelf++) {
                    double z = 0.1 + shelf * 0.28;
                    IsoBrush.box(graphics, projection, x, y, z, 0.7, 0.36, 0.025, color(Part.STEEL));
                    for (int box = 0; box < 3; box++) {
                        IsoBrush.box(graphics, projection, x + 0.05 + box * 0.21, y + 0.06, z + 0.025, 0.17, 0.24,
                                0.1 + 0.03 * ((shelf + box) % 2), SUPPLIES[(shelf * 2 + box) % SUPPLIES.length]);
                    }
                }
            }
            case INSTRUMENT_TABLE -> {
                IsoBrush.box(graphics, projection, x + 0.3, y + 0.15, 0, 0.06, 0.06, 0.7, color(Part.METAL));
                IsoBrush.box(graphics, projection, x + 0.1, y, 0.7, 0.5, 0.36, 0.04, color(Part.STEEL));
                IsoBrush.box(graphics, projection, x + 0.16, y + 0.06, 0.74, 0.38, 0.24, 0.015,
                        IsoPalette.SURGICAL_PAD);
            }
            case LOCKERS -> {
                IsoBrush.box(graphics, projection, x, y, 0, 0.7, 0.4, 1.0, LOCKER);
                double face = y + 0.404;
                graphics.setStroke(new BasicStroke(0.8f));
                graphics.setColor(IsoPalette.darker(LOCKER, 0.65));
                for (int door = 0; door < 3; door++) {
                    double left = x + 0.04 + door * 0.22;
                    graphics.draw(IsoBrush.polygon(projection, new double[][] {{left, face, 0.06},
                            {left + 0.2, face, 0.06}, {left + 0.2, face, 0.94}, {left, face, 0.94}}));
                }
            }
            case COFFEE -> {
                IsoBrush.box(graphics, projection, x, y, 0, 0.7, 0.42, 0.55, color(Part.DESK_TOP), color(Part.WOOD),
                        IsoPalette.darker(color(Part.WOOD), 0.8));
                IsoBrush.box(graphics, projection, x + 0.1, y + 0.06, 0.55, 0.22, 0.24, 0.3, color(Part.DEVICE));
                IsoBrush.box(graphics, projection, x + 0.42, y + 0.1, 0.55, 0.1, 0.1, 0.1, CUP);
            }
        }
    }

    private void add(List<IsoItem> items, Position spot, Consumer<Graphics2D> painter) {
        add(items, spot.col() + 0.5, spot.row() + 0.5, painter);
    }

    private void add(List<IsoItem> items, double gx, double gy, Consumer<Graphics2D> painter) {
        items.add(new IsoItem(gx + gy, IsoItem.FURNITURE, painter));
    }

    /** Mesas de la sala del personal con una silla en cada puesto que tiene dueño. */
    private void addStaffTables(List<IsoItem> items) {
        Set<Position> tables = new TreeSet<>(Comparator.comparingInt(Position::row).thenComparingInt(Position::col));
        List<StaffSeat> seats = new ArrayList<>();
        for (String code : layout.getStaffCodes()) {
            StaffSeat seat = layout.staffSeatOf(code).orElseThrow();
            tables.add(seat.table());
            seats.add(seat);
        }
        for (Position table : tables) {
            add(items, table, graphics -> IsoBrush.box(graphics, projection, table.col() + 0.36, table.row() + 0.28,
                    0, 0.28, 0.44, 0.4, color(Part.DESK_TOP), color(Part.DESK),
                    IsoPalette.darker(color(Part.DESK), 0.76)));
        }
        for (StaffSeat seat : seats) {
            Point2D point = flat.seatOf(seat);
            double gx = projection.gridX(point.getX());
            double gy = projection.gridY(point.getY());
            boolean backOnLeft = seat.side() == 0;
            add(items, gx, gy, graphics -> staffChair(graphics, gx, gy, backOnLeft));
        }
    }

    // ---------- muebles ----------

    private void bed(Graphics2D graphics, Position spot) {
        double x = spot.col() + BED_LEFT;
        double y = spot.row() + BED_TOP;
        double w = BED_RIGHT - BED_LEFT;
        double d = BED_BOTTOM - BED_TOP;
        IsoBrush.box(graphics, projection, x, y, 0, w, 0.06, 0.6, IsoPalette.darker(color(Part.BED_FRAME), 0.8));
        IsoBrush.box(graphics, projection, x, y + 0.06, 0, w, d - 0.06, 0.3, color(Part.BED_FRAME));
        IsoBrush.box(graphics, projection, x + 0.03, y + 0.08, 0.3, w - 0.06, d - 0.12, 0.08, color(Part.MATTRESS));
        IsoBrush.box(graphics, projection, x + 0.07, y + 0.11, MATTRESS_TOP, w - 0.14, 0.2, 0.06, color(Part.PILLOW));
        IsoBrush.box(graphics, projection, x + 0.01, y + d - 0.05, 0, w - 0.02, 0.05, 0.42,
                IsoPalette.darker(color(Part.BED_FRAME), 0.85));
    }

    private void monitor(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.25;
        double y = spot.row() + 0.22;
        IsoBrush.box(graphics, projection, x - 0.09, y - 0.09, 0, 0.18, 0.18, 0.03, color(Part.METAL));
        IsoBrush.box(graphics, projection, x - 0.02, y - 0.02, 0.03, 0.04, 0.04, 0.62, color(Part.METAL));
        screen(graphics, x, y, 0.66);
    }

    /** Pantalla con el trazo verde de los latidos, mirando hacia quien mira. */
    private void screen(Graphics2D graphics, double x, double y, double z) {
        IsoBrush.box(graphics, projection, x - 0.15, y - 0.05, z, 0.3, 0.1, 0.22, color(Part.DEVICE));
        double face = y + 0.051;
        graphics.setColor(new Color(0x06, 0x23, 0x1C));
        graphics.fill(IsoBrush.polygon(projection, new double[][] {{x - 0.13, face, z + 0.03},
                {x + 0.13, face, z + 0.03}, {x + 0.13, face, z + 0.19}, {x - 0.13, face, z + 0.19}}));
        Path2D.Double beat = new Path2D.Double();
        double[][] points = {{-0.11, 0.11}, {-0.04, 0.11}, {-0.02, 0.17}, {0.01, 0.05}, {0.04, 0.11}, {0.11, 0.11}};
        for (int index = 0; index < points.length; index++) {
            Point2D at = projection.at(x + points[index][0], face, z + points[index][1]);
            if (index == 0) {
                beat.moveTo(at.getX(), at.getY());
            } else {
                beat.lineTo(at.getX(), at.getY());
            }
        }
        graphics.setStroke(new BasicStroke(1f));
        graphics.setColor(IsoPalette.MONITOR_LINE);
        graphics.draw(beat);
    }

    private void operatingTable(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.52;
        double y = spot.row() + 0.12;
        IsoBrush.box(graphics, projection, x + 0.12, y + 0.3, 0, 0.16, 0.16, 0.41, color(Part.METAL));
        IsoBrush.box(graphics, projection, x, y, 0.41, 0.4, 0.76, 0.05, color(Part.STEEL));
        IsoBrush.box(graphics, projection, x + 0.02, y + 0.02, 0.46, 0.36, 0.72, 0.03, IsoPalette.SURGICAL_PAD);
    }

    private void anesthesia(Graphics2D graphics, Position spot) {
        IsoBrush.box(graphics, projection, spot.col() + 0.08, spot.row() + 0.08, 0, 0.24, 0.2, 0.55, color(Part.DEVICE));
        screen(graphics, spot.col() + 0.2, spot.row() + 0.18, 0.55);
    }

    /** Lámpara colgada sobre la mesa, con un halo de luz en el piso. */
    private void lamp(Graphics2D graphics, Position spot) {
        double gx = spot.col() + 0.72;
        double gy = spot.row() + 0.5;
        Point2D floor = projection.at(gx, gy, 0.5);
        Point2D hang = projection.at(gx, gy, 1.45);
        Point2D center = projection.at(gx, gy, 1.15);
        double rx = IsoBrush.screenRadius(0.3);
        double ry = rx / 2;
        graphics.setColor(new Color(0xFF, 0xF7, 0xD6, 26));
        graphics.fill(new Ellipse2D.Double(floor.getX() - rx * 1.3, floor.getY() - ry * 1.3, rx * 2.6, ry * 2.6));
        graphics.setStroke(new BasicStroke(1.8f));
        graphics.setColor(color(Part.METAL));
        graphics.draw(new Line2D.Double(hang, center));
        graphics.setColor(IsoPalette.darker(color(Part.METAL), 0.6));
        graphics.fill(new Ellipse2D.Double(center.getX() - rx, center.getY() - ry + 2.5, 2 * rx, 2 * ry));
        graphics.setColor(color(Part.METAL));
        graphics.fill(new Ellipse2D.Double(center.getX() - rx, center.getY() - ry, 2 * rx, 2 * ry));
        graphics.setColor(IsoPalette.LAMP_LIGHT);
        graphics.fill(new Ellipse2D.Double(center.getX() - rx * 0.6, center.getY() - ry * 0.6 + 1.5, rx * 1.2,
                ry * 1.2));
    }

    private void waitingChair(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.5 - CHAIR_SIZE / 2;
        double y = spot.row() + 0.5 - CHAIR_SIZE / 2;
        IsoBrush.box(graphics, projection, x, y, 0, CHAIR_SIZE, CHAIR_SIZE, SEAT_TOP, color(Part.CHAIR_TOP),
                color(Part.CHAIR), IsoPalette.darker(color(Part.CHAIR), 0.76));
        IsoBrush.box(graphics, projection, x, y, SEAT_TOP, CHAIR_SIZE, 0.07, 0.26, color(Part.CHAIR_TOP),
                color(Part.CHAIR), IsoPalette.darker(color(Part.CHAIR), 0.76));
    }

    private void staffChair(Graphics2D graphics, double gx, double gy, boolean backOnLeft) {
        double size = 0.3;
        double x = gx - size / 2;
        double y = gy - size / 2;
        IsoBrush.box(graphics, projection, x, y, 0, size, size, SEAT_TOP, color(Part.CHAIR_TOP), color(Part.CHAIR),
                IsoPalette.darker(color(Part.CHAIR), 0.76));
        double backX = backOnLeft ? x : x + size - 0.06;
        IsoBrush.box(graphics, projection, backX, y, SEAT_TOP, 0.06, size, 0.24, color(Part.CHAIR_TOP),
                color(Part.CHAIR), IsoPalette.darker(color(Part.CHAIR), 0.76));
    }

    private void triageDesk(Graphics2D graphics, Position spot) {
        IsoBrush.box(graphics, projection, spot.col() + 0.18, spot.row() + 0.14, 0, 0.64, 0.16, 0.42,
                IsoPalette.lighter(IsoPalette.accent(ZoneType.TRIAGE), 0.15), color(Part.DESK),
                IsoPalette.darker(color(Part.DESK), 0.76));
        IsoBrush.box(graphics, projection, spot.col() + 0.4, spot.row() + 0.16, 0.42, 0.2, 0.04, 0.14,
                color(Part.DEVICE));
    }

    /** Enfermera de triage de pie detrás de su puesto: no es personal del motor, siempre está ahí. */
    private void triageNurse(Graphics2D graphics, Position spot) {
        IsoBrush.shadow(graphics, projection, spot.col() + 0.5, spot.row() + 0.06, 0.18);
        IsoBrush.person(graphics, projection, spot.col() + 0.5, spot.row() + 0.06, 0, 0,
                new IsoBrush.Person(Palette.TRIAGE_NURSE, IsoPalette.cap(Palette.TRIAGE_NURSE), Sex.FEMALE, 1.0,
                        false));
    }

    /** Nevera con puerta de vidrio y bolsas de sangre a la vista, y su tipo de sangre encima. */
    private void fridge(Graphics2D graphics, Position spot, String label) {
        double x = spot.col() + 0.2;
        double y = spot.row() + 0.22;
        double w = 0.6;
        double d = 0.56;
        double h = 0.9;
        IsoBrush.box(graphics, projection, x, y, 0, w, d, h, color(Part.FRIDGE));
        double face = y + d + 0.004;
        graphics.setColor(IsoPalette.FRIDGE_GLASS);
        graphics.fill(IsoBrush.polygon(projection, new double[][] {{x + 0.05, face, 0.06}, {x + w - 0.05, face, 0.06},
                {x + w - 0.05, face, h - 0.06}, {x + 0.05, face, h - 0.06}}));
        graphics.setColor(IsoPalette.BLOOD_BAG);
        for (int shelf = 0; shelf < 3; shelf++) {
            double z = 0.12 + shelf * 0.24;
            for (int bag = 0; bag < 3; bag++) {
                double left = x + 0.09 + bag * 0.155;
                graphics.fill(IsoBrush.polygon(projection, new double[][] {{left, face, z}, {left + 0.12, face, z},
                        {left + 0.12, face, z + 0.15}, {left, face, z + 0.15}}));
            }
        }
        Point2D top = projection.at(x + w / 2, y + d / 2, h);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(IsoPalette.BLOOD_BAG);
        graphics.drawString(label, (float) (top.getX() - metrics.stringWidth(label) / 2.0),
                (float) (top.getY() + metrics.getAscent() / 2.0 - 1));
    }

    private void slab(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.22;
        double y = spot.row() + 0.14;
        IsoBrush.box(graphics, projection, x + 0.18, y + 0.25, 0, 0.2, 0.22, 0.36, color(Part.METAL));
        IsoBrush.box(graphics, projection, x, y, 0.36, 0.56, 0.72, 0.06, color(Part.STEEL));
    }

    private void consultDesk(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.6;
        double y = spot.row() + 0.2;
        IsoBrush.box(graphics, projection, x, y, 0, 0.34, 0.56, 0.4, color(Part.DESK_TOP), color(Part.DESK),
                IsoPalette.darker(color(Part.DESK), 0.76));
        IsoBrush.box(graphics, projection, x + 0.15, y + 0.12, 0.4, 0.04, 0.3, 0.2, color(Part.DEVICE));
    }

    /**
     * Dice qué mueble hay en una casilla, para saber si un paciente que está quieto en su centro va acostado o
     * sentado.
     *
     * @param spot casilla
     * @return el tipo de mueble, o vacío si no hay ninguno donde acostarse o sentarse
     */
    public Optional<Kind> kindAt(Position spot) {
        Optional<String> resource = layout.resourceAt(spot);
        if (resource.isPresent() && layout.positionOf(resource.get()).filter(spot::equals).isPresent()) {
            ZoneType zone = layout.areaAt(spot).map(Area::zone).orElse(ZoneType.HOSPITALIZATION);
            if (zone == ZoneType.OPERATING_ROOM) {
                return Optional.of(Kind.OPERATING_TABLE);
            }
            if (zone == ZoneType.HOSPITALIZATION || zone == ZoneType.ICU) {
                return Optional.of(Kind.BED);
            }
        }
        if (layout.getMorgueSpots().contains(spot)) {
            return Optional.of(Kind.SLAB);
        }
        if (layout.getWaitingSpots().contains(spot)) {
            return Optional.of(Kind.CHAIR);
        }
        return Optional.empty();
    }

    /** Muebles donde un paciente se acuesta o se sienta. */
    public enum Kind {
        /** Cama de hospitalización o de UCI. */
        BED,
        /** Mesa de operaciones. */
        OPERATING_TABLE,
        /** Mesa de la morgue. */
        SLAB,
        /** Silla de la sala de espera. */
        CHAIR
    }
}
