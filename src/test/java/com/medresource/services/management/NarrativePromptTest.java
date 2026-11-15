package com.medresource.services.management;

import com.google.gson.JsonObject;
import com.medresource.model.ReportSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de las instrucciones para la IA.
 */
class NarrativePromptTest {

    private static JsonObject facts() {
        JsonObject facts = new JsonObject();
        facts.addProperty("estrategia", "Prioridad clínica (triage)");
        JsonObject summary = new JsonObject();
        summary.addProperty("mortalidad_pct", 9.8);
        facts.add("resumen", summary);
        return facts;
    }

    @Test
    void endsWithTheFactsWithoutEscapingTheAccents() {
        String prompt = NarrativePrompt.of(facts(), 3);

        assertTrue(prompt.contains("HECHOS:"));
        assertTrue(prompt.contains("\"mortalidad_pct\": 9.8"));
        assertTrue(prompt.contains("Prioridad clínica (triage)"));
    }

    @Test
    void asksForEverySectionAndOneTextPerRecommendation() {
        String prompt = NarrativePrompt.of(facts(), 3);

        for (ReportSection section : ReportSection.values()) {
            assertTrue(prompt.contains("\"" + section.getKey() + "\""), section.getKey());
        }
        assertTrue(prompt.contains("[3 textos, uno por recomendación"));
    }

    @Test
    void forbidsInventedNumbersAndNewRecommendations() {
        String prompt = NarrativePrompt.of(facts(), 0);

        assertTrue(prompt.contains("No calcules, no redondees ni inventes cifras"));
        assertTrue(prompt.contains("No recomiendes nada que no esté"));
    }

    @Test
    void explainsThatTheHoursAreOfTheClockOfTheSimulation() {
        String prompt = NarrativePrompt.of(facts(), 0);

        assertTrue(prompt.contains("reloj de la simulación, que empieza a las 00:00"));
        assertTrue(prompt.contains("escribe \"hasta las\" y esa hora"));
    }

    @Test
    void needsTheFacts() {
        assertThrows(IllegalArgumentException.class, () -> NarrativePrompt.of(null, 1));
        assertThrows(IllegalArgumentException.class, () -> NarrativePrompt.of(facts(), -1));
    }
}
