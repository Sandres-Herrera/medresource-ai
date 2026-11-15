package com.medresource.services.simulation;

/**
 * Avance de un análisis largo: cuántas simulaciones van, cuántas son en total y cuánto tiempo lleva.
 *
 * <p>Con eso se estima el tiempo que falta suponiendo que las simulaciones que quedan tardan lo mismo que las
 * que ya terminaron; la estimación mejora a medida que avanza.</p>
 *
 * @param done          simulaciones terminadas
 * @param total         simulaciones en total, al menos 1
 * @param elapsedMillis milisegundos desde que empezó el análisis
 */
public record WhatIfProgress(int done, int total, long elapsedMillis) {

    /**
     * Valida los números.
     *
     * @throws IllegalArgumentException si el total no es positivo, si las terminadas están fuera de 0 a total o
     *                                  si el tiempo es negativo
     */
    public WhatIfProgress {
        if (total < 1) {
            throw new IllegalArgumentException("El total de simulaciones debe ser positivo.");
        }
        if (done < 0 || done > total) {
            throw new IllegalArgumentException("Las simulaciones terminadas deben estar entre 0 y el total.");
        }
        if (elapsedMillis < 0) {
            throw new IllegalArgumentException("El tiempo transcurrido no puede ser negativo.");
        }
    }

    /**
     * Calcula la parte terminada.
     *
     * @return fracción de 0 a 1
     */
    public double fraction() {
        return done / (double) total;
    }

    /**
     * Estima cuánto falta.
     *
     * @return milisegundos que faltan; {@code -1} si todavía no terminó ninguna simulación y no hay con qué
     *         estimar
     */
    public long estimatedRemainingMillis() {
        if (done == 0) {
            return -1;
        }
        return Math.round(elapsedMillis * (total - done) / (double) done);
    }

    /**
     * Indica si ya terminaron todas las simulaciones.
     *
     * @return {@code true} si no falta ninguna
     */
    public boolean isFinished() {
        return done == total;
    }
}
