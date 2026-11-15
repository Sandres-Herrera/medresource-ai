package com.medresource.model;

/**
 * Fallas de la atención que no son muertes: esperas por encima de la meta y pacientes críticos que
 * esperaron sin primeros auxilios.
 *
 * @param overTarget                       pacientes que empezaron su tratamiento después de la meta de su
 *                                         nivel de triage, o que nunca lo empezaron (sin contar remitidos)
 * @param criticalWithoutTimelyAid         pacientes críticos que esperaron sin primeros auxilios el tiempo
 *                                         de tolerancia o más
 * @param longestCriticalWithoutAidMinutes espera más larga de un crítico sin primeros auxilios
 */
public record CareFailures(int overTarget, int criticalWithoutTimelyAid, int longestCriticalWithoutAidMinutes) {

    /**
     * Valida los números.
     *
     * @throws IllegalArgumentException si algún número es negativo
     */
    public CareFailures {
        if (overTarget < 0 || criticalWithoutTimelyAid < 0 || longestCriticalWithoutAidMinutes < 0) {
            throw new IllegalArgumentException("Las fallas de la atención no pueden ser negativas.");
        }
    }
}
