package com.medresource.services.simulation;

import com.medresource.model.BloodReport;
import com.medresource.model.BloodType;
import com.medresource.model.BloodUsage;
import com.medresource.model.CareRecord;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.MinuteSample;
import com.medresource.model.MissingResource;
import com.medresource.model.PatientStatus;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.medresource.services.simulation.ReportFixtures.outcome;
import static com.medresource.services.simulation.ReportFixtures.result;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del resumen del banco de sangre.
 */
class BloodAnalyzerTest {

    private static CareRecord transfusion(String patient, BloodType recipient, Map<BloodType, Integer> taken) {
        return new CareRecord(patient, recipient, 0, 60, 200, "D01", Specialty.SURGERY, List.of(), "Q1", "H1-C1",
                false, taken, 50.0);
    }

    private static MinuteSample sample(int minute, int oNegative, int aPositive) {
        return new MinuteSample(minute, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(),
                Map.of(BloodType.O_NEGATIVE, oNegative, BloodType.A_POSITIVE, aPositive));
    }

    /**
     * Empiezan 2 unidades de O negativo y 4 de A positivo. P001 (A+) usa 2 de A+; P002 (A+) usa 1 de A+ y 1 de
     * O negativo; P003 (O-) usa la última de O negativo... que ya no alcanza, así que espera y muere.
     */
    private static SimulationTrace trace() {
        List<CareRecord> cares = List.of(
                transfusion("P001", BloodType.A_POSITIVE, Map.of(BloodType.A_POSITIVE, 2)),
                transfusion("P002", BloodType.A_POSITIVE, Map.of(BloodType.A_POSITIVE, 1, BloodType.O_NEGATIVE, 1)),
                transfusion("P004", BloodType.O_NEGATIVE, Map.of(BloodType.O_NEGATIVE, 1)),
                ReportFixtures.care(5, 0, 80.0));
        List<MinuteSample> samples = List.of(sample(0, 2, 2), sample(1, 1, 1), sample(2, 0, 1), sample(3, 0, 1));
        return new SimulationTrace(Map.of(), cares, samples, Map.of("P003", Map.of(MissingResource.BLOOD, 3)),
                Map.of(), Map.of(), Map.of(BloodType.O_NEGATIVE, 2, BloodType.A_POSITIVE, 4));
    }

    private static SimulationResult result5() {
        return result(60, 4,
                outcome(1, TriageLevel.II, 0, 0, 4, PatientStatus.DISCHARGED, null, 90.0),
                outcome(2, TriageLevel.II, 0, 1, 4, PatientStatus.DISCHARGED, null, 90.0),
                outcome(3, TriageLevel.II, 0, -1, 3, PatientStatus.DECEASED, CauseOfDeath.NO_COMPATIBLE_RESOURCE,
                        0.0),
                outcome(4, TriageLevel.II, 0, 2, 4, PatientStatus.DISCHARGED, null, 90.0),
                outcome(5, TriageLevel.IV, 0, 0, 4, PatientStatus.DISCHARGED, null, 90.0));
    }

    private static BloodUsage usageOf(BloodReport report, BloodType type) {
        return report.types().stream().filter(usage -> usage.type() == type).findFirst().orElseThrow();
    }

    @Test
    void listsTheEightTypesInOrder() {
        BloodReport report = new BloodAnalyzer().analyze(result5(), trace());

        assertEquals(List.of(BloodType.values()), report.types().stream().map(BloodUsage::type).toList());
    }

    @Test
    void addsUpTheUnitsUsedOfEachType() {
        BloodReport report = new BloodAnalyzer().analyze(result5(), trace());

        BloodUsage aPositive = usageOf(report, BloodType.A_POSITIVE);
        assertEquals(4, aPositive.initialUnits());
        assertEquals(3, aPositive.usedUnits());
        assertEquals(1, aPositive.remainingUnits());
        assertFalse(aPositive.ranOut());
        assertEquals(5, report.unitsUsed());
    }

    @Test
    void findsWhenATypeRanOut() {
        BloodUsage oNegative = usageOf(new BloodAnalyzer().analyze(result5(), trace()), BloodType.O_NEGATIVE);

        assertTrue(oNegative.ranOut());
        assertEquals(2, oNegative.firstEmptyMinute());
        assertEquals(2, oNegative.emptyMinutes());
    }

    @Test
    void countsTheTransfusionsAndTheUsesOfTheUniversalDonor() {
        BloodReport report = new BloodAnalyzer().analyze(result5(), trace());

        assertEquals(3, report.transfusions());
        assertEquals(2, report.universalDonorUses());
        assertEquals(1, report.universalDonorForOthers());
    }

    @Test
    void countsWhoWaitedAndWhoDiedForLackOfBlood() {
        BloodReport report = new BloodAnalyzer().analyze(result5(), trace());

        assertEquals(1, report.patientsWaitingForBlood());
        assertEquals(1, report.deathsForLackOfBlood());
    }

    @Test
    void needsTheResultAndTheTrace() {
        assertThrows(IllegalArgumentException.class, () -> new BloodAnalyzer().analyze(null, trace()));
        assertThrows(IllegalArgumentException.class, () -> new BloodAnalyzer().analyze(result5(), null));
    }
}
