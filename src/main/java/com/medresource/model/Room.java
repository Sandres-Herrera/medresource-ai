package com.medresource.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Habitación del hospital con un número fijo de camas.
 *
 * <p>La habitación crea sus propias camas (composición): las camas no existen
 * fuera de ella. La UCI es una habitación de tipo {@link RoomType#ICU}.</p>
 */
public class Room {

    private final String code;
    private final RoomType type;
    private final List<Bed> beds;

    /**
     * Crea la habitación y sus camas, con códigos {@code <código>-C1},
     * {@code <código>-C2}…
     *
     * @param code     código visible, por ejemplo {@code H1} o {@code UCI}
     * @param type     tipo de habitación
     * @param bedCount número de camas, mayor que cero
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public Room(String code, RoomType type, int bedCount) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código de la habitación es obligatorio.");
        }
        if (type == null) {
            throw new IllegalArgumentException("El tipo de habitación es obligatorio.");
        }
        if (bedCount <= 0) {
            throw new IllegalArgumentException("La habitación debe tener al menos una cama.");
        }
        this.code = code;
        this.type = type;
        List<Bed> created = new ArrayList<>();
        for (int i = 1; i <= bedCount; i++) {
            created.add(new Bed(code + "-C" + i, type));
        }
        this.beds = List.copyOf(created);
    }

    public List<Bed> freeBeds() {
        return beds.stream().filter(Bed::isAvailable).toList();
    }

    public int occupiedBedCount() {
        return getCapacity() - freeBeds().size();
    }

    public boolean isFull() {
        return freeBeds().isEmpty();
    }

    public int getCapacity() {
        return beds.size();
    }

    public String describe() {
        return code + " · " + type.getDisplayName() + " · " + occupiedBedCount() + "/" + getCapacity() + " camas ocupadas";
    }

    public String getCode() {
        return code;
    }

    public RoomType getType() {
        return type;
    }

    public List<Bed> getBeds() {
        return beds;
    }

    @Override
    public String toString() {
        return describe();
    }
}