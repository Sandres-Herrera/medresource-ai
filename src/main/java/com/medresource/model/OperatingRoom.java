package com.medresource.model;

/**
 * Quirófano donde se realizan las cirugías.
 */
public class OperatingRoom extends PhysicalResource {

    /**
     * Crea un quirófano; su código visible será {@code Q1}, {@code Q2}…
     *
     * @param number número del quirófano, mayor que cero
     * @throws IllegalArgumentException si el número no es válido
     */
    public OperatingRoom(int number) {
        super("Q" + requirePositive(number));
    }

    @Override
    public String describe() {
        return getCode() + " · Quirófano";
    }

    private static int requirePositive(int number) {
        if (number <= 0) {
            throw new IllegalArgumentException("El número del quirófano debe ser mayor que cero.");
        }
        return number;
    }
}