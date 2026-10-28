package com.medresource.services.strategy;

import com.medresource.model.Bed;
import com.medresource.model.Doctor;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.Patient;
import com.medresource.model.RoomType;
import com.medresource.model.Sex;
import com.medresource.model.Specialty;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.medresource.services.strategy.StrategyFixtures.GENERAL_FLU;
import static com.medresource.services.strategy.StrategyFixtures.patient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la decisión de asignación.
 */
class AssignmentTest {

    private final Patient patient = patient(1, 0, GENERAL_FLU, 30);
    private final Doctor doctor = new Doctor(1, Specialty.SURGERY, 40, Sex.FEMALE);
    private final Nurse nurse = new Nurse(1, 30, Sex.MALE);
    private final Nurse otherNurse = new Nurse(2, 30, Sex.MALE);
    private final Bed bed = new Bed("H1-C1", RoomType.HOSPITALIZATION);
    private final OperatingRoom operatingRoom = new OperatingRoom(1);

    @Test
    void listsEveryResourceTheTreatmentOccupies() {
        Assignment assignment = new Assignment(patient, doctor, List.of(nurse, otherNurse), bed, operatingRoom, 2);

        assertEquals(5, assignment.resources().size());
        assertTrue(assignment.resources().contains(doctor));
        assertTrue(assignment.resources().contains(nurse));
        assertTrue(assignment.resources().contains(otherNurse));
        assertTrue(assignment.resources().contains(bed));
        assertTrue(assignment.resources().contains(operatingRoom));
    }

    @Test
    void leavesOutTheResourcesThatAreNotNeeded() {
        Assignment consultation = new Assignment(patient, doctor, List.of(), null, null, 0);

        assertEquals(List.of(doctor), consultation.resources());
    }

    @Test
    void keepsItsOwnCopyOfTheNurses() {
        List<Nurse> nurses = new ArrayList<>(List.of(nurse));
        Assignment assignment = new Assignment(patient, doctor, nurses, null, null, 0);

        nurses.add(otherNurse);

        assertEquals(1, assignment.nurses().size());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new Assignment(null, doctor, List.of(), null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new Assignment(patient, null, List.of(), null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new Assignment(patient, doctor, null, null, null, 0));
    }

    @Test
    void rejectsNegativeBloodUnits() {
        assertThrows(IllegalArgumentException.class, () -> new Assignment(patient, doctor, List.of(), null, null, -1));
    }
}