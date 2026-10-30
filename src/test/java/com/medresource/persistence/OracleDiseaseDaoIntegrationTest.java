package com.medresource.persistence;

import com.medresource.model.Disease;
import org.junit.jupiter.api.Test;

import static com.medresource.model.DiseaseFixtures.assertSameDisease;
import static com.medresource.model.DiseaseFixtures.full;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Prueba de integración de {@link OracleDiseaseDao} con Oracle: solo se ejecuta en los equipos que tienen
 * {@code src/main/resources/db.properties}; en los demás se omite.
 *
 * <p>Usa un identificador muy alto para no tocar el catálogo real y borra lo que crea. Si las tablas todavía
 * no existen (no se ha corrido {@code schema.sql}), se omite con un aviso.</p>
 */
class OracleDiseaseDaoIntegrationTest {

    private static final int TEST_ID = 99990;
    private static final String TABLE_MISSING = "ORA-00942";

    @Test
    void savesReadsUpdatesAndDeletesADiseaseInOracle() {
        assumeTrue(DatabaseConfig.class.getResource(DatabaseConfig.RESOURCE_NAME) != null,
                "No hay db.properties: se omite la prueba con Oracle.");
        OracleDiseaseDao dao = new OracleDiseaseDao(new ConnectionManager(DatabaseConfig.fromClasspath()));
        try {
            deleteIfPresent(dao);
        } catch (DataAccessException error) {
            assumeTrue(!error.getMessage().contains(TABLE_MISSING),
                    "Las tablas no existen: corra db/schema.sql y repita la prueba.");
            throw error;
        }

        try {
            assertEquals(TEST_ID, dao.save(full(TEST_ID, "Enfermedad de prueba")).intValue());
            assertSameDisease(full(TEST_ID, "Enfermedad de prueba"), dao.findById(TEST_ID).orElseThrow());

            Disease changed = full(TEST_ID, "Enfermedad de prueba modificada");
            dao.update(changed);
            assertSameDisease(changed, dao.findById(TEST_ID).orElseThrow());
            assertTrue(dao.findAll().stream().anyMatch(disease -> disease.getId() == TEST_ID));

            dao.deleteById(TEST_ID);
            assertTrue(dao.findById(TEST_ID).isEmpty());
        } finally {
            deleteIfPresent(dao);
        }
    }

    private static void deleteIfPresent(OracleDiseaseDao dao) {
        if (dao.findById(TEST_ID).isPresent()) {
            dao.deleteById(TEST_ID);
        }
    }
}