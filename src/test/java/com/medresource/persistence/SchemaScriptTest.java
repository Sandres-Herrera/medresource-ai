package com.medresource.persistence;

import com.medresource.model.Specialty;
import com.medresource.model.Symptom;
import com.medresource.model.TreatmentType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.medresource.model.BloodType;
import com.medresource.model.CaseConfig;
import com.medresource.model.Scenario;
import com.medresource.model.Sex;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.CompatibilityLevel;
import com.medresource.model.EventType;
import com.medresource.model.PatientStatus;
import com.medresource.model.TriageLevel;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del script SQL: comprueban que sus restricciones coinciden con las enumeraciones
 * y reglas del modelo, porque Oracle no se puede ejecutar durante las pruebas.
 */
class SchemaScriptTest {

    private static final Pattern NAME = Pattern.compile("(?:CONSTRAINT|TABLE|INDEX)\\s+([a-z_]+)");
    private static final Pattern QUOTED = Pattern.compile("'([A-Z_]+)'");
    private static final Pattern BLANK_LINE = Pattern.compile("\\n[ \\t]*\\n");

    private static String read(String resource) throws IOException {
        try (InputStream stream = SchemaScriptTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "no se encontró " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> statements() throws IOException {
        return SchemaInstaller.statementsFrom(read(SchemaInstaller.SCHEMA_RESOURCE));
    }

    private static List<String> createdTables() throws IOException {
        List<String> tables = new ArrayList<>();
        for (String statement : statements()) {
            Matcher matcher = Pattern.compile("^CREATE TABLE ([a-z_]+)").matcher(statement);
            if (matcher.find()) {
                tables.add(matcher.group(1));
            }
        }
        return tables;
    }

    /** Texto de una restricción CHECK, desde su nombre hasta la siguiente restricción. */
    private static String constraint(String name) throws IOException {
        String script = read(SchemaInstaller.SCHEMA_RESOURCE);
        int start = script.indexOf("CONSTRAINT " + name + " CHECK");
        assertTrue(start >= 0, "no existe la restricción " + name);
        int end = script.indexOf("CONSTRAINT", start + 1);
        int semicolon = script.indexOf(");", start);
        int stop = end >= 0 ? Math.min(end, semicolon) : semicolon;
        return script.substring(start, stop);
    }

    /** Valores entre comillas de la lista {@code IN (...)} de una restricción. */
    private static Set<String> allowedValues(String name) throws IOException {
        String text = constraint(name);
        String list = text.substring(text.indexOf("IN ("));
        Set<String> values = new HashSet<>();
        Matcher matcher = QUOTED.matcher(list);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static Set<String> namesOf(Enum<?>[] constants) {
        Set<String> names = new HashSet<>();
        for (Enum<?> constant : constants) {
            names.add(constant.name());
        }
        return names;
    }

    private static void assertMatches(String constraint, Enum<?>[] constants) throws IOException {
        assertEquals(namesOf(constants), allowedValues(constraint), constraint);
    }

    // ---------- forma general ----------

    @Test
    void identifiersAreAtMostThirtyCharactersLong() throws IOException {
        Matcher matcher = NAME.matcher(read(SchemaInstaller.SCHEMA_RESOURCE));
        while (matcher.find()) {
            assertTrue(matcher.group(1).length() <= 30, matcher.group(1));
        }
    }

    @Test
    void everyStatementCreatesATableOrAnIndex() throws IOException {
        for (String statement : statements()) {
            assertTrue(statement.startsWith("CREATE TABLE ") || statement.startsWith("CREATE INDEX "), statement);
        }
    }

    @Test
    void theScriptRunsInSqlPlusWithoutSurprises() throws IOException {
        // SQL*Plus corta una sentencia en la primera línea en blanco y trata "&" como variable.
        for (String statement : statements()) {
            assertFalse(BLANK_LINE.matcher(statement).find(), "línea en blanco dentro de: " + statement);
        }
        assertFalse(read(SchemaInstaller.SCHEMA_RESOURCE).contains("&"));
    }

    @Test
    void everyForeignKeyPointsToATableCreatedBefore() throws IOException {
        List<String> created = new ArrayList<>();
        Pattern reference = Pattern.compile("REFERENCES ([a-z_]+) ");
        for (String statement : statements()) {
            Matcher table = Pattern.compile("^CREATE TABLE ([a-z_]+)").matcher(statement);
            if (!table.find()) {
                continue;
            }
            Matcher target = reference.matcher(statement);
            while (target.find()) {
                assertTrue(created.contains(target.group(1)), table.group(1) + " usa " + target.group(1) + " antes de crearla");
            }
            created.add(table.group(1));
        }
    }

    // ---------- catálogo ----------

    @Test
    void theCatalogTablesAreCreated() throws IOException {
        List<String> tables = createdTables();

        assertTrue(tables.contains("disease"));
        assertTrue(tables.contains("disease_secondary_specialty"));
        assertTrue(tables.contains("disease_symptom"));
    }

    @Test
    void theSpecialtiesMatchTheEnumeration() throws IOException {
        assertMatches("ck_disease_specialty", Specialty.values());
        assertMatches("ck_disease_secondary_specialty", Specialty.values());
    }

    @Test
    void theTreatmentsMatchTheEnumeration() throws IOException {
        assertMatches("ck_disease_treatment", TreatmentType.values());
    }

    @Test
    void theDiseaseSymptomsMatchTheEnumeration() throws IOException {
        assertMatches("ck_disease_symptom_name", Symptom.values());
    }

    // ---------- casos ----------

    @Test
    void theCaseTablesAreCreated() throws IOException {
        List<String> tables = createdTables();

        assertTrue(tables.contains("sim_case"));
        assertTrue(tables.contains("case_patient"));
        assertTrue(tables.contains("case_patient_symptom"));
    }

    @Test
    void theScenariosMatchTheEnumeration() throws IOException {
        assertMatches("ck_sim_case_scenario", Scenario.values());
    }

    @Test
    void theBloodTypesMatchTheEnumeration() throws IOException {
        assertMatches("ck_case_patient_blood", BloodType.values());
    }

    @Test
    void theSexesMatchTheEnumeration() throws IOException {
        assertMatches("ck_case_patient_sex", Sex.values());
    }

    @Test
    void thePatientSymptomsMatchTheEnumeration() throws IOException {
        assertMatches("ck_case_symptom_name", Symptom.values());
    }

    @Test
    void theCaseLimitsMatchTheCaseConfiguration() throws IOException {
        String patients = constraint("ck_sim_case_patients");
        String duration = constraint("ck_sim_case_duration");

        assertTrue(patients.contains(">= " + CaseConfig.MIN_PATIENTS));
        assertTrue(patients.contains("<= " + CaseConfig.MAX_PATIENTS));
        assertTrue(duration.contains(">= " + CaseConfig.MIN_DURATION_MINUTES));
        assertTrue(duration.contains("<= " + CaseConfig.MAX_DURATION_MINUTES));
    }

    // ---------- resultados ----------

    @Test
    void theResultTablesAreCreated() throws IOException {
        List<String> tables = createdTables();

        assertTrue(tables.contains("sim_result"));
        assertTrue(tables.contains("patient_result"));
        assertTrue(tables.contains("sim_event"));
    }

    @Test
    void theEventTypesMatchTheEnumeration() throws IOException {
        assertMatches("ck_sim_event_type", EventType.values());
    }

    @Test
    void theCausesOfDeathMatchTheEnumeration() throws IOException {
        assertMatches("ck_patient_result_cause", CauseOfDeath.values());
    }

    @Test
    void theCareLevelsMatchTheEnumeration() throws IOException {
        assertMatches("ck_patient_result_care", CompatibilityLevel.values());
    }

    @Test
    void theResultSexesMatchTheEnumeration() throws IOException {
        assertMatches("ck_patient_result_sex", Sex.values());
    }

    @Test
    void onlyTheFinalStatusesAreAllowedInAResult() throws IOException {
        Set<String> expected = new HashSet<>();
        for (PatientStatus status : PatientStatus.values()) {
            if (status.isFinal()) {
                expected.add(status.name());
            }
        }

        assertEquals(expected, allowedValues("ck_patient_result_status"));
        assertFalse(allowedValues("ck_patient_result_status").contains("WAITING"));
    }

    @Test
    void theTriageRangeMatchesTheTriageLevels() throws IOException {
        List<Integer> levels = Arrays.stream(TriageLevel.values()).map(TriageLevel::getLevel).toList();
        String triage = constraint("ck_patient_result_triage");

        assertTrue(triage.contains(">= " + levels.stream().min(Integer::compare).orElseThrow()));
        assertTrue(triage.contains("<= " + levels.stream().max(Integer::compare).orElseThrow()));
    }

    @Test
    void theScriptCreatesNineTablesAndOneIndex() throws IOException {
        assertEquals(9, createdTables().size());
        assertEquals(10, statements().size());
    }

    // ---------- borrado ----------

    @Test
    void theDropScriptRemovesEveryTable() throws IOException {
        List<String> dropped = new ArrayList<>();
        for (String statement : SchemaInstaller.statementsFrom(read("/db/drop_schema.sql"))) {
            Matcher matcher = Pattern.compile("^DROP TABLE ([a-z_]+) CASCADE CONSTRAINTS PURGE$").matcher(statement);
            assertTrue(matcher.matches(), statement);
            dropped.add(matcher.group(1));
        }

        assertEquals(new HashSet<>(createdTables()), new HashSet<>(dropped));
        assertEquals(createdTables().size(), dropped.size());
    }

    @Test
    void theDropScriptRemovesChildrenBeforeParents() throws IOException {
        List<String> dropped = new ArrayList<>();
        for (String statement : SchemaInstaller.statementsFrom(read("/db/drop_schema.sql"))) {
            dropped.add(statement.split(" ")[2]);
        }
        Pattern reference = Pattern.compile("REFERENCES ([a-z_]+) ");
        for (String statement : statements()) {
            Matcher table = Pattern.compile("^CREATE TABLE ([a-z_]+)").matcher(statement);
            if (!table.find()) {
                continue;
            }
            Matcher parent = reference.matcher(statement);
            while (parent.find()) {
                assertTrue(dropped.indexOf(table.group(1)) < dropped.indexOf(parent.group(1)),
                        table.group(1) + " debe borrarse antes que " + parent.group(1));
            }
        }
    }
}