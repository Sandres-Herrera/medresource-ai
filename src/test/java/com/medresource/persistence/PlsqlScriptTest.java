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
 * Pruebas del script PL/SQL: que tenga todas sus unidades, que cada una termine bien, que las validaciones se
 * hagan antes de cada INSERT y que use las mismas reglas que la aplicación. Que compile en Oracle lo comprueba la
 * prueba de integración con la base de datos.
 */
class PlsqlScriptTest {

    private static final Pattern HEADER =
            Pattern.compile("^CREATE OR REPLACE (FUNCTION|PROCEDURE|TRIGGER|VIEW) ([a-z_]+)");

    /** COMMIT o ROLLBACK como sentencia: al inicio de la línea, sin contar los comentarios. */
    private static final Pattern TRANSACTION_CONTROL =
            Pattern.compile("^\\s*(COMMIT|ROLLBACK)\\b", Pattern.MULTILINE);

    private static final Pattern COMMIT = Pattern.compile("^\\s*COMMIT\\b", Pattern.MULTILINE);

    /** Objetos que se retiraron: no deben estar en el script. */
    private static final List<String> RETIRED = List.of("pr_exigir_ejecucion", "fn_espera_promedio",
            "fn_espera_maxima", "fn_porcentaje_atendidos", "fn_tasa_mortalidad", "fn_pacientes_sin_atencion",
            "pr_resumen_simulacion");

    /** Funciones de validación nuevas. */
    private static final List<String> VALIDATIONS = List.of("fn_caso_integro", "fn_base_compatible",
            "fn_paciente_pertenece_caso", "fn_evento_paciente_valido", "fn_integridad_ejecucion");

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
            if (unit.startsWith("CREATE OR REPLACE VIEW")) {
                // Una vista no lleva «END»; y JDBC no acepta el «;» final en una sentencia DDL.
                assertFalse(unit.endsWith(";"), name);
            } else {
                assertTrue(unit.endsWith("END " + name + ";") || unit.endsWith("END;"), name);
            }
        }
    }

    @Test
    void theScriptRunsInSqlPlusWithoutSurprises() throws IOException {
        assertFalse(script().contains("&"), "SQL*Plus trata «&» como variable");
        assertFalse(TRANSACTION_CONTROL.matcher(script()).find(), "las transacciones las maneja quien llama");
    }

    @Test
    void theRetiredObjectsAreNotInTheScript() throws IOException {
        for (String name : RETIRED) {
            assertFalse(names().contains(name), name);
        }
    }

    @Test
    void migrationDropsExactlyTheSevenRetiredObjectsWithoutDroppingTables() throws IOException {
        String migration;
        try (InputStream stream = PlsqlScriptTest.class.getResourceAsStream(
                "/db/migracion_eliminar_plsql_antiguo.sql")) {
            assertNotNull(stream, "the PL/SQL cleanup migration is missing");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher drops = Pattern.compile("retirar\\('(FUNCTION|PROCEDURE)', '([A-Z_]+)'\\)")
                .matcher(migration);
        List<String> namesToDrop = new java.util.ArrayList<>();
        while (drops.find()) {
            namesToDrop.add(drops.group(2).toLowerCase(java.util.Locale.ROOT));
        }
        assertEquals(RETIRED.stream().sorted().toList(), namesToDrop.stream().sorted().toList());
        assertFalse(migration.toUpperCase(java.util.Locale.ROOT).contains("DROP TABLE"));
        assertTrue(migration.contains("SQLCODE = -4043"), "the cleanup must tolerate already missing objects");
    }

    // ---------- funciones de validación ----------

    @Test
    void hasTheValidationFunctionsAndTheExtraDoctorsBeforeTheProceduresThatUseThem() throws IOException {
        assertTrue(names().containsAll(VALIDATIONS));
        assertTrue(names().contains("fn_medicos_extra"));
        for (String function : VALIDATIONS) {
            assertTrue(names().indexOf(function) < names().indexOf("pr_crear_ejecucion"), function);
        }
    }

    @Test
    void theCaseIsCompleteWhenItHasTheRightPatientsSymptomsAgesAndArrivals() throws IOException {
        String caso = unit("fn_caso_integro");

        assertTrue(caso.contains("cantidad_pacientes"));
        assertTrue(caso.contains("NVL(v_minimo, 0) <> 1"));
        assertTrue(caso.contains("NOT EXISTS (SELECT 1 FROM caso_paciente_sintoma"));
        assertTrue(caso.contains("cp.edad < e.edad_minima OR cp.edad > e.edad_maxima"));
        assertTrue(caso.contains("minuto_llegada >= v_duracion"));
    }

    @Test
    void theRunIsCompleteWhenItsResultsAndEventsMatchTheCase() throws IOException {
        String run = unit("fn_integridad_ejecucion");

        assertTrue(run.contains("v_resultados <> v_pacientes"));
        assertTrue(run.contains("v_eventos = 0"), "A run without any events must be rejected");
        assertTrue(run.contains("r.irrecuperable <> cp.irrecuperable"));
        assertTrue(run.contains("GROUP BY codigo_paciente") && run.contains("HAVING COUNT(*) > 1"));
        assertTrue(run.contains("NOT EXISTS ("));
        assertTrue(run.contains("r.codigo_paciente = ev.codigo_paciente"));
        assertTrue(run.contains("HAVING COUNT(*) = 1"));
        assertFalse(run.contains("fn_evento_paciente_valido("),
                "La validación completa no debe llamar la función por cada evento");
    }

    // ---------- validaciones antes de cada INSERT ----------

    @Test
    void theCaseAndTheBaseAreCheckedBeforeTheExecutionIsCreated() throws IOException {
        String create = unit("pr_crear_ejecucion");

        assertTrue(create.contains("fn_caso_integro(p_id_caso) = 0"));
        assertTrue(create.contains("fn_base_compatible(p_id_caso, p_base) = 0"));
        assertTrue(create.contains("RAISE_APPLICATION_ERROR(-20004"));
        assertTrue(create.contains("RAISE_APPLICATION_ERROR(-20005"));
        assertTrue(create.indexOf("fn_caso_integro(") < create.indexOf("INSERT INTO ejecucion"));
        assertTrue(create.indexOf("fn_base_compatible(") < create.indexOf("INSERT INTO ejecucion"));
    }

    @Test
    void thePatientEventAndReportAreCheckedBeforeTheyAreSaved() throws IOException {
        String patient = unit("pr_agregar_resultado_paciente");
        assertTrue(patient.contains("fn_paciente_pertenece_caso(p_id_ejecucion, p_id_paciente) = 0"));
        assertTrue(patient.contains("RAISE_APPLICATION_ERROR(-20006"));
        assertTrue(patient.indexOf("fn_paciente_pertenece_caso(") < patient.indexOf("INSERT INTO resultado_paciente"));

        String event = unit("pr_agregar_evento");
        assertTrue(event.contains("fn_evento_paciente_valido(p_id_ejecucion, p_codigo_paciente) = 0"));
        assertTrue(event.contains("RAISE_APPLICATION_ERROR(-20007"));
        assertTrue(event.indexOf("fn_evento_paciente_valido(") < event.indexOf("INSERT INTO evento"));

        String report = unit("pr_guardar_reporte");
        assertTrue(report.contains("fn_integridad_ejecucion(p_id_ejecucion) = 0"));
        assertTrue(report.contains("RAISE_APPLICATION_ERROR(-20008"));
        assertTrue(report.indexOf("fn_integridad_ejecucion(") < report.indexOf("INSERT INTO reporte"));
    }

    @Test
    void theErrorCodesAreDifferentForEachRule() throws IOException {
        String script = script();

        for (String code : List.of("-20001", "-20003", "-20004", "-20005", "-20006", "-20007", "-20008")) {
            assertEquals(1, count(script, "RAISE_APPLICATION_ERROR(" + code), code);
        }
        assertEquals(0, count(script, "RAISE_APPLICATION_ERROR(-20002"), "-20002 se retiró con pr_exigir_ejecucion");
    }

    private static int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }

    // ---------- comparaciones ----------

    @Test
    void hasTheComparisonProceduresInTheRightOrder() throws IOException {
        assertTrue(names().containsAll(List.of("pr_validar_comparacion", "pr_guardar_comparacion")));
        assertTrue(names().indexOf("pr_validar_comparacion") < names().indexOf("pr_guardar_comparacion"));
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
        List<String> names = names();

        assertTrue(names.containsAll(List.of("trg_validar_tiempo_atencion", "trg_auditoria_reporte")));
        assertTrue(names.indexOf("trg_validar_tiempo_atencion") < names.indexOf("trg_auditoria_reporte"));
    }

    @Test
    void careStartsOnlyBetweenArrivalAndOutcomeUnlessNeverAttended() throws IOException {
        String trigger = unit("trg_validar_tiempo_atencion");

        assertTrue(trigger.contains("BEFORE INSERT OR UPDATE OF minuto_inicio_atencion, minuto_llegada, "
                + "minuto_desenlace ON resultado_paciente"));
        assertTrue(trigger.contains(":NEW.minuto_inicio_atencion <> -1"));
        assertTrue(trigger.contains(":NEW.minuto_inicio_atencion < :NEW.minuto_llegada"));
        assertTrue(trigger.contains(":NEW.minuto_inicio_atencion > :NEW.minuto_desenlace"));
        assertTrue(trigger.contains("RAISE_APPLICATION_ERROR(-20001"));
    }

    @Test
    void editingDiseaseAgesMustPreserveStoredCasePatients() throws IOException {
        String procedure = unit("pr_actualizar_enfermedad");

        assertTrue(procedure.contains("FROM caso_paciente"));
        assertTrue(procedure.contains("id_enfermedad = p_id"));
        assertTrue(procedure.contains("edad < p_edad_min OR edad > p_edad_max"));
        assertTrue(procedure.contains("RAISE_APPLICATION_ERROR(-20009"));
        assertTrue(procedure.indexOf("RAISE_APPLICATION_ERROR(-20009") < procedure.indexOf("UPDATE enfermedad"));
    }

    @Test
    void everySavedOrDeletedReportIsAudited() throws IOException {
        String trigger = unit("trg_auditoria_reporte");

        assertTrue(trigger.contains("AFTER INSERT OR DELETE ON reporte"));
        assertTrue(trigger.contains("VALUES (:NEW.id, :NEW.id_ejecucion, 'INSERT', USER);"));
        assertTrue(trigger.contains("VALUES (:OLD.id, :OLD.id_ejecucion, 'DELETE', USER);"));
    }

    // ---------- demostración ----------

    @Test
    void theDemoScriptShowsEveryFunctionAndChangesNothing() throws IOException {
        String demo;
        try (InputStream stream = PlsqlScriptTest.class.getResourceAsStream("/db/demo_plsql.sql")) {
            assertNotNull(stream, "no se encontró la demostración");
            demo = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String name : names()) {
            if (name.startsWith("fn_")) {
                assertTrue(demo.contains(name + "("), name);
            }
        }
        assertTrue(demo.contains("pr_validar_comparacion("));
        assertTrue(demo.contains("trg_validar_tiempo_atencion"));
        assertFalse(demo.contains("&"));
        // La demostración sí usa ROLLBACK para deshacer sus cambios, pero nunca confirma (COMMIT).
        assertFalse(COMMIT.matcher(demo).find());
    }
}
