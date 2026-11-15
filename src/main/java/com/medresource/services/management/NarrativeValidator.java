package com.medresource.services.management;

import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Revisa que el texto de la IA no traiga cifras inventadas.
 *
 * <p>Cada frase se revisa por separado: si trae una cifra u hora que no está en los hechos, la frase se quita.
 * Los códigos como {@code P024} no cuentan como cifras, y los enteros de 0 a {@value #SMALL_NUMBER_LIMIT} se
 * aceptan siempre porque se usan al contar («2 cirujanos», «3 recomendaciones»), igual que el
 * {@value #PER_HUNDRED} de «por cada 100 pacientes». Si el resumen o la conclusión
 * quedan vacíos, el texto no sirve; si alguna recomendación queda vacía, se quitan todas para no desordenarlas
 * (las pone después la plantilla).</p>
 */
public final class NarrativeValidator {

    /** Enteros que se aceptan aunque no estén en los hechos. */
    public static final int SMALL_NUMBER_LIMIT = 10;

    /** Base de las tasas («por cada 100 pacientes»), que se acepta siempre. */
    public static final int PER_HUNDRED = 100;

    private NarrativeValidator() {
    }

    /**
     * Quita las frases con cifras que no están en los hechos.
     *
     * @param narrative texto de la IA
     * @param allowed   cifras y horas de los hechos ({@link ReportFacts#allowedNumbers})
     * @return el texto sin esas frases; vacío si el resumen o la conclusión quedaron sin ninguna frase
     * @throws IllegalArgumentException si falta el texto o las cifras
     */
    public static Optional<ReportNarrative> clean(ReportNarrative narrative, Set<String> allowed) {
        if (narrative == null || allowed == null) {
            throw new IllegalArgumentException("El texto y las cifras permitidas son obligatorios.");
        }
        String summary = cleanText(narrative.summary(), allowed);
        String conclusion = cleanText(narrative.conclusion(), allowed);
        if (summary.isEmpty() || conclusion.isEmpty()) {
            return Optional.empty();
        }
        Map<ReportSection, String> sections = new EnumMap<>(ReportSection.class);
        narrative.sections().forEach((section, text) -> {
            String cleaned = cleanText(text, allowed);
            if (!cleaned.isEmpty()) {
                sections.put(section, cleaned);
            }
        });
        List<String> recommendations = new ArrayList<>();
        for (String text : narrative.recommendations()) {
            String cleaned = cleanText(text, allowed);
            if (cleaned.isEmpty()) {
                recommendations.clear();
                break;
            }
            recommendations.add(cleaned);
        }
        return Optional.of(new ReportNarrative(narrative.source(), summary, sections, recommendations, conclusion,
                narrative.note()));
    }

    /**
     * Indica si todas las cifras y horas de una frase están permitidas.
     *
     * @param sentence frase
     * @param allowed  cifras y horas de los hechos
     * @return {@code true} si la frase no inventa ninguna cifra
     */
    public static boolean isVerified(String sentence, Set<String> allowed) {
        String text = ReportFacts.CODE.matcher(sentence).replaceAll(" ");
        Matcher clock = ReportFacts.CLOCK.matcher(text);
        while (clock.find()) {
            if (!allowed.contains(clock.group())) {
                return false;
            }
        }
        Matcher number = ReportFacts.NUMBER.matcher(ReportFacts.CLOCK.matcher(text).replaceAll(" "));
        while (number.find()) {
            String canonical = ReportFacts.canonical(number.group());
            if (!allowed.contains(canonical) && !isAlwaysAllowed(canonical)) {
                return false;
            }
        }
        return true;
    }

    private static String cleanText(String text, Set<String> allowed) {
        List<String> kept = new ArrayList<>();
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            if (!sentence.isBlank() && isVerified(sentence, allowed)) {
                kept.add(sentence.strip());
            }
        }
        return String.join(" ", kept);
    }

    private static boolean isAlwaysAllowed(String canonical) {
        if (canonical.contains(".")) {
            return false;
        }
        BigDecimal value = new BigDecimal(canonical);
        return value.compareTo(BigDecimal.valueOf(SMALL_NUMBER_LIMIT)) <= 0
                || value.compareTo(BigDecimal.valueOf(PER_HUNDRED)) == 0;
    }
}
