package com.medresource.services.management;

import com.google.gson.JsonObject;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import com.medresource.model.SimulationReport;
import com.medresource.model.WhatIfReport;
import com.medresource.persistence.AiClient;
import com.medresource.persistence.AiClientException;
import com.medresource.persistence.AiConfig;
import com.medresource.persistence.DataAccessException;
import com.medresource.persistence.GeminiClient;
import com.medresource.persistence.NarrativeCache;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Redacta el texto del reporte de fin de simulación: con la IA si se puede y, si no, con las plantillas.
 *
 * <p>Pasos: arma los hechos ({@link ReportFacts}) y las instrucciones ({@link NarrativePrompt}); busca la
 * respuesta en la caché y, si no está, se la pide a la IA (una sola llamada) y anota cuánto tardó; lee la
 * respuesta ({@link NarrativeParser}); quita las frases con cifras inventadas ({@link NarrativeValidator});
 * completa con las plantillas las secciones que falten y, si las explicaciones no coinciden con las
 * recomendaciones, usa las de las plantillas. La respuesta solo se guarda en la caché si sirvió.</p>
 *
 * <p>Si no hay IA, si la IA falla o si su respuesta no sirve, el reporte sale igual, redactado con
 * {@link TemplateNarrator}, y su nota dice por qué. Los problemas de la caché nunca impiden redactar.</p>
 */
public class ReportNarrator {

    /** Nota cuando no hay clave de la IA. */
    public static final String NO_KEY_NOTE = "No hay clave de la IA (ai.properties o GEMINI_API_KEY)";

    /** Nota cuando la IA no respondió con el JSON pedido. */
    public static final String BAD_FORMAT_NOTE = "La respuesta de la IA no tenía el formato pedido";

    /** Nota cuando la IA escribió cifras que no están en los hechos en el resumen o la conclusión. */
    public static final String INVENTED_NUMBERS_NOTE = "La IA escribió cifras que no están en los datos";

    private final AiClient client;
    private final NarrativeCache cache;

    /**
     * Crea el redactor.
     *
     * @param client IA que redacta; {@code null} para usar siempre las plantillas
     * @param cache  caché de respuestas; {@code null} para no usar caché
     */
    public ReportNarrator(AiClient client, NarrativeCache cache) {
        this.client = client;
        this.cache = cache;
    }

    /**
     * Crea el redactor con Gemini si hay clave ({@link AiConfig#load()}) y con la caché en
     * {@value NarrativeCache#DEFAULT_DIRECTORY}.
     *
     * @return redactor listo para usar
     */
    public static ReportNarrator fromConfiguration() {
        AiClient client;
        try {
            client = AiConfig.load().map(GeminiClient::new).orElse(null);
        } catch (DataAccessException | IllegalArgumentException e) {
            client = null;
        }
        return new ReportNarrator(client, NarrativeCache.inDefaultDirectory());
    }

    /**
     * Indica si hay IA para redactar.
     *
     * @return {@code true} si hay un cliente de IA
     */
    public boolean usesAi() {
        return client != null;
    }

    /**
     * Estima cuánto tardará la redacción, para la barra de progreso.
     *
     * @param report reporte con los números
     * @param whatIf análisis «¿y si…?»; {@code null} si no se hizo
     * @return milisegundos: 0 si no hay IA o si la respuesta ya está en la caché; si no, el promedio de las
     *         últimas llamadas
     */
    public long estimatedMillis(SimulationReport report, WhatIfReport whatIf) {
        if (client == null) {
            return 0;
        }
        String key = keyOf(promptOf(report, whatIf));
        if (cached(key).isPresent()) {
            return 0;
        }
        try {
            return cache == null ? NarrativeCache.DEFAULT_CALL_MILLIS : cache.averageCallMillis();
        } catch (DataAccessException e) {
            return NarrativeCache.DEFAULT_CALL_MILLIS;
        }
    }

    /**
     * Redacta el texto del reporte.
     *
     * @param report reporte con los números
     * @param whatIf análisis «¿y si…?»; {@code null} si no se hizo
     * @return texto completo; de la IA si se pudo, si no de las plantillas con la nota de por qué
     * @throws IllegalArgumentException si falta el reporte
     */
    public ReportNarrative narrate(SimulationReport report, WhatIfReport whatIf) {
        if (report == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        if (client == null) {
            return TemplateNarrator.write(report, whatIf, NO_KEY_NOTE);
        }
        JsonObject facts = ReportFacts.of(report, whatIf);
        String prompt = NarrativePrompt.of(facts, recommendationsOf(whatIf));
        String key = keyOf(prompt);
        Optional<String> stored = cached(key);
        String answer;
        if (stored.isPresent()) {
            answer = stored.get();
        } else {
            try {
                long start = System.nanoTime();
                answer = client.generate(prompt);
                record((System.nanoTime() - start) / 1_000_000);
            } catch (AiClientException e) {
                return TemplateNarrator.write(report, whatIf, e.getMessage());
            }
        }
        Optional<ReportNarrative> parsed = NarrativeParser.parse(answer);
        if (parsed.isEmpty()) {
            return TemplateNarrator.write(report, whatIf, BAD_FORMAT_NOTE);
        }
        Set<String> allowed = ReportFacts.allowedNumbers(facts);
        Optional<ReportNarrative> cleaned = NarrativeValidator.clean(parsed.get(), allowed);
        if (cleaned.isEmpty()) {
            return TemplateNarrator.write(report, whatIf, INVENTED_NUMBERS_NOTE);
        }
        if (stored.isEmpty()) {
            save(key, answer);
        }
        return completed(cleaned.get(), report, whatIf);
    }

    /** Completa con las plantillas las secciones que faltan y, si no coinciden, las recomendaciones. */
    private static ReportNarrative completed(ReportNarrative narrative, SimulationReport report, WhatIfReport whatIf) {
        ReportNarrative template = TemplateNarrator.write(report, whatIf, NO_KEY_NOTE);
        Map<ReportSection, String> sections = new EnumMap<>(ReportSection.class);
        sections.putAll(template.sections());
        sections.putAll(narrative.sections());
        List<String> recommendations = narrative.recommendations().size() == recommendationsOf(whatIf)
                ? narrative.recommendations() : template.recommendations();
        return new ReportNarrative(narrative.source(), narrative.summary(), sections, recommendations,
                narrative.conclusion(), null);
    }

    private static int recommendationsOf(WhatIfReport whatIf) {
        return whatIf == null ? 0 : whatIf.recommendations().size();
    }

    private static String promptOf(SimulationReport report, WhatIfReport whatIf) {
        return NarrativePrompt.of(ReportFacts.of(report, whatIf), recommendationsOf(whatIf));
    }

    private String keyOf(String prompt) {
        return NarrativeCache.keyFor(client.modelName(), prompt);
    }

    private Optional<String> cached(String key) {
        try {
            return cache == null ? Optional.empty() : cache.find(key);
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }

    private void save(String key, String answer) {
        try {
            if (cache != null) {
                cache.save(key, answer);
            }
        } catch (DataAccessException e) {
            // Sin caché el reporte sale igual; la próxima vez se vuelve a pedir.
        }
    }

    private void record(long millis) {
        try {
            if (cache != null) {
                cache.recordCallMillis(millis);
            }
        } catch (DataAccessException e) {
            // El tiempo solo sirve para estimar; si no se guarda, se usa el valor por defecto.
        }
    }
}
