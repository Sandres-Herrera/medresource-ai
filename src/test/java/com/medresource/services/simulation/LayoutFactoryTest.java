package com.medresource.services.simulation;

import com.medresource.model.Area;
import com.medresource.model.Bed;
import com.medresource.model.BloodType;
import com.medresource.model.Doctor;
import com.medresource.model.Door;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.Position;
import com.medresource.model.Route;
import com.medresource.model.StaffSeat;
import com.medresource.model.ZoneType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        return List.of(SMALLEST, HospitalConfig.standard(), LARGEST, new HospitalConfig(7, 5, 9, 3, 2, 1, 11),
                new HospitalConfig(60, 12, 8, 32, 48, 20, 10), new HospitalConfig(30, 22, 5, 10, 10, 5, 10),
                new HospitalConfig(200, 1, 1, 1, 1, 0, 1));
    }

    private static Area onlyAreaOf(HospitalLayout layout, ZoneType zone) {
        List<Area> found = layout.areasOf(zone);
        assertEquals(1, found.size(), "Áreas de " + zone);
        return found.get(0);
    }

    // ---------- datos del hospital ----------

    @Test
    void doesNotCreateTheLayoutOfNoHospital() {
        assertThrows(IllegalArgumentException.class, () -> factory.create(null));
    }

    @Test
    void givesASpotToEveryResourceOfTheHospital() {
        for (HospitalConfig config : sizes()) {
            Hospital hospital = hospital(config);
            HospitalLayout layout = factory.create(hospital);
            Set<String> expected = new HashSet<>();
            hospital.getBeds().forEach(bed -> expected.add(bed.getCode()));
            hospital.getIcuBeds().forEach(bed -> expected.add(bed.getCode()));
            hospital.getOperatingRooms().forEach(room -> expected.add(room.getCode()));
            Set<String> staff = new HashSet<>();
            hospital.getDoctors().forEach(doctor -> staff.add(doctor.getCode()));
            hospital.getNurses().forEach(nurse -> staff.add(nurse.getCode()));

            assertEquals(expected, layout.getResourceCodes());
            assertEquals(staff, layout.getStaffCodes());
        }
    }

    @Test
    void putsEachResourceInTheAreaOfItsKind() {
        for (HospitalConfig config : sizes()) {
            Hospital hospital = hospital(config);
            HospitalLayout layout = factory.create(hospital);

            for (Bed bed : hospital.getBeds()) {
                assertEquals(ZoneType.HOSPITALIZATION, zoneOf(layout, bed.getCode()));
            }
            for (Bed bed : hospital.getIcuBeds()) {
                assertEquals(ZoneType.ICU, zoneOf(layout, bed.getCode()));
            }
            for (OperatingRoom room : hospital.getOperatingRooms()) {
                assertEquals(ZoneType.OPERATING_ROOM, zoneOf(layout, room.getCode()));
            }
            for (Doctor doctor : hospital.getDoctors()) {
                assertEquals(ZoneType.STAFF_ROOM, zoneOf(layout, doctor.getCode()));
            }
            for (Nurse nurse : hospital.getNurses()) {
                assertEquals(ZoneType.STAFF_ROOM, zoneOf(layout, nurse.getCode()));
            }
        }
    }

    private static ZoneType zoneOf(HospitalLayout layout, String code) {
        return layout.areaAt(layout.positionOf(code).orElseThrow()).orElseThrow().zone();
    }

    @Test
    void putsTheBedsInTheOrderTheHospitalHasThemWithAFreeCellAndAFreeRowBetweenThem() {
        Hospital hospital = hospital(HospitalConfig.standard());
        HospitalLayout layout = factory.create(hospital);
        Area ward = onlyAreaOf(layout, ZoneType.HOSPITALIZATION);

        Position previous = null;
        for (Bed bed : hospital.getBeds()) {
            Position spot = layout.positionOf(bed.getCode()).orElseThrow();
            assertTrue(ward.containsSlot(spot), "debajo del título de la sala: " + spot);
            if (previous != null && previous.row() == spot.row()) {
                assertEquals(previous.col() + 2, spot.col(), "una casilla libre entre dos camas");
            } else if (previous != null) {
                assertEquals(previous.row() + 2, spot.row(), "una fila libre entre dos filas de camas");
            }
            previous = spot;
        }
    }

    @Test
    void putsTheDoctorsBeforeTheNursesInTheStaffRoom() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());
        Area staff = onlyAreaOf(layout, ZoneType.STAFF_ROOM);

        assertEquals(new StaffSeat(staff.slot(0), 0), layout.staffSeatOf("D01").orElseThrow());
        assertEquals(new StaffSeat(staff.slot(0), 1), layout.staffSeatOf("D02").orElseThrow());
        assertEquals(new StaffSeat(staff.slot(8), 0), layout.staffSeatOf("E01").orElseThrow());
    }

    @Test
    void theStaffRoomHasASeatForEveryone() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            Area staff = onlyAreaOf(layout, ZoneType.STAFF_ROOM);

            assertEquals(config.doctors() + config.nurses(), layout.getStaffCodes().size());
            assertTrue(staff.slotCount() * StaffSeat.SEATS_PER_TABLE >= config.doctors() + config.nurses());
        }
    }

    @Test
    void twoStaffMembersShareEachTableSoTheStaffRoomFitsInHalfThePlan() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());
        Area staff = onlyAreaOf(layout, ZoneType.STAFF_ROOM);

        assertEquals(12, staff.cols());
        assertEquals(3, staff.rows());
        assertEquals(layout.staffSeatOf("D03").orElseThrow().table(),
                layout.staffSeatOf("D04").orElseThrow().table());
    }

    @Test
    void eachDoctorHasADeskInTheConsultingRooms() {
        for (HospitalConfig config : sizes()) {
            Hospital hospital = hospital(config);
            HospitalLayout layout = factory.create(hospital);
            Area consult = onlyAreaOf(layout, ZoneType.CONSULTATION);

            for (int index = 0; index < hospital.getDoctors().size(); index++) {
                String doctor = hospital.getDoctors().get(index).getCode();
                assertEquals(consult.slot(index), layout.consultDeskOf(doctor).orElseThrow());
            }
            assertTrue(layout.consultDeskOf(hospital.getNurses().get(0).getCode()).isEmpty());
            assertEquals("Consultorios (" + config.doctors() + ")", consult.title());
        }
    }

    // ---------- áreas ----------

    @Test
    void hasOneAreaOfEachKindAndOneForEachOperatingRoom() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);

            assertEquals(1, layout.areasOf(ZoneType.TRIAGE).size());
            assertEquals(1, layout.areasOf(ZoneType.WAITING_ROOM).size());
            assertEquals(1, layout.areasOf(ZoneType.HOSPITALIZATION).size());
            assertEquals(1, layout.areasOf(ZoneType.ICU).size());
            assertEquals(config.operatingRooms(), layout.areasOf(ZoneType.OPERATING_ROOM).size());
            assertEquals(1, layout.areasOf(ZoneType.BLOOD_BANK).size());
            assertEquals(1, layout.areasOf(ZoneType.MORGUE).size());
            assertEquals(1, layout.areasOf(ZoneType.DISCHARGE).size());
            assertEquals(1, layout.areasOf(ZoneType.STAFF_ROOM).size());
        }
    }

    @Test
    void titlesTheAreasWithTheirCounts() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());

        assertEquals("Triage", onlyAreaOf(layout, ZoneType.TRIAGE).title());
        assertEquals("Banco de sangre", onlyAreaOf(layout, ZoneType.BLOOD_BANK).title());
        assertEquals("Sala de espera", onlyAreaOf(layout, ZoneType.WAITING_ROOM).title());
        assertEquals("Hospitalización (30 camas)", onlyAreaOf(layout, ZoneType.HOSPITALIZATION).title());
        assertEquals("UCI (6 camas)", onlyAreaOf(layout, ZoneType.ICU).title());
        assertEquals("Morgue", onlyAreaOf(layout, ZoneType.MORGUE).title());
        assertEquals("Alta / salida", onlyAreaOf(layout, ZoneType.DISCHARGE).title());
        assertEquals("Sala del personal (40)", onlyAreaOf(layout, ZoneType.STAFF_ROOM).title());
    }

    @Test
    void numbersTheOperatingRoomsFromOne() {
        HospitalLayout layout = layoutOf(new HospitalConfig(7, 5, 9, 3, 2, 1, 11));
        List<String> titles = new ArrayList<>();
        layout.areasOf(ZoneType.OPERATING_ROOM).forEach(area -> titles.add(area.title()));

        assertEquals(List.of("Quirófano 1", "Quirófano 2", "Quirófano 3", "Quirófano 4", "Quirófano 5",
                "Quirófano 6", "Quirófano 7", "Quirófano 8", "Quirófano 9"), titles);
    }

    @Test
    void groupsTheOperatingRoomsInASquareBlock() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());
        Position q1 = layout.positionOf("Q1").orElseThrow();
        Position q2 = layout.positionOf("Q2").orElseThrow();
        Position q3 = layout.positionOf("Q3").orElseThrow();

        assertEquals(q1.row(), q2.row());
        assertTrue(q2.col() > q1.col());
        assertEquals(q1.col(), q3.col());
        assertTrue(q3.row() > q1.row());
    }

    @Test
    void eachOperatingRoomHasItsTableInItsOwnArea() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());
        List<Area> rooms = layout.areasOf(ZoneType.OPERATING_ROOM);

        for (int index = 0; index < rooms.size(); index++) {
            assertEquals(rooms.get(index), layout.areaAt(layout.positionOf("Q" + (index + 1)).orElseThrow()).orElseThrow());
        }
    }

    @Test
    void theWardTakesTheWidthThatTriageAndTheWaitingRoomLeave() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            Area ward = onlyAreaOf(layout, ZoneType.HOSPITALIZATION);

            assertEquals(layout.getCols() - 9, ward.cols());
            assertEquals(layout.getCols(), ward.right());
        }
    }

    @Test
    void theIcuGrowsWiderWithTheNumberOfBedsUpToALimit() {
        assertEquals(6, onlyAreaOf(layoutOf(SMALLEST), ZoneType.ICU).cols());
        assertEquals(18, onlyAreaOf(layoutOf(LARGEST), ZoneType.ICU).cols());
    }

    @Test
    void theWardHasRoomForAllItsBeds() {
        for (HospitalConfig config : sizes()) {
            Area ward = onlyAreaOf(layoutOf(config), ZoneType.HOSPITALIZATION);

            assertTrue(ward.slotCount() >= config.hospitalizationBeds());
        }
    }

    // ---------- orden del plano ----------

    @Test
    void followsThePatientFromLeftToRightAcrossTheTopBand() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            Area triage = onlyAreaOf(layout, ZoneType.TRIAGE);
            Area waiting = onlyAreaOf(layout, ZoneType.WAITING_ROOM);
            Area ward = onlyAreaOf(layout, ZoneType.HOSPITALIZATION);

            assertEquals(0, triage.col());
            assertEquals(triage.right(), waiting.col());
            assertEquals(waiting.right(), ward.col());
            assertEquals(0, triage.row());
            assertEquals(0, waiting.row());
            assertEquals(0, ward.row());
        }
    }

    @Test
    void theAreasOfTheTopBandAllHaveTheSameHeight() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            int height = onlyAreaOf(layout, ZoneType.TRIAGE).rows();

            assertEquals(height, onlyAreaOf(layout, ZoneType.WAITING_ROOM).rows());
            assertEquals(height, onlyAreaOf(layout, ZoneType.HOSPITALIZATION).rows());
        }
    }

    @Test
    void theMiddleBandIsUnderTheTopBandAndHasNoGapsNorHolesInItsWidth() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            Area top = onlyAreaOf(layout, ZoneType.TRIAGE);
            Area icu = onlyAreaOf(layout, ZoneType.ICU);
            Area blood = onlyAreaOf(layout, ZoneType.BLOOD_BANK);
            Area morgue = onlyAreaOf(layout, ZoneType.MORGUE);
            Area discharge = onlyAreaOf(layout, ZoneType.DISCHARGE);
            List<Area> rooms = layout.areasOf(ZoneType.OPERATING_ROOM);
            int roomsRight = rooms.stream().mapToInt(Area::right).max().orElseThrow();

            assertEquals(top.bottom() + 2, icu.row());
            assertEquals(0, icu.col());
            assertEquals(icu.right() + 2, blood.col());
            assertEquals(blood.right(), rooms.get(0).col());
            assertEquals(roomsRight, morgue.col());
            assertEquals(morgue.right(), discharge.col());
            assertEquals(layout.getCols(), discharge.right());
        }
    }

    @Test
    void theAreasOfTheMiddleBandAllReachTheSameBottom() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            int bottom = onlyAreaOf(layout, ZoneType.ICU).bottom();

            assertEquals(bottom, onlyAreaOf(layout, ZoneType.BLOOD_BANK).bottom());
            assertEquals(bottom, onlyAreaOf(layout, ZoneType.MORGUE).bottom());
            assertEquals(bottom, onlyAreaOf(layout, ZoneType.DISCHARGE).bottom());
            assertEquals(bottom, layout.areasOf(ZoneType.OPERATING_ROOM).stream().mapToInt(Area::bottom).max().orElseThrow());
            assertEquals(bottom + 2, onlyAreaOf(layout, ZoneType.STAFF_ROOM).row());
        }
    }

    // ---------- pasillos y puertas ----------

    @Test
    void aCorridorTwoCellsHighCrossesThePlanBetweenEachTwoBands() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            List<Area> corridors = layout.areasOf(ZoneType.CORRIDOR).stream()
                    .filter(corridor -> corridor.cols() > 2).toList();

            assertEquals(2, corridors.size());
            for (Area corridor : corridors) {
                assertEquals(0, corridor.col());
                assertEquals(layout.getCols(), corridor.cols());
                assertEquals(2, corridor.rows());
            }
            assertEquals(onlyAreaOf(layout, ZoneType.TRIAGE).bottom(), corridors.get(0).row());
            assertEquals(onlyAreaOf(layout, ZoneType.ICU).bottom(), corridors.get(1).row());
        }
    }

    @Test
    void aVerticalCorridorBetweenTheIcuAndTheBloodBankJoinsBothCorridors() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            Area icu = onlyAreaOf(layout, ZoneType.ICU);
            List<Area> vertical = layout.areasOf(ZoneType.CORRIDOR).stream()
                    .filter(corridor -> corridor.cols() == 2).toList();

            assertEquals(1, vertical.size());
            Area corridor = vertical.get(0);
            assertEquals(icu.right(), corridor.col());
            assertEquals(onlyAreaOf(layout, ZoneType.BLOOD_BANK).col(), corridor.right());
            assertEquals(icu.row(), corridor.row());
            assertEquals(icu.bottom(), corridor.bottom());
        }
    }

    @Test
    void everyRoomHasOneDoorInTheMiddleOfItsEdgeTowardsACorridor() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);

            for (Area room : layout.getAreas()) {
                if (room.zone() == ZoneType.CORRIDOR) {
                    continue;
                }
                List<Door> doors = layout.doorsOf(room);
                assertEquals(1, doors.size(), room.title());
                assertEquals(room.col() + room.cols() / 2, doors.get(0).inside().col(), room.title());
            }
        }
    }

    @Test
    void theRoomsOfTheTopBandOpenDownwardsAndTheOthersUpwardsWhenTheyCan() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());
        Area triage = onlyAreaOf(layout, ZoneType.TRIAGE);
        Area icu = onlyAreaOf(layout, ZoneType.ICU);
        List<Area> rooms = layout.areasOf(ZoneType.OPERATING_ROOM);
        Area lowerRoom = rooms.get(rooms.size() - 1);

        assertEquals(triage.bottom(), layout.doorsOf(triage).get(0).outside().row());
        assertEquals(icu.row() - 1, layout.doorsOf(icu).get(0).outside().row());
        assertEquals(lowerRoom.bottom(), layout.doorsOf(lowerRoom).get(0).outside().row());
    }

    // ---------- todo el plano está conectado ----------

    @Test
    void fromTheEntranceEveryRoomAndCorridorCanBeReached() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            PathFinder finder = new PathFinder(layout);

            for (Area area : layout.getAreas()) {
                Position inside = area.zone() == ZoneType.CORRIDOR
                        ? area.slot(0)
                        : layout.doorsOf(area).get(0).inside();

                assertTrue(finder.find(layout.getEntrance(), inside).isPresent(), config + " " + area.title());
            }
        }
    }

    @Test
    void everyPlaceWherePeopleGoCanBeReachedFromTheEntrance() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            PathFinder finder = new PathFinder(layout);
            List<Position> places = new ArrayList<>();
            places.addAll(layout.getTriageSpots());
            places.addAll(layout.getWaitingSpots());
            places.addAll(layout.getMorgueSpots());
            for (String code : layout.getResourceCodes()) {
                places.add(layout.positionOf(code).orElseThrow());
            }
            for (String code : layout.getStaffCodes()) {
                places.add(layout.staffSeatOf(code).orElseThrow().table());
                layout.consultDeskOf(code).ifPresent(places::add);
            }

            for (Position place : places) {
                assertTrue(finder.find(layout.getEntrance(), place).isPresent(), config + " " + place);
            }
        }
    }

    @Test
    void theOperatingRoomsOfTheLowerRowAreReachedThroughTheLowerCorridor() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());
        List<Area> rooms = layout.areasOf(ZoneType.OPERATING_ROOM);
        Area lowerRoom = rooms.get(rooms.size() - 1);
        Door door = layout.doorsOf(lowerRoom).get(0);

        Route route = new PathFinder(layout).find(layout.getEntrance(), lowerRoom.slot(0)).orElseThrow();

        assertEquals(lowerRoom.bottom(), door.outside().row());
        assertTrue(route.passesThrough(door.outside()));
        assertTrue(route.passesThrough(door.inside()));
    }

    @Test
    void theOperatingRoomsFillTheirBlockWithoutHoles() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            List<Area> rooms = layout.areasOf(ZoneType.OPERATING_ROOM);
            int right = rooms.stream().mapToInt(Area::right).max().orElseThrow();
            Area morgue = onlyAreaOf(layout, ZoneType.MORGUE);

            assertEquals(right, morgue.col());
            for (Area room : rooms) {
                boolean lastOfItsRow = rooms.stream().noneMatch(other -> other.row() == room.row() && other.col() > room.col());
                if (lastOfItsRow) {
                    assertEquals(right, room.right());
                }
            }
        }
    }

    @Test
    void theBloodBankIsBetweenTheIcuAndTheOperatingRooms() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());
        Area icu = onlyAreaOf(layout, ZoneType.ICU);
        Area blood = onlyAreaOf(layout, ZoneType.BLOOD_BANK);
        Area firstRoom = layout.areasOf(ZoneType.OPERATING_ROOM).get(0);

        assertTrue(icu.right() <= blood.col());
        assertTrue(blood.right() <= firstRoom.col());
    }

    // ---------- sala de espera, entrada y morgue ----------

    @Test
    void theWaitingRoomHasThirtySixSeats() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            Area waiting = onlyAreaOf(layout, ZoneType.WAITING_ROOM);

            assertEquals(36, layout.getWaitingSpots().size());
            assertEquals(36, new HashSet<>(layout.getWaitingSpots()).size());
            layout.getWaitingSpots().forEach(spot -> assertEquals(waiting, layout.areaAt(spot).orElseThrow()));
        }
    }

    @Test
    void theEntranceIsInTheTriageAndNextToThreeTriageStations() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            Area triage = onlyAreaOf(layout, ZoneType.TRIAGE);

            assertEquals(triage, layout.areaAt(layout.getEntrance()).orElseThrow());
            assertEquals(triage.slot(0), layout.getEntrance());
            assertEquals(3, layout.getTriageSpots().size());
            assertEquals(3, new HashSet<>(layout.getTriageSpots()).size());
            assertTrue(!layout.getTriageSpots().contains(layout.getEntrance()));
            layout.getTriageSpots().forEach(spot -> assertEquals(triage, layout.areaAt(spot).orElseThrow()));
            layout.getTriageSpots().forEach(spot -> assertEquals(triage.col() + 1, spot.col()));
        }
    }

    @Test
    void eachTriageStationOfTheHospitalHasItsPlace() {
        for (HospitalConfig config : sizes()) {
            Hospital hospital = hospital(config);
            HospitalLayout layout = new LayoutFactory().create(hospital);

            for (int index = 0; index < hospital.getTriageStations().size(); index++) {
                assertEquals(layout.getTriageSpots().get(index),
                        layout.positionOf(hospital.getTriageStations().get(index).getCode()).orElseThrow());
            }
        }
    }

    @Test
    void theTriageHasRoomForAQueueNextToTheEntrance() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            Area triage = onlyAreaOf(layout, ZoneType.TRIAGE);

            assertTrue(layout.getTriageQueueSpots().size() >= 6);
            layout.getTriageQueueSpots().forEach(spot -> assertEquals(triage, layout.areaAt(spot).orElseThrow()));
        }
    }

    @Test
    void theBloodBankHasOnePlaceForEachBloodType() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            Area blood = onlyAreaOf(layout, ZoneType.BLOOD_BANK);

            assertEquals(BloodType.values().length, layout.getBloodSpots().size());
            assertEquals(BloodType.values().length, new HashSet<>(layout.getBloodSpots()).size());
            layout.getBloodSpots().forEach(spot -> assertEquals(blood, layout.areaAt(spot).orElseThrow()));
        }
    }

    @Test
    void theMorgueHasOnePlaceForEachSpaceOfTheMorgue() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            Area morgue = onlyAreaOf(layout, ZoneType.MORGUE);

            assertEquals(config.morgueCapacity(), layout.getMorgueSpots().size());
            assertEquals(new HashSet<>(layout.getMorgueSpots()).size(), layout.getMorgueSpots().size());
            layout.getMorgueSpots().forEach(spot -> assertEquals(morgue, layout.areaAt(spot).orElseThrow()));
        }
    }

    @Test
    void theMorgueIsAtMostTenPlacesWide() {
        assertEquals(10, onlyAreaOf(layoutOf(LARGEST), ZoneType.MORGUE).cols());
        assertEquals(5, onlyAreaOf(layoutOf(HospitalConfig.standard()), ZoneType.MORGUE).cols());
    }

    // ---------- tamaño y estabilidad ----------

    @Test
    void thePlanIsNeverNarrowerThanItsWidestBand() {
        assertEquals(18, layoutOf(SMALLEST).getCols());
    }

    @Test
    void thePlanHasTheStandardSize() {
        HospitalLayout layout = layoutOf(HospitalConfig.standard());

        assertEquals(24, layout.getCols());
        assertEquals(21, layout.getRows());
    }

    @Test
    void theConsultingRoomsAreOnTheLeftAndTheStaffRoomFarthestOnTheRightOfTheBottomBand() {
        for (HospitalConfig config : sizes()) {
            HospitalLayout layout = layoutOf(config);
            Area staff = onlyAreaOf(layout, ZoneType.STAFF_ROOM);
            Area consult = onlyAreaOf(layout, ZoneType.CONSULTATION);

            assertEquals(0, consult.col());
            assertEquals((layout.getCols() + 1) / 2, staff.cols());
            assertEquals(consult.right(), staff.col());
            assertEquals(layout.getCols(), staff.right());
            assertEquals(staff.row(), consult.row());
            assertEquals(layout.getRows(), staff.bottom());
            assertEquals(layout.getRows(), consult.bottom());
        }
    }

    @Test
    void theSameHospitalAlwaysGivesTheSamePlan() {
        HospitalLayout first = layoutOf(HospitalConfig.standard());
        HospitalLayout second = layoutOf(HospitalConfig.standard());

        assertEquals(first.getAreas(), second.getAreas());
        assertEquals(first.getEntrance(), second.getEntrance());
        assertEquals(first.getMorgueSpots(), second.getMorgueSpots());
        assertEquals(first.getResourceCodes(), second.getResourceCodes());
        for (String code : first.getResourceCodes()) {
            assertEquals(first.positionOf(code), second.positionOf(code));
        }
    }

    @Test
    void aBiggerHospitalGivesABiggerPlan() {
        assertNotEquals(layoutOf(SMALLEST).getRows(), layoutOf(LARGEST).getRows());
        assertTrue(layoutOf(LARGEST).getRows() > layoutOf(SMALLEST).getRows());
    }
}