package com.medresource.model;

/**
 * Enfermero que acompaña hospitalizaciones, cirugías y la UCI.
 */
public class Nurse extends StaffMember {

    /**
     * Crea un enfermero; su código visible será {@code E01}, {@code E02}…
     *
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public Nurse(int id, int age, Sex sex) {
        super(id, String.format("E%02d", id), age, sex);
    }

    @Override
    public String describe() {
        return getCode() + " · Enfermería";
    }
}