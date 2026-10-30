package com.medresource.services.simulation;

import com.medresource.model.Bed;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.Position;
import com.medresource.model.Room;
import com.medresource.model.ZoneType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma el plano de un hospital: dónde está cada cama, quirófano, médico y enfermero.
 *
 * <p>El plano tiene siempre {@value #COLUMNS} columnas y crece hacia abajo según los recursos del
 * hospital. De arriba abajo hay: la entrada, la sala de espera y el triage; las habitaciones de
 * hospitalización; la UCI; los quirófanos; la morgue y la sala del personal. Las casillas que no
 * pertenecen a ninguna zona son pasillo. El mismo hospital da siempre el mismo plano: no hay azar.</p>
 *
 * <p>Los recursos se ubican con los códigos que ya tienen en el hospital, así los eventos de la
 * simulación se pueden situar en el plano sin traducir nada.</p>
 */
public class LayoutFactory {

    /** Columnas del plano, iguales para cualquier hospital. */
    public static final int COLUMNS = 36;

    /** Filas de la franja de recepción (entrada, sala de espera y triage). */
    private static final int RECEPTION_ROWS = 5;
    private static final int ENTRANCE_FIRST_COL = 0;
    private static final int ENTRANCE_LAST_COL = 1;
    private static final int WAITING_FIRST_COL = 3;
    private static final int WAITING_LAST_COL = 17;
    private static final int TRIAGE_FIRST_COL = 19;
    private static final int TRIAGE_LAST_COL = 26;

    /** Camas de cada bloque de una habitación o de la UCI: 2 columnas por 2 filas. */
    private static final int BEDS_PER_BLOCK = 4;
    private static final int BED_BLOCK_COLS = 2;
    private static final int BED_BLOCK_ROWS = 2;
    private static final int OPERATING_BLOCK_COLS = 3;
    private static final int OPERATING_BLOCK_ROWS = 2;
    /** Lugares de la morgue por fila. */
    private static final int MORGUE_SPOTS_PER_ROW = 10;

    /**
     * Arma el plano del hospital.
     *
     * @param hospital hospital cuyos recursos se ubican
     * @return plano con un lugar para cada cama, quirófano, médico y enfermero, y para cada lugar de la
     *         morgue
     * @throws IllegalArgumentException si el hospital es nulo
     */
    public HospitalLayout create(Hospital hospital) {
        if (hospital == null) {
            throw new IllegalArgumentException("El hospital es obligatorio.");
        }
        List<List<Bed>> bedBlocks = bedBlocks(hospital);
        int roomCount = hospital.getRooms().size();
        List<List<Bed>> roomBlocks = bedBlocks.subList(0, roomCount);
        List<List<Bed>> icuBlocks = bedBlocks.subList(roomCount, bedBlocks.size());
        int morgueCapacity = hospital.getMorgue().getCapacity();
        int staffCount = hospital.getDoctors().size() + hospital.getNurses().size();

        int rows = RECEPTION_ROWS + 1
                + bandRows(roomBlocks.size(), BED_BLOCK_COLS, BED_BLOCK_ROWS)
                + bandRows(icuBlocks.size(), BED_BLOCK_COLS, BED_BLOCK_ROWS)
                + bandRows(hospital.getOperatingRooms().size(), OPERATING_BLOCK_COLS, OPERATING_BLOCK_ROWS)
                + morgueRows(morgueCapacity) + staffRows(staffCount);
        ZoneType[][] zones = new ZoneType[rows][COLUMNS];
        for (ZoneType[] row : zones) {
            Arrays.fill(row, ZoneType.CORRIDOR);
        }
        Map<String, Position> spots = new HashMap<>();

        fill(zones, ZoneType.ENTRANCE, ENTRANCE_FIRST_COL, ENTRANCE_LAST_COL, 0, RECEPTION_ROWS - 1);
        fill(zones, ZoneType.WAITING_ROOM, WAITING_FIRST_COL, WAITING_LAST_COL, 0, RECEPTION_ROWS - 1);
        fill(zones, ZoneType.TRIAGE, TRIAGE_FIRST_COL, TRIAGE_LAST_COL, 0, RECEPTION_ROWS - 1);

        int top = RECEPTION_ROWS + 1;
        top = placeBedBlocks(zones, spots, roomBlocks, ZoneType.HOSPITALIZATION, top);
        top = placeBedBlocks(zones, spots, icuBlocks, ZoneType.ICU, top);
        top = placeOperatingRooms(zones, spots, hospital.getOperatingRooms(), top);
        List<Position> morgueSpots = placeMorgue(zones, morgueCapacity, top);
        top += morgueRows(morgueCapacity);
        placeStaff(zones, spots, hospital.getDoctors(), hospital.getNurses(), top);
        return new HospitalLayout(zones, spots, new Position(ENTRANCE_FIRST_COL, RECEPTION_ROWS / 2),
                new Position((TRIAGE_FIRST_COL + TRIAGE_LAST_COL) / 2, RECEPTION_ROWS / 2),
                waitingSpots(), morgueSpots);
    }

    /** Camas de cada habitación y, al final, las de la UCI en grupos de cuatro. */
    private List<List<Bed>> bedBlocks(Hospital hospital) {
        List<List<Bed>> blocks = new ArrayList<>();
        for (Room room : hospital.getRooms()) {
            blocks.add(room.getBeds());
        }
        List<Bed> icuBeds = hospital.getIcuBeds();
        for (int first = 0; first < icuBeds.size(); first += BEDS_PER_BLOCK) {
            blocks.add(icuBeds.subList(first, Math.min(first + BEDS_PER_BLOCK, icuBeds.size())));
        }
        return blocks;
    }

    /** Lugares de la sala de espera: una casilla sí y otra no, como un tablero de ajedrez. */
    private List<Position> waitingSpots() {
        List<Position> spots = new ArrayList<>();
        for (int row = 0; row < RECEPTION_ROWS; row++) {
            for (int col = WAITING_FIRST_COL; col <= WAITING_LAST_COL; col++) {
                if ((col + row) % 2 == 0) {
                    spots.add(new Position(col, row));
                }
            }
        }
        return spots;
    }

    /**
     * Coloca bloques de camas, uno al lado del otro y en filas, separados por una casilla de pasillo.
     *
     * @return primera fila libre después de las camas
     */
    private int placeBedBlocks(ZoneType[][] zones, Map<String, Position> spots, List<List<Bed>> blocks,
                               ZoneType zone, int top) {
        int perRow = blocksPerRow(BED_BLOCK_COLS);
        for (int index = 0; index < blocks.size(); index++) {
            int col = 1 + (index % perRow) * (BED_BLOCK_COLS + 1);
            int row = top + (index / perRow) * (BED_BLOCK_ROWS + 1);
            fill(zones, zone, col, col + BED_BLOCK_COLS - 1, row, row + BED_BLOCK_ROWS - 1);
            List<Bed> beds = blocks.get(index);
            for (int bed = 0; bed < beds.size(); bed++) {
                spots.put(beds.get(bed).getCode(),
                        new Position(col + bed % BED_BLOCK_COLS, row + bed / BED_BLOCK_COLS));
            }
        }
        return top + bandRows(blocks.size(), BED_BLOCK_COLS, BED_BLOCK_ROWS);
    }

    /**
     * Coloca los quirófanos como bloques de 3 por 2 casillas; la mesa de operaciones queda en la casilla
     * central de la fila de arriba.
     *
     * @return primera fila libre después de los quirófanos
     */
    private int placeOperatingRooms(ZoneType[][] zones, Map<String, Position> spots,
                                    List<OperatingRoom> operatingRooms, int top) {
        int perRow = blocksPerRow(OPERATING_BLOCK_COLS);
        for (int index = 0; index < operatingRooms.size(); index++) {
            int col = 1 + (index % perRow) * (OPERATING_BLOCK_COLS + 1);
            int row = top + (index / perRow) * (OPERATING_BLOCK_ROWS + 1);
            fill(zones, ZoneType.OPERATING_ROOM, col, col + OPERATING_BLOCK_COLS - 1, row,
                    row + OPERATING_BLOCK_ROWS - 1);
            spots.put(operatingRooms.get(index).getCode(), new Position(col + OPERATING_BLOCK_COLS / 2, row));
        }
        return top + bandRows(operatingRooms.size(), OPERATING_BLOCK_COLS, OPERATING_BLOCK_ROWS);
    }

    /** Coloca un lugar de la morgue por cada capacidad, en filas de {@value #MORGUE_SPOTS_PER_ROW}. */
    private List<Position> placeMorgue(ZoneType[][] zones, int capacity, int top) {
        List<Position> spots = new ArrayList<>();
        for (int index = 0; index < capacity; index++) {
            Position spot = new Position(1 + index % MORGUE_SPOTS_PER_ROW, top + index / MORGUE_SPOTS_PER_ROW);
            zones[spot.row()][spot.col()] = ZoneType.MORGUE;
            spots.add(spot);
        }
        return spots;
    }

    /** Coloca primero a los médicos y después a los enfermeros, una casilla cada uno. */
    private void placeStaff(ZoneType[][] zones, Map<String, Position> spots, List<Doctor> doctors,
                            List<Nurse> nurses, int top) {
        List<String> codes = new ArrayList<>();
        doctors.forEach(doctor -> codes.add(doctor.getCode()));
        nurses.forEach(nurse -> codes.add(nurse.getCode()));
        for (int index = 0; index < codes.size(); index++) {
            Position spot = new Position(index % COLUMNS, top + index / COLUMNS);
            zones[spot.row()][spot.col()] = ZoneType.STAFF_ROOM;
            spots.put(codes.get(index), spot);
        }
    }

    /** Filas de la morgue más una fila de pasillo debajo. */
    private static int morgueRows(int capacity) {
        return (capacity + MORGUE_SPOTS_PER_ROW - 1) / MORGUE_SPOTS_PER_ROW + 1;
    }

    private static int staffRows(int staffCount) {
        return (staffCount + COLUMNS - 1) / COLUMNS;
    }

    /** Cuántos bloques caben en una fila: dejan una casilla de pasillo al inicio y entre bloques. */
    private static int blocksPerRow(int blockCols) {
        return (COLUMNS - 1) / (blockCols + 1);
    }

    /** Filas que ocupa una franja de bloques, con la fila de pasillo que va debajo de cada fila. */
    private static int bandRows(int blockCount, int blockCols, int blockRows) {
        if (blockCount == 0) {
            return 0;
        }
        int perRow = blocksPerRow(blockCols);
        return (blockCount + perRow - 1) / perRow * (blockRows + 1);
    }

    private static void fill(ZoneType[][] zones, ZoneType zone, int firstCol, int lastCol, int firstRow,
                             int lastRow) {
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = firstCol; col <= lastCol; col++) {
                zones[row][col] = zone;
            }
        }
    }
}