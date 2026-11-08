package com.medresource.ui.render;

import com.medresource.model.SimulationEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Línea de tiempo de la escena: le da a cada evento de la simulación su propio segundo dentro del minuto.
 *
 * <p>El motor trabaja por minutos y entrega juntos todos los eventos de un minuto. Para que en pantalla no
 * pase todo de golpe, la línea de tiempo reparte en los 60 segundos del minuto los eventos que mueven a
 * alguien (llegadas, inicios de tratamiento, salidas…), en el mismo orden en que los emitió el motor: el
 * minuto se divide en tantos tramos como eventos de ese tipo haya, y cada evento cae en un punto de su tramo
 * que depende del minuto, del paciente y del tipo de evento. Así, el mismo caso se ve siempre igual. Los
 * eventos que no mueven a nadie (triage, diagnóstico…) ocurren en el mismo momento que el evento anterior.</p>
 *
 * <p>Es solo para la vista: no cambia el orden ni el resultado de la simulación.</p>
 */
public final class SceneTimeline {

    /** Un evento con el momento (en segundos simulados desde el inicio) en que se ve en pantalla. */
    public record TimedEvent(double time, SimulationEvent event) {
    }

    /** Parte de su tramo en la que puede caer un evento; el resto separa un evento del siguiente. */
    private static final double SPREAD = 0.8;

    private static final int SECONDS_PER_MINUTE = 60;

    private final Deque<TimedEvent> pending = new ArrayDeque<>();
    private int lastMinute = -1;

    /**
     * Programa los eventos de un minuto.
     *
     * @param minute minuto en que ocurrieron (el mismo de todos los eventos)
     * @param events eventos en el orden en que el motor los emitió
     * @throws IllegalArgumentException si falta la lista o algún evento, si el minuto es negativo o anterior
     *                                  a uno ya programado, o si algún evento es de otro minuto
     */
    public void schedule(int minute, List<SimulationEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("La lista de eventos es obligatoria.");
        }
        if (minute < 0 || minute <= lastMinute) {
            throw new IllegalArgumentException("El minuto debe ser posterior al último programado.");
        }
        for (SimulationEvent event : events) {
            if (event == null || event.minute() != minute) {
                throw new IllegalArgumentException("Todos los eventos deben ser del minuto " + minute + ".");
            }
        }
        lastMinute = minute;
        long moving = events.stream().filter(event -> SceneState.changesPlacement(event.type())).count();
        double start = (double) minute * SECONDS_PER_MINUTE;
        double slot = moving == 0 ? SECONDS_PER_MINUTE : SECONDS_PER_MINUTE / (double) moving;
        double current = start;
        int index = 0;
        for (SimulationEvent event : events) {
            if (SceneState.changesPlacement(event.type())) {
                current = start + index * slot + jitter(event) * slot * SPREAD;
                index++;
            }
            pending.addLast(new TimedEvent(current, event));
        }
    }

    /**
     * Saca los eventos que ya deben verse.
     *
     * @param time momento actual, en segundos simulados desde el inicio
     * @return eventos con momento menor o igual que {@code time}, en orden
     */
    public List<TimedEvent> releaseUntil(double time) {
        List<TimedEvent> due = new ArrayList<>();
        while (!pending.isEmpty() && pending.peekFirst().time() <= time) {
            due.add(pending.pollFirst());
        }
        return due;
    }

    /** Momento del próximo evento pendiente; vacío si no hay ninguno. */
    public OptionalDouble nextTime() {
        return pending.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(pending.peekFirst().time());
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    /** Cantidad de eventos que todavía no se han visto. */
    public int size() {
        return pending.size();
    }

    /** Número entre 0 y 1 que parece al azar pero siempre es el mismo para el mismo evento. */
    static double jitter(SimulationEvent event) {
        long z = event.minute() * 0x9E3779B97F4A7C15L
                + String.valueOf(event.patientCode()).hashCode() * 0xBF58476D1CE4E5B9L
                + event.type().ordinal() * 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }
}
