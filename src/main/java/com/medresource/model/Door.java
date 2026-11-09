package com.medresource.model;

/**
 * Puerta de una sala hacia un pasillo del plano.
 *
 * <p>Une dos casillas vecinas: la del borde de la sala ({@code inside}) y la del pasillo ({@code outside}).
 * Por ahí entra y sale quien camina entre salas.</p>
 *
 * @param inside  casilla del borde de la sala
 * @param outside casilla del pasillo, pegada a la de la sala
 */
public record Door(Position inside, Position outside) {

    /**
     * Valida la puerta.
     *
     * @throws IllegalArgumentException si falta alguna casilla o si las dos casillas no están pegadas
     */
    public Door {
        if (inside == null || outside == null) {
            throw new IllegalArgumentException("Las dos casillas de la puerta son obligatorias.");
        }
        if (inside.stepsTo(outside) != 1) {
            throw new IllegalArgumentException("Las casillas de una puerta deben estar pegadas.");
        }
    }
}
