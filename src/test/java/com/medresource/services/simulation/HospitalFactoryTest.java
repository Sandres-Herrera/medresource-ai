package com.medresource.services.simulation;

import com.medresource.model.Bed;
import com.medresource.model.BloodType;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Patient;
import com.medresource.model.Room;
import com.medresource.model.RoomType;
import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import com.medresource.model.StaffMember;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la fábrica de hospitales.
 */
class HospitalFactoryTest {

    private final HospitalFactory factory = new HospitalFactory();

    private Hospital standardHospital() {
        return factory.create(HospitalConfig.standard());
    }

    private long countSpecialty(Hospital hospital, Specialty specialty) {
        return hospital.getDoctors().stream().filter(doctor -> doctor.getSpecialty() == specialty).count();
    }

    @Test
    void standardHospitalHasTheConfiguredResources() {
        Hospital hospital = standardHospital();

        assertEquals(30, hospital.getBeds().size());
        assertEquals(6, hospital.getIcuBeds().size());
        assertEquals(4, hospital.getOperatingRooms().size());
        assertEquals(16, hospital.getDoctors().size());
        assertEquals(24, hospital.getNurses().size());
        assertEquals(5, hospital.getMorgue().getCapacity());
    }

    @Test
    void everyResourceStartsFree() {
        Hospital hospital = standardHospital();

        assertEquals(30, hospital.freeBeds().size());
        assertEquals(6, hospital.freeIcuBeds().size());
        assertEquals(4, hospital.freeOperatingRooms().size());
        assertEquals(16, hospital.freeDoctors().size());
        assertEquals(24, hospital.freeNurses().size());
        assertEquals(0, hospital.getMorgue().occupancy());
    }

    @Test
    void hospitalizationBedsAreGroupedInRoomsOfFour() {
        Hospital hospital = factory.create(new HospitalConfig(10, 2, 1, 6, 4, 5, 3));
        List<Room> rooms = hospital.getRooms();

        assertEquals(3, rooms.size());
        assertEquals(4, rooms.get(0).getCapacity());
        assertEquals(4, rooms.get(1).getCapacity());
        assertEquals(2, rooms.get(2).getCapacity());
        assertEquals("H1", rooms.get(0).getCode());
        assertEquals("H3", rooms.get(2).getCode());
        assertEquals(10, hospital.getBeds().size());
    }

    @Test
    void icuIsAnIcuRoomWithTheConfiguredBeds() {
        Hospital hospital = standardHospital();

        assertEquals(RoomType.ICU, hospital.getIcu().getType());
        assertEquals("UCI", hospital.getIcu().getCode());
        assertEquals(6, hospital.getIcu().getCapacity());
        for (Bed bed : hospital.getIcuBeds()) {
            assertTrue(bed.isIcu());
        }
    }

    @Test
    void bloodBankHasTheSameUnitsOfEveryType() {
        Hospital hospital = standardHospital();

        for (BloodType type : BloodType.values()) {
            assertEquals(10, hospital.getBloodBank().available(type));
        }
        assertEquals(80, hospital.getBloodBank().totalUnits());
    }

    @Test
    void standardDoctorsCoverEverySpecialty() {
        Hospital hospital = standardHospital();

        for (Specialty specialty : Specialty.values()) {
            assertTrue(countSpecialty(hospital, specialty) >= 1, specialty.name());
        }
        assertEquals(3, countSpecialty(hospital, Specialty.EMERGENCY_MEDICINE));
        assertEquals(3, countSpecialty(hospital, Specialty.GENERAL_MEDICINE));
    }

    @Test
    void fewDoctorsAreAssignedByPriorityOrder() {
        Hospital hospital = factory.create(new HospitalConfig(4, 1, 1, 3, 2, 5, 1));

        assertEquals(Specialty.EMERGENCY_MEDICINE, hospital.getDoctors().get(0).getSpecialty());
        assertEquals(Specialty.GENERAL_MEDICINE, hospital.getDoctors().get(1).getSpecialty());
        assertEquals(Specialty.INTERNAL_MEDICINE, hospital.getDoctors().get(2).getSpecialty());
    }

    @Test
    void standardHospitalCanTreatEveryCatalogDisease() {
        Hospital hospital = standardHospital();

        for (Disease disease : DiseaseCatalog.defaultDiseases()) {
            for (int age : new int[]{disease.getMinAge(), disease.getMaxAge()}) {
                boolean someoneCanTreat = hospital.getDoctors().stream()
                        .anyMatch(doctor -> doctor.canTreat(disease, age));

                assertTrue(someoneCanTreat, disease.getName() + " a los " + age + " años");
            }
        }
    }

    @Test
    void staffIsAdultAndHasBothSexes() {
        Hospital hospital = factory.create(new HospitalConfig(10, 2, 2, HospitalConfig.MAX_DOCTORS,
                HospitalConfig.MAX_NURSES, 5, 3));
        List<StaffMember> staff = new ArrayList<>();
        staff.addAll(hospital.getDoctors());
        staff.addAll(hospital.getNurses());

        for (StaffMember member : staff) {
            assertTrue(member.getAge() >= StaffMember.MIN_STAFF_AGE, member.getCode());
        }
        assertTrue(staff.stream().anyMatch(member -> member.getSex() == Sex.FEMALE));
        assertTrue(staff.stream().anyMatch(member -> member.getSex() == Sex.MALE));
    }

    @Test
    void sameConfigurationBuildsTheSameStaff() {
        List<Doctor> first = standardHospital().getDoctors();
        List<Doctor> second = standardHospital().getDoctors();

        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getCode(), second.get(i).getCode());
            assertEquals(first.get(i).getSpecialty(), second.get(i).getSpecialty());
            assertEquals(first.get(i).getAge(), second.get(i).getAge());
            assertEquals(first.get(i).getSex(), second.get(i).getSex());
        }
    }

    @Test
    void everyHospitalIsIndependentFromTheOthers() {
        Hospital first = standardHospital();
        Hospital second = standardHospital();
        Disease disease = DiseaseCatalog.findById(1).orElseThrow();
        Patient patient = new Patient(1, 30, Sex.MALE, BloodType.O_POSITIVE, disease,
                disease.getSymptoms(), 0, 90.0, false);

        first.getBeds().get(0).assign(patient, 30);
        first.getBloodBank().withdraw(BloodType.O_POSITIVE, 2);

        assertEquals(29, first.freeBeds().size());
        assertEquals(30, second.freeBeds().size());
        assertEquals(78, first.getBloodBank().totalUnits());
        assertEquals(80, second.getBloodBank().totalUnits());
    }

    @Test
    void buildsTheLargestPossibleHospitalWithoutRepeatedCodes() {
        Hospital hospital = factory.create(new HospitalConfig(HospitalConfig.MAX_BEDS, HospitalConfig.MAX_ICU_BEDS,
                HospitalConfig.MAX_OPERATING_ROOMS, HospitalConfig.MAX_DOCTORS, HospitalConfig.MAX_NURSES,
                HospitalConfig.MAX_BLOOD_UNITS_PER_TYPE, HospitalConfig.MAX_MORGUE_CAPACITY));

        assertEquals(HospitalConfig.MAX_BEDS, hospital.getBeds().size());
        assertFalse(hospital.getDoctors().isEmpty());
    }

    @Test
    void rejectsAMissingConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> factory.create(null));
    }
}