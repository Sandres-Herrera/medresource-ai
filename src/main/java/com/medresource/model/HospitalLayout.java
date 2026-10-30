package com.medresource.model;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plano del hospital: una cuadrícula de casillas con el tipo de zona de cada una y el lugar de cada
 * recurso.
 *
 * <p>Es la geometría que comparten las vistas 2D y 2.5D: ninguna regla de la simulación depende de ella
 * y el motor no la conoce. Los recursos se buscan por el mismo código que usa el motor en sus eventos
 * ({@code H1-C2}, {@code UCI-C1}, {@code Q1}, {@code D01}, {@code E01}). Los lugares de la morgue no
 * tienen código propio porque el motor no los nombra: son una lista ordenada.</p>
 *
 * <p>Es inmutable: las listas que devuelve no se pueden modificar.</p>
 */
public final class HospitalLayout {

    private final ZoneType[][] zones;
    private final Map<String, Position> spots;
    private final Position entrance;
    private final Position triagePoint;
    private final List<Position> waitingSpots;
    private final List<Position> morgueSpots;

    /**
     * Crea el plano validando que sea coherente.
     *
     * @param zones        tipo de zona de cada casilla, indexado por fila y luego por columna; no puede
     *                     estar vacío ni tener filas de distinto largo
     * @param spots        posición de cada recurso según su código
     * @param entrance     casilla por donde llegan los pacientes, dentro de una zona de entrada
     * @param triagePoint  casilla donde se hace el triage, dentro de una zona de triage
     * @param waitingSpots lugares de la sala de espera, al menos uno, dentro de zonas de sala de espera
     * @param morgueSpots  lugares de la morgue, dentro de zonas de morgue
     * @throws IllegalArgumentException si falta algún dato, el plano está mal formado, algún lugar queda
     *                                  fuera del plano o en una zona que no le corresponde, o dos
     *                                  recursos ocupan la misma casilla
     */
    public HospitalLayout(ZoneType[][] zones, Map<String, Position> spots, Position entrance,
                          Position triagePoint, List<Position> waitingSpots, List<Position> morgueSpots) {
        if (zones == null || spots == null || entrance == null || triagePoint == null
                || waitingSpots == null || morgueSpots == null) {
            throw new IllegalArgumentException("Faltan datos del plano del hospital.");
        }
        if (zones.length == 0 || zones[0].length == 0) {
            throw new IllegalArgumentException("El plano debe tener al menos una casilla.");
        }
        ZoneType[][] copy = new ZoneType[zones.length][];
        for (int row = 0; row < zones.length; row++) {
            if (zones[row] == null || zones[row].length != zones[0].length) {
                throw new IllegalArgumentException("Todas las filas del plano deben tener el mismo largo.");
            }
            for (ZoneType zone : zones[row]) {
                if (zone == null) {
                    throw new IllegalArgumentException("Cada casilla del plano debe tener una zona.");
                }
            }
            copy[row] = zones[row].clone();
        }
        this.zones = copy;
        this.spots = Map.copyOf(spots);
        this.entrance = entrance;
        this.triagePoint = triagePoint;
        this.waitingSpots = List.copyOf(waitingSpots);
        this.morgueSpots = List.copyOf(morgueSpots);
        validate();
    }

    private void validate() {
        requireZone(entrance, ZoneType.ENTRANCE, "La entrada");
        requireZone(triagePoint, ZoneType.TRIAGE, "El punto de triage");
        if (waitingSpots.isEmpty()) {
            throw new IllegalArgumentException("La sala de espera necesita al menos un lugar.");
        }
        for (Position spot : waitingSpots) {
            requireZone(spot, ZoneType.WAITING_ROOM, "Un lugar de la sala de espera");
        }
        for (Position spot : morgueSpots) {
            requireZone(spot, ZoneType.MORGUE, "Un lugar de la morgue");
        }
        Set<Position> used = new HashSet<>();
        for (Map.Entry<String, Position> spot : spots.entrySet()) {
            if (!contains(spot.getValue())) {
                throw new IllegalArgumentException("El recurso " + spot.getKey() + " está fuera del plano.");
            }
            if (!used.add(spot.getValue())) {
                throw new IllegalArgumentException("Hay dos recursos en la casilla " + spot.getValue() + ".");
            }
        }
        for (Position spot : morgueSpots) {
            if (!used.add(spot)) {
                throw new IllegalArgumentException("Hay dos recursos en la casilla " + spot + ".");
            }
        }
    }

    private void requireZone(Position position, ZoneType expected, String what) {
        if (!contains(position)) {
            throw new IllegalArgumentException(what + " está fuera del plano.");
        }
        if (zoneAt(position) != expected) {
            throw new IllegalArgumentException(what + " debe estar en una zona de tipo " + expected + ".");
        }
    }

    /**
     * Indica si una casilla está dentro del plano.
     *
     * @param position casilla
     * @return {@code true} si la columna y la fila existen en el plano
     */
    public boolean contains(Position position) {
        return position != null && position.row() < getRows() && position.col() < getCols();
    }

    /**
     * Devuelve la zona de una casilla.
     *
     * @param position casilla
     * @return tipo de zona de la casilla
     * @throws IllegalArgumentException si la casilla es nula o está fuera del plano
     */
    public ZoneType zoneAt(Position position) {
        if (!contains(position)) {
            throw new IllegalArgumentException("La casilla " + position + " está fuera del plano.");
        }
        return zones[position.row()][position.col()];
    }

    /**
     * Busca el lugar de un recurso por su código.
     *
     * @param code código del recurso, el mismo que usa el motor en sus eventos
     * @return la casilla del recurso, o vacío si el plano no tiene ese código
     */
    public Optional<Position> positionOf(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(spots.get(code));
    }

    /**
     * Cuenta las casillas de un tipo de zona.
     *
     * @param type tipo de zona
     * @return cantidad de casillas de ese tipo
     */
    public int countOf(ZoneType type) {
        int count = 0;
        for (ZoneType[] row : zones) {
            for (ZoneType zone : row) {
                if (zone == type) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Códigos de todos los recursos que tienen un lugar en el plano. */
    public Set<String> getResourceCodes() {
        return new HashSet<>(spots.keySet());
    }

    public int getCols() {
        return zones[0].length;
    }

    public int getRows() {
        return zones.length;
    }

    public Position getEntrance() {
        return entrance;
    }

    public Position getTriagePoint() {
        return triagePoint;
    }

    public List<Position> getWaitingSpots() {
        return waitingSpots;
    }

    public List<Position> getMorgueSpots() {
        return morgueSpots;
    }

    @Override
    public String toString() {
        return "Plano de " + getCols() + " columnas por " + getRows() + " filas";
    }
}