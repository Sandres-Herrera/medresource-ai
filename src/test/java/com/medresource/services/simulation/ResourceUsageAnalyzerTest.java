package com.medresource.services.simulation;

import com.medresource.model.BloodBank;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.MinuteSample;
import com.medresource.model.Morgue;
import com.medresource.model.OperatingRoom;
import com.medresource.model.ResourceGroup;
import com.medresource.model.ResourceUsage;
import com.medresource.model.Room;
import com.medresource.model.RoomType;
import com.medresource.model.Sex;
import com.medresource.model.SimulationTrace;
import com.medresource.model.Specialty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la ocupación de los grupos de recursos.
 */
class ResourceUsageAnalyzerTest {

    /** 2 camas, 1 de UCI, 1 quirófano, 2 médicos, ningún enfermero y morgue de 2. */
    private static Hospital hospital() {
        return new Hospital(List.of(new Room("H1", RoomType.HOSPITALIZATION, 2)), new Room("UCI", RoomType.ICU, 1),
                List.of(new OperatingRoom(1)),
                List.of(new Doctor(1, Specialty.GENERAL_MEDICINE, 40, Sex.MALE),
                        new Doctor(2, Specialty.SURGERY, 40, Sex.FEMALE)),
                List.of(), new BloodBank(Map.of()), new Morgue(2));
    }

    private static SimulationTrace trace() {
        List<MinuteSample> samples = List.of(
                new MinuteSample(0, 0, 0, 3, 0, 2, 0, 1, 1, 0, 0, Map.of(), Map.of()),
                new MinuteSample(1, 0, 0, 3, 0, 1, 1, 1, 2, 0, 1, Map.of(), Map.of()));
        return ReportFixtures.trace(Map.of(), List.of(), samples);
    }

    private static ResourceUsage usageOf(List<ResourceUsage> usages, ResourceGroup group) {
        return usages.stream().filter(usage -> usage.group() == group).findFirst().orElseThrow();
    }

    @Test
    void aGroupTheHospitalDoesNotHaveIsLeftOut() {
        List<ResourceUsage> usages = new ResourceUsageAnalyzer().analyze(trace(), hospital());

        assertEquals(List.of(ResourceGroup.BEDS, ResourceGroup.ICU_BEDS, ResourceGroup.OPERATING_ROOMS,
                ResourceGroup.DOCTORS, ResourceGroup.MORGUE), usages.stream().map(ResourceUsage::group).toList());
    }

    @Test
    void measuresTheAverageThePeakAndTheMinutesFull() {
        List<ResourceUsage> usages = new ResourceUsageAnalyzer().analyze(trace(), hospital());

        ResourceUsage beds = usageOf(usages, ResourceGroup.BEDS);
        assertEquals(2, beds.capacity());
        assertEquals(0.75, beds.averageOccupancy(), 1e-12);
        assertEquals(2, beds.peakBusy());
        assertEquals(1, beds.fullMinutes());

        ResourceUsage rooms = usageOf(usages, ResourceGroup.OPERATING_ROOMS);
        assertEquals(1.0, rooms.averageOccupancy(), 1e-12);
        assertEquals(2, rooms.fullMinutes());

        ResourceUsage morgue = usageOf(usages, ResourceGroup.MORGUE);
        assertEquals(0.25, morgue.averageOccupancy(), 1e-12);
        assertEquals(0, morgue.fullMinutes());
    }

    @Test
    void withoutSamplesNothingWasOccupied() {
        List<ResourceUsage> usages = new ResourceUsageAnalyzer().analyze(
                ReportFixtures.trace(Map.of(), List.of(), List.of()), hospital());

        assertEquals(0.0, usageOf(usages, ResourceGroup.DOCTORS).averageOccupancy(), 0.0);
    }

    @Test
    void needsTheTraceAndTheHospital() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceUsageAnalyzer().analyze(null, hospital()));
        assertThrows(IllegalArgumentException.class, () -> new ResourceUsageAnalyzer().analyze(trace(), null));
    }
}
