package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.GeneratedReport;
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

/**
 * Reporte de ejemplo para las pruebas de la ventana del reporte: un caso corto de catástrofe en el hospital
 * pequeño, con el texto de las plantillas y dos casos por alternativa para que sea rápido.
 */
final class ReportViewFixtures {

    /** Caso corto de catástrofe: hay esperas, muertes y cuellos de botella. */
    static final CaseConfig SHORT_CASE = new CaseConfig(7L, Scenario.CATASTROPHE, 40, 120);

    private static GeneratedReport generated;

    private ReportViewFixtures() {
    }

    /**
     * Simula el caso corto y arma lo que hace falta para su reporte.
     *
     * @return datos de esa simulación
     */
    static ReportRequest request() {
        Hospital hospital = new HospitalFactory().create(HospitalConfig.small());
        SimulationEngine engine = new SimulationEngine(new CaseGenerator().generate(SHORT_CASE), hospital,
                new ClinicalPriorityStrategy());
        return new ReportRequest(engine.run(), engine.getTrace(), hospital, HospitalConfig.small(),
                ClinicalPriorityStrategy::new);
    }

    /**
     * Generador sin IA y con dos casos por alternativa.
     *
     * @return generador rápido
     */
    static ReportGenerator generator() {
        return new ReportGenerator(new WhatIfAnalyzer(), new ReportNarrator(null, null), 2);
    }

    /**
     * Reporte terminado del caso corto; se genera una sola vez y se reutiliza.
     *
     * @return el reporte
     */
    static synchronized GeneratedReport generated() {
        if (generated == null) {
            generated = generator().generate(request(), progress -> { });
        }
        return generated;
    }
}
