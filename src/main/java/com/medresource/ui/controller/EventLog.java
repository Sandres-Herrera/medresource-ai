package com.medresource.ui.controller;

import com.medresource.model.SimulationEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Registro de los eventos que se vieron en la simulación, con la hora en que se vieron.
 *
 * <p>Guarda los últimos {@value #CAPACITY} eventos; cuando se llena, olvida los más viejos. Así se puede cambiar
 * el filtro de la ventana y volver a mostrar los eventos anteriores que lo cumplen, por ejemplo toda la historia
 * de un paciente.</p>
 */
public final class EventLog {

    /** Cantidad máxima de eventos que se guardan. */
    public static final int CAPACITY = 3000;

    /**
     * Un evento con la hora del día en que se vio.
     *
     * @param time  hora en formato {@code hh:mm:ss}
     * @param event evento de la simulación
     */
    public record Entry(String time, SimulationEvent event) {

        /**
         * Valida la entrada.
         *
         * @throws IllegalArgumentException si falta la hora o el evento
         */
        public Entry {
            if (time == null || time.isBlank() || event == null) {
                throw new IllegalArgumentException("La hora y el evento son obligatorios.");
            }
        }

        /** Línea que se escribe en el registro: hora, tipo de evento y descripción. */
        public String line() {
            return String.format("[%s] %-22s %s", time, event.type().getDisplayName(), event.description());
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();

    /**
     * Agrega un evento al final del registro; si ya hay {@value #CAPACITY}, olvida el más viejo.
     *
     * @param time  hora en que se vio, en formato {@code hh:mm:ss}
     * @param event evento
     * @return la entrada agregada
     * @throws IllegalArgumentException si falta la hora o el evento
     */
    public Entry add(String time, SimulationEvent event) {
        Entry entry = new Entry(time, event);
        if (entries.size() == CAPACITY) {
            entries.removeFirst();
        }
        entries.addLast(entry);
        return entry;
    }

    /** Olvida todos los eventos, por ejemplo al reiniciar la simulación. */
    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }

    /**
     * Eventos que cumplen un filtro, del más viejo al más reciente.
     *
     * @param filter filtro
     * @param limit  cuántos de los más recientes se devuelven como mucho
     * @return entradas que cumplen el filtro
     * @throws IllegalArgumentException si el filtro es nulo o el límite no es positivo
     */
    public List<Entry> entries(EventFilter filter, int limit) {
        if (filter == null) {
            throw new IllegalArgumentException("El filtro es obligatorio.");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("El límite debe ser positivo.");
        }
        List<Entry> accepted = new ArrayList<>();
        for (Entry entry : entries) {
            if (filter.accepts(entry.event())) {
                accepted.add(entry);
            }
        }
        return List.copyOf(accepted.subList(Math.max(0, accepted.size() - limit), accepted.size()));
    }
}
