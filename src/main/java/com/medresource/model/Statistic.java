package com.medresource.model;

import java.util.Arrays;

/**
 * Resumen de una lista de números: cuántos son, su promedio, su desviación y su rango.
 *
 * <p>Sirve para resumir una métrica medida en muchos casos. El intervalo de confianza del 95 % usa la
 * aproximación normal ({@value #Z_95} desviaciones estándar del promedio); es razonable con 30 casos o más.</p>
 *
 * @param count             cuántos números se resumieron (sin contar los que no son un número)
 * @param mean              promedio; 0 si no había números
 * @param standardDeviation desviación estándar de la muestra; 0 si había menos de dos números
 * @param min               el menor; 0 si no había números
 * @param max               el mayor; 0 si no había números
 */
public record Statistic(int count, double mean, double standardDeviation, double min, double max) {

    /** Valor de la distribución normal que deja el 95 % de los casos en el centro. */
    public static final double Z_95 = 1.96;

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si la cantidad o la desviación son negativas, si el promedio no
     *                                  es un número, o si el mínimo es mayor que el máximo
     */
    public Statistic {
        if (count < 0) {
            throw new IllegalArgumentException("La cantidad no puede ser negativa.");
        }
        if (standardDeviation < 0 || Double.isNaN(standardDeviation)) {
            throw new IllegalArgumentException("La desviación estándar no puede ser negativa.");
        }
        if (Double.isNaN(mean) || Double.isNaN(min) || Double.isNaN(max)) {
            throw new IllegalArgumentException("El resumen debe ser de números.");
        }
        if (min > max) {
            throw new IllegalArgumentException("El mínimo no puede ser mayor que el máximo.");
        }
    }

    /**
     * Resume una lista de números.
     *
     * @param values números a resumir; los {@link Double#NaN} se ignoran
     * @return el resumen
     * @throws IllegalArgumentException si la lista es nula
     */
    public static Statistic of(double... values) {
        if (values == null) {
            throw new IllegalArgumentException("Los números a resumir son obligatorios.");
        }
        double[] valid = Arrays.stream(values).filter(value -> !Double.isNaN(value)).toArray();
        if (valid.length == 0) {
            return new Statistic(0, 0, 0, 0, 0);
        }
        double mean = Arrays.stream(valid).average().orElse(0);
        double squares = Arrays.stream(valid).map(value -> (value - mean) * (value - mean)).sum();
        double deviation = valid.length < 2 ? 0 : Math.sqrt(squares / (valid.length - 1));
        return new Statistic(valid.length, mean, deviation,
                Arrays.stream(valid).min().orElse(0), Arrays.stream(valid).max().orElse(0));
    }

    /**
     * Calcula la mitad del ancho del intervalo de confianza del 95 % del promedio.
     *
     * @return {@value #Z_95} × desviación / √cantidad; 0 si hay menos de dos números
     */
    public double confidenceHalfWidth() {
        return count < 2 ? 0.0 : Z_95 * standardDeviation / Math.sqrt(count);
    }

    /**
     * Indica si el promedio es distinto de cero con confianza del 95 %.
     *
     * @return {@code true} si el intervalo de confianza no incluye el cero
     */
    public boolean excludesZero() {
        return count >= 2 && Math.abs(mean) > confidenceHalfWidth();
    }
}