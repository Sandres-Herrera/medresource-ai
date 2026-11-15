package com.medresource.services.management;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.medresource.model.NarrativeSource;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lee el JSON que respondió la IA y lo convierte en el texto del reporte.
 *
 * <p>Acepta la respuesta envuelta en un bloque de código ({@code ```json … ```}), que algunos modelos agregan.
 * Las secciones con claves desconocidas o vacías se ignoran; si falta el resumen o la conclusión, la respuesta
 * no sirve.</p>
 */
public final class NarrativeParser {

    private NarrativeParser() {
    }

    /**
     * Convierte la respuesta de la IA en el texto del reporte.
     *
     * @param answer texto que respondió la IA
     * @return el texto del reporte, redactado por la IA; vacío si la respuesta no tiene el formato pedido
     */
    public static Optional<ReportNarrative> parse(String answer) {
        if (answer == null || answer.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonObject root = JsonParser.parseString(withoutCodeFence(answer)).getAsJsonObject();
            String summary = text(root.get("resumen"));
            String conclusion = text(root.get("conclusion"));
            if (summary == null || conclusion == null) {
                return Optional.empty();
            }
            return Optional.of(new ReportNarrative(NarrativeSource.AI, summary, sectionsOf(root),
                    recommendationsOf(root), conclusion, null));
        } catch (JsonParseException | IllegalStateException | ClassCastException | UnsupportedOperationException e) {
            return Optional.empty();
        }
    }

    /**
     * Quita el bloque de código que envuelve la respuesta, si lo tiene.
     *
     * @param answer respuesta de la IA
     * @return el JSON sin el bloque de código
     */
    static String withoutCodeFence(String answer) {
        String trimmed = answer.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int start = trimmed.indexOf('\n');
        int end = trimmed.lastIndexOf("```");
        return start < 0 || end <= start ? trimmed : trimmed.substring(start + 1, end).trim();
    }

    private static Map<ReportSection, String> sectionsOf(JsonObject root) {
        Map<ReportSection, String> sections = new EnumMap<>(ReportSection.class);
        JsonObject json = root.has("secciones") && root.get("secciones").isJsonObject()
                ? root.getAsJsonObject("secciones") : new JsonObject();
        for (ReportSection section : ReportSection.values()) {
            String text = text(json.get(section.getKey()));
            if (text != null) {
                sections.put(section, text);
            }
        }
        return sections;
    }

    private static List<String> recommendationsOf(JsonObject root) {
        List<String> recommendations = new ArrayList<>();
        if (root.has("recomendaciones") && root.get("recomendaciones").isJsonArray()) {
            JsonArray array = root.getAsJsonArray("recomendaciones");
            for (JsonElement element : array) {
                String text = text(element);
                if (text != null) {
                    recommendations.add(text);
                }
            }
        }
        return recommendations;
    }

    /** Texto limpio de un elemento, o {@code null} si no es un texto con contenido. */
    private static String text(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        String text = element.getAsString().strip();
        return text.isEmpty() ? null : text;
    }
}
