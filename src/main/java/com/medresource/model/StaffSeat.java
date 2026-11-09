package com.medresource.model;

/**
 * Puesto de un médico o enfermero en la sala del personal.
 *
 * <p>La sala del personal tiene mesas de {@value #SEATS_PER_TABLE} puestos: cada mesa ocupa una casilla del
 * plano y cada persona se sienta a un lado de ella. Así la sala necesita la mitad de casillas que si cada
 * persona tuviera la suya.</p>
 *
 * @param table casilla de la mesa
 * @param side  lado de la mesa: {@code 0} a la izquierda y {@code 1} a la derecha
 */
public record StaffSeat(Position table, int side) {

    /** Puestos que tiene cada mesa de la sala del personal. */
    public static final int SEATS_PER_TABLE = 2;

    /**
     * Valida el puesto.
     *
     * @throws IllegalArgumentException si falta la mesa o el lado no es 0 ni 1
     */
    public StaffSeat {
        if (table == null) {
            throw new IllegalArgumentException("La mesa del puesto es obligatoria.");
        }
        if (side < 0 || side >= SEATS_PER_TABLE) {
            throw new IllegalArgumentException(
                    "El lado de la mesa debe estar entre 0 y " + (SEATS_PER_TABLE - 1) + ".");
        }
    }
}
