package com.medresource.model;

import java.util.Map;

/**
 * Fallecidos de una ejecución según cuándo murieron respecto a su atención.
 *
 * <ul>
 *   <li><b>Esperando:</b> murieron sin empezar su tratamiento.</li>
 *   <li><b>Atendidos tarde:</b> empezaron su tratamiento después de la meta de su nivel de triage.</li>
 *   <li><b>Atendidos a tiempo:</b> empezaron dentro de la meta y aun así murieron.</li>
 * </ul>
 *
 * <p>Las dos primeras son evitables con más recursos o mejores decisiones; la última, no.</p>
 *
 * @param whileWaiting      murieron esperando
 * @param afterLateCare     murieron después de una atención tardía
 * @param despiteTimelyCare murieron aunque se les atendió a tiempo
 * @param byCause           fallecidos por cada causa registrada; solo aparecen las causas que ocurrieron
 */
public record DeathBreakdown(int whileWaiting, int afterLateCare, int despiteTimelyCare,
                             Map<CauseOfDeath, Integer> byCause) {

    /**
     * Valida los números y guarda una copia inmutable de las causas.
     *
     * @throws IllegalArgumentException si hay números negativos, si faltan las causas o si las causas no suman
     *                                  lo mismo que los fallecidos
     */
    public DeathBreakdown {
        if (whileWaiting < 0 || afterLateCare < 0 || despiteTimelyCare < 0 || byCause == null) {
            throw new IllegalArgumentException("Los fallecidos no pueden ser negativos.");
        }
        if (byCause.values().stream().mapToInt(Integer::intValue).sum()
                != whileWaiting + afterLateCare + despiteTimelyCare) {
            throw new IllegalArgumentException("Las causas deben sumar lo mismo que los fallecidos.");
        }
        byCause = Map.copyOf(byCause);
    }

    /**
     * Suma todos los fallecidos.
     *
     * @return fallecidos
     */
    public int total() {
        return whileWaiting + afterLateCare + despiteTimelyCare;
    }

    /**
     * Cuenta los fallecidos que se pudieron evitar: los que murieron esperando o atendidos tarde.
     *
     * @return muertes evitables
     */
    public int avoidable() {
        return whileWaiting + afterLateCare;
    }

    /**
     * Cuenta los fallecidos que murieron aunque se les atendió a tiempo.
     *
     * @return muertes inevitables
     */
    public int unavoidable() {
        return despiteTimelyCare;
    }
}
