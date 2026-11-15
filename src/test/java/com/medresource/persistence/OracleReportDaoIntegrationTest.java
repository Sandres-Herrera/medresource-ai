package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.GeneratedReport;
import com.medresource.model.GeneratedReportFixtures;
import com.medresource.model.HospitalConfig;
import com.medresource.model.PatientStatus;
import com.medresource.model.RunRecord;
import com.medresource.model.SavedReport;
import com.medresource.model.SavedReportSummary;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationResult;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.FifoStrategy;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de integración de {@link OracleReportDao} con Oracle: solo se ejecuta en los equipos que tienen
 * {@code src/main/resources/db.properties}; en los demás se omite.
 *
 * <p>Guarda un caso de 20 pacientes con una semilla poco común, una ejecución y su reporte, y comprueba que el
 * reporte se lee igual y que aparece en la lista con sus fallecidos. Borra lo que crea: al borrar el caso se
 * borran sus ejecuciones y sus reportes. Si las tablas todavía no existen (no se ha corrido {@code schema.sql} o
 * {@code add_report_table.sql}), se omite con un aviso.</p>
 */
class OracleReportDaoIntegrationTest {

    private static final CaseConfig CONFIG = new CaseConfig(987_654_324L, Scenario.CATASTROPHE, 20, 240);
    private static final HospitalConfig HOSPITAL = new HospitalConfig(6, 2, 1, 3, 4, 2, 2);
    private static final String TABLE_MISSING = "ORA-00942";

    @Test
    void savesReadsAndListsAReportInOracle() {
        assumeTrue(DatabaseConfig.class.getResource(DatabaseConfig.RESOURCE_NAME) != null,
                "No hay db.properties: se omite la prueba con Oracle.");
        ConnectionManager manager = new ConnectionManager(DatabaseConfig.fromClasspath());
        OracleDiseaseDao diseases = new OracleDiseaseDao(manager);
        OracleCaseDao cases = new OracleCaseDao(manager, diseases);
        OracleResultDao results = new OracleResultDao(manager, diseases);
        OracleReportDao dao = new OracleReportDao(manager);
        try {
            deleteCaseIfPresent(cases);
            dao.summaries();
        } catch (DataAccessException error) {
            assumeTrue(!error.getMessage().contains(TABLE_MISSING),
                    "Las tablas no existen: corra db/schema.sql (o db/add_report_table.sql) y repita la prueba.");
            throw error;
        }

        try {
            loadMissingDiseases(diseases);
            SimulationCase generated = new CaseGenerator(diseases.findAll()).generate(CONFIG);
            int caseId = cases.save(generated);
            SimulationResult result = new SimulationEngine(generated, new HospitalFactory().create(HOSPITAL),
                    new FifoStrategy()).run();
            int resultId = results.save(new RunRecord(caseId, HOSPITAL, result));
            GeneratedReport report = GeneratedReportFixtures.small();

            int id = dao.save(SavedReport.toSave(resultId, report));

            SavedReport saved = dao.findById(id).orElseThrow();
            assertEquals(report, saved.report());
            assertEquals(resultId, saved.resultId());
            assertEquals(Optional.of(saved), dao.findByResult(resultId));
            SavedReportSummary summary = dao.summaries().stream()
                    .filter(candidate -> candidate.id() == id).findFirst().orElseThrow();
            assertEquals(CONFIG, summary.config());
            assertEquals(result.strategyName(), summary.strategyName());
            assertEquals(result.outcomes().stream().filter(outcome -> outcome.status() == PatientStatus.DECEASED)
                    .count(), summary.deaths());

            results.deleteById(resultId);
            assertTrue(dao.findById(id).isEmpty());
        } finally {
            deleteCaseIfPresent(cases);
        }
    }

    private static void deleteCaseIfPresent(OracleCaseDao cases) {
        cases.findIdByConfig(CONFIG).ifPresent(cases::deleteById);
    }

    /** Guarda las enfermedades del catálogo inicial que todavía no estén en la base de datos. */
    private static void loadMissingDiseases(OracleDiseaseDao diseases) {
        for (Disease disease : DiseaseCatalog.defaultDiseases()) {
            if (diseases.findById(disease.getId()).isEmpty()) {
                diseases.save(disease);
            }
        }
    }
}
