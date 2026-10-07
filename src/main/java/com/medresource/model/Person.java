package com.medresource.model;

import java.util.Objects;

/**
 * Persona del hospital: paciente o miembro del personal.
 *
 * <p>Agrupa los datos comunes a todas las personas de la simulación.
 */
public abstract class Person {

    public static final int MAX_AGE = 120;

    /** Por debajo de esta edad la persona se considera paciente pediátrico. */
    public static final int MINOR_AGE_LIMIT = 15;

    private final int id;
    private final String code;
    private final int age;
    private final Sex sex;

    /**
     * Crea una persona validando sus datos.
     *
     * @param id   identificador único, mayor que cero
     * @param code código visible, por ejemplo {@code P001} o {@code D03}
     * @param age  edad en años, entre 0 y {@link #MAX_AGE}
     * @param sex  sexo biológico
     * @throws IllegalArgumentException si algún dato no es válido
     */
    protected Person(int id, String code, int age, Sex sex) {
        if (id <= 0) {
            throw new IllegalArgumentException("El id de la persona debe ser mayor que cero.");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código de la persona es obligatorio.");
        }
        if (age < 0 || age > MAX_AGE) {
            throw new IllegalArgumentException("La edad debe estar entre 0 y " + MAX_AGE + " años.");
        }
        if (sex == null) {
            throw new IllegalArgumentException("El sexo de la persona es obligatorio.");
        }
        this.id = id;
        this.code = code;
        this.age = age;
        this.sex = sex;
    }

    /**
     * @return descripción de la persona
     */
    public abstract String describe();

    /**
     * Indica si la persona está en edad pediátrica.
     *
     * @return {@code true} si es menor de {@link #MINOR_AGE_LIMIT} años
     */
    public boolean isMinor() {
        return age < MINOR_AGE_LIMIT;
    }

    public int getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getAge() {
        return age;
    }

    public Sex getSex() {
        return sex;
    }

    /**
     * Dos personas son iguales si son del mismo tipo y tienen el mismo id.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return id == ((Person) other).id;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass(), id);
    }

    @Override
    public String toString() {
        return describe();
    }
}