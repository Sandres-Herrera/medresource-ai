package com.medresource.persistence;

import com.medresource.model.ComparisonType;
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

    // ---------- procedimientos ----------

    @Test
    void hasTheProceduresOfTheSummaryAndTheComparisons() throws IOException {
        assertTrue(names().containsAll(List.of("pr_resumen_simulacion", "pr_validar_comparacion",
                "pr_guardar_comparacion")));
        assertTrue(names().indexOf("pr_validar_comparacion") < names().indexOf("pr_guardar_comparacion"));
    }

    @Test
    void theSummaryReturnsTheIndicatorsAndPrintsThem() throws IOException {
        String summary = unit("pr_resumen_simulacion");

        for (String output : List.of("p_pacientes", "p_atendidos", "p_fallecidos", "p_sin_atencion",
                "p_espera_promedio", "p_espera_maxima")) {
            assertTrue(summary.contains(output), output);
        }
        assertTrue(summary.contains("DBMS_OUTPUT.PUT_LINE"));
    }

    @Test
    void theValidationUsesTheTypesAndTheMessagesOfTheApplication() throws IOException {
        String validation = unit("pr_validar_comparacion");

        for (ComparisonType type : ComparisonType.values()) {
            assertTrue(validation.contains("p_tipo := '" + type.name() + "';"), type.name());
        }
        assertTrue(validation.contains("'Elige dos ejecuciones distintas: una ejecución no se compara consigo "
                + "misma.'"));
        assertTrue(validation.contains("solo se comparan ejecuciones del mismo caso, con los mismos pacientes."));
        assertTrue(validation.contains("'Las dos ejecuciones tienen la misma estrategia y los mismos recursos: "
                + "darían los mismos '"));
    }

    @Test
    void theValidationComparesEveryResourceAndTheExtraDoctors() throws IOException {
        String validation = unit("pr_validar_comparacion");

        for (String column : List.of("camas", "camas_uci", "quirofanos", "medicos", "enfermeros",
                "unidades_sangre_por_tipo", "capacidad_morgue", "fn_medicos_extra(id)")) {
            assertTrue(validation.contains(column), column);
        }
    }

    @Test
    void savingValidatesFirstAndDoesNotRepeatAPair() throws IOException {
        String saving = unit("pr_guardar_comparacion");

        assertTrue(saving.indexOf("pr_validar_comparacion(") < saving.indexOf("INSERT INTO comparacion"));
        assertTrue(saving.contains("RAISE_APPLICATION_ERROR(-20003, v_mensaje);"));
        assertTrue(saving.contains("LEAST(id_ejecucion_a, id_ejecucion_b) = LEAST(p_id_a, p_id_b)"));
        assertTrue(saving.contains("p_nueva := 0;"));
        assertTrue(saving.contains("RETURNING id INTO p_id;"));
    }

    // ---------- triggers ----------

    @Test
    void hasTheTriggersOfTheCareTimeAndOfTheAudit() throws IOException {
        assertEquals(List.of("trg_validar_tiempo_atencion", "trg_auditoria_reporte"),
                names().subList(names().size() - 2, names().size()));
    }

    @Test
    void aPatientCannotStartCareBeforeArrivingUnlessItWasNeverAttended() throws IOException {
        String trigger = unit("trg_validar_tiempo_atencion");

        assertTrue(trigger.contains("BEFORE INSERT OR UPDATE OF minuto_inicio_atencion, minuto_llegada ON "
                + "resultado_paciente"));
        assertTrue(trigger.contains(":NEW.minuto_inicio_atencion <> -1 AND :NEW.minuto_inicio_atencion < "
                + ":NEW.minuto_llegada"));
        assertTrue(trigger.contains("RAISE_APPLICATION_ERROR(-20001"));
    }

    @Test
    void everySavedOrDeletedReportIsAudited() throws IOException {
        String trigger = unit("trg_auditoria_reporte");

        assertTrue(trigger.contains("AFTER INSERT OR DELETE ON reporte"));
        assertTrue(trigger.contains("VALUES (:NEW.id, :NEW.id_ejecucion, 'INSERT', USER);"));
        assertTrue(trigger.contains("VALUES (:OLD.id, :OLD.id_ejecucion, 'DELETE', USER);"));
    }

    @Test
    void theErrorCodesAreDifferentForEachRule() throws IOException {
        String script = script();

        assertEquals(1, count(script, "RAISE_APPLICATION_ERROR(-20001"));
        assertEquals(1, count(script, "RAISE_APPLICATION_ERROR(-20002"));
        assertEquals(1, count(script, "RAISE_APPLICATION_ERROR(-20003"));
    }

    private static int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
