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
    /** Columna del monitor de signos vitales dentro de la casilla de su cama de UCI: el centro de la cama. */
    public static final double MONITOR_X = (BED_LEFT + BED_RIGHT) / 2;
    /** Fila del monitor dentro de la casilla de su cama de UCI: justo detrás de la cabecera. */
    public static final double MONITOR_Y = -0.08;
    /** Casillas entre un objeto y el siguiente a lo largo de un pasillo. */
    public static final int CORRIDOR_PROP_STEP = 3;
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
    private static final Color BIN_GREEN = new Color(0x16, 0xA3, 0x4A);
    private static final Color EXIT_GREEN = new Color(0x16, 0xA3, 0x4A);
    private static final Color PARTITION = new Color(0xBF, 0xE3, 0xF5, 110);
    private static final Color WHEELCHAIR_SEAT = new Color(0x25, 0x63, 0xEB);
    private static final Color CART_BLUE = new Color(0x3B, 0x82, 0xF6);
    private static final Color SCREEN = new Color(0x1E, 0x5A, 0x8C);
    private static final Color KEYBOARD = new Color(0xD1, 0xD5, 0xDB);
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

    /** Tema de colores de los muebles. */
    public IsoTheme getTheme() {
        return theme;
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
                    add(items, spot.col() + MONITOR_X, spot.row() + MONITOR_Y, graphics -> monitor(graphics, spot));
                    addWardExtras(items, spot);
                }
                case OPERATING_ROOM -> {
                    add(items, spot, graphics -> operatingTable(graphics, spot));
                    add(items, spot.col() + 0.2, spot.row() + 0.2, graphics -> anesthesia(graphics, spot));
                    items.add(new IsoItem(Math.max(projection.depth(spot.col() + 0.6, spot.row() + 0.6),
                            projection.depth(spot.col() + 0.5, spot.row() + 0.5) + 0.2), IsoItem.PERSON + 1,
                            graphics -> lamp(graphics, spot)));
                }
                default -> {
                    add(items, spot, graphics -> bed(graphics, spot));
                    if (zone == ZoneType.HOSPITALIZATION) {
                        addWardExtras(items, spot);
                    }
                }
            }
        }
        for (Position seat : layout.getWaitingSpots()) {
            add(items, seat, graphics -> waitingChair(graphics, seat));
        }
        for (Position station : layout.getTriageSpots()) {
            add(items, station.col() + 0.5, station.row() + 0.02, graphics -> triageChair(graphics, station));
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
        addDischarge(items);
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
            if (props.length == 0 || area.zone() == ZoneType.CORRIDOR && area.cols() <= area.rows()) {
                continue;
            }
            if (area.zone() == ZoneType.CORRIDOR) {
                addCorridorProps(items, area, props);
                continue;
            }
            int first = area.col() + Math.min(SIGN_CELLS, area.cols() - 1);
            int step = area.cols() > 4 ? 2 : 1;
            int placed = 0;
            for (int col = first; col < area.right(); col++) {
                Position cell = new Position(col, area.row());
                if (taken.contains(cell) || nearDoor(cell) || (col - first) % step != 0) {
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
     * Objetos de un pasillo a lo largo de sus dos muros, alternando: uno contra el muro de arriba y el siguiente
     * contra el de abajo, dejando libre el centro para caminar y las casillas junto a las puertas.
     */
    private void addCorridorProps(List<IsoItem> items, Area corridor, Prop[] props) {
        int placed = 0;
        for (int col = corridor.col() + 2; col < corridor.right() - 1; col += CORRIDOR_PROP_STEP) {
            boolean top = placed % 2 == 0;
            Position cell = new Position(col, top ? corridor.row() : corridor.bottom() - 1);
            if (nearDoor(cell)) {
                continue;
            }
            Prop prop = props[placed % props.length];
            double gx = col;
            double gy = top ? corridor.row() : corridor.bottom() - 1 + 0.42;
            add(items, gx + 0.5, gy + 0.35, graphics -> prop(graphics, prop, gx, gy));
            placed++;
        }
    }
    /**
     * Junto a cada cama de hospitalización o de UCI que tiene la casilla de la derecha libre: una mesa de noche, un atril
     * con suero y una mampara de vidrio que la separa de la cama siguiente.
     */
    private void addWardExtras(List<IsoItem> items, Position bed) {
        Position side = new Position(bed.col() + 1, bed.row());
        Optional<Area> area = layout.areaAt(side);
        if (area.isEmpty() || area.get().zone() != layout.areaAt(bed).map(Area::zone).orElse(null)
                || layout.resourceAt(side).isPresent()) {
            return;
        }
        double x = side.col();
        double y = side.row();
        add(items, x + 0.17, y + 0.28, graphics -> {
            IsoBrush.box(graphics, projection, x + 0.04, y + 0.15, 0, 0.26, 0.26, 0.45, color(Part.COUNTER_TOP),
                    color(Part.CABINET), IsoPalette.darker(color(Part.CABINET), 0.8));
            IsoBrush.box(graphics, projection, x + 0.1, y + 0.2, 0.45, 0.1, 0.08, 0.06, CUP);
        });
        add(items, x + 0.2, y + 0.7, graphics -> {
            IsoBrush.box(graphics, projection, x + 0.12, y + 0.62, 0, 0.16, 0.16, 0.02, color(Part.METAL));
            IsoBrush.box(graphics, projection, x + 0.19, y + 0.69, 0.02, 0.02, 0.02, 0.95, color(Part.METAL));
            IsoBrush.box(graphics, projection, x + 0.15, y + 0.67, 0.78, 0.1, 0.05, 0.13, WATER);
        });
        add(items, x + 0.62, y + 0.5, graphics -> IsoBrush.pane(graphics, projection, x + 0.6, y + 0.06, 0, 0.04,
                0.88, 0.85, PARTITION, color(Part.METAL)));
    }
    /**
     * Zona de alta: el mostrador de salida, una banca, el letrero verde «SALIDA» y el vigilante de pie junto a la
     * puerta de la calle (en la fachada derecha, a media altura de la zona). El vigilante no es personal del motor.
     */
    private void addDischarge(List<IsoItem> items) {
        for (Area area : layout.areasOf(ZoneType.DISCHARGE)) {
            if (area.rows() < 3 || area.cols() < 2) {
                continue;
            }
            int exitRow = area.row() + area.rows() / 2;
            double right = area.right();
            double gx = right - 0.42;
            double gy = exitRow - 0.25;
            add(items, gx, gy, graphics -> {
                IsoBrush.shadow(graphics, projection, gx, gy, 0.18);
                IsoBrush.person(graphics, projection, gx, gy, 0, 0, new IsoBrush.Person(Palette.GUARD_UNIFORM,
                        IsoPalette.cap(Palette.GUARD_UNIFORM), Sex.MALE, 1.0, false));
            });
            double cx = right - 1.9;
            double cy = exitRow - 0.7;
            add(items, cx + 0.4, cy + 0.2, graphics -> {
                IsoBrush.box(graphics, projection, cx, cy, 0, 0.9, 0.32, 0.55, color(Part.COUNTER_TOP),
                        color(Part.DESK), IsoPalette.darker(color(Part.DESK), 0.76));
                IsoBrush.box(graphics, projection, cx + 0.3, cy + 0.06, 0.55, 0.26, 0.03, 0.17, color(Part.DEVICE));
            });
            double bx = area.col() + 0.2;
            double by = area.bottom() - 0.62;
            add(items, bx + 0.4, by + 0.2, graphics -> prop(graphics, Prop.BENCH, bx, by));
            double sx = right - 0.25;
            double sy = exitRow + 1.05;
            add(items, sx, sy, graphics -> {
                IsoBrush.box(graphics, projection, sx - 0.02, sy - 0.02, 0, 0.04, 0.04, 0.95, color(Part.METAL));
                IsoBrush.box(graphics, projection, sx - 0.2, sy - 0.03, 0.95, 0.4, 0.06, 0.16, EXIT_GREEN);
            });
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
            case CORRIDOR -> new Prop[] {Prop.PARKED_STRETCHER, Prop.PLANT, Prop.BENCH, Prop.BIN, Prop.WHEELCHAIR,
                Prop.WATER_COOLER, Prop.CLEANING_CART, Prop.WET_FLOOR};
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
        COFFEE,
        /** Camilla vacía estacionada contra el muro del pasillo. */
        PARKED_STRETCHER,
        /** Banca de espera del pasillo. */
        BENCH,
        /** Silla de ruedas vacía. */
        WHEELCHAIR,
        /** Carro de limpieza con su balde y su trapero. */
        CLEANING_CART,
        /** Caneca de basura con tapa. */
        BIN,
        /** Aviso amarillo de piso mojado. */
        WET_FLOOR
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
                if (!projection.faces(0, 1)) {
                    return;
                }
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
                for (int drawer = 0; drawer < 4 && projection.faces(0, 1); drawer++) {
                    double z = 0.14 + drawer * 0.15;
                    graphics.setColor(IsoPalette.darker(ALARM_RED, 0.75));
                    graphics.fill(IsoBrush.polygon(projection, new double[][] {{x + 0.14, y + 0.404, z},
                            {x + 0.56, y + 0.404, z}, {x + 0.56, y + 0.404, z + 0.11},
                            {x + 0.14, y + 0.404, z + 0.11}}));
                }
                IsoBrush.box(graphics, projection, x + 0.18, y + 0.08, 0.78, 0.3, 0.22, 0.1, color(Part.DEVICE));
            }
            case VENDING -> {
                IsoBrush.box(graphics, projection, x, y, 0, 0.6, 0.45, 1.1, ALARM_RED);
                if (!projection.faces(0, 1)) {
                    return;
                }
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
                if (!projection.faces(0, 1)) {
                    return;
                }
                double face = y + 0.404;
                graphics.setStroke(new BasicStroke(0.8f));
                graphics.setColor(IsoPalette.darker(LOCKER, 0.65));
                for (int door = 0; door < 3; door++) {
                    double left = x + 0.04 + door * 0.22;
                    graphics.draw(IsoBrush.polygon(projection, new double[][] {{left, face, 0.06},
                            {left + 0.2, face, 0.06}, {left + 0.2, face, 0.94}, {left, face, 0.94}}));
                }
            }
            case PARKED_STRETCHER -> {
                for (double wheel : new double[] {x + 0.05, x + 0.65}) {
                    Point2D at = projection.at(wheel, y + 0.46, 0.04);
                    graphics.setColor(IsoPalette.OUTLINE);
                    graphics.fill(new Ellipse2D.Double(at.getX() - 2.5, at.getY() - 1.6, 5, 3.2));
                }
                IsoBrush.box(graphics, projection, x + 0.05, y + 0.08, 0.05, 0.6, 0.3, 0.2, color(Part.METAL));
                IsoBrush.box(graphics, projection, x - 0.05, y + 0.02, 0.25, 0.8, 0.42, 0.05, color(Part.BED_FRAME));
                IsoBrush.box(graphics, projection, x - 0.03, y + 0.04, 0.3, 0.76, 0.38, 0.05, color(Part.MATTRESS));
                IsoBrush.box(graphics, projection, x, y + 0.08, 0.35, 0.18, 0.3, 0.05, color(Part.PILLOW));
            }
            case BENCH -> {
                IsoBrush.box(graphics, projection, x, y + 0.04, 0, 0.06, 0.3, 0.22, color(Part.METAL));
                IsoBrush.box(graphics, projection, x + 0.64, y + 0.04, 0, 0.06, 0.3, 0.22, color(Part.METAL));
                IsoBrush.box(graphics, projection, x - 0.05, y + 0.02, 0.22, 0.8, 0.34, 0.05, color(Part.CHAIR_TOP),
                        color(Part.CHAIR), IsoPalette.darker(color(Part.CHAIR), 0.76));
                IsoBrush.box(graphics, projection, x - 0.05, y, 0.27, 0.8, 0.06, 0.26, color(Part.CHAIR_TOP),
                        color(Part.CHAIR), IsoPalette.darker(color(Part.CHAIR), 0.76));
            }
            case WHEELCHAIR -> {
                for (double wheel : new double[] {y + 0.06, y + 0.42}) {
                    Point2D at = projection.at(x + 0.2, wheel, 0.18);
                    graphics.setStroke(new BasicStroke(1.8f));
                    graphics.setColor(IsoPalette.OUTLINE);
                    graphics.draw(new Ellipse2D.Double(at.getX() - 4.5, at.getY() - 7, 9, 14));
                }
                IsoBrush.box(graphics, projection, x + 0.18, y + 0.08, 0.24, 0.36, 0.34, 0.04, WHEELCHAIR_SEAT);
                IsoBrush.box(graphics, projection, x + 0.18, y + 0.08, 0.28, 0.05, 0.34, 0.32, WHEELCHAIR_SEAT);
            }
            case CLEANING_CART -> {
                IsoBrush.box(graphics, projection, x + 0.05, y + 0.05, 0.05, 0.55, 0.32, 0.45, SHARPS);
                IsoBrush.box(graphics, projection, x + 0.1, y + 0.1, 0.5, 0.22, 0.22, 0.18, CART_BLUE);
                IsoBrush.box(graphics, projection, x + 0.42, y + 0.12, 0.5, 0.04, 0.04, 0.55, color(Part.METAL));
            }
            case BIN -> {
                IsoBrush.box(graphics, projection, x + 0.25, y + 0.08, 0, 0.22, 0.22, 0.32, BIN_GREEN);
                IsoBrush.box(graphics, projection, x + 0.23, y + 0.06, 0.32, 0.26, 0.26, 0.03,
                        IsoPalette.darker(BIN_GREEN, 0.75));
            }
            case WET_FLOOR -> {
                Point2D left = projection.at(x + 0.2, y + 0.3, 0);
                Point2D right = projection.at(x + 0.45, y + 0.3, 0);
                Point2D top = projection.at(x + 0.32, y + 0.3, 0.45);
                Path2D.Double sign = new Path2D.Double();
                sign.moveTo(left.getX(), left.getY());
                sign.lineTo(right.getX(), right.getY());
                sign.lineTo(top.getX(), top.getY());
                sign.closePath();
                graphics.setColor(SHARPS);
                graphics.fill(sign);
                graphics.setStroke(new BasicStroke(0.8f));
                graphics.setColor(IsoPalette.darker(SHARPS, 0.55));
                graphics.draw(sign);
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
        items.add(new IsoItem(projection.depth(gx, gy), IsoItem.FURNITURE, painter));
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

    /**
     * Monitor de signos vitales de una cama de UCI: en su soporte, detrás de la cabecera y centrado con la cama, así
     * ningún muro lo tapa y la pantalla se ve por encima de la cabecera.
     */
    private void monitor(Graphics2D graphics, Position spot) {
        double x = spot.col() + MONITOR_X;
        double y = spot.row() + MONITOR_Y;
        IsoBrush.box(graphics, projection, x - 0.09, y - 0.09, 0, 0.18, 0.18, 0.03, color(Part.METAL));
        IsoBrush.box(graphics, projection, x - 0.02, y - 0.02, 0.03, 0.04, 0.04, 0.62, color(Part.METAL));
        screen(graphics, x, y, 0.66);
    }

    /** Pantalla con el trazo verde de los latidos, en su cara de las filas mayores; de espaldas solo se ve la caja. */
    private void screen(Graphics2D graphics, double x, double y, double z) {
        IsoBrush.box(graphics, projection, x - 0.15, y - 0.05, z, 0.3, 0.1, 0.22, color(Part.DEVICE));
        if (!projection.faces(0, 1)) {
            return;
        }
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
        IsoBrush.box(graphics, projection, spot.col() + 0.08, spot.row() + 0.08, 0, 0.24, 0.2, 0.55,
                color(Part.DEVICE));
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
        double ry = rx * projection.groundRatio();
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

    /** Puesto de triage: escritorio ancho con dos computadores, teclado, tensiómetro y la silla de la enfermera. */
    private void triageDesk(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.06;
        double y = spot.row() + 0.16;
        IsoBrush.box(graphics, projection, x, y, 0, 0.88, 0.24, 0.42, color(Part.DESK_TOP), color(Part.DESK),
                IsoPalette.darker(color(Part.DESK), 0.76));
        for (double left : new double[] {x + 0.12, x + 0.46}) {
            IsoBrush.box(graphics, projection, left + 0.1, y + 0.05, 0.42, 0.04, 0.04, 0.06, color(Part.DEVICE));
            IsoBrush.box(graphics, projection, left, y + 0.03, 0.48, 0.26, 0.03, 0.17, color(Part.DEVICE));
            if (!projection.faces(0, 1)) {
                continue;
            }
            graphics.setColor(SCREEN);
            graphics.fill(IsoBrush.polygon(projection, new double[][] {{left + 0.02, y + 0.062, 0.5},
                {left + 0.24, y + 0.062, 0.5}, {left + 0.24, y + 0.062, 0.63}, {left + 0.02, y + 0.062, 0.63}}));
        }
        IsoBrush.box(graphics, projection, x + 0.2, y + 0.14, 0.42, 0.26, 0.07, 0.012, KEYBOARD);
        IsoBrush.box(graphics, projection, x + 0.76, y + 0.08, 0.42, 0.09, 0.1, 0.07, color(Part.DEVICE));
    }

    /** Silla de oficina de la enfermera de triage, detrás del escritorio. */
    private void triageChair(Graphics2D graphics, Position spot) {
        double x = spot.col() + 0.38;
        double y = spot.row() + 0.0;
        IsoBrush.box(graphics, projection, x + 0.1, y + 0.05, 0, 0.04, 0.04, 0.22, color(Part.METAL));
        IsoBrush.box(graphics, projection, x, y - 0.04, 0.22, 0.24, 0.2, 0.05, color(Part.CHAIR_TOP),
                color(Part.CHAIR), IsoPalette.darker(color(Part.CHAIR), 0.76));
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
        if (projection.faces(0, 1)) {
            double face = y + d + 0.004;
            graphics.setColor(IsoPalette.FRIDGE_GLASS);
            graphics.fill(IsoBrush.polygon(projection, new double[][] {{x + 0.05, face, 0.06},
                {x + w - 0.05, face, 0.06}, {x + w - 0.05, face, h - 0.06}, {x + 0.05, face, h - 0.06}}));
            graphics.setColor(IsoPalette.BLOOD_BAG);
            for (int shelf = 0; shelf < 3; shelf++) {
                double z = 0.12 + shelf * 0.24;
                for (int bag = 0; bag < 3; bag++) {
                    double left = x + 0.09 + bag * 0.155;
                    graphics.fill(IsoBrush.polygon(projection, new double[][] {{left, face, z},
                        {left + 0.12, face, z}, {left + 0.12, face, z + 0.15}, {left, face, z + 0.15}}));
                }
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
