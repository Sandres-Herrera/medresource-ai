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
 * ({@code H1-C2}, {@code UCI-C1}, {@code Q1}, {@code D01}, {@code E01}); las estaciones de triage
 * ({@code T1}, {@code T2}…) son los puestos de triage en orden. Los lugares que el motor no nombra (las
 * sillas de la sala de espera, la fila del triage, los tipos de sangre del banco de sangre y los lugares de
 * la morgue) son listas ordenadas.</p>
 *
 * <p>Los médicos y enfermeros tienen su {@linkplain StaffSeat puesto} en una mesa de la sala del personal,
 * dos por mesa; se buscan por su código ({@code D01}, {@code E01}) igual que los demás recursos. Cada médico
 * tiene además un escritorio en los consultorios, donde atiende las consultas.</p>
 *
 * <p>Las salas se comunican por {@linkplain Door puertas} que dan a los pasillos. Cada casilla mide
 * {@value #METERS_PER_CELL} m de lado.</p>
 *
 * <p>Los pacientes llegan por la {@linkplain #getEntrance() entrada}, que está en el área de triage. Es
 * inmutable: las listas que devuelve no se pueden modificar.</p>
 */
public final class HospitalLayout {

    /** Metros que mide el lado de una casilla del plano (supuesto). */
    public static final double METERS_PER_CELL = 1.0;

    private final int cols;
    private final int rows;
    private final List<Area> areas;
    private final Map<String, Position> spots;
    private final Position entrance;
    private final List<Position> triageSpots;
    private final List<Position> waitingSpots;
    private final List<Position> bloodSpots;
    private final List<Position> morgueSpots;
    private final Map<String, StaffSeat> staffSeats;
    private final Map<String, Position> consultDesks;
    private final List<Door> doors;

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
        this(cols, rows, areas, spots, entrance, triageSpots, waitingSpots, bloodSpots, morgueSpots, Map.of());
    }

    /**
     * Crea el plano con los puestos del personal en las mesas de su sala.
     *
     * @param cols         columnas del plano, al menos 1
     * @param rows         filas del plano, al menos 1
     * @param areas        áreas del plano; deben quedar dentro del plano y no pisarse
     * @param spots        lugar de cada recurso según su código; cada lugar debe estar en un área
     * @param entrance     lugar por donde llegan los pacientes, dentro de un área de triage
     * @param triageSpots  puestos de triage, al menos uno, dentro de áreas de triage
     * @param waitingSpots lugares de la sala de espera, al menos uno, dentro de salas de espera
     * @param bloodSpots   lugares del banco de sangre, dentro de áreas de banco de sangre
     * @param morgueSpots  lugares de la morgue, dentro de áreas de morgue
     * @param staffSeats   puesto de cada médico y enfermero según su código, en mesas de una sala del personal
     * @throws IllegalArgumentException si falta algún dato o el plano no es coherente (ver el otro
     *                                  constructor), si un puesto queda fuera de una sala del personal, si dos
     *                                  personas comparten el mismo puesto, si una mesa coincide con otro lugar
     *                                  o si un código tiene a la vez lugar y puesto
     */
    public HospitalLayout(int cols, int rows, List<Area> areas, Map<String, Position> spots,
                          Position entrance, List<Position> triageSpots, List<Position> waitingSpots,
                          List<Position> bloodSpots, List<Position> morgueSpots, Map<String, StaffSeat> staffSeats) {
        this(cols, rows, areas, spots, entrance, triageSpots, waitingSpots, bloodSpots, morgueSpots, staffSeats,
                Map.of(), List.of());
    }

    /**
     * Crea el plano completo: con los puestos del personal, los escritorios de los consultorios y las puertas.
     *
     * @param cols         columnas del plano, al menos 1
     * @param rows         filas del plano, al menos 1
     * @param areas        áreas del plano; deben quedar dentro del plano y no pisarse
     * @param spots        lugar de cada recurso según su código; cada lugar debe estar en un área
     * @param entrance     lugar por donde llegan los pacientes, dentro de un área de triage
     * @param triageSpots  puestos de triage, al menos uno, dentro de áreas de triage
     * @param waitingSpots lugares de la sala de espera, al menos uno, dentro de salas de espera
     * @param bloodSpots   lugares del banco de sangre, dentro de áreas de banco de sangre
     * @param morgueSpots  lugares de la morgue, dentro de áreas de morgue
     * @param staffSeats   puesto de cada médico y enfermero según su código, en mesas de una sala del personal
     * @param consultDesks escritorio de cada médico según su código, en un área de consultorios
     * @param doors        puertas de las salas hacia los pasillos
     * @throws IllegalArgumentException si falta algún dato o el plano no es coherente (ver los otros
     *                                  constructores), si un escritorio queda fuera de los consultorios o
     *                                  coincide con otro lugar, o si una puerta no va de una sala a un pasillo
     */
    public HospitalLayout(int cols, int rows, List<Area> areas, Map<String, Position> spots,
                          Position entrance, List<Position> triageSpots, List<Position> waitingSpots,
                          List<Position> bloodSpots, List<Position> morgueSpots, Map<String, StaffSeat> staffSeats,
                          Map<String, Position> consultDesks, List<Door> doors) {
        if (areas == null || spots == null || entrance == null || triageSpots == null
                || waitingSpots == null || bloodSpots == null || morgueSpots == null || staffSeats == null
                || consultDesks == null || doors == null) {
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
        this.staffSeats = Map.copyOf(staffSeats);
        this.consultDesks = Map.copyOf(consultDesks);
        this.doors = List.copyOf(doors);
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
        validateStaffSeats(used);
        for (Map.Entry<String, Position> desk : consultDesks.entrySet()) {
            requireIn(desk.getValue(), ZoneType.CONSULTATION, "El escritorio de " + desk.getKey());
            claim(used, desk.getValue());
        }
        doors.forEach(this::validateDoor);
    }

    /** Una puerta va del borde de una sala (que no sea pasillo) a un pasillo. */
    private void validateDoor(Door door) {
        Optional<Area> room = areaCovering(door.inside());
        Optional<Area> corridor = areaCovering(door.outside());
        if (room.isEmpty() || room.get().zone() == ZoneType.CORRIDOR) {
            throw new IllegalArgumentException("La puerta " + door + " no sale de una sala.");
        }
        if (corridor.isEmpty() || corridor.get().zone() != ZoneType.CORRIDOR) {
            throw new IllegalArgumentException("La puerta " + door + " no da a un pasillo.");
        }
    }

    private Optional<Area> areaCovering(Position position) {
        return areas.stream().filter(area -> area.covers(position)).findFirst();
    }

    /** Cada puesto va en una mesa de la sala del personal, sin repetirse; las mesas no pisan otros lugares. */
    private void validateStaffSeats(Set<Position> used) {
        Set<StaffSeat> taken = new HashSet<>();
        Set<Position> tables = new HashSet<>();
        for (Map.Entry<String, StaffSeat> seat : staffSeats.entrySet()) {
            if (spots.containsKey(seat.getKey())) {
                throw new IllegalArgumentException("El código " + seat.getKey() + " tiene lugar y puesto a la vez.");
            }
            requireIn(seat.getValue().table(), ZoneType.STAFF_ROOM, "El puesto de " + seat.getKey());
            if (!taken.add(seat.getValue())) {
                throw new IllegalArgumentException("Dos personas comparten el puesto de " + seat.getKey() + ".");
            }
            tables.add(seat.getValue().table());
        }
        tables.forEach(table -> claim(used, table));
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
     * Indica si se puede pisar una casilla: cualquier casilla de un área, también la fila de su título. Fuera de
     * las áreas no se camina.
     *
     * @param position casilla
     * @return {@code true} si la casilla está dentro de un área del plano
     */
    public boolean isWalkable(Position position) {
        return position != null && areaCovering(position).isPresent();
    }

    /**
     * Indica si se puede dar un paso de una casilla a otra vecina sin atravesar una pared.
     *
     * <p>Dentro de una misma área se camina libremente, y de un pasillo a otro también. Entre una sala y un
     * pasillo solo se pasa por una {@linkplain Door puerta}, en cualquiera de los dos sentidos; entre dos salas
     * no se pasa nunca.</p>
     *
     * @param from casilla donde está la persona
     * @param to   casilla a la que quiere pasar
     * @return {@code true} si las dos casillas son vecinas y se puede pasar de una a la otra
     */
    public boolean canStep(Position from, Position to) {
        if (from == null || to == null || from.stepsTo(to) != 1) {
            return false;
        }
        Optional<Area> here = areaCovering(from);
        Optional<Area> there = areaCovering(to);
        if (here.isEmpty() || there.isEmpty()) {
            return false;
        }
        if (here.get().equals(there.get())) {
            return true;
        }
        if (here.get().zone() == ZoneType.CORRIDOR && there.get().zone() == ZoneType.CORRIDOR) {
            return true;
        }
        return doors.stream().anyMatch(door -> door.inside().equals(from) && door.outside().equals(to)
                || door.inside().equals(to) && door.outside().equals(from));
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
     * Busca el lugar de un recurso por su código. Una estación de triage ({@code T1}, {@code T2}…) está en
     * el puesto de triage de su mismo número, y un médico o enfermero, en la mesa de su puesto.
     *
     * @param code código del recurso, el mismo que usa el motor en sus eventos
     * @return la casilla del recurso, o vacío si el plano no tiene ese código
     */
    public Optional<Position> positionOf(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Position spot = spots.get(code);
        if (spot != null) {
            return Optional.of(spot);
        }
        StaffSeat seat = staffSeats.get(code);
        if (seat != null) {
            return Optional.of(seat.table());
        }
        int station = TriageStation.numberOf(code);
        return station >= 1 && station <= triageSpots.size()
                ? Optional.of(triageSpots.get(station - 1))
                : Optional.empty();
    }

    /**
     * Busca el puesto de un médico o enfermero en la sala del personal.
     *
     * @param code código del médico o enfermero
     * @return su puesto, o vacío si el plano no le asigna uno
     */
    public Optional<StaffSeat> staffSeatOf(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(staffSeats.get(code));
    }

    /**
     * Busca el escritorio de un médico en los consultorios.
     *
     * @param code código del médico
     * @return su escritorio, o vacío si el plano no le asigna uno
     */
    public Optional<Position> consultDeskOf(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(consultDesks.get(code));
    }

    /**
     * Lugar donde se atiende a un paciente según el recurso que nombra el motor: el escritorio del médico si
     * es una consulta con un médico que tiene escritorio, o el lugar del recurso en los demás casos.
     *
     * @param code código del recurso (cama, quirófano o médico)
     * @return la casilla donde está el paciente durante el tratamiento, o vacío si el plano no la tiene
     */
    public Optional<Position> treatmentPlaceOf(String code) {
        Optional<Position> desk = consultDeskOf(code);
        return desk.isPresent() ? desk : positionOf(code);
    }

    /**
     * Busca qué recurso está en una casilla: la cama, la cama de UCI o el quirófano que tiene ese lugar, o el
     * médico cuyo escritorio de consultorio está ahí. Es lo contrario de {@link #treatmentPlaceOf(String)}.
     *
     * @param position casilla del plano
     * @return código del recurso, o vacío si en esa casilla no hay ninguno (una silla, un pasillo…)
     */
    public Optional<String> resourceAt(Position position) {
        if (position == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, Position> spot : spots.entrySet()) {
            if (spot.getValue().equals(position)) {
                return Optional.of(spot.getKey());
            }
        }
        for (Map.Entry<String, Position> desk : consultDesks.entrySet()) {
            if (desk.getValue().equals(position)) {
                return Optional.of(desk.getKey());
            }
        }
        return Optional.empty();
    }

    /**
     * Puertas de las salas hacia los pasillos.
     *
     * @return puertas en el orden del plano; la lista no se puede modificar
     */
    public List<Door> getDoors() {
        return doors;
    }

    /**
     * Puertas de un área.
     *
     * @param area área del plano
     * @return puertas cuya casilla de adentro está en esa área
     */
    public List<Door> doorsOf(Area area) {
        return doors.stream().filter(door -> area != null && area.covers(door.inside())).toList();
    }

    /** Códigos de los médicos y enfermeros que tienen puesto en la sala del personal. */
    public Set<String> getStaffCodes() {
        return new HashSet<>(staffSeats.keySet());
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

    /**
     * Lugares de la fila del triage: los del área de triage que no son la entrada ni un puesto de triage, en
     * orden. Ahí esperan su turno los pacientes cuando todas las estaciones están ocupadas.
     *
     * @return lugares en el orden en que se llenan; la lista no se puede modificar
     */
    public List<Position> getTriageQueueSpots() {
        List<Position> queue = new ArrayList<>();
        for (Area area : areasOf(ZoneType.TRIAGE)) {
            for (int index = 0; index < area.slotCount(); index++) {
                Position slot = area.slot(index);
                if (!slot.equals(entrance) && !triageSpots.contains(slot)) {
                    queue.add(slot);
                }
            }
        }
        return List.copyOf(queue);
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