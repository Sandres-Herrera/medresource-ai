package com.medresource.ui.controller;

import java.util.List;

/**
 * Reloj de la simulación en pantalla, en segundos simulados.
 *
 * <p>Con velocidad ×1 un segundo real es un segundo dentro del hospital; con ×10, diez. El motor avanza por
 * minutos, así que el reloj acumula los segundos y avisa cada vez que se completa un minuto. Entre un minuto
 * y otro, {@link #minuteFraction()} dice qué parte del minuto pasó, para mover a los pacientes con suavidad.
 * El reloj solo cambia lo que se ve: no conoce el motor ni el resultado de la simulación.</p>
 *
 * <p>Si un cuadro llega muy tarde (por ejemplo, porque la ventana se congeló), el reloj completa como mucho
 * un minuto y descarta el tiempo sobrante, para que los pacientes no salten varios lugares de golpe.</p>
 */
public final class SimulationClock {

    /** Velocidades que se pueden elegir: segundos simulados por cada segundo real. */
    public static final List<Integer> SPEEDS = List.of(1, 2, 5, 10);

    /** Velocidad inicial: tiempo real. */
    public static final int DEFAULT_SPEED = 1;

    /** Segundos que tiene un minuto simulado. */
    public static final int SECONDS_PER_MINUTE = 60;

    private static final int SECONDS_PER_DAY = 24 * 60 * SECONDS_PER_MINUTE;

    private int speed = DEFAULT_SPEED;
    private double secondsIntoMinute;

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
     * Suma el tiempo real que pasó, multiplicado por la velocidad.
     *
     * @param elapsedMillis milisegundos reales desde el cuadro anterior
     * @return {@code true} si con este tiempo se completó un minuto simulado
     * @throws IllegalArgumentException si el tiempo es negativo
     */
    public boolean advance(double elapsedMillis) {
        if (elapsedMillis < 0) {
            throw new IllegalArgumentException("El tiempo transcurrido no puede ser negativo.");
        }
        secondsIntoMinute += speed * elapsedMillis / 1000.0;
        if (secondsIntoMinute < SECONDS_PER_MINUTE) {
            return false;
        }
        secondsIntoMinute -= SECONDS_PER_MINUTE;
        if (secondsIntoMinute >= SECONDS_PER_MINUTE) {
            secondsIntoMinute = 0;
        }
        return true;
    }

    /** Vuelve al segundo 0 del minuto actual, sin cambiar la velocidad. */
    public void restartMinute() {
        secondsIntoMinute = 0;
    }

    /** Segundo dentro del minuto actual, de 0 a 59. */
    public int getSecond() {
        return (int) secondsIntoMinute;
    }

    /** Parte del minuto actual que ya pasó, de 0 (recién empieza) a casi 1. */
    public double minuteFraction() {
        return secondsIntoMinute / SECONDS_PER_MINUTE;
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