package com.medresource.services.simulation;

import com.medresource.model.BottleneckStats;
import com.medresource.model.CollapsePeriod;
import com.medresource.model.MinuteSample;
import com.medresource.model.MissingResource;
import com.medresource.model.SimulationTrace;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Encuentra, a partir de la traza, los recursos que hicieron esperar a los pacientes y los periodos en que el
 * servicio colapsó.
 *
 * <p>La espera por un recurso se mide en minutos-paciente: un minuto con cuatro pacientes esperando un
 * quirófano suma cuatro. El servicio está colapsado mientras haya al menos un paciente sin tratamiento que ya
 * pasó la meta de su nivel de triage; dos colapsos separados por menos de {@value #COLLAPSE_GAP_MINUTES}
 * minutos se toman como uno solo (supuesto del reporte), para no partir un colapso por un alivio
 * momentáneo.</p>
 */
public class BottleneckAnalyzer {

    /** Pausa más corta que, entre dos colapsos, no cuenta como recuperación (supuesto). */
    public static final int COLLAPSE_GAP_MINUTES = 10;

    /**
     * Calcula los cuellos de botella: cada recurso que le faltó a alguien, del que más espera causó al que
     * menos (a igual espera, en el orden de {@link MissingResource}).
     *
     * @param trace traza de la simulación
     * @return cuellos de botella; vacío si nadie esperó por un recurso
     * @throws IllegalArgumentException si falta la traza
     */
    public List<BottleneckStats> bottlenecks(SimulationTrace trace) {
        requireTrace(trace);
        List<BottleneckStats> found = new ArrayList<>();
        long allMinutes = trace.samples().stream()
                .flatMap(sample -> sample.blocked().values().stream())
                .mapToLong(Integer::longValue)
                .sum();
        for (MissingResource resource : MissingResource.values()) {
            statsOf(resource, trace, allMinutes).ifPresent(found::add);
        }
        found.sort(Comparator.comparingInt(BottleneckStats::waitingMinutes).reversed());
        return found;
    }

    /**
     * Calcula los periodos de colapso, en orden.
     *
     * @param trace traza de la simulación
     * @return periodos en que hubo pacientes fuera de su meta; vacío si nunca pasó
     * @throws IllegalArgumentException si falta la traza
     */
    public List<CollapsePeriod> collapses(SimulationTrace trace) {
        requireTrace(trace);
        List<CollapsePeriod> periods = new ArrayList<>();
        int start = -1;
        int end = -1;
        int peak = 0;
        int peakMinute = -1;
        for (MinuteSample sample : trace.samples()) {
            if (sample.overTarget() == 0) {
                continue;
            }
            int minute = sample.minute();
            if (start >= 0 && minute - end - 1 >= COLLAPSE_GAP_MINUTES) {
                periods.add(new CollapsePeriod(start, end, peak, peakMinute));
                start = -1;
            }
            if (start < 0) {
                start = minute;
                peak = 0;
            }
            end = minute;
            if (sample.overTarget() > peak) {
                peak = sample.overTarget();
                peakMinute = minute;
            }
        }
        if (start >= 0) {
            periods.add(new CollapsePeriod(start, end, peak, peakMinute));
        }
        return periods;
    }

    private static Optional<BottleneckStats> statsOf(MissingResource resource, SimulationTrace trace,
                                                                long allMinutes) {
        int minutes = 0;
        int first = -1;
        int last = -1;
        int peak = 0;
        int peakMinute = -1;
        for (MinuteSample sample : trace.samples()) {
            int blocked = sample.blockedBy(resource);
            if (blocked == 0) {
                continue;
            }
            minutes += blocked;
            if (first < 0) {
                first = sample.minute();
            }
            last = sample.minute();
            if (blocked > peak) {
                peak = blocked;
                peakMinute = sample.minute();
            }
        }
        if (minutes == 0) {
            return Optional.empty();
        }
        int affected = (int) trace.blockedMinutes().values().stream()
                .filter(byResource -> byResource.containsKey(resource))
                .count();
        return Optional.of(new BottleneckStats(resource, minutes, minutes / (double) allMinutes, affected,
                first, last, peak, peakMinute));
    }

    private static void requireTrace(SimulationTrace trace) {
        if (trace == null) {
            throw new IllegalArgumentException("La traza de la simulación es obligatoria.");
        }
    }
}
