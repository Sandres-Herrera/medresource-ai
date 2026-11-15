package com.medresource.services.simulation;

import com.medresource.model.BloodBank;
import com.medresource.model.BloodType;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Morgue;
import com.medresource.model.Nurse;
import com.medresource.model.OperatingRoom;
import com.medresource.model.Room;
import com.medresource.model.RoomType;
import com.medresource.model.Sex;
import com.medresource.model.Specialty;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Construye hospitales listos para simular.
 *
 * <p>Los recursos se ocupan mientras corre la simulación, por eso cada ejecución
 * necesita su propio hospital: se crea uno nuevo con {@link #create(HospitalConfig)}
 * en lugar de reutilizar el anterior. La construcción es siempre igual para la
 * misma configuración: el personal no se genera al azar.</p>
 */
public class HospitalFactory {

    /** Camas de cada habitación de hospitalización; la última puede tener menos. */
    private static final int BEDS_PER_ROOM = 4;

    /**
     * Orden en que se asignan las especialidades a los médicos, según la demanda de cada una.
     *
     * <p>Con seis médicos o más ya hay uno de cada especialidad. Los siguientes van primero a las que más
     * tiempo de médico piden los pacientes: medicina interna (dengue, neumonía, trombosis, infarto y cáncer,
     * cerca de un tercio de la demanda), traumatología y cirugía. Con los 16 médicos del hospital estándar
     * quedan 5 de medicina interna, 3 de cirugía, 3 de traumatología, 2 de medicina general, 2 de urgencias
     * y 1 de pediatría. Con más de 16 el orden vuelve a empezar, así que se conserva la proporción.</p>
     */
    private static final List<Specialty> DOCTOR_ROTATION = List.of(
            Specialty.EMERGENCY_MEDICINE,
            Specialty.GENERAL_MEDICINE,
            Specialty.INTERNAL_MEDICINE,
            Specialty.SURGERY,
            Specialty.TRAUMATOLOGY,
            Specialty.PEDIATRICS,
            Specialty.INTERNAL_MEDICINE,
            Specialty.TRAUMATOLOGY,
            Specialty.SURGERY,
            Specialty.INTERNAL_MEDICINE,
            Specialty.GENERAL_MEDICINE,
            Specialty.INTERNAL_MEDICINE,
            Specialty.EMERGENCY_MEDICINE,
            Specialty.TRAUMATOLOGY,
            Specialty.SURGERY,
            Specialty.INTERNAL_MEDICINE);

    /**
     * Crea un hospital nuevo con todos sus recursos libres.
     *
     * @param config cantidad de recursos de cada tipo
     * @return hospital nuevo, independiente de los creados antes
     * @throws IllegalArgumentException si la configuración es nula
     */
    public Hospital create(HospitalConfig config) {
        return create(config, List.of());
    }

    /**
     * Crea un hospital nuevo con todos sus recursos libres y, además de los médicos de la configuración,
     * médicos extra de las especialidades indicadas. Sirve para probar qué pasaría con un especialista más.
     *
     * <p>Los médicos extra van después de los demás, con los códigos que siguen (con 16 médicos, el primer
     * extra es {@code D17}).</p>
     *
     * @param config       cantidad de recursos de cada tipo
     * @param extraDoctors especialidad de cada médico extra; vacío si no hay
     * @return hospital nuevo, independiente de los creados antes
     * @throws IllegalArgumentException si falta la configuración o la lista, o si algún médico extra no tiene
     *                                  especialidad
     */
    public Hospital create(HospitalConfig config, List<Specialty> extraDoctors) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración del hospital es obligatoria.");
        }
        if (extraDoctors == null || extraDoctors.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Cada médico extra debe tener especialidad.");
        }
        return new Hospital(
                createRooms(config.hospitalizationBeds()),
                new Room("UCI", RoomType.ICU, config.icuBeds()),
                createOperatingRooms(config.operatingRooms()),
                createDoctors(config.doctors(), extraDoctors),
                createNurses(config.nurses()),
                createBloodBank(config.bloodUnitsPerType()),
                new Morgue(config.morgueCapacity()));
    }

    private List<Room> createRooms(int totalBeds) {
        List<Room> rooms = new ArrayList<>();
        int remaining = totalBeds;
        int number = 1;
        while (remaining > 0) {
            int beds = Math.min(BEDS_PER_ROOM, remaining);
            rooms.add(new Room("H" + number, RoomType.HOSPITALIZATION, beds));
            remaining -= beds;
            number++;
        }
        return rooms;
    }

    private List<OperatingRoom> createOperatingRooms(int count) {
        List<OperatingRoom> operatingRooms = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            operatingRooms.add(new OperatingRoom(number));
        }
        return operatingRooms;
    }

    private List<Doctor> createDoctors(int count, List<Specialty> extraDoctors) {
        List<Doctor> doctors = new ArrayList<>();
        for (int id = 1; id <= count; id++) {
            Specialty specialty = DOCTOR_ROTATION.get((id - 1) % DOCTOR_ROTATION.size());
            doctors.add(new Doctor(id, specialty, 28 + (id * 7) % 25, sexFor(id)));
        }
        int id = count;
        for (Specialty specialty : extraDoctors) {
            id++;
            doctors.add(new Doctor(id, specialty, 28 + (id * 7) % 25, sexFor(id)));
        }
        return doctors;
    }

    private List<Nurse> createNurses(int count) {
        List<Nurse> nurses = new ArrayList<>();
        for (int id = 1; id <= count; id++) {
            nurses.add(new Nurse(id, 24 + (id * 5) % 20, sexFor(id)));
        }
        return nurses;
    }

    private BloodBank createBloodBank(int unitsPerType) {
        Map<BloodType, Integer> units = new EnumMap<>(BloodType.class);
        for (BloodType type : BloodType.values()) {
            units.put(type, unitsPerType);
        }
        return new BloodBank(units);
    }

    private Sex sexFor(int id) {
        return id % 2 == 0 ? Sex.FEMALE : Sex.MALE;
    }
}