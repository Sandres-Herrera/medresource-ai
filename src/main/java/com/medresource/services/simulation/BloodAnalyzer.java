package com.medresource.services.simulation;

import com.medresource.model.BloodReport;
import com.medresource.model.BloodType;
import com.medresource.model.BloodUsage;
import com.medresource.model.CareRecord;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.MinuteSample;
import com.medresource.model.MissingResource;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;

import java.util.ArrayList;
import java.util.List;

/**
 * Resume el uso del banco de sangre: unidades usadas de cada tipo, cuándo se agotó cada uno, cuántas veces se
 * usó O negativo y cuántos pacientes esperaron o murieron por falta de sangre compatible.
 *
 * <p>Un tipo está agotado en un minuto si al final de ese minuto no le quedaba ninguna unidad. Un fallecido
 * murió por falta de sangre si su causa es {@link CauseOfDeath#NO_COMPATIBLE_RESOURCE}: el motor solo la usa
 * cuando no había sangre compatible.</p>
 */
public class BloodAnalyzer {

    /**
     * Resume el uso del banco de sangre.
     *
     * @param result resultado de la simulación
     * @param trace  traza de la misma simulación
     * @return resumen del banco de sangre, con los ocho tipos en el orden de {@link BloodType}
     * @throws IllegalArgumentException si falta el resultado o la traza
     */
    public BloodReport analyze(SimulationResult result, SimulationTrace trace) {
        if (result == null || trace == null) {
            throw new IllegalArgumentException("El resultado y la traza son obligatorios.");
        }
        List<BloodUsage> types = new ArrayList<>();
        for (BloodType type : BloodType.values()) {
            types.add(usageOf(type, trace));
        }
        List<CareRecord> transfusions = trace.cares().stream().filter(care -> care.bloodUnits() > 0).toList();
        int universal = (int) transfusions.stream().filter(CareRecord::usedUniversalDonor).count();
        int universalForOthers = (int) transfusions.stream()
                .filter(CareRecord::usedUniversalDonor)
                .filter(care -> care.recipientType() != BloodType.O_NEGATIVE)
                .count();
        int waitingForBlood = (int) trace.blockedMinutes().values().stream()
                .filter(byResource -> byResource.containsKey(MissingResource.BLOOD))
                .count();
        int deaths = (int) result.outcomes().stream()
                .filter(outcome -> outcome.causeOfDeath() == CauseOfDeath.NO_COMPATIBLE_RESOURCE)
                .count();
        return new BloodReport(types, transfusions.size(), universal, universalForOthers, waitingForBlood, deaths);
    }

    private static BloodUsage usageOf(BloodType type, SimulationTrace trace) {
        int initial = trace.initialBlood().getOrDefault(type, 0);
        int used = trace.cares().stream().mapToInt(care -> care.bloodTaken().getOrDefault(type, 0)).sum();
        int firstEmpty = -1;
        int emptyMinutes = 0;
        for (MinuteSample sample : trace.samples()) {
            if (sample.bloodOf(type) == 0) {
                emptyMinutes++;
                if (firstEmpty < 0) {
                    firstEmpty = sample.minute();
                }
            }
        }
        return new BloodUsage(type, initial, used, initial - used, firstEmpty, emptyMinutes);
    }
}
