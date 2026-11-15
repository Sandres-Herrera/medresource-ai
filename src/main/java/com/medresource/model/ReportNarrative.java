package com.medresource.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Texto del reporte de fin de simulación: el resumen, una explicación por sección, una explicación por cada
 * recomendación y la conclusión.
 *
 * <p>Los números del reporte los calcula el código; este texto solo los explica. Lo redacta la IA o, si no hay
 * IA disponible, las plantillas; {@link #source()} dice cuál y {@link #note()} explica por qué se usaron las
 * plantillas.</p>
 *
 * @param source          quién lo redactó
 * @param summary         resumen general
 * @param sections        texto de cada sección; puede faltar alguna
 * @param recommendations explicación de cada recomendación, en el orden de las recomendaciones
 * @param conclusion      conclusión
 * @param note            por qué se usaron las plantillas; {@code null} si lo redactó la IA
 */
public record ReportNarrative(NarrativeSource source, String summary, Map<ReportSection, String> sections,
                              List<String> recommendations, String conclusion, String note) {

    /**
     * Valida los datos y guarda copias inmutables.
     *
     * @throws IllegalArgumentException si falta el origen, el resumen, la conclusión o alguna lista, si algún
     *                                  texto está vacío o si la nota no corresponde con el origen
     */
    public ReportNarrative {
        if (source == null || sections == null || recommendations == null) {
            throw new IllegalArgumentException("Faltan datos del texto del reporte.");
        }
        if (isBlank(summary) || isBlank(conclusion)) {
            throw new IllegalArgumentException("El resumen y la conclusión son obligatorios.");
        }
        if (sections.values().stream().anyMatch(ReportNarrative::isBlank)
                || recommendations.stream().anyMatch(ReportNarrative::isBlank)) {
            throw new IllegalArgumentException("Los textos del reporte no pueden estar vacíos.");
        }
        if ((source == NarrativeSource.TEMPLATE) == isBlank(note)) {
            throw new IllegalArgumentException("Solo un texto de plantillas lleva la nota de por qué.");
        }
        sections = Map.copyOf(sections);
        recommendations = List.copyOf(recommendations);
    }

    /**
     * Devuelve el texto de una sección.
     *
     * @param section sección
     * @return texto; vacío si la sección no tiene
     */
    public Optional<String> sectionText(ReportSection section) {
        return Optional.ofNullable(sections.get(section));
    }

    /**
     * Indica si tiene texto para todas las secciones.
     *
     * @return {@code true} si no falta ninguna
     */
    public boolean isComplete() {
        return sections.keySet().containsAll(List.of(ReportSection.values()));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
