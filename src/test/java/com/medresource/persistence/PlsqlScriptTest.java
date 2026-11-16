package com.medresource.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del script PL/SQL: que tenga todas sus unidades, que cada una termine bien y que use las mismas reglas
 * que la aplicación. Que compile en Oracle lo comprueba la prueba de integración con la base de datos.
 */
class PlsqlScriptTest {

    private static final Pattern HEADER =
            Pattern.compile("^CREATE OR REPLACE (FUNCTION|PROCEDURE|TRIGGER) ([a-z_]+)");

    static String script() throws IOException {
        try (InputStream stream = PlsqlScriptTest.class.getResourceAsStream(SchemaInstaller.PLSQL_RESOURCE)) {
            assertNotNull(stream, "no se encontró " + SchemaInstaller.PLSQL_RESOURCE);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static List<String> units() throws IOException {
        return SchemaInstaller.programUnitsFrom(script());
    }

    /** Nombres de las unidades del script, en orden. */
    static List<String> names() throws IOException {
        return units().stream().map(PlsqlScriptTest::nameOf).toList();
    }

    static String unit(String name) throws IOException {
        return units().stream().filter(unit -> nameOf(unit).equals(name)).findFirst().orElseThrow();
    }

    private static String nameOf(String unit) {
        Matcher matcher = HEADER.matcher(unit);
        assertTrue(matcher.find(), "unidad sin encabezado: " + unit.lines().findFirst().orElse(""));
        return matcher.group(2);
    }

    @Test
    void everyUnitIsCreatedOrReplacedAndEndsWithItsName() throws IOException {
        for (String unit : units()) {
            String name = nameOf(unit);
            assertTrue(unit.endsWith("END " + name + ";") || unit.endsWith("END;"), name);
        }
    }

    @Test
    void theScriptRunsInSqlPlusWithoutSurprises() throws IOException {
        assertFalse(script().contains("&"), "SQL*Plus trata «&» como variable");
        assertFalse(script().contains("COMMIT"), "las transacciones las maneja quien llama");
    }

    // ---------- funciones ----------

    @Test
    void hasTheFunctionsOfTheIndicators() throws IOException {
        assertTrue(names().containsAll(List.of("pr_exigir_ejecucion", "fn_espera_promedio", "fn_espera_maxima",
                "fn_porcentaje_atendidos", "fn_tasa_mortalidad", "fn_pacientes_sin_atencion", "fn_medicos_extra")));
        assertEquals("pr_exigir_ejecucion", names().get(0));
    }

    @Test
    void theIndicatorsFailWithTheSameErrorForAMissingRun() throws IOException {
        assertTrue(unit("pr_exigir_ejecucion").contains("RAISE_APPLICATION_ERROR(-20002"));
        for (String function : List.of("fn_espera_promedio", "fn_espera_maxima", "fn_porcentaje_atendidos",
                "fn_tasa_mortalidad", "fn_pacientes_sin_atencion")) {
            assertTrue(unit(function).contains("pr_exigir_ejecucion(p_id_ejecucion);"), function);
        }
    }

    @Test
    void theWaitIsMeasuredLikeInTheApplication() throws IOException {
        String rule = "CASE WHEN minuto_inicio_atencion >= 0 THEN minuto_inicio_atencion - minuto_llegada\n"
                + "                    ELSE minuto_desenlace - minuto_llegada END";

        assertTrue(unit("fn_espera_promedio").contains("AVG(" + rule + ")"));
        assertTrue(unit("fn_espera_maxima").contains("MAX(" + rule + ")"));
    }

    @Test
    void theAttendedTheDeathsAndTheUnattendedAreCountedLikeInTheApplication() throws IOException {
        assertTrue(unit("fn_porcentaje_atendidos").contains("WHEN minuto_inicio_atencion >= 0 THEN 1"));
        assertTrue(unit("fn_tasa_mortalidad").contains("WHEN estado = 'DECEASED' THEN 1"));
        assertTrue(unit("fn_pacientes_sin_atencion").contains("estado = 'UNATTENDED'"));
        assertTrue(unit("fn_porcentaje_atendidos").contains("IF v_total = 0 THEN"));
        assertTrue(unit("fn_tasa_mortalidad").contains("IF v_total = 0 THEN"));
    }
}
