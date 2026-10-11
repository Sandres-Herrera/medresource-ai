package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del hospital: composición de recursos y consulta de recursos libres.
 */
class HospitalTest {

    private static final Disease FLU = DiseaseCatalog.findById(1).orElseThrow();

    private Patient newPatient() {
        return new Patient(1, 40, Sex.FEMALE, BloodType.A_POSITIVE, FLU,
                List.of(Symptom.FEVER), 0, 90.0, false);
    }

    private Hospital newHospital() {
        return new Hospital(
                List.of(new Room("H1", RoomType.HOSPITALIZATION, 2), new Room("H2", RoomType.HOSPITALIZATION, 2)),
                new Room("UCI", RoomType.ICU, 2),
                List.of(new OperatingRoom(1), new OperatingRoom(2)),
                List.of(new Doctor(1, Specialty.GENERAL_MEDICINE, 40, Sex.MALE),
                        new Doctor(2, Specialty.SURGERY, 45, Sex.FEMALE)),
                List.of(new Nurse(1, 30, Sex.FEMALE), new Nurse(2, 32, Sex.MALE)),
                new BloodBank(Map.of(BloodType.O_POSITIVE, 5)),
                new Morgue(2));
    }

    @Test
    void hospitalExposesAllItsResources() {
        Hospital hospital = newHospital();
        assertEquals(4, hospital.getBeds().size());
        assertEquals(2, hospital.getIcuBeds().size());
        assertEquals(2, hospital.getOperatingRooms().size());
        assertEquals(2, hospital.getDoctors().size());
        assertEquals(2, hospital.getNurses().size());
        assertEquals(5, hospital.getBloodBank().totalUnits());
        assertEquals(2, hospital.getMorgue().getCapacity());
    }

    @Test
    void assignedStaffIsNoLongerFree() {
        Hospital hospital = newHospital();
        hospital.getDoctors().get(0).assign(newPatient(), 60);
        hospital.getNurses().get(1).assign(newPatient(), 60);
        assertEquals(1, hospital.freeDoctors().size());
        assertEquals("D02", hospital.freeDoctors().get(0).getCode());
        assertEquals(1, hospital.freeNurses().size());
        assertEquals("E01", hospital.freeNurses().get(0).getCode());
    }

    @Test
    void assignedBedsAndOperatingRoomsAreNoLongerFree() {
        Hospital hospital = newHospital();
        hospital.getBeds().get(0).assign(newPatient(), 60);
        hospital.getIcuBeds().get(0).assign(newPatient(), 60);
        hospital.getOperatingRooms().get(1).assign(newPatient(), 60);
        assertEquals(3, hospital.freeBeds().size());
        assertEquals(1, hospital.freeIcuBeds().size());
        assertEquals(1, hospital.freeOperatingRooms().size());
        assertEquals("Q1", hospital.freeOperatingRooms().get(0).getCode());
    }

    @Test
    void releasedResourcesAreFreeAgain() {
        Hospital hospital = newHospital();
        Bed bed = hospital.getBeds().get(0);
        bed.assign(newPatient(), 60);
        bed.release();
        assertEquals(4, hospital.freeBeds().size());
    }

    @Test
    void roomTypesMustMatchTheirPlace() {
        Room icu = new Room("UCI", RoomType.ICU, 1);
        Room ward = new Room("H1", RoomType.HOSPITALIZATION, 1);
        List<OperatingRoom> operatingRooms = List.of(new OperatingRoom(1));
        List<Doctor> doctors = List.of(new Doctor(1, Specialty.SURGERY, 40, Sex.MALE));
        BloodBank bank = new BloodBank(Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> new Hospital(List.of(ward), ward, operatingRooms, doctors, List.of(), bank, new Morgue(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Hospital(List.of(icu), icu, operatingRooms, doctors, List.of(), bank, new Morgue(1)));
    }

    @Test
    void hospitalNeedsRoomsOperatingRoomsAndDoctors() {
        Room icu = new Room("UCI", RoomType.ICU, 1);
        List<Room> rooms = List.of(new Room("H1", RoomType.HOSPITALIZATION, 1));
        List<OperatingRoom> operatingRooms = List.of(new OperatingRoom(1));
        List<Doctor> doctors = List.of(new Doctor(1, Specialty.SURGERY, 40, Sex.MALE));
        BloodBank bank = new BloodBank(Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> new Hospital(List.of(), icu, operatingRooms, doctors, List.of(), bank, new Morgue(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Hospital(rooms, icu, List.of(), doctors, List.of(), bank, new Morgue(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new Hospital(rooms, icu, operatingRooms, List.of(), List.of(), bank, new Morgue(1)));
    }

    @Test
    void repeatedRoomCodesAreRejected() {
        Room icu = new Room("UCI", RoomType.ICU, 1);
        List<Room> rooms = List.of(new Room("H1", RoomType.HOSPITALIZATION, 1),
                new Room("H1", RoomType.HOSPITALIZATION, 1));
        assertThrows(IllegalArgumentException.class, () -> new Hospital(rooms, icu,
                List.of(new OperatingRoom(1)), List.of(new Doctor(1, Specialty.SURGERY, 40, Sex.MALE)),
                List.of(), new BloodBank(Map.of()), new Morgue(1)));
    }

    @Test
    void repeatedStaffCodesAreRejected() {
        Room icu = new Room("UCI", RoomType.ICU, 1);
        List<Room> rooms = List.of(new Room("H1", RoomType.HOSPITALIZATION, 1));
        List<Doctor> sameDoctorTwice = List.of(new Doctor(1, Specialty.SURGERY, 40, Sex.MALE),
                new Doctor(1, Specialty.PEDIATRICS, 35, Sex.FEMALE));
        assertThrows(IllegalArgumentException.class, () -> new Hospital(rooms, icu,
                List.of(new OperatingRoom(1)), sameDoctorTwice, List.of(),
                new BloodBank(Map.of()), new Morgue(1)));
    }

    @Test
    void resourceListsCannotBeModified() {
        Hospital hospital = newHospital();
        assertThrows(UnsupportedOperationException.class,
                () -> hospital.getDoctors().add(new Doctor(9, Specialty.SURGERY, 40, Sex.MALE)));
        assertTrue(hospital.getDoctors().size() == 2);
    }
}