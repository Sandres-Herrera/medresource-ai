package com.medresource.services.simulation;

import com.medresource.model.Bed;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.Position;
import com.medresource.model.ZoneType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del armado del plano del hospital con hospitales de distinto tamaño.
 */
class LayoutFactoryTest {

    private static final HospitalConfig SMALLEST = new HospitalConfig(1, 1, 1, 1, 1, 0, 1);
    private static final HospitalConfig LARGEST = new HospitalConfig(
            HospitalConfig.MAX_BEDS, HospitalConfig.MAX_ICU_BEDS, HospitalConfig.MAX_OPERATING_ROOMS,
            HospitalConfig.MAX_DOCTORS, HospitalConfig.MAX_NURSES, HospitalConfig.MAX_BLOOD_UNITS_PER_TYPE,
            HospitalConfig.MAX_MORGUE_CAPACITY);

    private final LayoutFactory factory = new LayoutFactory();

    private static Hospital hospital(HospitalConfig config) {
        return new HospitalFactory().create(config);
    }

    private HospitalLayout layoutOf(HospitalConfig config) {
        return factory.create(hospital(config));
    }

    private static List<HospitalConfig> sizes() {
        return List.of(SMALLEST, HospitalConfig.standard(), LARGEST, new HospitalConfig(7, 5, 9, 3, 2, 1, 11));
    }

    // ---------- recepción, habitaciones y UCI ----------

    @Test
    void doesNotCreateTheLayoutOfNoHospital() {
        assertThrows(IllegalArgumentException.class, () -> factory.create(null));
    }

    @Test
    void alwaysHasTheSameNumberOfColumns() {
        for (HospitalConfig config : sizes()) {
            assertEquals(LayoutFactory.COLUMNS, layoutOf(config).getCols());
        }
    }

    @Test
    void theSameHospitalGivesTheSameLayout() {
        HospitalLayout first = layoutOf(HospitalConfig.standard());
        HospitalLayout second = layoutOf(HospitalConfig.standard());

        assertEquals(first.getRows(), second.getRows());
        assertEquals(first.getResourceCodes(), second.getResourceCodes());
        for (String code : first.getResourceCodes()) {
            assertEquals(first.positionOf(code), second.positionOf(code));
        }
        assertEquals(first.getWaitingSpots(), second.getWaitingSpots());
    }

    @Test
    void aBiggerHospitalNeedsMoreRows() {
        assertTrue(layoutOf(LARGEST).getRows() > layoutOf(HospitalConfig.standard()).getRows());
        assertTrue(layoutOf(HospitalConfig.standard()).getRows() > layoutOf(SMALLEST).getRows());
    }

    @Test
    void theReceptionIsAtTheTop() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());

        assertEquals(new Position(0, 2), layout.getEntrance());
        assertEquals(ZoneType.ENTRANCE, layout.zoneAt(layout.getEntrance()));
        assertEquals(ZoneType.TRIAGE, layout.zoneAt(layout.getTriagePoint()));
        assertEquals(10, layout.countOf(ZoneType.ENTRANCE));
        assertEquals(40, layout.countOf(ZoneType.TRIAGE));
        assertEquals(75, layout.countOf(ZoneType.WAITING_ROOM));
    }

    @Test
    void theWaitingRoomHasSeatsLikeAChessBoard() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());

        assertEquals(37, layout.getWaitingSpots().size());
        assertEquals(37, new HashSet<>(layout.getWaitingSpots()).size());
        for (Position seat : layout.getWaitingSpots()) {
            assertEquals(ZoneType.WAITING_ROOM, layout.zoneAt(seat));
            assertEquals(0, (seat.col() + seat.row()) % 2);
        }
    }

    @Test
    void everyBedHasASquareInTheRightZone() {
        for (HospitalConfig config : sizes()) {
            Hospital hospital = hospital(config);
            HospitalLayout layout = factory.create(hospital);

            for (Bed bed : hospital.getBeds()) {
                assertEquals(ZoneType.HOSPITALIZATION, layout.zoneAt(layout.positionOf(bed.getCode()).orElseThrow()),
                        bed.getCode());
            }
            for (Bed bed : hospital.getIcuBeds()) {
                assertEquals(ZoneType.ICU, layout.zoneAt(layout.positionOf(bed.getCode()).orElseThrow()),
                        bed.getCode());
            }
        }
    }

    @Test
    void theBedsOfARoomAreTogetherInASquareOfTwoByTwo() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());

        assertEquals(new Position(1, 6), layout.positionOf("H1-C1").orElseThrow());
        assertEquals(new Position(2, 6), layout.positionOf("H1-C2").orElseThrow());
        assertEquals(new Position(1, 7), layout.positionOf("H1-C3").orElseThrow());
        assertEquals(new Position(2, 7), layout.positionOf("H1-C4").orElseThrow());
        assertEquals(new Position(4, 6), layout.positionOf("H2-C1").orElseThrow());
    }

    @Test
    void theRoomsWrapToANewRowWhenTheLineIsFull() {
        HospitalLayout layout = layoutOf(LARGEST);

        assertEquals(new Position(31, 6), layout.positionOf("H11-C1").orElseThrow());
        assertEquals(new Position(1, 9), layout.positionOf("H12-C1").orElseThrow());
    }

    @Test
    void theIcuBedsFollowTheRoomsInGroupsOfFour() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());

        assertEquals(new Position(1, 9), layout.positionOf("UCI-C1").orElseThrow());
        assertEquals(new Position(2, 10), layout.positionOf("UCI-C4").orElseThrow());
        assertEquals(new Position(4, 9), layout.positionOf("UCI-C5").orElseThrow());
        assertEquals(8, layout.countOf(ZoneType.ICU));
    }

    // ---------- quirófanos, morgue y personal ----------

    @Test
    void everyOperatingRoomHasItsTableInsideItsBlock() {
        for (HospitalConfig config : sizes()) {
            Hospital hospital = hospital(config);
            HospitalLayout layout = factory.create(hospital);

            for (OperatingRoom room : hospital.getOperatingRooms()) {
                assertEquals(ZoneType.OPERATING_ROOM, layout.zoneAt(layout.positionOf(room.getCode()).orElseThrow()),
                        room.getCode());
            }
            assertEquals(6 * hospital.getOperatingRooms().size(), layout.countOf(ZoneType.OPERATING_ROOM));
        }
    }

    @Test
    void theOperatingRoomsFollowTheIcu() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());

        assertEquals(new Position(2, 12), layout.positionOf("Q1").orElseThrow());
        assertEquals(new Position(6, 12), layout.positionOf("Q2").orElseThrow());
    }

    @Test
    void theMorgueHasOneSpotForEachPlaceOfCapacity() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);

            assertEquals(config.morgueCapacity(), layout.getMorgueSpots().size());
            assertEquals(config.morgueCapacity(), layout.countOf(ZoneType.MORGUE));
            assertEquals(config.morgueCapacity(), new HashSet<>(layout.getMorgueSpots()).size());
        }
    }

    @Test
    void theMorgueFillsRowsOfTenSpots() {
        HospitalLayout layout = layoutOf(LARGEST);

        assertEquals(new Position(1, 36), layout.getMorgueSpots().get(0));
        assertEquals(new Position(10, 36), layout.getMorgueSpots().get(9));
        assertEquals(new Position(1, 37), layout.getMorgueSpots().get(10));
        assertEquals(new Position(10, 40), layout.getMorgueSpots().get(49));
    }

    @Test
    void everyDoctorAndNurseHasASeatInTheStaffRoom() {
        for (HospitalConfig config : sizes()) {
            Hospital hospital = hospital(config);
            HospitalLayout layout = factory.create(hospital);

            for (Doctor doctor : hospital.getDoctors()) {
                assertEquals(ZoneType.STAFF_ROOM, layout.zoneAt(layout.positionOf(doctor.getCode()).orElseThrow()));
            }
            for (Nurse nurse : hospital.getNurses()) {
                assertEquals(ZoneType.STAFF_ROOM, layout.zoneAt(layout.positionOf(nurse.getCode()).orElseThrow()));
            }
            assertEquals(config.doctors() + config.nurses(), layout.countOf(ZoneType.STAFF_ROOM));
        }
    }

    @Test
    void theStaffRoomIsAtTheBottomWithDoctorsFirst() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());
        int last = layout.getRows() - 1;

        assertEquals(new Position(0, last - 1), layout.positionOf("D01").orElseThrow());
        assertEquals(new Position(15, last - 1), layout.positionOf("D16").orElseThrow());
        assertEquals(new Position(16, last - 1), layout.positionOf("E01").orElseThrow());
        assertEquals(new Position(3, last), layout.positionOf("E24").orElseThrow());
    }

    @Test
    void everyResourceHasItsOwnSquare() {
        for (HospitalConfig config : sizes()) {
            Hospital hospital = hospital(config);
            HospitalLayout layout = factory.create(hospital);
            List<String> codes = new ArrayList<>();
            hospital.getBeds().forEach(bed -> codes.add(bed.getCode()));
            hospital.getIcuBeds().forEach(bed -> codes.add(bed.getCode()));
            hospital.getOperatingRooms().forEach(room -> codes.add(room.getCode()));
            hospital.getDoctors().forEach(doctor -> codes.add(doctor.getCode()));
            hospital.getNurses().forEach(nurse -> codes.add(nurse.getCode()));

            assertEquals(new HashSet<>(codes), layout.getResourceCodes());
            Set<Position> squares = new HashSet<>();
            codes.forEach(code -> squares.add(layout.positionOf(code).orElseThrow()));
            layout.getMorgueSpots().forEach(squares::add);
            assertEquals(codes.size() + config.morgueCapacity(), squares.size());
        }
    }

    @Test
    void zonesOfDifferentKindsBelowTheReceptionNeverTouch() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);

            for (int row = 6; row < layout.getRows(); row++) {
                for (int col = 0; col < layout.getCols(); col++) {
                    ZoneType here = layout.zoneAt(new Position(col, row));
                    if (col + 1 < layout.getCols()) {
                        assertTouchOnlyIfSame(here, layout.zoneAt(new Position(col + 1, row)));
                    }
                    if (row + 1 < layout.getRows()) {
                        assertTouchOnlyIfSame(here, layout.zoneAt(new Position(col, row + 1)));
                    }
                }
            }
        }
    }

    private static void assertTouchOnlyIfSame(ZoneType first, ZoneType second) {
        if (first != ZoneType.CORRIDOR && second != ZoneType.CORRIDOR) {
            assertEquals(first, second);
        }
    }
}