package com.medresource.services.strategy;

import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.Specialty;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.medresource.services.strategy.StrategyFixtures.APPENDICITIS;
import static com.medresource.services.strategy.StrategyFixtures.DENGUE;
import static com.medresource.services.strategy.StrategyFixtures.GENERAL_FLU;
import static com.medresource.services.strategy.StrategyFixtures.HEART_ATTACK;
import static com.medresource.services.strategy.StrategyFixtures.OPEN_FRACTURE;
import static com.medresource.services.strategy.StrategyFixtures.SEVERE_HEAD_TRAUMA;
import static com.medresource.services.strategy.StrategyFixtures.apply;
import static com.medresource.services.strategy.StrategyFixtures.disease;
import static com.medresource.services.strategy.StrategyFixtures.hospital;
import static com.medresource.services.strategy.StrategyFixtures.patient;
import static com.medresource.services.strategy.StrategyFixtures.waiting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la elección de recursos común a las estrategias por prioridad.
 *
 * <p>Se prueba con una estrategia de prueba que atiende primero al de mayor edad,
 * para no depender del orden de ninguna estrategia real.</p>
 */
class PriorityBasedStrategyTest {

    private static class OldestFirst extends PriorityBasedStrategy {
        @Override
        public String getName() {
            return "Prueba: el mayor primero";
        }

        @Override
        protected Comparator<WaitingPatient> priorityOrder(int minute) {
            return Comparator.comparingInt((WaitingPatient waiting) -> waiting.patient().getAge()).reversed();
        }
    }

    private final PriorityBasedStrategy strategy = new OldestFirst();

    @Test
    void servesTheFirstPatientInPriorityOrder() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        List<WaitingPatient> waiting = List.of(
                waiting(1, 0, GENERAL_FLU, 20),
                waiting(2, 1, GENERAL_FLU, 60));

        Assignment assignment = strategy.nextAssignment(10, waiting, hospital).orElseThrow();

        assertEquals(2, assignment.patient().getId());
    }

    @Test
    void returnsNothingWhenNobodyIsWaiting() {
        assertTrue(strategy.nextAssignment(0, List.of(), hospital(Specialty.GENERAL_MEDICINE)).isEmpty());
    }

    @Test
    void returnsNothingWhenNoDoctorIsFree() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);
        Doctor onlyDoctor = hospital.getDoctors().get(0);
        onlyDoctor.assign(patient(9, 0, GENERAL_FLU, 30), 60);

        assertTrue(strategy.nextAssignment(10, List.of(waiting(1, 0, GENERAL_FLU, 30)), hospital).isEmpty());
    }

    @Test
    void skipsAPatientWhoCannotBeServedAndServesTheNext() {
        // El de mayor edad necesita quirófano y no hay ninguno libre.
        Hospital hospital = hospital(3, 5, 1, Specialty.SURGERY, Specialty.GENERAL_MEDICINE);
        hospital.getOperatingRooms().get(0).assign(patient(9, 0, APPENDICITIS, 30), 60);
        List<WaitingPatient> waiting = List.of(
                waiting(1, 0, APPENDICITIS, 50),
                waiting(2, 1, GENERAL_FLU, 20));

        Assignment assignment = strategy.nextAssignment(10, waiting, hospital).orElseThrow();

        assertEquals(2, assignment.patient().getId());
    }

    @Test
    void prefersThePrimarySpecialistOverASecondaryOne() {
        // Para la gripa: internista = secundario, médico general = principal.
        Hospital hospital = hospital(Specialty.INTERNAL_MEDICINE, Specialty.GENERAL_MEDICINE);

        Assignment assignment = strategy.nextAssignment(0, List.of(waiting(1, 0, GENERAL_FLU, 30)), hospital)
                .orElseThrow();

        assertEquals(Specialty.GENERAL_MEDICINE, assignment.doctor().getSpecialty());
    }

    @Test
    void usesASecondarySpecialistWhenThePrimaryIsBusy() {
        Hospital hospital = hospital(Specialty.INTERNAL_MEDICINE, Specialty.GENERAL_MEDICINE);
        hospital.getDoctors().get(1).assign(patient(9, 0, GENERAL_FLU, 30), 60);

        Assignment assignment = strategy.nextAssignment(0, List.of(waiting(1, 0, GENERAL_FLU, 30)), hospital)
                .orElseThrow();

        assertEquals(Specialty.INTERNAL_MEDICINE, assignment.doctor().getSpecialty());
    }

    @Test
    void withEqualSpecialistsTheFirstDoctorInTheListIsChosen() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE, Specialty.GENERAL_MEDICINE);

        Assignment assignment = strategy.nextAssignment(0, List.of(waiting(1, 0, GENERAL_FLU, 30)), hospital)
                .orElseThrow();

        assertEquals(hospital.getDoctors().get(0), assignment.doctor());
    }

    @Test
    void neverUsesADoctorWhoCannotTreatTheDiagnosis() {
        // Un médico de urgencias no atiende una gripa.
        Hospital hospital = hospital(Specialty.EMERGENCY_MEDICINE);

        assertTrue(strategy.nextAssignment(0, List.of(waiting(1, 0, GENERAL_FLU, 30)), hospital).isEmpty());
    }

    @Test
    void aConsultationOnlyNeedsADoctor() {
        Assignment assignment = strategy.nextAssignment(0, List.of(waiting(1, 0, GENERAL_FLU, 30)),
                hospital(Specialty.GENERAL_MEDICINE)).orElseThrow();

        assertTrue(assignment.nurses().isEmpty());
        assertNull(assignment.bed());
        assertNull(assignment.operatingRoom());
        assertEquals(0, assignment.bloodUnits());
    }

    @Test
    void hospitalizationNeedsABedAndANurse() {
        Assignment assignment = strategy.nextAssignment(0, List.of(waiting(1, 0, DENGUE, 30)),
                hospital(Specialty.INTERNAL_MEDICINE)).orElseThrow();

        assertEquals(1, assignment.nurses().size());
        assertFalse(assignment.bed().isIcu());
        assertNull(assignment.operatingRoom());
        assertEquals(0, assignment.bloodUnits());
    }

    @Test
    void aDiseaseThatNeedsIntensiveCareTakesAnIcuBed() {
        Assignment assignment = strategy.nextAssignment(0, List.of(waiting(1, 0, HEART_ATTACK, 60)),
                hospital(Specialty.INTERNAL_MEDICINE)).orElseThrow();

        assertTrue(assignment.bed().isIcu());
        assertEquals(1, assignment.nurses().size());
    }

    @Test
    void surgeryNeedsAnOperatingRoomAndTwoNurses() {
        Assignment assignment = strategy.nextAssignment(0, List.of(waiting(1, 0, APPENDICITIS, 30)),
                hospital(Specialty.SURGERY)).orElseThrow();

        assertEquals(2, assignment.nurses().size());
        assertEquals("Q1", assignment.operatingRoom().getCode());
        assertNull(assignment.bed());
    }

    @Test
    void waitsWhenThereAreNotEnoughNurses() {
        Hospital hospital = hospital(1, 5, 1, Specialty.SURGERY);

        assertTrue(strategy.nextAssignment(0, List.of(waiting(1, 0, APPENDICITIS, 30)), hospital).isEmpty());
    }

    @Test
    void waitsWhenThereIsNoFreeIcuBed() {
        Hospital hospital = hospital(Specialty.INTERNAL_MEDICINE);
        hospital.getIcuBeds().forEach(bed -> bed.assign(patient(9, 0, HEART_ATTACK, 60), 100));

        assertTrue(strategy.nextAssignment(0, List.of(waiting(1, 0, HEART_ATTACK, 60)), hospital).isEmpty());
    }

    @Test
    void aTreatmentThatNeedsBloodTakesTheUnits() {
        Assignment assignment = strategy.nextAssignment(0, List.of(waiting(1, 0, OPEN_FRACTURE, 30)),
                hospital(Specialty.TRAUMATOLOGY)).orElseThrow();

        assertEquals(PriorityBasedStrategy.BLOOD_UNITS_PER_TREATMENT, assignment.bloodUnits());
    }

    @Test
    void waitsWhenThereIsNoCompatibleBlood() {
        Hospital hospital = hospital(3, 0, 1, Specialty.TRAUMATOLOGY);

        assertTrue(strategy.nextAssignment(0, List.of(waiting(1, 0, OPEN_FRACTURE, 30)), hospital).isEmpty());
    }

    @Test
    void aPatientWithEveryRequirementTakesOperatingRoomIcuBedBloodAndTwoNurses() {
        Assignment assignment = strategy.nextAssignment(0, List.of(waiting(1, 0, SEVERE_HEAD_TRAUMA, 30)),
                hospital(Specialty.SURGERY)).orElseThrow();

        assertEquals("Q1", assignment.operatingRoom().getCode());
        assertTrue(assignment.bed().isIcu());
        assertEquals(2, assignment.nurses().size());
        assertEquals(2, assignment.bloodUnits());
    }

    @Test
    void decidesWithTheDiagnosisAndNotWithTheRealDisease() {
        // Tiene gripa, pero lo diagnosticaron como apendicitis: se le reserva quirófano.
        WaitingPatient misdiagnosed = new WaitingPatient(patient(1, 0, GENERAL_FLU, 30), disease(APPENDICITIS));

        Assignment assignment = strategy.nextAssignment(0, List.of(misdiagnosed),
                hospital(Specialty.SURGERY, Specialty.GENERAL_MEDICINE)).orElseThrow();

        assertEquals(Specialty.SURGERY, assignment.doctor().getSpecialty());
        assertEquals("Q1", assignment.operatingRoom().getCode());
    }

    @Test
    void consecutiveDecisionsNeverReuseOccupiedResources() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE, Specialty.GENERAL_MEDICINE);
        List<WaitingPatient> waiting = List.of(
                waiting(1, 0, GENERAL_FLU, 60),
                waiting(2, 1, GENERAL_FLU, 50),
                waiting(3, 2, GENERAL_FLU, 40));

        Assignment first = strategy.nextAssignment(0, waiting, hospital).orElseThrow();
        apply(hospital, first, 30);
        Assignment second = strategy.nextAssignment(0, waiting.subList(1, 3), hospital).orElseThrow();
        apply(hospital, second, 30);
        Optional<Assignment> third = strategy.nextAssignment(0, waiting.subList(2, 3), hospital);

        assertEquals(1, first.patient().getId());
        assertEquals(2, second.patient().getId());
        assertFalse(first.doctor().equals(second.doctor()));
        assertTrue(third.isEmpty());
    }

    @Test
    void rejectsMissingArguments() {
        Hospital hospital = hospital(Specialty.GENERAL_MEDICINE);

        assertThrows(IllegalArgumentException.class, () -> strategy.nextAssignment(0, null, hospital));
        assertThrows(IllegalArgumentException.class, () -> strategy.nextAssignment(0, List.of(), null));
    }
}