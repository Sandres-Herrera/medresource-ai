package com.medresource.services.strategy;

import com.medresource.model.BloodBank;
import com.medresource.model.BloodType;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.Morgue;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.Patient;
import com.medresource.model.Room;
import com.medresource.model.RoomType;
import com.medresource.model.Sex;
import com.medresource.model.Specialty;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Datos de prueba compartidos por las pruebas de las estrategias.
 *
 * <p>No es una prueba: solo arma hospitales pequeños y pacientes en espera.</p>
 */
final class StrategyFixtures {

    static final int GENERAL_FLU = 1;
    static final int DENGUE = 2;
    static final int OPEN_FRACTURE = 7;
    static final int APPENDICITIS = 8;
    static final int HEART_ATTACK = 9;
    static final int SEVERE_HEAD_TRAUMA = 10;

    private StrategyFixtures() {
    }

    /** Hospital pequeño con un médico por cada especialidad indicada, 3 enfermeros y 5 unidades de cada sangre. */
    static Hospital hospital(Specialty... specialties) {
        return hospital(3, 5, 1, specialties);
    }

    static Hospital hospital(int nurses, int bloodUnitsPerType, int operatingRooms, Specialty... specialties) {
        List<Doctor> doctors = new ArrayList<>();
        for (int i = 0; i < specialties.length; i++) {
            doctors.add(new Doctor(i + 1, specialties[i], 40, Sex.FEMALE));
        }
        List<Nurse> nurseList = new ArrayList<>();
        for (int i = 1; i <= nurses; i++) {
            nurseList.add(new Nurse(i, 30, Sex.MALE));
        }
        List<OperatingRoom> rooms = new ArrayList<>();
        for (int i = 1; i <= operatingRooms; i++) {
            rooms.add(new OperatingRoom(i));
        }
        Map<BloodType, Integer> units = new EnumMap<>(BloodType.class);
        for (BloodType type : BloodType.values()) {
            units.put(type, bloodUnitsPerType);
        }
        return new Hospital(List.of(new Room("H1", RoomType.HOSPITALIZATION, 4)),
                new Room("UCI", RoomType.ICU, 2), rooms, doctors, nurseList,
                new BloodBank(units), new Morgue(3));
    }

    static Disease disease(int id) {
        return DiseaseCatalog.findById(id).orElseThrow();
    }

    /** Paciente cuya enfermedad real es la indicada y cuyo diagnóstico coincide con ella. */
    static WaitingPatient waiting(int id, int arrivalMinute, int diseaseId, int age) {
        return new WaitingPatient(patient(id, arrivalMinute, diseaseId, age), disease(diseaseId));
    }

    static Patient patient(int id, int arrivalMinute, int diseaseId, int age) {
        Disease disease = disease(diseaseId);
        return new Patient(id, age, Sex.MALE, BloodType.O_POSITIVE, disease, disease.getSymptoms(),
                arrivalMinute, 80.0, false);
    }

    /** Ocupa los recursos de una decisión y retira su sangre, como hará el motor de simulación. */
    static void apply(Hospital hospital, Assignment assignment, int untilMinute) {
        assignment.resources().forEach(resource -> resource.assign(assignment.patient(), untilMinute));
        if (assignment.bloodUnits() > 0) {
            hospital.getBloodBank().withdraw(assignment.patient().getBloodType(), assignment.bloodUnits());
        }
    }
}