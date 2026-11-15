package com.medresource.services.simulation;

import com.medresource.model.BottleneckStats;
import com.medresource.model.CollapsePeriod;
import com.medresource.model.MinuteSample;
import com.medresource.model.MissingResource;
import com.medresource.model.SimulationTrace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.medresource.services.simulation.ReportFixtures.blockedSample;
import static com.medresource.services.simulation.ReportFixtures.emptySample;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los cuellos de botella y los periodos de colapso.
 */
class BottleneckAnalyzerTest {

    /**
     * Minutos 0 a 2 con pacientes esperando médico y quirófano; un alivio largo del 3 al 14; y fuera de la meta
     * otra vez en los minutos 15 y 17, separados por un alivio corto.
     */
    private static SimulationTrace trace() {
        List<MinuteSample> samples = new ArrayList<>();
        samples.add(blockedSample(0, 2, 0, Map.of(MissingResource.DOCTOR, 2)));
        samples.add(blockedSample(1, 3, 1, Map.of(MissingResource.DOCTOR, 3, MissingResource.OPERATING_ROOM, 1)));
        samples.add(blockedSample(2, 2, 2, Map.of(MissingResource.OPERATING_ROOM, 1)));
        for (int minute = 3; minute <= 14; minute++) {
            samples.add(emptySample(minute));
        }
        samples.add(blockedSample(15, 1, 1, Map.of()));
        samples.add(emptySample(16));
        samples.add(blockedSample(17, 1, 1, Map.of()));
        Map<String, Map<MissingResource, Integer>> blocked = Map.of(
                "P001", Map.of(MissingResource.DOCTOR, 2),
                "P002", Map.of(MissingResource.DOCTOR, 2, MissingResource.OPERATING_ROOM, 2),
                "P003", Map.of(MissingResource.DOCTOR, 1));
        return new SimulationTrace(Map.of(), List.of(), samples, blocked, Map.of(), Map.of(), Map.of());
    }

    @Test
    void theResourceThatCausedMoreWaitingComesFirst() {
        List<BottleneckStats> bottlenecks = new BottleneckAnalyzer().bottlenecks(trace());

        assertEquals(List.of(MissingResource.DOCTOR, MissingResource.OPERATING_ROOM),
                bottlenecks.stream().map(BottleneckStats::resource).toList());
    }

    @Test
    void measuresTheWaitingTheShareAndWhenTheResourceWasMissing() {
        BottleneckStats doctor = new BottleneckAnalyzer().bottlenecks(trace()).get(0);

        assertEquals(5, doctor.waitingMinutes());
        assertEquals(5 / 7.0, doctor.share(), 1e-12);
        assertEquals(3, doctor.patientsAffected());
        assertEquals(0, doctor.firstMinute());
        assertEquals(1, doctor.lastMinute());
        assertEquals(3, doctor.peakPatients());
        assertEquals(1, doctor.peakMinute());
    }

    @Test
    void withoutWaitsThereAreNoBottlenecks() {
        SimulationTrace trace = ReportFixtures.trace(Map.of(), List.of(), ReportFixtures.emptySamples(5));

        assertTrue(new BottleneckAnalyzer().bottlenecks(trace).isEmpty());
        assertTrue(new BottleneckAnalyzer().collapses(trace).isEmpty());
    }

    @Test
    void aLongReliefSplitsTheCollapseAndAShortOneDoesNot() {
        List<CollapsePeriod> collapses = new BottleneckAnalyzer().collapses(trace());

        assertEquals(List.of(new CollapsePeriod(1, 2, 2, 2), new CollapsePeriod(15, 17, 1, 15)), collapses);
    }

    @Test
    void needsTheTrace() {
        assertThrows(IllegalArgumentException.class, () -> new BottleneckAnalyzer().bottlenecks(null));
        assertThrows(IllegalArgumentException.class, () -> new BottleneckAnalyzer().collapses(null));
    }
}
