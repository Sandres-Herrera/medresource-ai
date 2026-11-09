package com.medresource.model;

/**
 * Estación de triage donde una enfermera de triage valora a un paciente recién llegado.
 *
 * <p>Cada estación tiene su propia enfermera de triage, que no forma parte de los enfermeros del hospital:
 * clasificar pacientes no le quita personal a la hospitalización ni a cirugía. Una estación valora a un
 * paciente a la vez.</p>
 */
public class TriageStation extends PhysicalResource {

    /** Prefijo del código de las estaciones: {@code T1}, {@code T2}… */
    public static final String CODE_PREFIX = "T";

    /**
     * Crea una estación de triage; su código visible será {@code T1}, {@code T2}…
     *
     * @param number número de la estación, mayor que cero
     * @throws IllegalArgumentException si el número no es válido
     */
    public TriageStation(int number) {
        super(CODE_PREFIX + requirePositive(number));
    }

    /**
     * Devuelve el número de la estación que tiene un código, sin crearla.
     *
     * @param code código como {@code T2}
     * @return número de la estación, o {@code -1} si el código no es de una estación de triage
     */
    public static int numberOf(String code) {
        if (code == null || !code.startsWith(CODE_PREFIX) || code.length() == CODE_PREFIX.length()) {
            return -1;
        }
        String digits = code.substring(CODE_PREFIX.length());
        if (!digits.chars().allMatch(Character::isDigit) || digits.length() > 4) {
            return -1;
        }
        int number = Integer.parseInt(digits);
        return number > 0 ? number : -1;
    }

    @Override
    public String describe() {
        return getCode() + " · Estación de triage";
    }

    private static int requirePositive(int number) {
        if (number <= 0) {
            throw new IllegalArgumentException("El número de la estación de triage debe ser mayor que cero.");
        }
        return number;
    }
}
