package com.medresource.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Referencia bibliográfica en normas APA 7.ª edición, con la cita que le corresponde dentro del texto.
 *
 * <p>Las referencias del reporte las pone el código desde una lista fija; la IA nunca cita fuentes. La referencia se
 * guarda en tres partes porque el título va en cursiva: {@code lead} (autor y fecha), {@code title} y
 * {@code tail} (lo que sigue al título).</p>
 *
 * @param citation cita dentro del texto, sin paréntesis; por ejemplo «Herrera y Gámez, 2026»
 * @param lead     autor y fecha, con el punto y el espacio finales
 * @param title    título, que va en cursiva
 * @param tail     lo que sigue al título: descripción, editorial o enlace
 */
public record ApaReference(String citation, String lead, String title, String tail) {

    /** Manual de publicaciones de la APA, 7.ª edición. */
    public static final ApaReference APA_MANUAL = new ApaReference("American Psychological Association, 2020",
            "American Psychological Association. (2020). ",
            "Publication manual of the American Psychological Association",
            " (7.ª ed.). https://doi.org/10.1037/0000165-000");
    /** Documentación de la API de Gemini, la IA que redacta el texto del reporte. */
    public static final ApaReference GEMINI = new ApaReference("Google, s.f.",
            "Google. (s.f.). ", "Gemini API", " [Documentación]. https://ai.google.dev/gemini-api/docs");
    /** El simulador MedResource AI. */
    public static final ApaReference SIMULATOR = new ApaReference("Herrera y Gámez, 2026",
            "Herrera, S. y Gámez, O. (2026). ", "MedResource AI: simulador de recursos hospitalarios",
            " [Software]. GitHub. https://github.com/Sandres-Herrera/medresource-ai");
    /** Resolución 5596 de 2015 del Ministerio de Salud y Protección Social, sobre el triage en Colombia. */
    public static final ApaReference TRIAGE_RESOLUTION = new ApaReference(
            "Ministerio de Salud y Protección Social, 2015",
            "Ministerio de Salud y Protección Social. (2015). ",
            "Resolución 5596 de 2015, por la cual se definen los criterios técnicos para el Sistema de Selección "
                    + "y Clasificación de pacientes en los servicios de urgencias «Triage»",
            ". https://www.minsalud.gov.co/Normatividad_Nuevo/Resoluci%C3%B3n%205596%20de%202015.pdf");

    /**
     * Valida que ninguna parte esté vacía.
     *
     * @throws IllegalArgumentException si falta la cita, el autor y la fecha o el título
     */
    public ApaReference {
        if (isBlank(citation) || isBlank(lead) || isBlank(title) || tail == null) {
            throw new IllegalArgumentException("La cita, el autor, la fecha y el título son obligatorios.");
        }
    }

    /**
     * Elige las referencias del reporte, en orden alfabético como pide APA.
     *
     * <p>Siempre van el manual de la APA, el simulador y la resolución del triage; la documentación de Gemini solo
     * va si el texto lo redactó la IA, porque solo entonces se cita.</p>
     *
     * @param source quién redactó el texto del reporte
     * @return referencias en orden alfabético
     * @throws IllegalArgumentException si falta el origen del texto
     */
    public static List<ApaReference> forReport(NarrativeSource source) {
        if (source == null) {
            throw new IllegalArgumentException("El origen del texto es obligatorio.");
        }
        List<ApaReference> references = new ArrayList<>(List.of(APA_MANUAL, SIMULATOR, TRIAGE_RESOLUTION));
        if (source == NarrativeSource.AI) {
            references.add(GEMINI);
        }
        references.sort(Comparator.comparing(ApaReference::lead, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(references);
    }

    /**
     * Devuelve la cita entre paréntesis, como va dentro del texto.
     *
     * @return por ejemplo «(Herrera y Gámez, 2026)»
     */
    public String parenthetical() {
        return "(" + citation + ")";
    }

    /**
     * Escribe la referencia completa en una sola línea, sin cursivas.
     *
     * @return autor, fecha, título y lo que sigue
     */
    public String text() {
        return lead + title + tail;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
