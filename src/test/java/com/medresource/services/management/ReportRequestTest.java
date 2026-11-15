package com.medresource.services.management;

import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.SimulationResult;
import com.medresource.model.SimulationTrace;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de los datos para generar el reporte.
 */
class ReportRequestTest {

    /**
     * Simula el caso corto de catástrofe en el hospital pequeño y arma lo que hace falta para su reporte.
     *
     * @return datos de esa simulación
     */
    static ReportRequest request() {
        Hospital hospital = new HospitalFactory().create(HospitalConfig.small());
        SimulationEngine engine = new SimulationEngine(new CaseGenerator().generate(ReportFactsTest.SHORT_CASE),
                hospital, new ClinicalPriorityStrategy());
        SimulationResult result = engine.run();
        return new ReportRequest(result, engine.getTrace(), hospital, HospitalConfig.small(),
                ClinicalPriorityStrategy::new);
    }

    @Test
    void theCaseIsTheOneOfTheResult() {
        assertEquals(ReportFactsTest.SHORT_CASE, request().caseConfig());
    }

    @Test
    void nothingCanBeMissing() {
        ReportRequest request = request();
        SimulationResult result = request.result();
        SimulationTrace trace = request.trace();
        Hospital hospital = request.hospital();

        assertThrows(IllegalArgumentException.class, () -> new ReportRequest(null, trace, hospital,
                HospitalConfig.small(), ClinicalPriorityStrategy::new));
        assertThrows(IllegalArgumentException.class, () -> new ReportRequest(result, null, hospital,
                HospitalConfig.small(), ClinicalPriorityStrategy::new));
        assertThrows(IllegalArgumentException.class, () -> new ReportRequest(result, trace, null,
                HospitalConfig.small(), ClinicalPriorityStrategy::new));
        assertThrows(IllegalArgumentException.class, () -> new ReportRequest(result, trace, hospital, null,
                ClinicalPriorityStrategy::new));
        assertThrows(IllegalArgumentException.class, () -> new ReportRequest(result, trace, hospital,
                HospitalConfig.small(), null));
    }
}
