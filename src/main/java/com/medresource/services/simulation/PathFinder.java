package com.medresource.services.simulation;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.Route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Busca por dónde camina una persona de una casilla a otra del plano del hospital, sin atravesar paredes.
 *
 * <p>Usa el algoritmo A*: avanza casilla por casilla (arriba, abajo, a la izquierda o a la derecha) por donde
 * el plano deja dar el paso ({@link HospitalLayout#canStep(Position, Position)}), y siempre prueba primero la
 * casilla que promete el camino más corto: los pasos ya dados más la distancia en línea quebrada que falta
 * hasta el destino ({@link Position#stepsTo(Position)}). Como esa distancia nunca es mayor que la que de verdad
 * falta, la ruta que encuentra es la más corta. Así, para pasar de una sala a otra, la ruta sale por la puerta
 * de la primera, sigue los pasillos y entra por la puerta de la segunda.</p>
 *
 * <p>Si hay varias rutas igual de cortas elige siempre la misma: el mismo plano da siempre las mismas rutas.
 * No guarda nada entre búsquedas.</p>
 */
public final class PathFinder {

    /** Vecinos de una casilla en el orden en que se prueban: arriba, derecha, abajo e izquierda. */
    private static final int[][] DIRECTIONS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** Primero la casilla que promete el camino más corto; si empatan, la más cerca del destino y la más antigua. */
    private static final Comparator<Step> MOST_PROMISING = Comparator.comparingInt(Step::promised)
            .thenComparingInt(Step::remaining)
            .thenComparingLong(Step::order);

    private final HospitalLayout layout;

    /**
     * Crea el buscador para un plano.
     *
     * @param layout plano del hospital
     * @throws IllegalArgumentException si el plano es nulo
     */
    public PathFinder(HospitalLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("El plano es obligatorio.");
        }
        this.layout = layout;
    }

    /**
     * Busca la ruta más corta entre dos casillas.
     *
     * @param from casilla de salida
     * @param to   casilla de llegada
     * @return la ruta, que empieza en {@code from} y termina en {@code to}; vacío si no hay forma de llegar
     * @throws IllegalArgumentException si falta alguna casilla o si alguna no se puede pisar
     */
    public Optional<Route> find(Position from, Position to) {
        requireWalkable(from, "de salida");
        requireWalkable(to, "de llegada");
        Map<Position, Integer> walked = new HashMap<>();
        Map<Position, Position> cameFrom = new HashMap<>();
        Set<Position> done = new HashSet<>();
        PriorityQueue<Step> open = new PriorityQueue<>(MOST_PROMISING);
        long order = 0;
        walked.put(from, 0);
        open.add(new Step(from, 0, from.stepsTo(to), order++));
        while (!open.isEmpty()) {
            Step current = open.poll();
            if (current.cell().equals(to)) {
                return Optional.of(rebuild(cameFrom, to));
            }
            if (!done.add(current.cell())) {
                continue;
            }
            for (int[] direction : DIRECTIONS) {
                Optional<Position> next = neighbour(current.cell(), direction);
                if (next.isEmpty() || done.contains(next.get()) || !layout.canStep(current.cell(), next.get())) {
                    continue;
                }
                int steps = current.walked() + 1;
                if (steps < walked.getOrDefault(next.get(), Integer.MAX_VALUE)) {
                    walked.put(next.get(), steps);
                    cameFrom.put(next.get(), current.cell());
                    open.add(new Step(next.get(), steps, next.get().stepsTo(to), order++));
                }
            }
        }
        return Optional.empty();
    }

    private void requireWalkable(Position position, String which) {
        if (position == null) {
            throw new IllegalArgumentException("La casilla " + which + " es obligatoria.");
        }
        if (!layout.isWalkable(position)) {
            throw new IllegalArgumentException("La casilla " + which + " " + position + " no se puede pisar.");
        }
    }

    /** Casilla vecina en una dirección, o vacío si quedaría fuera del plano. */
    private static Optional<Position> neighbour(Position cell, int[] direction) {
        int col = cell.col() + direction[0];
        int row = cell.row() + direction[1];
        return col < 0 || row < 0 ? Optional.empty() : Optional.of(new Position(col, row));
    }

    /** Arma la ruta desde la llegada hacia atrás, siguiendo de dónde vino cada casilla. */
    private static Route rebuild(Map<Position, Position> cameFrom, Position to) {
        List<Position> cells = new ArrayList<>();
        for (Position cell = to; cell != null; cell = cameFrom.get(cell)) {
            cells.add(cell);
        }
        Collections.reverse(cells);
        return new Route(cells);
    }

    /**
     * Casilla por probar.
     *
     * @param cell      casilla
     * @param walked    pasos dados desde la salida hasta ella
     * @param remaining pasos que faltan como mínimo hasta la llegada
     * @param order     orden en que se encontró, para desempatar siempre igual
     */
    private record Step(Position cell, int walked, int remaining, long order) {

        /** Pasos de la ruta más corta que esta casilla promete. */
        int promised() {
            return walked + remaining;
        }
    }
}
