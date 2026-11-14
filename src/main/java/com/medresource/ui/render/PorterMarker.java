package com.medresource.ui.render;

/**
 * Lo que hace falta para dibujar a un camillero que va a buscar a un paciente a la sala de espera o lo acompaña hasta
 * su lugar de atención: a quién busca y dónde está.
 *
 * <p>Los camilleros no son personal del motor: no le quitan a nadie médicos ni enfermeros y no cambian el
 * resultado; solo se ven.</p>
 *
 * @param patientCode código del paciente que busca o acompaña
 * @param x           posición horizontal en el mundo, en píxeles
 * @param y           posición vertical en el mundo, en píxeles
 * @param moving      {@code true} si va caminando; quieto, espera junto a la silla del paciente
 */
public record PorterMarker(String patientCode, double x, double y, boolean moving) {

    /**
     * Valida el marcador.
     *
     * @throws IllegalArgumentException si falta el código del paciente o la posición no es un número
     */
    public PorterMarker {
        if (patientCode == null || patientCode.isBlank()) {
            throw new IllegalArgumentException("El código del paciente del camillero es obligatorio.");
        }
        if (Double.isNaN(x) || Double.isNaN(y)) {
            throw new IllegalArgumentException("La posición del camillero debe ser un número.");
        }
    }

    /**
     * Crea un camillero quieto.
     *
     * @param patientCode código del paciente que busca o acompaña
     * @param x           posición horizontal en el mundo, en píxeles
     * @param y           posición vertical en el mundo, en píxeles
     * @throws IllegalArgumentException si falta el código del paciente o la posición no es un número
     */
    public PorterMarker(String patientCode, double x, double y) {
        this(patientCode, x, y, false);
    }
}
