package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.GeneratedReport;
import com.medresource.model.GeneratedReportFixtures;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Scenario;
import com.medresource.services.management.ReportGenerator;
import com.medresource.services.management.ReportNarrator;
import com.medresource.services.management.ReportRequest;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.simulation.WhatIfAnalyzer;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la conversión del reporte a JSON.
 */
class ReportJsonTest {

    private static final CaseConfig SHORT_CASE = new CaseConfig(7L, Scenario.CATASTROPHE, 40, 120);

    @Test
    void aSimulatedReportComesBackExactlyTheSame() {
        GeneratedReport report = simulated();

        assertEquals(report, ReportJson.fromJson(ReportJson.toJson(report)));
    }

    @Test
    void aHandmadeReportComesBackExactlyTheSame() {
        GeneratedReport report = GeneratedReportFixtures.small();

        assertEquals(report, ReportJson.fromJson(ReportJson.toJson(report)));
    }

    @Test
    void anEmptyOrDamagedTextCannotBeOpened() {
        assertThrows(IllegalArgumentException.class, () -> ReportJson.toJson(null));
        assertThrows(DataAccessException.class, () -> ReportJson.fromJson(null));
        assertThrows(DataAccessException.class, () -> ReportJson.fromJson(" "));
        assertThrows(DataAccessException.class, () -> ReportJson.fromJson("null"));
        assertThrows(DataAccessException.class, () -> ReportJson.fromJson("{\"report\":"));
        assertThrows(DataAccessException.class, () -> ReportJson.fromJson("{}"));
    }

    /**
     * Simula el caso corto de catástrofe y genera su reporte con las plantillas.
     *
     * @return reporte de una simulación real
     */
    static GeneratedReport simulated() {
        Hospital hospital = new HospitalFactory().create(HospitalConfig.small());
        SimulationEngine engine = new SimulationEngine(new CaseGenerator().generate(SHORT_CASE), hospital,
                new ClinicalPriorityStrategy());
        ReportRequest request = new ReportRequest(engine.run(), engine.getTrace(), hospital, HospitalConfig.small(),
                ClinicalPriorityStrategy::new);
        return new ReportGenerator(new WhatIfAnalyzer(), new ReportNarrator(null, null), 2)
                .generate(request, progress -> { });
    }
}
