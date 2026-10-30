package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationCase;
import com.medresource.services.simulation.CaseGenerator;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.medresource.model.CaseFixtures.assertSameCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de integración de {@link OracleCaseDao} con Oracle: solo se ejecuta en los equipos que tienen
 * {@code src/main/resources/db.properties}; en los demás se omite.
 *
 * <p>Usa una semilla poco común para no tocar los casos reales y borra lo que crea. Necesita las
 * enfermedades del catálogo inicial: si no están guardadas, las carga. Si las tablas todavía no existen
 * (no se ha corrido {@code schema.sql}), se omite con un aviso.</p>
 */
class OracleCaseDaoIntegrationTest {

    private static final CaseConfig CONFIG = new CaseConfig(987_654_321L, Scenario.CATASTROPHE, 30, 240);
    private static final String TABLE_MISSING = "ORA-00942";

    @Test
    void savesReadsAndDeletesACaseInOracle() {
        assumeTrue(DatabaseConfig.class.getResource(DatabaseConfig.RESOURCE_NAME) != null,
                "No hay db.properties: se omite la prueba con Oracle.");
        ConnectionManager manager = new ConnectionManager(DatabaseConfig.fromClasspath());
        OracleDiseaseDao diseases = new OracleDiseaseDao(manager);
        OracleCaseDao dao = new OracleCaseDao(manager, diseases);
        try {
            deleteIfPresent(dao);
        } catch (DataAccessException error) {
            assumeTrue(!error.getMessage().contains(TABLE_MISSING),
                    "Las tablas no existen: corra db/schema.sql y repita la prueba.");
            throw error;
        }

        try {
            loadMissingDiseases(diseases);
            SimulationCase generated = new CaseGenerator(diseases.findAll()).generate(CONFIG);

            int id = dao.save(generated);

            assertEquals(Optional.of(id), dao.findIdByConfig(CONFIG));
            assertSameCase(generated, dao.findById(id).orElseThrow());
            assertTrue(dao.findAll().stream().anyMatch(stored -> stored.config().equals(CONFIG)));

            dao.deleteById(id);
            assertEquals(Optional.empty(), dao.findIdByConfig(CONFIG));
        } finally {
            deleteIfPresent(dao);
        }
    }

    private static void deleteIfPresent(OracleCaseDao dao) {
        dao.findIdByConfig(CONFIG).ifPresent(dao::deleteById);
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