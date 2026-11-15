package com.medresource.services.management;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.medresource.model.BloodUsage;
import com.medresource.model.BottleneckStats;
import com.medresource.model.CollapsePeriod;
import com.medresource.model.ResourceUsage;
import com.medresource.model.ReviewCase;
import com.medresource.model.SimulationReport;
import com.medresource.model.SpecialtyLoad;
import com.medresource.model.StaffLoad;
import com.medresource.model.TriageLevelStats;
import com.medresource.model.WhatIfReport;
import com.medresource.model.WhatIfResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hechos del reporte que se le envían a la IA: un JSON con los números ya calculados por el código, con claves
 * en español, para que la IA solo tenga que explicarlos.
 *
 * <p>Los decimales se redondean a uno, los porcentajes van de 0 a 100 y los minutos de la simulación van como
 * hora del reloj ({@code hh:mm}). Nunca se envían los eventos crudos ni datos de personas reales: solo datos
 * simulados y resumidos.</p>
 *
 * <p>También dice qué cifras se pueden escribir en el texto ({@link #allowedNumbers(JsonElement)}): todas las
 * que aparecen en los hechos, tal cual o sin decimales si el decimal es cero. Así se descarta cualquier cifra
 * que la IA invente. Los códigos como {@code P024} o {@code D01} no cuentan como cifras.</p>
 */
public final class ReportFacts {

    /** Números, con o sin decimales, separados con punto o coma. */
    static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    /** Horas del reloj de la simulación, por ejemplo {@code 01:17}. */
    static final Pattern CLOCK = Pattern.compile("\\b\\d{1,2}:\\d{2}\\b");

    /** Códigos con letras y números, como {@code P024}, {@code D01} o {@code H1-C2}: no son cifras. */
    static final Pattern CODE = Pattern.compile("\\b[A-Za-z]+\\d+(?:-[A-Za-z]*\\d+)*\\b");

    private ReportFacts() {
    }

    /**
     * Arma los hechos del reporte y, si lo hay, del análisis «¿y si…?».
     *
     * @param report reporte con los números
     * @param whatIf análisis «¿y si…?»; {@code null} si no se hizo
     * @return hechos en JSON
     * @throws IllegalArgumentException si falta el reporte
     */
    public static JsonObject of(SimulationReport report, WhatIfReport whatIf) {
        if (report == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        JsonObject facts = new JsonObject();
        facts.add("caso", caseOf(report));
        facts.addProperty("estrategia", report.metrics().strategyName());
        facts.add("resumen", summaryOf(report));
        facts.add("triage", triageOf(report));
        facts.add("fallecidos", deathsOf(report));
        facts.add("fallas", failuresOf(report));
        facts.add("sin_terminar", unfinishedOf(report));
        facts.add("cuellos_de_botella", bottlenecksOf(report));
        facts.add("colapsos", collapsesOf(report));
        facts.add("recursos", resourcesOf(report));
        facts.add("personal", staffOf(report));
        facts.add("sangre", bloodOf(report));
        facts.add("casos_para_revisar", casesOf(report));
        if (whatIf != null) {
            facts.add("analisis_y_si", whatIfOf(whatIf));
        }
        return facts;
    }

    /**
     * Junta todas las cifras que aparecen en los hechos, en números y dentro de los textos, para comparar con
     * las del texto de la IA. Las horas se guardan completas ({@code 01:17}).
     *
     * @param facts hechos en JSON
     * @return cifras en forma canónica ({@link #canonical(String)}) y horas
     * @throws IllegalArgumentException si faltan los hechos
     */
    public static Set<String> allowedNumbers(JsonElement facts) {
        if (facts == null) {
            throw new IllegalArgumentException("Los hechos son obligatorios.");
        }
        Set<String> allowed = new HashSet<>();
        collect(facts, allowed);
        return allowed;
    }

    /**
     * Escribe un número en forma canónica: punto decimal y sin ceros sobrantes ({@code 13,0} → {@code 13},
     * {@code 45,90} → {@code 45.9}).
     *
     * @param number número escrito con punto o coma
     * @return el mismo número en forma canónica
     */
    static String canonical(String number) {
        return new BigDecimal(number.replace(',', '.')).stripTrailingZeros().toPlainString();
    }

    private static void collect(JsonElement element, Set<String> allowed) {
        if (element.isJsonObject()) {
            element.getAsJsonObject().entrySet().forEach(entry -> collect(entry.getValue(), allowed));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> collect(item, allowed));
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                allowed.add(canonical(primitive.getAsBigDecimal().abs().toPlainString()));
            } else if (primitive.isString()) {
                String text = primitive.getAsString();
                Matcher clock = CLOCK.matcher(text);
                while (clock.find()) {
                    allowed.add(clock.group());
                }
                String withoutClocksOrCodes = CODE.matcher(CLOCK.matcher(text).replaceAll(" ")).replaceAll(" ");
                Matcher number = NUMBER.matcher(withoutClocksOrCodes);
                while (number.find()) {
                    allowed.add(canonical(number.group()));
                }
            }
        }
    }

    private static JsonObject caseOf(SimulationReport report) {
        JsonObject json = new JsonObject();
        json.addProperty("escenario", report.config().scenario().getDisplayName());
        json.addProperty("semilla", report.config().seed());
        json.addProperty("pacientes", report.config().patientCount());
        json.addProperty("duracion_de_las_llegadas", clock(report.config().durationMinutes()));
        json.addProperty("duracion_total", clock(report.totalMinutes()));
        return json;
    }

    private static JsonObject summaryOf(SimulationReport report) {
        JsonObject json = new JsonObject();
        json.addProperty("atendidos", report.treated());
        json.addProperty("fallecidos", report.metrics().deaths());
        add(json, "mortalidad_pct", percent(report.metrics().mortalityRate()));
        json.addProperty("remitidos", report.metrics().referred());
        json.addProperty("sin_atender", report.metrics().unattended());
        add(json, "espera_promedio_min", round(report.metrics().averageWaitMinutes()));
        json.addProperty("espera_maxima_min", report.maxWaitMinutes());
        return json;
    }

    private static JsonArray triageOf(SimulationReport report) {
        JsonArray array = new JsonArray();
        for (TriageLevelStats stats : report.triage()) {
            if (stats.patients() == 0) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("nivel", stats.level().getDisplayName());
            json.addProperty("meta_min", stats.level().getTargetMinutes());
            json.addProperty("pacientes", stats.patients());
            json.addProperty("fallecidos", stats.deaths());
            add(json, "espera_promedio_min", round(stats.averageWaitMinutes()));
            json.addProperty("espera_maxima_min", stats.maxWaitMinutes());
            add(json, "atendidos_en_la_meta_pct", percent(stats.withinTargetRate()));
            add(json, "salud_perdida_esperando", round(stats.averageHealthLost()));
            array.add(json);
        }
        return array;
    }

    private static JsonObject deathsOf(SimulationReport report) {
        JsonObject json = new JsonObject();
        json.addProperty("evitables", report.deaths().avoidable());
        json.addProperty("murieron_esperando", report.deaths().whileWaiting());
        json.addProperty("murieron_atendidos_tarde", report.deaths().afterLateCare());
        json.addProperty("murieron_atendidos_a_tiempo", report.deaths().unavoidable());
        JsonObject causes = new JsonObject();
        report.deaths().byCause().forEach((cause, count) -> causes.addProperty(cause.getDisplayName(), count));
        json.add("por_causa", causes);
        return json;
    }

    private static JsonObject failuresOf(SimulationReport report) {
        JsonObject json = new JsonObject();
        json.addProperty("pacientes_fuera_de_la_meta", report.failures().overTarget());
        json.addProperty("criticos_sin_primeros_auxilios_a_tiempo", report.failures().criticalWithoutTimelyAid());
        json.addProperty("espera_mas_larga_sin_primeros_auxilios_min",
                report.failures().longestCriticalWithoutAidMinutes());
        return json;
    }

    private static JsonObject unfinishedOf(SimulationReport report) {
        JsonObject json = new JsonObject();
        json.addProperty("esperando_al_cerrar_las_llegadas", report.unfinished().stillWaiting());
        json.addProperty("en_tratamiento_al_cerrar_las_llegadas", report.unfinished().inTreatment());
        json.addProperty("minutos_extra_para_terminar", report.unfinished().extraMinutes());
        return json;
    }

    private static JsonArray bottlenecksOf(SimulationReport report) {
        JsonArray array = new JsonArray();
        for (BottleneckStats stats : report.bottlenecks()) {
            JsonObject json = new JsonObject();
            json.addProperty("recurso", stats.resource().getDisplayName());
            add(json, "parte_de_la_espera_pct", percent(stats.share()));
            json.addProperty("minutos_paciente", stats.waitingMinutes());
            json.addProperty("pacientes_afectados", stats.patientsAffected());
            json.addProperty("desde", clock(stats.firstMinute()));
            json.addProperty("hasta", clock(stats.lastMinute()));
            json.addProperty("maximo_esperando", stats.peakPatients());
            json.addProperty("hora_del_maximo", clock(stats.peakMinute()));
            array.add(json);
        }
        return array;
    }

    private static JsonArray collapsesOf(SimulationReport report) {
        JsonArray array = new JsonArray();
        for (CollapsePeriod collapse : report.collapses()) {
            JsonObject json = new JsonObject();
            json.addProperty("desde", clock(collapse.startMinute()));
            json.addProperty("hasta", clock(collapse.endMinute()));
            json.addProperty("duracion_min", collapse.durationMinutes());
            json.addProperty("peor_momento", clock(collapse.peakMinute()));
            json.addProperty("pacientes_fuera_de_la_meta_en_el_peor_momento", collapse.peakOverTarget());
            array.add(json);
        }
        return array;
    }

    private static JsonArray resourcesOf(SimulationReport report) {
        JsonArray array = new JsonArray();
        for (ResourceUsage usage : report.resources()) {
            JsonObject json = new JsonObject();
            json.addProperty("grupo", usage.group().getDisplayName());
            json.addProperty("capacidad", usage.capacity());
            add(json, "ocupacion_promedio_pct", percent(usage.averageOccupancy()));
            add(json, "ocupacion_maxima_pct", percent(usage.peakOccupancy()));
            json.addProperty("minutos_llenos", usage.fullMinutes());
            json.addProperty("subutilizado", usage.isUnderused());
            array.add(json);
        }
        return array;
    }

    private static JsonObject staffOf(SimulationReport report) {
        JsonObject json = new JsonObject();
        json.add("saturados", codes(report.saturatedStaff()));
        json.add("libres_la_mayor_parte_del_tiempo", codes(report.idleStaff()));
        JsonArray specialties = new JsonArray();
        for (SpecialtyLoad load : report.specialties()) {
            JsonObject item = new JsonObject();
            item.addProperty("especialidad", load.specialty().getDisplayName());
            item.addProperty("medicos", load.doctors());
            item.addProperty("pacientes", load.patients());
            add(item, "ocupacion_pct", percent(load.averageOccupancy()));
            item.addProperty("minutos_paciente_esperando_medico", load.doctorWaitMinutes());
            specialties.add(item);
        }
        json.add("especialidades", specialties);
        return json;
    }

    private static JsonObject bloodOf(SimulationReport report) {
        JsonObject json = new JsonObject();
        json.addProperty("transfusiones", report.blood().transfusions());
        json.addProperty("unidades_usadas", report.blood().unitsUsed());
        json.addProperty("usos_de_o_negativo", report.blood().universalDonorUses());
        json.addProperty("o_negativo_en_pacientes_de_otro_tipo", report.blood().universalDonorForOthers());
        json.addProperty("pacientes_que_esperaron_sangre", report.blood().patientsWaitingForBlood());
        json.addProperty("fallecidos_por_falta_de_sangre", report.blood().deathsForLackOfBlood());
        JsonArray ranOut = new JsonArray();
        for (BloodUsage usage : report.blood().ranOut()) {
            JsonObject item = new JsonObject();
            item.addProperty("tipo", usage.type().getLabel());
            item.addProperty("se_agoto_a_las", clock(usage.firstEmptyMinute()));
            ranOut.add(item);
        }
        json.add("tipos_agotados", ranOut);
        return json;
    }

    private static JsonArray casesOf(SimulationReport report) {
        JsonArray array = new JsonArray();
        for (ReviewCase review : report.cases()) {
            JsonObject json = new JsonObject();
            json.addProperty("paciente", review.patientCode());
            json.addProperty("motivo", review.reason().getDisplayName());
            json.addProperty("triage", review.triage().getDisplayName());
            json.addProperty("espera_min", review.waitMinutes());
            add(json, "salud_perdida", round(review.healthLost()));
            json.addProperty("desenlace", review.status().getDisplayName());
            json.addProperty("ver_a_las", clock(review.focusMinute()));
            array.add(json);
        }
        return array;
    }

    private static JsonObject whatIfOf(WhatIfReport whatIf) {
        JsonObject json = new JsonObject();
        json.addProperty("casos_simulados_por_alternativa", whatIf.cases());
        add(json, "mortalidad_actual_pct", percent(whatIf.mortality().mean()));
        add(json, "margen_de_la_mortalidad_actual_pct", percent(whatIf.mortality().confidenceHalfWidth()));
        add(json, "espera_actual_min", round(whatIf.waitMinutes().mean()));
        json.add("cambios_de_recursos", alternatives(whatIf.resources()));
        json.add("otras_estrategias", alternatives(whatIf.policies()));
        json.add("recomendaciones", alternatives(whatIf.recommendations()));
        whatIf.betterPolicy().ifPresent(policy -> json.addProperty("estrategia_que_conviene", policy.strategyName()));
        return json;
    }

    private static JsonArray alternatives(List<WhatIfResult> results) {
        JsonArray array = new JsonArray();
        for (WhatIfResult result : results) {
            JsonObject json = new JsonObject();
            json.addProperty("alternativa", result.label());
            add(json, "muertes_evitadas_por_cada_100_pacientes", round(result.deathsAvoidedPer100()));
            add(json, "margen", round(100 * result.mortalityChange().confidenceHalfWidth()));
            add(json, "cambio_de_la_espera_min", round(result.waitChange().mean()));
            json.addProperty("mejora", result.isImprovement());
            array.add(json);
        }
        return array;
    }

    private static JsonArray codes(List<StaffLoad> loads) {
        JsonArray array = new JsonArray();
        loads.forEach(load -> array.add(load.code()));
        return array;
    }

    /** Agrega un número, o nada si no es un número (por ejemplo, el promedio de un grupo vacío). */
    private static void add(JsonObject json, String key, BigDecimal value) {
        if (value != null) {
            json.addProperty(key, value);
        }
    }

    private static BigDecimal percent(double fraction) {
        return round(100 * fraction);
    }

    private static BigDecimal round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.scale() < 0 ? rounded.setScale(0) : rounded;
    }

    /** Minuto de la simulación como hora del reloj, por ejemplo 70 → {@code 01:10}. */
    static String clock(int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }
}
