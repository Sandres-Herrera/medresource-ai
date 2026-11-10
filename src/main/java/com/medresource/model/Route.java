package com.medresource.model;

import java.util.List;

/**
 * Recorrido de una persona por el plano del hospital: las casillas que pisa, en orden, desde donde sale hasta
 * donde llega.
 *
 * <p>Cada paso va a una casilla vecina (arriba, abajo, a la izquierda o a la derecha), así que la ruta mide
 * tantos metros como pasos tiene por {@value HospitalLayout#METERS_PER_CELL} m. Una ruta de una sola casilla
 * es la de quien ya está donde quiere ir: mide 0 m. Es inmutable.</p>
 *
 * @param cells casillas de la ruta, en orden, al menos una
 */
public record Route(List<Position> cells) {

    /**
     * Valida la ruta.
     *
     * @throws IllegalArgumentException si no tiene casillas, si alguna falta o si un paso no va a una casilla
     *                                  vecina
     */
    public Route {
        if (cells == null || cells.isEmpty()) {
            throw new IllegalArgumentException("La ruta debe tener al menos una casilla.");
        }
        for (int index = 0; index < cells.size(); index++) {
            if (cells.get(index) == null) {
                throw new IllegalArgumentException("Las casillas de la ruta son obligatorias.");
            }
            if (index > 0 && cells.get(index - 1).stepsTo(cells.get(index)) != 1) {
                throw new IllegalArgumentException("Cada paso de la ruta debe ir a una casilla vecina.");
            }
        }
        cells = List.copyOf(cells);
    }

    /** Casilla donde empieza la ruta. */
    public Position start() {
        return cells.get(0);
    }

    /** Casilla donde termina la ruta. */
    public Position end() {
        return cells.get(cells.size() - 1);
    }

    /** Pasos de la ruta: una casilla menos que las que pisa. */
    public int steps() {
        return cells.size() - 1;
    }

    /** Largo de la ruta en metros. */
    public double lengthInMeters() {
        return steps() * HospitalLayout.METERS_PER_CELL;
    }

    /**
     * Indica si la ruta pisa una casilla.
     *
     * @param position casilla
     * @return {@code true} si la casilla es parte de la ruta
     */
    public boolean passesThrough(Position position) {
        return cells.contains(position);
    }
}
