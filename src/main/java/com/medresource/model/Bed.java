package com.medresource.model;

/**
 * Cama de una habitación de hospitalización o de la UCI.
 *
 * <p>Si un paciente fallece y la morgue está llena, el cuerpo sigue ocupando
 * la cama hasta que haya cupo; por eso la cama solo se libera con
 * {@link #release()}.</p>
 */
public class Bed extends PhysicalResource {

    private final RoomType roomType;

    /**
     * @param code     código visible, por ejemplo {@code H1-C2}
     * @param roomType tipo de habitación donde está la cama
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public Bed(String code, RoomType roomType) {
        super(code);
        if (roomType == null) {
            throw new IllegalArgumentException("El tipo de habitación de la cama es obligatorio.");
        }
        this.roomType = roomType;
    }

    public boolean isIcu() {
        return roomType == RoomType.ICU;
    }

    @Override
    public String describe() {
        return getCode() + " · Cama de " + roomType.getDisplayName().toLowerCase();
    }

    public RoomType getRoomType() {
        return roomType;
    }
}
