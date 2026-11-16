package com.medresource.persistence;

import com.google.gson.JsonParser;
import com.medresource.model.ComparisonFixtures;
import com.medresource.model.RunComparison;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la conversión de una comparación a JSON y de vuelta.
 */
class ComparisonJsonTest {

    private final RunComparison comparison = ComparisonFixtures.strategies(1, 2);

    @Test
    void aComparisonComesBackExactlyTheSame() {
        assertEquals(comparison, ComparisonJson.fromJson(ComparisonJson.toJson(comparison)));
    }

    @Test
    void theTextIsStandardJsonForOracle() {
        String json = ComparisonJson.toJson(comparison);

        assertTrue(JsonParser.parseString(json).isJsonObject());
        assertFalse(json.contains("NaN"));
        assertTrue(json.contains("\"type\":\"STRATEGY\""));
    }

    @Test
    void anEmptyOrDamagedTextCannotBeOpened() {
        assertThrows(IllegalArgumentException.class, () -> ComparisonJson.toJson(null));
        assertThrows(DataAccessException.class, () -> ComparisonJson.fromJson(null));
        assertThrows(DataAccessException.class, () -> ComparisonJson.fromJson(" "));
        assertThrows(DataAccessException.class, () -> ComparisonJson.fromJson("null"));
        assertThrows(DataAccessException.class, () -> ComparisonJson.fromJson("{\"caseId\":"));
        assertThrows(DataAccessException.class, () -> ComparisonJson.fromJson("{\"caseId\":1}"));
    }
}
