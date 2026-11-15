package com.medresource.services.simulation;

import com.medresource.model.Hospital;
import com.medresource.model.MinuteSample;
import com.medresource.model.ResourceGroup;
import com.medresource.model.ResourceUsage;
import com.medresource.model.SimulationTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Mide, a partir de las fotos de cada minuto, cuánto se ocupó cada grupo de recursos del hospital: camas,
 * UCI, quirófanos, médicos, enfermeros y morgue.
 *
 * <p>La ocupación promedio es la suma de los recursos ocupados en cada minuto dividida entre los minutos y la
 * capacidad del grupo. Un grupo que el hospital no tiene (por ejemplo, cero enfermeros) no aparece.</p>
 */
public class ResourceUsageAnalyzer {

    /**
     * Mide la ocupación de cada grupo de recursos.
     *
     * @param trace    traza de la simulación
     * @param hospital hospital simulado, para conocer la capacidad de cada grupo
     * @return ocupación de cada grupo, en el orden de {@link ResourceGroup}
     * @throws IllegalArgumentException si falta la traza o el hospital
     */
    public List<ResourceUsage> analyze(SimulationTrace trace, Hospital hospital) {
        if (trace == null || hospital == null) {
            throw new IllegalArgumentException("La traza y el hospital son obligatorios.");
        }
        List<ResourceUsage> usages = new ArrayList<>();
        add(usages, ResourceGroup.BEDS, hospital.getBeds().size(), trace, MinuteSample::busyBeds);
        add(usages, ResourceGroup.ICU_BEDS, hospital.getIcuBeds().size(), trace, MinuteSample::busyIcuBeds);
        add(usages, ResourceGroup.OPERATING_ROOMS, hospital.getOperatingRooms().size(), trace,
                MinuteSample::busyOperatingRooms);
        add(usages, ResourceGroup.DOCTORS, hospital.getDoctors().size(), trace, MinuteSample::busyDoctors);
        add(usages, ResourceGroup.NURSES, hospital.getNurses().size(), trace, MinuteSample::busyNurses);
        add(usages, ResourceGroup.MORGUE, hospital.getMorgue().getCapacity(), trace, MinuteSample::morgueBodies);
        return usages;
    }

    private static void add(List<ResourceUsage> usages, ResourceGroup group, int capacity, SimulationTrace trace,
                            ToIntFunction<MinuteSample> busy) {
        if (capacity < 1) {
            return;
        }
        List<MinuteSample> samples = trace.samples();
        long total = 0;
        int peak = 0;
        int full = 0;
        for (MinuteSample sample : samples) {
            int count = Math.min(capacity, busy.applyAsInt(sample));
            total += count;
            peak = Math.max(peak, count);
            if (count == capacity) {
                full++;
            }
        }
        double average = samples.isEmpty() ? 0.0 : total / ((double) samples.size() * capacity);
        usages.add(new ResourceUsage(group, capacity, average, peak, full));
    }
}
