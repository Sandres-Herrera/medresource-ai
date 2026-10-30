package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.HospitalConfig;
import com.medresource.model.RunRecord;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationResult;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.FifoStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.medresource.model.ResultFixtures.assertSameRun;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de integración de {@link OracleResultDao} con Oracle: solo se ejecuta en los equipos que tienen
 * {@code src/main/resources/db.properties}; en los demás se omite.
 *
 * <p>Simula un caso de 20 pacientes con una semilla poco común, guarda la ejecución con un hospital
 * pequeño y comprueba que se lee igual. Borra lo que crea (al borrar el caso también se borran sus
 * ejecuciones). Necesita las enfermedades del catálogo inicial: si no están guardadas, las carga. Si las
 * tablas todavía no existen (no se ha corrido {@code schema.sql}), se omite con un aviso.</p>
 */
class OracleResultDaoIntegrationTest {

    private static final CaseConfig CONFIG = new CaseConfig(987_654_323L, Scenario.CATASTROPHE, 20, 240);
    private static final HospitalConfig HOSPITAL = new HospitalConfig(6, 2, 1, 3, 4, 2, 2);
    private static final String TABLE_MISSING = "ORA-00942";

    @Test
    void savesReadsAndDeletesARunInOracle() {
        assumeTrue(DatabaseConfig.class.getResource(DatabaseConfig.RESOURCE_NAME) != null,
                "No hay db.properties: se omite la prueba con Oracle.");
        ConnectionManager manager = new ConnectionManager(DatabaseConfig.fromClasspath());
        OracleDiseaseDao diseases = new OracleDiseaseDao(manager);
        OracleCaseDao cases = new OracleCaseDao(manager, diseases);
        OracleResultDao dao = new OracleResultDao(manager, diseases);
        try {
            deleteCaseIfPresent(cases);
        } catch (DataAccessException error) {
            assumeTrue(!error.getMessage().contains(TABLE_MISSING),
                    "Las tablas no existen: corra db/schema.sql y repita la prueba.");
            throw error;
        }

        try {
            loadMissingDiseases(diseases);
            SimulationCase generated = new CaseGenerator(diseases.findAll()).generate(CONFIG);
            int caseId = cases.save(generated);
            SimulationResult result = new SimulationEngine(generated, new HospitalFactory().create(HOSPITAL),
                    new FifoStrategy()).run();
            RunRecord run = new RunRecord(caseId, HOSPITAL, result);

            int id = dao.save(run);

            assertEquals(List.of(id), dao.findIdsByCase(caseId));
            assertSameRun(run, dao.findById(id).orElseThrow());
            assertTrue(dao.findAll().stream().anyMatch(stored -> stored.caseId() == caseId));

            dao.deleteById(id);
            assertEquals(Optional.empty(), dao.findById(id));
            assertTrue(dao.findIdsByCase(caseId).isEmpty());
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