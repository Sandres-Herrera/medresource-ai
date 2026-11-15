package com.medresource.ui.view;

import com.medresource.model.ReportProgress;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Escribe los números de la ventana del reporte como se leen en español: coma decimal, porcentajes enteros, horas
 * del reloj y el tiempo que falta.
 *
 * <p>Redondea igual que los hechos que se le envían a la IA (a un decimal, la mitad hacia arriba), para que las
 * cifras de las gráficas coincidan con las del texto.</p>
 */
public final class ReportFormat {

    /** Texto mientras todavía no se sabe cuánto falta. */
    public static final String CALCULATING = "Calculando el tiempo que falta…";
    /** Texto cuando falta un segundo o menos. */
    public static final String ALMOST_DONE = "Falta un momento";

    private static final int SECONDS_PER_MINUTE = 60;

    private ReportFormat() {
    }

    /**
     * Escribe un número con un decimal como máximo y coma decimal; sin ceros sobrantes.
     *
     * @param value número
     * @return por ejemplo {@code 12,3} o {@code 13}
     */
    public static String decimal(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "—";
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.scale() < 0) {
            rounded = rounded.setScale(0);
        }
        return rounded.toPlainString().replace('.', ',');
    }

    /**
     * Escribe una parte de 0 a 1 como porcentaje con un decimal como máximo.
     *
     * @param fraction parte de 0 a 1
     * @return por ejemplo {@code 38 %} o {@code 12,5 %}
     */
    public static String percent(double fraction) {
        return decimal(100 * fraction) + " %";
    }

    /**
     * Escribe un minuto de la simulación como hora del reloj, empezando a las 00:00.
     *
     * @param minute minuto de la simulación
     * @return por ejemplo {@code 01:10} para el minuto 70
     * @throws IllegalArgumentException si el minuto es negativo
     */
    public static String clock(int minute) {
        if (minute < 0) {
            throw new IllegalArgumentException("El minuto no puede ser negativo.");
        }
        return String.format("%02d:%02d", minute / SECONDS_PER_MINUTE % 24, minute % SECONDS_PER_MINUTE);
    }

    /**
     * Escribe cuánto falta para que termine la generación del reporte.
     *
     * @param progress avance de la generación
     * @return por ejemplo «Faltan unos 25 s», «Faltan unos 1 min 10 s», {@value #ALMOST_DONE} o, sin estimación,
     *         {@value #CALCULATING}
     * @throws IllegalArgumentException si falta el avance
     */
    public static String remaining(ReportProgress progress) {
        if (progress == null) {
            throw new IllegalArgumentException("El avance es obligatorio.");
        }
        if (!progress.isEstimated()) {
            return CALCULATING;
        }
        long seconds = (progress.remainingMillis() + 999) / 1000;
        if (seconds <= 1) {
            return ALMOST_DONE;
        }
        if (seconds < SECONDS_PER_MINUTE) {
            return "Faltan unos " + seconds + " s";
        }
        long minutes = seconds / SECONDS_PER_MINUTE;
        long rest = seconds % SECONDS_PER_MINUTE;
        return "Faltan unos " + minutes + " min" + (rest == 0 ? "" : " " + rest + " s");
    }
}
