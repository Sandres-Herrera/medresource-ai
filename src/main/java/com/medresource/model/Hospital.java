package com.medresource.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Hospital de una simulación: reúne todos los recursos finitos y responde
 * cuáles están libres.
 *
 * <p>Las habitaciones, la UCI, los quirófanos, el banco de sangre y la morgue
 * pertenecen al hospital (composición); los médicos y enfermeros son personal
 * que trabaja en él.
 */
public class Hospital {

    private final List<Room> rooms;
    private final Room icu;
    private final List<OperatingRoom> operatingRooms;
    private final List<Doctor> doctors;
    private final List<Nurse> nurses;
    private final BloodBank bloodBank;
    private final Morgue morgue;
    private final List<Bed> beds;

    /**
     * Crea el hospital validando que esté completo y sin códigos repetidos.
     *
     * @param rooms          habitaciones de hospitalización, al menos una
     * @param icu            unidad de cuidados intensivos (habitación de tipo UCI)
     * @param operatingRooms quirófanos, al menos uno
     * @param doctors        médicos, al menos uno
     * @param nurses         enfermeros (pueden ser ninguno)
     * @param bloodBank      banco de sangre
     * @param morgue         morgue
     * @throws IllegalArgumentException si falta algún recurso, algún tipo de
     *                                  habitación no corresponde o hay códigos repetidos
     */
    public Hospital(List<Room> rooms, Room icu, List<OperatingRoom> operatingRooms,
                    List<Doctor> doctors, List<Nurse> nurses, BloodBank bloodBank, Morgue morgue) {
        requireNotEmpty(rooms, "El hospital necesita al menos una habitación.");
        requireNotEmpty(operatingRooms, "El hospital necesita al menos un quirófano.");
        requireNotEmpty(doctors, "El hospital necesita al menos un médico.");
        if (nurses == null) {
            throw new IllegalArgumentException("La lista de enfermeros es obligatoria.");
        }
        if (icu == null || bloodBank == null || morgue == null) {
            throw new IllegalArgumentException("La UCI, el banco de sangre y la morgue son obligatorios.");
        }
        if (icu.getType() != RoomType.ICU) {
            throw new IllegalArgumentException("La UCI debe ser una habitación de tipo UCI.");
        }
        if (rooms.stream().anyMatch(room -> room.getType() != RoomType.HOSPITALIZATION)) {
            throw new IllegalArgumentException("Las habitaciones deben ser de hospitalización.");
        }
        requireUniqueCodes(allRoomCodes(rooms, icu), "Hay habitaciones con el mismo código.");
        requireUniqueCodes(operatingRooms.stream().map(OperatingRoom::getCode).toList(),
                "Hay quirófanos con el mismo código.");
        requireUniqueCodes(doctors.stream().map(Doctor::getCode).toList(), "Hay médicos con el mismo código.");
        requireUniqueCodes(nurses.stream().map(Nurse::getCode).toList(), "Hay enfermeros con el mismo código.");

        this.rooms = List.copyOf(rooms);
        this.icu = icu;
        this.operatingRooms = List.copyOf(operatingRooms);
        this.doctors = List.copyOf(doctors);
        this.nurses = List.copyOf(nurses);
        this.bloodBank = bloodBank;
        this.morgue = morgue;
        this.beds = this.rooms.stream().flatMap(room -> room.getBeds().stream()).toList();
    }

    public List<Doctor> freeDoctors() {
        return free(doctors);
    }

    public List<Nurse> freeNurses() {
        return free(nurses);
    }

    public List<Bed> freeBeds() {
        return free(beds);
    }

    public List<Bed> freeIcuBeds() {
        return free(icu.getBeds());
    }

    public List<OperatingRoom> freeOperatingRooms() {
        return free(operatingRooms);
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public Room getIcu() {
        return icu;
    }

    public List<OperatingRoom> getOperatingRooms() {
        return operatingRooms;
    }

    public List<Doctor> getDoctors() {
        return doctors;
    }

    public List<Nurse> getNurses() {
        return nurses;
    }

    public BloodBank getBloodBank() {
        return bloodBank;
    }

    public Morgue getMorgue() {
        return morgue;
    }

    /**
     * Devuelve todas las camas de hospitalización, sin las de la UCI.
     */
    public List<Bed> getBeds() {
        return beds;
    }

    public List<Bed> getIcuBeds() {
        return icu.getBeds();
    }

    private static <T extends Assignable> List<T> free(List<T> resources) {
        return resources.stream().filter(Assignable::isAvailable).toList();
    }

    private static List<String> allRoomCodes(List<Room> rooms, Room icu) {
        List<String> codes = new ArrayList<>(rooms.stream().map(Room::getCode).toList());
        codes.add(icu.getCode());
        return codes;
    }

    private static void requireNotEmpty(List<?> list, String message) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireUniqueCodes(Collection<String> codes, String message) {
        if (new HashSet<>(codes).size() != codes.size()) {
            throw new IllegalArgumentException(message);
        }
    }
}