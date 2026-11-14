package com.medresource.services.simulation;

import com.medresource.model.Area;
import com.medresource.model.Bed;
import com.medresource.model.BloodType;
import com.medresource.model.Doctor;
import com.medresource.model.Door;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Nurse;
import com.medresource.model.Position;
import com.medresource.model.StaffSeat;
import com.medresource.model.ZoneType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma el plano de un hospital: dónde está cada sala, cama, quirófano, médico y enfermero.
 *
 * <p>El plano sigue el recorrido del paciente y se arma en tres franjas, cada una con todas sus áreas de la
 * misma altura, separadas por un pasillo de dos casillas de alto a todo lo ancho; un pasillo vertical de dos
 * casillas entre la UCI y el banco de sangre une los dos pasillos:</p>
 * <ol>
 *   <li>Arriba, de izquierda a derecha: el triage (con la entrada), la sala de espera y la hospitalización
 *       con todas sus camas.</li>
 *   <li>En medio: la UCI, el pasillo vertical, el banco de sangre (cerca de los quirófanos, que son los que
 *       más lo usan), un área por cada quirófano, la morgue y la zona de alta o salida.</li>
 *   <li>Abajo: a la izquierda, los consultorios, con un escritorio por médico, cerca del pasillo vertical; a
 *       la derecha, lo más lejos de la entrada, la sala del personal, con mesas de dos puestos, en la mitad
 *       del ancho.</li>
 * </ol>
 *
 * <p>Cada sala tiene una puerta en el centro de su borde hacia un pasillo: las de arriba, hacia el pasillo de
 * abajo; las demás, hacia el pasillo de arriba si lo tocan (si no, hacia el de abajo). Cada casilla mide
 * {@value HospitalLayout#METERS_PER_CELL} m.</p>
 *
 * <p>Las salas con muchas camas se ensanchan en columnas para que el plano no se alargue demasiado, y el
 * espacio que sobra en cada franja lo toma el último área para que ambas franjas terminen en el mismo borde.
 * El mismo hospital da siempre el mismo plano: no hay azar.</p>
 *
 * <p>Los recursos se ubican con los códigos que ya tienen en el hospital, así los eventos de la
 * simulación se pueden situar en el plano sin traducir nada.</p>
 */
public class LayoutFactory {

    /** Columnas del triage: la de la entrada y la fila, la de los puestos y una libre para pasar. */
    private static final int TRIAGE_COLS = 3;
    /** Alto de los pasillos entre las franjas: dos casillas, para que quepan camillas y personas a la vez. */
    private static final int CORRIDOR_ROWS = 2;
    /** Ancho del pasillo vertical que une los dos pasillos, entre la UCI y el banco de sangre: dos casillas. */
    private static final int CORRIDOR_COLS = 2;
    /**
     * Casillas entre una cama y la siguiente, a lo ancho y a lo alto (hospitalización y UCI): la cama y un espacio
     * libre, para quien la atiende al lado y para que los pies de una cama no toquen la cabecera de la de abajo.
     */
    private static final int BED_STEP = 2;
    /** Columnas de la sala de espera. */
    private static final int WAITING_COLS = 6;
    /** Filas de lugares de la sala de espera: {@code 6 × 6 = 36} sillas. */
    private static final int WAITING_SLOT_ROWS = 6;
    private static final int OPERATING_COLS = 2;
    private static final int OPERATING_ROWS = 2;
    /** Filas del bloque de quirófanos como máximo: así cada quirófano toca el pasillo de arriba o el de abajo. */
    private static final int MAX_OPERATING_BLOCK_ROWS = 2;
    private static final int BLOOD_COLS = 4;
    /** Título y dos filas de lugares: {@code 4 × 2 = 8} tipos de sangre. */
    private static final int BLOOD_ROWS = 3;
    private static final int MORGUE_MAX_COLS = 10;
    private static final int DISCHARGE_COLS = 3;
    private static final int DISCHARGE_ROWS = 3;
    private static final int MIN_BED_COLS = 4;
    private static final int MAX_BED_COLS = 18;
    private static final int MIN_ICU_COLS = 3;
    private static final int MAX_ICU_COLS = 9;
    /** Ancho mínimo del plano, para que la sala del personal no quede diminuta. */
    private static final int MIN_PLAN_COLS = 12;
    /** Cuánto más ancha que alta se deja una sala de camas. */
    private static final double ASPECT = 1.5;

    /**
     * Arma el plano del hospital.
     *
     * @param hospital hospital cuyos recursos se ubican
     * @return plano con un lugar para cada cama, quirófano, médico y enfermero, y para cada puesto de triage,
     *         tipo de sangre y lugar de la morgue
     * @throws IllegalArgumentException si el hospital es nulo
     */
    public HospitalLayout create(Hospital hospital) {
        if (hospital == null) {
            throw new IllegalArgumentException("El hospital es obligatorio.");
        }
        List<Bed> beds = hospital.getBeds();
        List<Bed> icuBeds = hospital.getIcuBeds();
        int operatingRooms = hospital.getOperatingRooms().size();
        int morgueCapacity = hospital.getMorgue().getCapacity();
        int staffCount = hospital.getDoctors().size() + hospital.getNurses().size();
        // Un puesto por estación de triage, uno cada dos filas en la columna del medio del área.
        int triageStations = hospital.getTriageStations().size();
        int triageMinRows = 2 * triageStations;

        // Anchos de las franjas: la más ancha manda y la otra reparte lo que le sobra.
        int bedCols = columnsFor(beds.size(), MIN_BED_COLS, MAX_BED_COLS);
        int icuPerRow = columnsFor(icuBeds.size(), MIN_ICU_COLS, MAX_ICU_COLS);
        int icuCols = icuPerRow * BED_STEP;
        // Bloque de quirófanos casi cuadrado, pero de dos filas como máximo para que todos toquen un pasillo.
        int operatingPerRow = (int) Math.ceil(Math.sqrt(operatingRooms));
        if ((operatingRooms + operatingPerRow - 1) / operatingPerRow > MAX_OPERATING_BLOCK_ROWS) {
            operatingPerRow = (operatingRooms + MAX_OPERATING_BLOCK_ROWS - 1) / MAX_OPERATING_BLOCK_ROWS;
        }
        int operatingBlockRows = (operatingRooms + operatingPerRow - 1) / operatingPerRow;
        int operatingCols = operatingPerRow * OPERATING_COLS;
        int morgueCols = Math.min(morgueCapacity, MORGUE_MAX_COLS);
        int topWidth = TRIAGE_COLS + WAITING_COLS + bedCols;
        int middleWidth = icuCols + CORRIDOR_COLS + BLOOD_COLS + operatingCols + morgueCols + DISCHARGE_COLS;
        int planCols = Math.max(MIN_PLAN_COLS, Math.max(topWidth, middleWidth));
        int wardCols = planCols - TRIAGE_COLS - WAITING_COLS;
        int dischargeCols = planCols - icuCols - CORRIDOR_COLS - BLOOD_COLS - operatingCols - morgueCols;

        // Alturas de las franjas: la sala más alta manda y las demás se estiran hasta ella.
        int topHeight = Math.max(Math.max(1 + WAITING_SLOT_ROWS, triageMinRows),
                1 + spacedRows(rowsFor(beds.size(), Math.max(1, wardCols / BED_STEP))));
        int middleHeight = Math.max(Math.max(1 + spacedRows(rowsFor(icuBeds.size(), icuPerRow)),
                operatingBlockRows * OPERATING_ROWS),
                Math.max(Math.max(1 + rowsFor(morgueCapacity, morgueCols), BLOOD_ROWS), DISCHARGE_ROWS));
        int middleTop = topHeight + CORRIDOR_ROWS;
        int staffTop = middleTop + middleHeight + CORRIDOR_ROWS;
        int staffCols = (planCols + 1) / 2;
        int consultCols = planCols - staffCols;
        int doctorCount = hospital.getDoctors().size();
        int staffRows = 1 + Math.max(rowsFor(staffCount, staffCols * StaffSeat.SEATS_PER_TABLE),
                rowsFor(doctorCount, consultCols));

        List<Area> areas = new ArrayList<>();
        Map<String, Position> spots = new HashMap<>();

        // Franja de arriba.
        Area triage = new Area(ZoneType.TRIAGE, "Triage", 0, 0, TRIAGE_COLS, topHeight);
        areas.add(triage);
        Area waiting = new Area(ZoneType.WAITING_ROOM, "Sala de espera", TRIAGE_COLS, 0, WAITING_COLS, topHeight);
        areas.add(waiting);
        Area ward = new Area(ZoneType.HOSPITALIZATION, "Hospitalización (" + beds.size() + " camas)",
                TRIAGE_COLS + WAITING_COLS, 0, wardCols, topHeight);
        areas.add(ward);
        placeSpacedBeds(spots, ward, beds);

        areas.add(new Area(ZoneType.CORRIDOR, "Pasillo", 0, topHeight, planCols, CORRIDOR_ROWS));

        // Franja del medio, de izquierda a derecha.
        int col = 0;
        Area icu = new Area(ZoneType.ICU, "UCI (" + icuBeds.size() + " camas)", col, middleTop, icuCols, middleHeight);
        areas.add(icu);
        placeSpacedBeds(spots, icu, icuBeds);
        col += icuCols;
        areas.add(new Area(ZoneType.CORRIDOR, "Pasillo", col, middleTop, CORRIDOR_COLS, middleHeight));
        col += CORRIDOR_COLS;
        Area blood = new Area(ZoneType.BLOOD_BANK, "Banco de sangre", col, middleTop, BLOOD_COLS, middleHeight);
        areas.add(blood);
        col += BLOOD_COLS;
        placeOperatingRooms(areas, spots, hospital, col, middleTop, middleHeight, operatingPerRow, operatingBlockRows);
        col += operatingCols;
        Area morgue = new Area(ZoneType.MORGUE, "Morgue", col, middleTop, morgueCols, middleHeight);
        areas.add(morgue);
        col += morgueCols;
        areas.add(new Area(ZoneType.DISCHARGE, "Alta / salida", col, middleTop, dischargeCols, middleHeight));
        areas.add(new Area(ZoneType.CORRIDOR, "Pasillo", 0, middleTop + middleHeight, planCols, CORRIDOR_ROWS));

        // Franja de abajo: los consultorios a la izquierda, cerca del pasillo vertical, y la sala del personal a
        // la derecha, lo más lejos de la entrada; cada uno en la mitad del ancho.
        Area consult = new Area(ZoneType.CONSULTATION, "Consultorios (" + doctorCount + ")", 0, staffTop,
                consultCols, staffRows);
        areas.add(consult);
        Area staff = new Area(ZoneType.STAFF_ROOM, "Sala del personal (" + staffCount + ")", consultCols, staffTop,
                staffCols, staffRows);
        areas.add(staff);
        Map<String, StaffSeat> staffSeats = seatStaff(staff, hospital.getDoctors(), hospital.getNurses());
        Map<String, Position> consultDesks = new HashMap<>();
        for (int index = 0; index < doctorCount; index++) {
            consultDesks.put(hospital.getDoctors().get(index).getCode(), consult.slot(index));
        }

        List<Position> triageSpots = new ArrayList<>();
        for (int index = 0; index < triageStations; index++) {
            triageSpots.add(new Position(triage.col() + 1, triage.row() + 1 + 2 * index));
        }
        List<Position> waitingSpots = new ArrayList<>();
        for (int index = 0; index < WAITING_COLS * WAITING_SLOT_ROWS; index++) {
            waitingSpots.add(waiting.slot(index));
        }
        List<Position> bloodSpots = new ArrayList<>();
        for (int index = 0; index < BloodType.values().length; index++) {
            bloodSpots.add(blood.slot(index));
        }
        List<Position> morgueSpots = new ArrayList<>();
        for (int index = 0; index < morgueCapacity; index++) {
            morgueSpots.add(morgue.slot(index));
        }
        return new HospitalLayout(planCols, staffTop + staffRows, areas, spots, triage.slot(0), triageSpots,
                waitingSpots, bloodSpots, morgueSpots, staffSeats, consultDesks, doorsOf(areas));
    }

    /**
     * Pone una puerta a cada sala, en el centro de su borde: hacia el pasillo de arriba si lo toca y, si no,
     * hacia el de abajo.
     */
    private static List<Door> doorsOf(List<Area> areas) {
        List<Area> corridors = areas.stream().filter(area -> area.zone() == ZoneType.CORRIDOR).toList();
        List<Door> doors = new ArrayList<>();
        for (Area room : areas) {
            if (room.zone() == ZoneType.CORRIDOR) {
                continue;
            }
            int center = room.col() + room.cols() / 2;
            Position above = new Position(center, Math.max(0, room.row() - 1));
            Position below = new Position(center, room.bottom());
            if (room.row() > 0 && isCorridor(corridors, above)) {
                doors.add(new Door(new Position(center, room.row()), above));
            } else if (isCorridor(corridors, below)) {
                doors.add(new Door(new Position(center, room.bottom() - 1), below));
            }
        }
        return doors;
    }

    private static boolean isCorridor(List<Area> corridors, Position cell) {
        return corridors.stream().anyMatch(corridor -> corridor.covers(cell));
    }

    /**
     * Pone los quirófanos en un bloque cuadrado. Las filas del bloque se reparten la altura de la franja y el
     * último quirófano se ensancha si su fila queda incompleta, para que el bloque no tenga huecos.
     */
    private void placeOperatingRooms(List<Area> areas, Map<String, Position> spots, Hospital hospital, int col,
                                     int top, int height, int perRow, int blockRows) {
        int rowHeight = height / blockRows;
        int rooms = hospital.getOperatingRooms().size();
        for (int index = 0; index < rooms; index++) {
            int blockRow = index / perRow;
            int rowTop = top + blockRow * rowHeight;
            int roomHeight = blockRow == blockRows - 1 ? top + height - rowTop : rowHeight;
            int roomCols = index == rooms - 1 ? (perRow - index % perRow) * OPERATING_COLS : OPERATING_COLS;
            Area room = new Area(ZoneType.OPERATING_ROOM, "Quirófano " + (index + 1),
                    col + (index % perRow) * OPERATING_COLS, rowTop, roomCols, roomHeight);
            areas.add(room);
            spots.put(hospital.getOperatingRooms().get(index).getCode(), room.slot(0));
        }
    }

    /**
     * Pone las camas con una casilla libre entre cada una a lo ancho y una fila libre entre cada fila de camas,
     * debajo del título: así el personal tiene espacio para pararse junto a cada paciente y los pies de una cama no
     * tocan la cabecera de la de abajo.
     */
    private void placeSpacedBeds(Map<String, Position> spots, Area area, List<Bed> beds) {
        int perRow = Math.max(1, area.cols() / BED_STEP);
        int offset = (area.cols() - (perRow - 1) * BED_STEP - 1) / 2;
        for (int index = 0; index < beds.size(); index++) {
            spots.put(beds.get(index).getCode(), new Position(area.col() + offset + index % perRow * BED_STEP,
                    area.row() + 1 + index / perRow * BED_STEP));
        }
    }

    /** Sienta al personal en las mesas de su sala, dos por mesa: primero los médicos y después los enfermeros. */
    private Map<String, StaffSeat> seatStaff(Area area, List<Doctor> doctors, List<Nurse> nurses) {
        List<String> codes = new ArrayList<>();
        doctors.forEach(doctor -> codes.add(doctor.getCode()));
        nurses.forEach(nurse -> codes.add(nurse.getCode()));
        Map<String, StaffSeat> seats = new HashMap<>();
        for (int index = 0; index < codes.size(); index++) {
            seats.put(codes.get(index), new StaffSeat(area.slot(index / StaffSeat.SEATS_PER_TABLE),
                    index % StaffSeat.SEATS_PER_TABLE));
        }
        return seats;
    }

    /** Columnas de una sala de camas: algo más ancha que alta, entre un mínimo y un máximo. */
    private static int columnsFor(int count, int min, int max) {
        int wanted = (int) Math.ceil(Math.sqrt(count * ASPECT));
        return Math.max(min, Math.min(max, wanted));
    }

    /** Filas que ocupan unas filas de camas con una fila libre entre cada dos. */
    private static int spacedRows(int bedRows) {
        return Math.max(1, bedRows * BED_STEP - 1);
    }

    private static int rowsFor(int count, int cols) {
        return (count + cols - 1) / cols;
    }
}