package com.medresource.ui.controller;

/**
 * Lo que pide el usuario al terminar el formulario de una simulación nueva: la configuración y la velocidad con
 * la que quiere empezar a verla.
 *
 * @param setup hospital, caso y estrategia
 * @param speed velocidad inicial; una de {@link SimulationClock#SPEEDS}
 */
public record SimulationRequest(SimulationSetup setup, int speed) {

    /**
     * Valida el pedido.
     *
     * @throws IllegalArgumentException si falta la configuración o la velocidad no es una de las permitidas
     */
    public SimulationRequest {
        if (setup == null) {
            throw new IllegalArgumentException("La configuración de la simulación es obligatoria.");
        }
        if (!SimulationClock.SPEEDS.contains(speed)) {
            throw new IllegalArgumentException("La velocidad debe ser una de " + SimulationClock.SPEEDS + ".");
        }
    }
}
