package com.medresource.model;

import java.util.List;

/**
 * Resumen del banco de sangre: cuánto se usó de cada tipo, cuándo se agotó y cuántas veces hubo que recurrir
 * al O negativo, el donante universal.
 *
 * @param types                      uso de cada tipo de sangre
 * @param transfusions               atenciones que usaron sangre
 * @param universalDonorUses         transfusiones que usaron O negativo
 * @param universalDonorForOthers    transfusiones que usaron O negativo en pacientes de otro tipo
 * @param patientsWaitingForBlood    pacientes que esperaron al menos un minuto por sangre compatible
 * @param deathsForLackOfBlood       fallecidos porque no había sangre compatible
 */
public record BloodReport(List<BloodUsage> types, int transfusions, int universalDonorUses,
                          int universalDonorForOthers, int patientsWaitingForBlood, int deathsForLackOfBlood) {

    /**
     * Valida los datos y guarda una copia inmutable de la lista.
     *
     * @throws IllegalArgumentException si falta la lista, si hay números negativos o si los usos de O negativo
     *                                  no cuadran con las transfusiones
     */
    public BloodReport {
        if (types == null) {
            throw new IllegalArgumentException("El uso de cada tipo de sangre es obligatorio.");
        }
        if (transfusions < 0 || universalDonorUses < 0 || universalDonorForOthers < 0 || patientsWaitingForBlood < 0
                || deathsForLackOfBlood < 0) {
            throw new IllegalArgumentException("El resumen del banco de sangre no puede ser negativo.");
        }
        if (universalDonorUses > transfusions || universalDonorForOthers > universalDonorUses) {
            throw new IllegalArgumentException("Los usos de O negativo no pueden superar las transfusiones.");
        }
        types = List.copyOf(types);
    }

    /**
     * Suma las unidades usadas de todos los tipos.
     *
     * @return unidades retiradas del banco
     */
    public int unitsUsed() {
        return types.stream().mapToInt(BloodUsage::usedUnits).sum();
    }

    /**
     * Devuelve los tipos que se agotaron en algún momento.
     *
     * @return tipos agotados, en el orden del reporte
     */
    public List<BloodUsage> ranOut() {
        return types.stream().filter(BloodUsage::ranOut).toList();
    }
}
