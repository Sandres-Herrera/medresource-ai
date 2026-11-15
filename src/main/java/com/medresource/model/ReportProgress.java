package com.medresource.model;

/**
 * Avance de la generación del reporte: en qué etapa va, cuánto tiempo lleva y cuánto se estima que falta.
 *
 * <p>La parte terminada se calcula con el tiempo: lo que lleva sobre lo que lleva más lo que falta. Así la barra
 * avanza parejo aunque una etapa tarde mucho más que otra. Mientras no hay con qué estimar (por ejemplo, antes de
 * que termine la primera simulación del «¿y si…?»), la barra se queda al comienzo de la etapa y la ventana dice que
 * está calculando el tiempo.</p>
 *
 * @param stage           etapa actual
 * @param elapsedMillis   milisegundos desde que empezó la generación
 * @param remainingMillis milisegundos que se estima que faltan para terminar todo; {@value #UNKNOWN} si todavía
 *                        no se sabe
 */
public record ReportProgress(ReportStage stage, long elapsedMillis, long remainingMillis) {

    /** Valor de {@code remainingMillis} cuando todavía no se sabe cuánto falta. */
    public static final long UNKNOWN = -1;

    /** Lo menos que puede faltar mientras la generación no termine: nunca se muestra que faltan 0 segundos. */
    public static final long MIN_REMAINING_MILLIS = 1000;

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta la etapa, si el tiempo transcurrido es negativo o si lo que falta
     *                                  es negativo y no es {@value #UNKNOWN}
     */
    public ReportProgress {
        if (stage == null) {
            throw new IllegalArgumentException("La etapa es obligatoria.");
        }
        if (elapsedMillis < 0) {
            throw new IllegalArgumentException("El tiempo transcurrido no puede ser negativo.");
        }
        if (remainingMillis < UNKNOWN) {
            throw new IllegalArgumentException("El tiempo que falta no puede ser negativo.");
        }
    }

    /**
     * Crea el avance de una generación que acaba de empezar: primera etapa, sin tiempo y sin estimación.
     *
     * @return el avance inicial
     */
    public static ReportProgress start() {
        return new ReportProgress(ReportStage.METRICS, 0, UNKNOWN);
    }

    /**
     * Indica si ya se sabe cuánto falta.
     *
     * @return {@code true} si hay estimación
     */
    public boolean isEstimated() {
        return remainingMillis != UNKNOWN;
    }

    /**
     * Calcula la parte terminada.
     *
     * @return fracción de 0 a 1: el tiempo que lleva sobre el total estimado; sin estimación, el comienzo de la
     *         etapa actual
     */
    public double fraction() {
        long total = elapsedMillis + remainingMillis;
        if (!isEstimated() || total == 0) {
            return stage.ordinal() / (double) ReportStage.count();
        }
        return elapsedMillis / (double) total;
    }

    /**
     * Calcula cómo va el avance un rato después, sin noticias nuevas de la generación: el tiempo sigue corriendo
     * y lo que falta baja, pero nunca de {@value #MIN_REMAINING_MILLIS} ms (salvo que ya fuera menos).
     *
     * <p>Sirve para que la ventana cuente hacia atrás mientras espera la respuesta de la IA, que no avisa nada
     * hasta que termina.</p>
     *
     * @param millis milisegundos que pasaron desde este avance
     * @return el avance en ese momento
     * @throws IllegalArgumentException si los milisegundos son negativos
     */
    public ReportProgress after(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("El tiempo que pasó no puede ser negativo.");
        }
        if (!isEstimated()) {
            return new ReportProgress(stage, elapsedMillis + millis, UNKNOWN);
        }
        long remaining = Math.max(Math.min(remainingMillis, MIN_REMAINING_MILLIS), remainingMillis - millis);
        return new ReportProgress(stage, elapsedMillis + millis, remaining);
    }
}
