package com.medresource.services.simulation;

import com.medresource.model.Area;
import com.medresource.model.Bed;
import com.medresource.model.BloodType;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Nurse;
import com.medresource.model.Position;
import com.medresource.model.ZoneType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Arma el plano de un hospital: dónde está cada sala, cama, quirófano, médico y enfermero.
 *
 * <p>El plano sigue el recorrido del paciente y se arma en tres franjas, cada una con todas sus áreas de la
 * misma altura:</p>
 * <ol>
 *   <li>Arriba, de izquierda a derecha: el triage (con la entrada), la sala de espera y la hospitalización
 *       con todas sus camas.</li>
 *   <li>En medio: la UCI, el banco de sangre (entre la UCI y los quirófanos, que son los que más lo usan), un
 *       área por cada quirófano, la morgue y la zona de alta o salida.</li>
 *   <li>Abajo, a todo lo ancho: la sala del personal.</li>
 * </ol>
 *
 * <p>Las salas con muchas camas se ensanchan en columnas para que el plano no se alargue demasiado, y el
 * espacio que sobra en cada franja lo toma el último área para que ambas franjas terminen en el mismo borde.
 * El mismo hospital da siempre el mismo plano: no hay azar.</p>
 *
 * <p>Los recursos se ubican con los códigos que ya tienen en el hospital, así los eventos de la
 * simulación se pueden situar en el plano sin traducir nada.</p>
 */
public class LayoutFactory {

    private static final int TRIAGE_COLS = 2;
    /** Puestos de triage, en la segunda columna del área. */
    private static final int TRIAGE_STATIONS = 3;
    private static final int TRIAGE_MIN_ROWS = 1 + TRIAGE_STATIONS;
    /** Columnas de la sala de espera. */
    private static final int WAITING_COLS = 6;
    /** Filas de lugares de la sala de espera: {@code 6 × 6 = 36} sillas. */
    private static final int WAITING_SLOT_ROWS = 6;
    private static final int OPERATING_COLS = 2;
    private static final int OPERATING_ROWS = 2;
    private static final int BLOOD_COLS = 4;
    /** Título y dos filas de lugares: {@code 4 × 2 = 8} tipos de sangre. */
    private static final int BLOOD_ROWS = 3;
    private static final int MORGUE_MAX_COLS = 10;
    private static final int DISCHARGE_COLS = 4;
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

        // Anchos de las franjas: la más ancha manda y la otra reparte lo que le sobra.
        int bedCols = columnsFor(beds.size(), MIN_BED_COLS, MAX_BED_COLS);
        int icuCols = columnsFor(icuBeds.size(), MIN_ICU_COLS, MAX_ICU_COLS);
        int operatingPerRow = (int) Math.ceil(Math.sqrt(operatingRooms));
        int operatingBlockRows = (operatingRooms + operatingPerRow - 1) / operatingPerRow;
        int operatingCols = operatingPerRow * OPERATING_COLS;
        int morgueCols = Math.min(morgueCapacity, MORGUE_MAX_COLS);
        int topWidth = TRIAGE_COLS + WAITING_COLS + bedCols;
        int middleWidth = icuCols + BLOOD_COLS + operatingCols + morgueCols + DISCHARGE_COLS;
        int planCols = Math.max(MIN_PLAN_COLS, Math.max(topWidth, middleWidth));
        int wardCols = planCols - TRIAGE_COLS - WAITING_COLS;
        int dischargeCols = planCols - icuCols - BLOOD_COLS - operatingCols - morgueCols;

        // Alturas de las franjas: la sala más alta manda y las demás se estiran hasta ella.
        int topHeight = Math.max(Math.max(1 + WAITING_SLOT_ROWS, TRIAGE_MIN_ROWS), 1 + rowsFor(beds.size(), wardCols));
        int middleHeight = Math.max(Math.max(1 + rowsFor(icuBeds.size(), icuCols), operatingBlockRows * OPERATING_ROWS),
                Math.max(Math.max(1 + rowsFor(morgueCapacity, morgueCols), BLOOD_ROWS), DISCHARGE_ROWS));
        int staffTop = topHeight + middleHeight;
        int staffRows = 1 + rowsFor(staffCount, planCols);

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
        placeBeds(spots, ward, beds);

        // Franja del medio, de izquierda a derecha.
        int col = 0;
        Area icu = new Area(ZoneType.ICU, "UCI (" + icuBeds.size() + " camas)", col, topHeight, icuCols, middleHeight);
        areas.add(icu);
        placeBeds(spots, icu, icuBeds);
        col += icuCols;
        Area blood = new Area(ZoneType.BLOOD_BANK, "Banco de sangre", col, topHeight, BLOOD_COLS, middleHeight);
        areas.add(blood);
        col += BLOOD_COLS;
        placeOperatingRooms(areas, spots, hospital, col, topHeight, middleHeight, operatingPerRow, operatingBlockRows);
        col += operatingCols;
        Area morgue = new Area(ZoneType.MORGUE, "Morgue", col, topHeight, morgueCols, middleHeight);
        areas.add(morgue);
        col += morgueCols;
        areas.add(new Area(ZoneType.DISCHARGE, "Alta / salida", col, topHeight, dischargeCols, middleHeight));

        // Franja de abajo.
        Area staff = new Area(ZoneType.STAFF_ROOM, "Sala del personal (" + staffCount + ")", 0, staffTop,
                planCols, staffRows);
        areas.add(staff);
        placeStaff(spots, staff, hospital.getDoctors(), hospital.getNurses());

        List<Position> triageSpots = new ArrayList<>();
        for (int index = 0; index < TRIAGE_STATIONS; index++) {
            triageSpots.add(triage.slot(1 + 2 * index));
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
                waitingSpots, bloodSpots, morgueSpots);
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

    /** Pone cada cama en un lugar del área, en el orden en que el hospital las tiene. */
    private void placeBeds(Map<String, Position> spots, Area area, List<Bed> beds) {
        for (int index = 0; index < beds.size(); index++) {
            spots.put(beds.get(index).getCode(), area.slot(index));
        }
    }

    /** Pone primero a los médicos y después a los enfermeros, un lugar cada uno. */
    private void placeStaff(Map<String, Position> spots, Area area, List<Doctor> doctors, List<Nurse> nurses) {
        List<String> codes = new ArrayList<>();
        doctors.forEach(doctor -> codes.add(doctor.getCode()));
        nurses.forEach(nurse -> codes.add(nurse.getCode()));
        for (int index = 0; index < codes.size(); index++) {
            spots.put(codes.get(index), area.slot(index));
        }
    }

    /** Columnas de una sala de camas: algo más ancha que alta, entre un mínimo y un máximo. */
    private static int columnsFor(int count, int min, int max) {
        int wanted = (int) Math.ceil(Math.sqrt(count * ASPECT));
        return Math.max(min, Math.min(max, wanted));
    }

    private static int rowsFor(int count, int cols) {
        return (count + cols - 1) / cols;
    }
}