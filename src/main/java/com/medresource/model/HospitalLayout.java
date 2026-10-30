package com.medresource.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plano del hospital: las áreas (salas) con su título y el lugar de cada recurso.
 *
 * <p>Es la geometría que comparten las vistas: ninguna regla de la simulación depende de ella y el motor
 * no la conoce. Los recursos se buscan por el mismo código que usa el motor en sus eventos
 * ({@code H1-C2}, {@code UCI-C1}, {@code Q1}, {@code D01}, {@code E01}). Los lugares que el motor no nombra
 * (los puestos de triage, las sillas de la sala de espera, los tipos de sangre del banco de sangre y los
 * lugares de la morgue) son listas ordenadas.</p>
 *
 * <p>Los pacientes llegan por la {@linkplain #getEntrance() entrada}, que está en el área de triage. Es
 * inmutable: las listas que devuelve no se pueden modificar.</p>
 */
public final class HospitalLayout {

    private final int cols;
    private final int rows;
    private final List<Area> areas;
    private final Map<String, Position> spots;
    private final Position entrance;
    private final List<Position> triageSpots;
    private final List<Position> waitingSpots;
    private final List<Position> bloodSpots;
    private final List<Position> morgueSpots;

    /**
     * Crea el plano validando que sea coherente.
     *
     * @param cols         columnas del plano, al menos 1
     * @param rows         filas del plano, al menos 1
     * @param areas        áreas del plano; deben quedar dentro del plano y no pisarse
     * @param spots        lugar de cada recurso según su código; cada lugar debe estar en un área
     * @param entrance     lugar por donde llegan los pacientes, dentro de un área de triage
     * @param triageSpots  puestos de triage, al menos uno, dentro de áreas de triage
     * @param waitingSpots lugares de la sala de espera, al menos uno, dentro de salas de espera
     * @param bloodSpots   lugares del banco de sangre, uno por tipo de sangre, dentro de áreas de banco de
     *                     sangre
     * @param morgueSpots  lugares de la morgue, dentro de áreas de morgue
     * @throws IllegalArgumentException si falta algún dato, el tamaño no es válido, algún área queda fuera
     *                                  del plano o pisa a otra, algún lugar queda fuera de las áreas o en
     *                                  un área que no le corresponde, o dos lugares coinciden
     */
    public HospitalLayout(int cols, int rows, List<Area> areas, Map<String, Position> spots,
                          Position entrance, List<Position> triageSpots, List<Position> waitingSpots,
                          List<Position> bloodSpots, List<Position> morgueSpots) {
        if (areas == null || spots == null || entrance == null || triageSpots == null
                || waitingSpots == null || bloodSpots == null || morgueSpots == null) {
            throw new IllegalArgumentException("Faltan datos del plano del hospital.");
        }
        if (cols < 1 || rows < 1) {
            throw new IllegalArgumentException("El plano debe tener al menos una columna y una fila.");
        }
        this.cols = cols;
        this.rows = rows;
        this.areas = List.copyOf(areas);
        this.spots = Map.copyOf(spots);
        this.entrance = entrance;
        this.triageSpots = List.copyOf(triageSpots);
        this.waitingSpots = List.copyOf(waitingSpots);
        this.bloodSpots = List.copyOf(bloodSpots);
        this.morgueSpots = List.copyOf(morgueSpots);
        validate();
    }

    private void validate() {
        for (int i = 0; i < areas.size(); i++) {
            Area area = areas.get(i);
            if (area.right() > cols || area.bottom() > rows) {
                throw new IllegalArgumentException("El área \"" + area.title() + "\" queda fuera del plano.");
            }
            for (int j = 0; j < i; j++) {
                if (area.overlaps(areas.get(j))) {
                    throw new IllegalArgumentException(
                            "Las áreas \"" + areas.get(j).title() + "\" y \"" + area.title() + "\" se pisan.");
                }
            }
        }
        if (triageSpots.isEmpty() || waitingSpots.isEmpty()) {
            throw new IllegalArgumentException("El triage y la sala de espera necesitan al menos un lugar.");
        }
        requireIn(entrance, ZoneType.TRIAGE, "La entrada");
        requireAllIn(triageSpots, ZoneType.TRIAGE, "Un puesto de triage");
        requireAllIn(waitingSpots, ZoneType.WAITING_ROOM, "Un lugar de la sala de espera");
        requireAllIn(bloodSpots, ZoneType.BLOOD_BANK, "Un lugar del banco de sangre");
        requireAllIn(morgueSpots, ZoneType.MORGUE, "Un lugar de la morgue");
        Set<Position> used = new HashSet<>();
        claim(used, entrance);
        triageSpots.forEach(spot -> claim(used, spot));
        bloodSpots.forEach(spot -> claim(used, spot));
        morgueSpots.forEach(spot -> claim(used, spot));
        for (Map.Entry<String, Position> spot : spots.entrySet()) {
            if (areaAt(spot.getValue()).isEmpty()) {
                throw new IllegalArgumentException("El recurso " + spot.getKey() + " está fuera de las áreas.");
            }
            claim(used, spot.getValue());
        }
    }

    private void claim(Set<Position> used, Position spot) {
        if (!used.add(spot)) {
            throw new IllegalArgumentException("Hay dos cosas en el lugar " + spot + ".");
        }
    }

    private void requireAllIn(List<Position> positions, ZoneType expected, String what) {
        for (Position position : positions) {
            requireIn(position, expected, what);
        }
    }

    private void requireIn(Position position, ZoneType expected, String what) {
        Optional<Area> area = areaAt(position);
        if (area.isEmpty() || area.get().zone() != expected) {
            throw new IllegalArgumentException(what + " debe estar en un área de tipo " + expected + ".");
        }
    }

    /**
     * Busca el área a la que pertenece un lugar.
     *
     * @param position casilla
     * @return el área cuyo lugar es esa casilla, o vacío si cae fuera de las áreas o en la fila de un título
     */
    public Optional<Area> areaAt(Position position) {
        return areas.stream().filter(area -> area.containsSlot(position)).findFirst();
    }

    /**
     * Busca las áreas de un tipo de zona.
     *
     * @param zone tipo de zona
     * @return áreas de ese tipo, en el orden del plano; lista vacía si no hay ninguna
     */
    public List<Area> areasOf(ZoneType zone) {
        List<Area> found = new ArrayList<>();
        for (Area area : areas) {
            if (area.zone() == zone) {
                found.add(area);
            }
        }
        return found;
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

    /** Códigos de todos los recursos que tienen un lugar en el plano. */
    public Set<String> getResourceCodes() {
        return new HashSet<>(spots.keySet());
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    public List<Area> getAreas() {
        return areas;
    }

    /** Lugar por donde llegan los pacientes, dentro del triage. */
    public Position getEntrance() {
        return entrance;
    }

    public List<Position> getTriageSpots() {
        return triageSpots;
    }

    public List<Position> getWaitingSpots() {
        return waitingSpots;
    }

    /** Lugares del banco de sangre, uno por tipo de sangre en el orden de {@link BloodType#values()}. */
    public List<Position> getBloodSpots() {
        return bloodSpots;
    }

    public List<Position> getMorgueSpots() {
        return morgueSpots;
    }

    @Override
    public String toString() {
        return "Plano de " + cols + " columnas por " + rows + " filas con " + areas.size() + " áreas";
    }
}