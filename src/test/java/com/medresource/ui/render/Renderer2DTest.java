package com.medresource.ui.render;

import com.medresource.model.Area;
import com.medresource.model.Door;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.StaffSeat;
import com.medresource.model.ZoneType;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del dibujo del plano en la vista 2D.
 *
 * <p>Dibuja el hospital estándar en una imagen en memoria y comprueba el color de los píxeles. Los colores
 * que se comprueban son los de los muebles y los muros que dibuja {@link Renderer2D}.</p>
 */
class Renderer2DTest {

    private static final int CELL_W = Renderer2D.CELL_W;
    private static final int CELL_H = Renderer2D.CELL_H;
    private static final int BLACK = 0xFF000000;

    /** Muro oscuro que rodea cada sala. */
    private static final int WALL = 0xFF263F65;
    /** Color del texto de los títulos y de los códigos. */
    private static final int INK = 0xFF15283E;
    /** Color de los íconos que acompañan el título de cada sala. */
    private static final int ICON = 0xFF1F4863;
    /** Piso de los pasillos y franja de su borde. */
    private static final int CORRIDOR_FLOOR = 0xFFFAFCFF;
    private static final int CORRIDOR_EDGE = 0xFFA7BBD0;
    /** Rojo de los extintores de los pasillos y café de las macetas. */
    private static final int EXTINGUISHER = 0xFFFA343F;
    private static final int POT = 0xFF895B36;
    /** Marco de cada cama y canto de cada mesa de cirugía. */
    private static final int BED_FRAME = 0xFF344E63;
    private static final int OPERATING_TABLE_SIDE = 0xFF2B6979;
    /** Marco de cada cajón de la morgue. */
    private static final int MORGUE_DRAWER = 0xFF40546A;
    /** Mesa de la sala del personal y carpeta de cada puesto. */
    private static final int STAFF_TABLE = 0xFF6B9DA2;
    private static final int STAFF_FOLDER = 0xFFE5F4F8;
    /** Marco del escritorio de cada consultorio. */
    private static final int CONSULT_DESK = 0xFF455E86;
    /** Marco de la camilla de triage. */
    private static final int TRIAGE_STRETCHER = 0xFF526F82;
    /** Marco de cada unidad del banco de sangre. */
    private static final int BLOOD_UNIT = 0xFF841C2F;
    /** Marco y vidrio de cada puerta. */
    private static final int DOOR_FRAME = 0xFF37657C;
    private static final int DOOR_GLASS = 0xFF77BFAF;
    /** Verde del letrero de la entrada. */
    private static final int ENTRANCE_SIGN = 0xFF35B97E;
    /** Cabezal de la lámpara de cada quirófano. */
    private static final int SURGICAL_LAMP = 0xFF2F6675;
    /** Manija de cada cajón de la morgue. */
    private static final int DRAWER_HANDLE = 0xFF607587;
    /** Pantalla del computador de cada escritorio de los consultorios. */
    private static final int DESK_SCREEN = 0xFF8CD4E3;
    /** Línea clara que divide el vidrio de cada puerta en dos hojas. */
    private static final int DOOR_LEAF_LINE = 0xFFEFFDFD;
    private static final int WHITE = 0xFFFFFFFF;

    private final Renderer2D renderer = new Renderer2D();
    private final HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    private final BufferedImage image = draw();

    private BufferedImage draw() {
        Dimension size = renderer.worldSize(layout);
        BufferedImage picture = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = picture.createGraphics();
        renderer.render(graphics, layout);
        graphics.dispose();
        return picture;
    }

    private int pixelAtCenterOf(Position position) {
        Point2D center = renderer.centerOf(position);
        return image.getRGB((int) center.getX(), (int) center.getY());
    }

    /** Píxel de una casilla, contado desde su esquina de arriba a la izquierda. */
    private int pixelIn(Position position, int dx, int dy) {
        return image.getRGB(position.col() * CELL_W + dx, position.row() * CELL_H + dy);
    }

    /** Cuenta los píxeles de un color dentro de un rectángulo de la imagen. */
    private int count(int color, int x, int y, int width, int height) {
        int total = 0;
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                if (image.getRGB(x + dx, y + dy) == color) {
                    total++;
                }
            }
        }
        return total;
    }

    /** Cuenta los píxeles de un color dentro de una casilla. */
    private int countInCell(int color, Position position) {
        return count(color, position.col() * CELL_W, position.row() * CELL_H, CELL_W, CELL_H);
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }

    // ---------- tamaño, casillas y datos obligatorios ----------

    @Test
    void theWorldIsOneCellBigForEachSquareOfThePlan() {
        Dimension size = renderer.worldSize(layout);

        assertEquals(layout.getCols() * CELL_W, size.width);
        assertEquals(layout.getRows() * CELL_H, size.height);
    }

    @Test
    void theCenterOfASquareIsInTheMiddleOfItsCell() {
        Point2D center = renderer.centerOf(new Position(2, 3));

        assertEquals(2 * CELL_W + CELL_W / 2.0, center.getX(), 0.0);
        assertEquals(3 * CELL_H + CELL_H / 2.0, center.getY(), 0.0);
    }

    @Test
    void doesNotDrawWithMissingData() {
        Graphics2D graphics = image.createGraphics();

        assertThrows(IllegalArgumentException.class, () -> renderer.worldSize(null));
        assertThrows(IllegalArgumentException.class, () -> renderer.centerOf(null));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(null, layout));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(graphics, null));
        graphics.dispose();
    }

    @Test
    void theTwoSeatsOfATableAreAQuarterOfACellLeftAndRightOfItsCenter() {
        Position table = new Position(2, 3);
        Point2D center = renderer.centerOf(table);

        assertEquals(center.getX() - CELL_W / 4.0, renderer.seatOf(new StaffSeat(table, 0)).getX(), 0.0);
        assertEquals(center.getX() + CELL_W / 4.0, renderer.seatOf(new StaffSeat(table, 1)).getX(), 0.0);
        assertEquals(center.getY(), renderer.seatOf(new StaffSeat(table, 1)).getY(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> renderer.seatOf(null));
    }

    @Test
    void drawsTheSameWhenItIsDrawnAgain() {
        BufferedImage second = draw();

        for (int y = 0; y < image.getHeight(); y += 7) {
            for (int x = 0; x < image.getWidth(); x += 5) {
                assertEquals(image.getRGB(x, y), second.getRGB(x, y));
            }
        }
        assertNotEquals(BLACK, image.getRGB(40, 5));
    }

    // ---------- salas y pasillos ----------

    @Test
    void surroundsEveryRoomWithADarkWall() {
        for (Area area : layout.getAreas()) {
            if (area.hasTitleRow()) {
                assertEquals(WALL, image.getRGB(area.col() * CELL_W + 40, area.row() * CELL_H + 5), area.title());
            }
        }
    }

    @Test
    void doesNotPaintWhereThereIsNoArea() {
        // Fuera de los muros: la esquina del plano y la franja entre el triage y la sala de espera.
        Area waiting = layout.areasOf(ZoneType.WAITING_ROOM).get(0);
        assertEquals(BLACK, image.getRGB(0, 0));
        assertEquals(BLACK, image.getRGB(waiting.col() * CELL_W, 40));
    }

    @Test
    void writesTheTitleOfEveryAreaThatHasATitleRow() {
        for (Area area : layout.getAreas()) {
            if (!area.hasTitleRow()) {
                continue;
            }
            assertTrue(count(INK, area.col() * CELL_W, area.row() * CELL_H, area.cols() * CELL_W, 40) > 0,
                    "Título de " + area.title());
        }
    }

    @Test
    void drawsAnIconBesideTheTitleOfEveryRoom() {
        for (Area area : layout.getAreas()) {
            if (!area.hasTitleRow()) {
                continue;
            }
            assertTrue(count(ICON, area.col() * CELL_W + 14, area.row() * CELL_H + 14, 34, 34) > 0,
                    "Ícono de " + area.title());
        }
    }

    @Test
    void paintsTheCorridorsFromEdgeToEdgeWithoutATitle() {
        for (Area corridor : layout.areasOf(ZoneType.CORRIDOR)) {
            int x = corridor.col() * CELL_W;
            int y = corridor.row() * CELL_H;

            assertEquals(CORRIDOR_FLOOR, image.getRGB(x + 1, y + corridor.rows() * CELL_H / 2));
            assertEquals(CORRIDOR_FLOOR, pixelAtCenterOf(corridor.slot(0)));
            assertEquals(CORRIDOR_EDGE, image.getRGB(x + 10, y + 1));
            assertEquals(0, count(INK, x, y, 2 * CELL_W, 40));
        }
    }

    @Test
    void doesNotChangeTheBrushOfWhoeverAsksForTheDrawing() {
        BufferedImage picture = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = picture.createGraphics();
        Font font = new Font(Font.MONOSPACED, Font.ITALIC, 7);
        Stroke stroke = new BasicStroke(3f);
        graphics.setFont(font);
        graphics.setStroke(stroke);
        graphics.setColor(Color.MAGENTA);

        renderer.render(graphics, layout);

        assertSame(font, graphics.getFont());
        assertSame(stroke, graphics.getStroke());
        assertEquals(Color.MAGENTA, graphics.getColor());
        assertEquals(null, graphics.getClip());
        graphics.dispose();
    }

    // ---------- piezas decorativas ----------

    @Test
    void putsAFireExtinguisherOnEveryLongCorridor() {
        for (Area corridor : layout.areasOf(ZoneType.CORRIDOR)) {
            if (corridor.cols() * CELL_W > 4 * CELL_W + 120) {
                assertTrue(count(EXTINGUISHER, corridor.col() * CELL_W, corridor.row() * CELL_H,
                        corridor.cols() * CELL_W, corridor.rows() * CELL_H) > 0, corridor.toString());
            }
        }
    }

    @Test
    void putsPlantsInTheTriageAndTheWaitingRoom() {
        for (ZoneType zone : List.of(ZoneType.TRIAGE, ZoneType.WAITING_ROOM)) {
            Area area = layout.areasOf(zone).get(0);

            assertTrue(count(POT, area.col() * CELL_W, area.row() * CELL_H, area.cols() * CELL_W,
                    area.rows() * CELL_H) > 0, area.title());
        }
    }

    @Test
    void theDecorationLeavesTheBedsAndTheOperatingTablesFree() {
        for (String code : layout.getResourceCodes()) {
            assertEquals(0, countInCell(POT, layout.positionOf(code).orElseThrow()), code);
        }
    }

    // ---------- camas y quirófanos ----------

    @Test
    void drawsABedWhereEachBedIs() {
        for (String code : List.of("H1-C1", "H7-C4", "UCI-C1", "UCI-C6")) {
            Position bed = layout.positionOf(code).orElseThrow();

            assertEquals(BED_FRAME, pixelIn(bed, 27, 23), code);
        }
    }

    @Test
    void theBlanketIsBlueInHospitalizationAndPinkInTheIntensiveCareUnit() {
        int hospitalization = pixelIn(layout.positionOf("H1-C1").orElseThrow(), 51, 31);
        int intensiveCare = pixelIn(layout.positionOf("UCI-C1").orElseThrow(), 51, 31);

        assertTrue(blue(hospitalization) > red(hospitalization), "Hospitalización");
        assertTrue(red(intensiveCare) > blue(intensiveCare), "UCI");
    }

    @Test
    void drawsAnOperatingTableWhereEachOperatingRoomIs() {
        for (String code : List.of("Q1", "Q2", "Q3", "Q4")) {
            Position table = layout.positionOf(code).orElseThrow();

            assertEquals(OPERATING_TABLE_SIDE, pixelIn(table, 46, 32), code);
        }
    }

    @Test
    void eachOperatingTableHasALampOverIt() {
        for (String code : List.of("Q1", "Q2", "Q3", "Q4")) {
            Position table = layout.positionOf(code).orElseThrow();

            assertTrue(count(SURGICAL_LAMP, table.col() * CELL_W, table.row() * CELL_H, 2 * CELL_W, CELL_H) > 0,
                    code);
        }
    }

    @Test
    void writesTheCodeUnderEachBedAndEachOperatingTable() {
        for (String code : layout.getResourceCodes()) {
            Position place = layout.positionOf(code).orElseThrow();

            int width = Math.min(2 * CELL_W, image.getWidth() - place.col() * CELL_W);

            assertTrue(count(INK, place.col() * CELL_W, place.row() * CELL_H + 30, width, 18) > 0, code);
        }
    }

    @Test
    void anUnusedSquareOfAnAreaHasNoBed() {
        Position bed = layout.positionOf("H1-C1").orElseThrow();
        Position unused = new Position(bed.col() + 1, bed.row());

        assertNotEquals(BED_FRAME, pixelIn(unused, 27, 23));
        assertEquals(0, countInCell(INK, unused));
    }

    // ---------- sala de espera y morgue ----------

    @Test
    void eachSeatOfTheWaitingRoomIsACushionOfABench() {
        for (Position spot : layout.getWaitingSpots()) {
            int cushion = pixelAtCenterOf(spot);

            assertTrue(blue(cushion) > red(cushion) + 60, spot.toString());
        }
    }

    @Test
    void twoNeighbourSeatsShareOneBench() {
        Position first = layout.getWaitingSpots().get(0);
        Position second = layout.getWaitingSpots().get(1);
        assertEquals(first.row(), second.row());
        assertEquals(first.col() + 1, second.col());

        // El borde entre las dos casillas cae sobre el cojín del medio de la banca.
        int middle = image.getRGB(second.col() * CELL_W, second.row() * CELL_H + CELL_H / 2);

        assertTrue(blue(middle) > red(middle) + 60, Integer.toHexString(middle));
    }

    @Test
    void drawsADrawerForEachPlaceOfTheMorgue() {
        for (Position spot : layout.getMorgueSpots()) {
            assertEquals(MORGUE_DRAWER, pixelIn(spot, 9, 21), spot.toString());
        }
    }

    @Test
    void eachDrawerOfTheMorgueHasAHandle() {
        for (Position spot : layout.getMorgueSpots()) {
            assertEquals(DRAWER_HANDLE, pixelIn(spot, 44, 26), spot.toString());
        }
    }

    // ---------- sala del personal y consultorios ----------

    @Test
    void drawsATableForEveryTwoMembersOfTheStaff() {
        assertEquals(STAFF_TABLE, pixelIn(layout.positionOf("D01").orElseThrow(), 5, 20));
        assertEquals(STAFF_TABLE, pixelIn(layout.positionOf("E01").orElseThrow(), 5, 20));
    }

    @Test
    void leavesAFolderWhereEachMemberOfTheStaffSits() {
        for (String code : List.of("D01", "D02", "E01", "E24")) {
            Point2D seat = renderer.seatOf(layout.staffSeatOf(code).orElseThrow());

            assertEquals(STAFF_FOLDER, image.getRGB((int) seat.getX(), (int) seat.getY() - 3), code);
        }
    }

    @Test
    void drawsADeskWithTheCodeOfEachDoctorInTheConsultingRooms() {
        Position desk = layout.consultDeskOf("D01").orElseThrow();

        assertEquals(CONSULT_DESK, pixelIn(desk, 3, 20));
        assertTrue(countInCell(INK, desk) > 0);
    }

    @Test
    void eachDeskOfTheConsultingRoomsHasAScreen() {
        for (String code : List.of("D01", "D16")) {
            assertEquals(DESK_SCREEN, pixelIn(layout.consultDeskOf(code).orElseThrow(), 56, 12), code);
        }
    }

    @Test
    void theFurnitureOfTheStaffRoomDrawsNobody() {
        // Las personas las dibuja quien pinta al personal; el plano solo deja las mesas vacías.
        Area staffRoom = layout.areasOf(ZoneType.STAFF_ROOM).get(0);

        assertEquals(0, count(Palette.PATIENT_HEAD.getRGB(), staffRoom.col() * CELL_W, staffRoom.row() * CELL_H,
                staffRoom.cols() * CELL_W, staffRoom.rows() * CELL_H));
    }

    // ---------- triage, banco de sangre, puertas y entrada ----------

    @Test
    void drawsAStretcherForEachTriagePlace() {
        for (Position spot : layout.getTriageSpots()) {
            assertEquals(TRIAGE_STRETCHER, pixelIn(spot, 9, 25), spot.toString());
        }
    }

    @Test
    void eachTriageStationHasItsTriageNurseInLilac() {
        for (Position spot : layout.getTriageSpots()) {
            int x = spot.col() * CELL_W + Renderer2D.TRIAGE_NURSE_X;
            int y = spot.row() * CELL_H + CELL_H / 2 + 4;
            assertEquals(Palette.TRIAGE_NURSE.getRGB(), image.getRGB(x, y));
        }
    }

    @Test
    void drawsARedUnitWithTheLabelForEachBloodType() {
        for (Position spot : layout.getBloodSpots()) {
            assertEquals(BLOOD_UNIT, pixelIn(spot, 6, 20), spot.toString());
            assertTrue(countInCell(WHITE, spot) > 0, spot.toString());
        }
    }

    @Test
    void marksEachDoorOnTheEdgeBetweenItsRoomAndTheCorridor() {
        for (Door door : layout.getDoors()) {
            int edge = Math.max(door.inside().row(), door.outside().row()) * CELL_H;
            int center = door.inside().col() * CELL_W + CELL_W / 2;

            assertEquals(DOOR_GLASS, image.getRGB(center - 10, edge), door.toString());
            assertEquals(DOOR_GLASS, image.getRGB(center + 10, edge), door.toString());
        }
    }

    @Test
    void eachDoorHasAFrameAroundItsGlass() {
        for (Door door : layout.getDoors()) {
            int edge = Math.max(door.inside().row(), door.outside().row()) * CELL_H;
            int left = door.inside().col() * CELL_W + CELL_W / 4;

            assertEquals(DOOR_FRAME, image.getRGB(left, edge), door.toString());
            assertEquals(DOOR_FRAME, image.getRGB(left + CELL_W / 2 - 1, edge), door.toString());
        }
    }

    @Test
    void theGlassOfEachDoorIsSplitInTwoLeaves() {
        for (Door door : layout.getDoors()) {
            int edge = Math.max(door.inside().row(), door.outside().row()) * CELL_H;
            int center = door.inside().col() * CELL_W + CELL_W / 2;

            assertEquals(DOOR_LEAF_LINE, image.getRGB(center, edge), door.toString());
        }
    }

    @Test
    void marksTheEntranceWithAGreenSign() {
        Position entrance = layout.getEntrance();
        int left = entrance.col() * CELL_W + 8;
        int middle = entrance.row() * CELL_H + CELL_H / 2;

        assertEquals(ENTRANCE_SIGN, image.getRGB(left + 30, middle - 7));
        assertEquals(ENTRANCE_SIGN, image.getRGB(left + 2, middle));
    }

    @Test
    void theEntranceSignHasAWhiteArrowAndText() {
        Position entrance = layout.getEntrance();
        int left = entrance.col() * CELL_W + 8;
        int middle = entrance.row() * CELL_H + CELL_H / 2;

        assertEquals(WHITE, image.getRGB(left + 7, middle));
        assertTrue(count(WHITE, left + 18, middle - 9, 42, 18) > 0);
    }
}
