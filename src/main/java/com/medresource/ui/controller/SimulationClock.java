package com.medresource.ui.controller;

import java.util.List;

/**
 * Reloj de la simulación en pantalla: lleva el tiempo simulado en segundos desde el inicio.
 *
 * <p>Con velocidad ×1 un segundo real es un segundo dentro del hospital; con ×10, diez. El reloj solo cambia
 * lo que se ve: no conoce el motor ni el resultado de la simulación.</p>
 *
 * <p>Si un cuadro llega muy tarde (por ejemplo, porque la ventana se congeló), el reloj avanza como mucho
 * {@value #MAX_SECONDS_PER_FRAME} segundos simulados y descarta el resto, para que los pacientes no salten de
 * un lugar a otro.</p>
 */
public final class SimulationClock {

    /** Velocidades que se pueden elegir: segundos simulados por cada segundo real. */
    public static final List<Integer> SPEEDS = List.of(1, 2, 5, 10);

    /** Velocidad inicial: tiempo real. */
    public static final int DEFAULT_SPEED = 1;

    /** Segundos que tiene un minuto simulado. */
    public static final int SECONDS_PER_MINUTE = 60;

    /** Máximo de segundos simulados que avanza el reloj en un solo cuadro. */
    public static final double MAX_SECONDS_PER_FRAME = 5.0;

    private static final int SECONDS_PER_DAY = 24 * 60 * SECONDS_PER_MINUTE;

    private int speed = DEFAULT_SPEED;
    private double time;

    /**
     * Cambia la velocidad.
     *
     * @param newSpeed segundos simulados por segundo real; debe ser una de {@link #SPEEDS}
     * @throws IllegalArgumentException si la velocidad no está en la lista
     */
    public void setSpeed(int newSpeed) {
        if (!SPEEDS.contains(newSpeed)) {
            throw new IllegalArgumentException("La velocidad debe ser una de " + SPEEDS + ".");
        }
        this.speed = newSpeed;
    }

    public int getSpeed() {
        return speed;
    }

    /**
     * Suma el tiempo real que pasó, multiplicado por la velocidad, sin pasar de
     * {@value #MAX_SECONDS_PER_FRAME} segundos simulados.
     *
     * @param elapsedMillis milisegundos reales desde el cuadro anterior
     * @throws IllegalArgumentException si el tiempo es negativo
     */
    public void advance(double elapsedMillis) {
        if (elapsedMillis < 0) {
            throw new IllegalArgumentException("El tiempo transcurrido no puede ser negativo.");
        }
        time += Math.min(speed * elapsedMillis / 1000.0, MAX_SECONDS_PER_FRAME);
    }

    /**
     * Adelanta el reloj hasta un momento dado (por ejemplo, para saltar al siguiente evento).
     *
     * @param newTime segundos simulados desde el inicio
     * @throws IllegalArgumentException si el momento es anterior al actual
     */
    public void jumpTo(double newTime) {
        if (newTime < time) {
            throw new IllegalArgumentException("El reloj no puede retroceder.");
        }
        time = newTime;
    }

    /** Vuelve al segundo 0 de la simulación, sin cambiar la velocidad. */
    public void reset() {
        time = 0;
    }

    /** Segundos simulados desde el inicio. */
    public double getTime() {
        return time;
    }

    /** Minuto simulado actual. */
    public int getMinute() {
        return (int) (time / SECONDS_PER_MINUTE);
    }

    /** Segundo dentro del minuto actual, de 0 a 59. */
    public int getSecond() {
        return (int) (time % SECONDS_PER_MINUTE);
    }

    /**
     * Escribe solo la hora del día con segundos (por ejemplo, el minuto 75 y el segundo 9 es «01:15:09»),
     * para el registro de eventos.
     *
     * @param minute minuto simulado
     * @param second segundo dentro del minuto, de 0 a 59
     * @return hora con el formato hh:mm:ss
     * @throws IllegalArgumentException si el minuto es negativo o el segundo está fuera de rango
     */
    public static String formatTimeOfDay(int minute, int second) {
        String full = format(minute, second);
        return full.substring(full.length() - "hh:mm:ss".length());
    }

    /**
     * Escribe un momento de la simulación como día y hora con segundos, empezando el día 1 a las 00:00:00
     * (por ejemplo, el minuto 1515 y el segundo 30 es «Día 2 · 01:15:30»).
     *
     * @param minute minuto simulado
     * @param second segundo dentro del minuto, de 0 a 59
     * @return texto con el día y la hora
     * @throws IllegalArgumentException si el minuto es negativo o el segundo está fuera de rango
     */
    public static String format(int minute, int second) {
        if (minute < 0) {
            throw new IllegalArgumentException("El minuto no puede ser negativo.");
        }
        if (second < 0 || second >= SECONDS_PER_MINUTE) {
            throw new IllegalArgumentException("El segundo debe estar entre 0 y 59.");
        }
        long total = (long) minute * SECONDS_PER_MINUTE + second;
        long day = total / SECONDS_PER_DAY + 1;
        long ofDay = total % SECONDS_PER_DAY;
        return String.format("Día %d · %02d:%02d:%02d", day, ofDay / 3600, ofDay / 60 % 60, ofDay % 60);
    }
}
