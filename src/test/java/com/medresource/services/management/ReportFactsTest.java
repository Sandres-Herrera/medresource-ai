package com.medresource.services.management;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.medresource.model.CaseConfig;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationReport;
import com.medresource.model.TriageLevelStats;
import com.medresource.model.WhatIfReport;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.simulation.SimulationReportBuilder;
import com.medresource.services.simulation.WhatIfAnalyzer;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.services.strategy.FifoStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los hechos que se le envían a la IA.
 */
class ReportFactsTest {

    /** Caso corto de catástrofe en el hospital pequeño, para que haya esperas y cuellos de botella. */
    static final CaseConfig SHORT_CASE = new CaseConfig(7L, Scenario.CATASTROPHE, 40, 120);

    static SimulationReport report() {
        Hospital hospital = new HospitalFactory().create(HospitalConfig.small());
        SimulationEngine engine = new SimulationEngine(new CaseGenerator().generate(SHORT_CASE), hospital,
                new ClinicalPriorityStrategy());
        return new SimulationReportBuilder().build(engine.run(), engine.getTrace(), hospital);
    }

    static WhatIfReport whatIf() {
        return new WhatIfAnalyzer().analyze(SHORT_CASE, HospitalConfig.small(), ClinicalPriorityStrategy::new,
                List.of(FifoStrategy::new), 2, progress -> { });
    }

    @Test
    void hasEverySectionOfTheReport() {
        JsonObject facts = ReportFacts.of(report(), null);

        for (String key : new String[]{"caso", "estrategia", "resumen", "triage", "fallecidos", "fallas",
                "sin_terminar", "cuellos_de_botella", "colapsos", "recursos", "personal", "sangre",
                "casos_para_revisar"}) {
            assertTrue(facts.has(key), key);
        }
        assertFalse(facts.has("analisis_y_si"));
        assertTrue(ReportFacts.of(report(), whatIf()).has("analisis_y_si"));
    }

    @Test
    void roundsToOneDecimalAndWritesTheMinutesAsClockTimes() {
        SimulationReport report = report();
        JsonObject facts = ReportFacts.of(report, null);

        BigDecimal expected = BigDecimal.valueOf(100 * report.metrics().mortalityRate())
                .setScale(1, RoundingMode.HALF_UP);
        assertEquals(0, expected.compareTo(facts.getAsJsonObject("resumen").get("mortalidad_pct").getAsBigDecimal()));
        JsonObject bottleneck = facts.getAsJsonArray("cuellos_de_botella").get(0).getAsJsonObject();
        assertTrue(bottleneck.get("desde").getAsString().matches("\\d{2}:\\d{2}"));
        assertEquals("08:00", ReportFacts.clock(480));
    }

    @Test
    void leavesOutTheTriageLevelsWithoutPatients() {
        SimulationReport report = report();
        JsonArray triage = ReportFacts.of(report, null).getAsJsonArray("triage");

        assertEquals(report.triage().stream().filter(stats -> stats.patients() > 0).count(), triage.size());
        assertTrue(report.triage().stream().mapToInt(TriageLevelStats::patients).sum() > 0);
    }

    @Test
    void collectsTheNumbersAndTimesThatCanBeWritten() {
        JsonElement facts = JsonParser.parseString(
                "{\"a\":13.0,\"b\":[45.9,-3.3],\"c\":\"de 00:16 a 06:39, paciente P024\",\"d\":\"5 camas más\"}");

        Set<String> allowed = ReportFacts.allowedNumbers(facts);

        assertTrue(allowed.containsAll(Set.of("13", "45.9", "3.3", "00:16", "06:39", "5")));
        assertFalse(allowed.contains("24"));
    }

    @Test
    void writesTheNumbersInACanonicalWay() {
        assertEquals("13", ReportFacts.canonical("13,0"));
        assertEquals("45.9", ReportFacts.canonical("45,90"));
        assertEquals("7", ReportFacts.canonical("7"));
    }

    @Test
    void needsTheReport() {
        assertThrows(IllegalArgumentException.class, () -> ReportFacts.of(null, null));
        assertThrows(IllegalArgumentException.class, () -> ReportFacts.allowedNumbers(null));
    }
}
