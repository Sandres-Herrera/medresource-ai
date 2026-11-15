package com.medresource.model;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Formatos en los que se puede descargar el reporte de fin de simulación.
 */
public enum ExportFormat {

    /** Documento de Word, para seguir editándolo. */
    WORD("Word (.docx)", "Para editarlo o completarlo", "docx"),
    /** PDF, para imprimirlo o enviarlo tal como está. */
    PDF("PDF", "Para imprimirlo o enviarlo", "pdf");

    private final String label;
    private final String detail;
    private final String extension;

    ExportFormat(String label, String detail, String extension) {
        this.label = label;
        this.detail = detail;
        this.extension = extension;
    }

    /**
     * Devuelve el nombre que ve el usuario.
     *
     * @return por ejemplo «Word (.docx)»
     */
    public String getLabel() {
        return label;
    }

    /**
     * Devuelve para qué sirve este formato, en una frase corta.
     *
     * @return por ejemplo «Para imprimirlo o enviarlo»
     */
    public String getDetail() {
        return detail;
    }

    /**
     * Devuelve la extensión del archivo, sin el punto.
     *
     * @return «docx» o «pdf»
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Propone el nombre del archivo del reporte: escenario sin tildes, semilla y fecha.
     *
     * @param scenario escenario de la simulación
     * @param seed     semilla de la simulación
     * @param date     fecha del reporte
     * @return por ejemplo «Reporte_catastrofe_semilla7_2026-11-15.docx»
     * @throws IllegalArgumentException si falta el escenario o la fecha
     */
    public String fileName(Scenario scenario, long seed, LocalDate date) {
        if (scenario == null || date == null) {
            throw new IllegalArgumentException("El escenario y la fecha son obligatorios.");
        }
        String plain = Normalizer.normalize(scenario.getDisplayName(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");
        return "Reporte_" + plain + "_semilla" + seed + "_" + date + "." + extension;
    }

    /**
     * Pone la extensión de este formato a un archivo que no la tiene.
     *
     * @param name nombre que escribió el usuario
     * @return el mismo nombre si ya termina en la extensión (sin importar mayúsculas); si no, con la extensión
     * @throws IllegalArgumentException si el nombre está vacío
     */
    public String withExtension(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del archivo es obligatorio.");
        }
        String suffix = "." + extension;
        return name.toLowerCase(Locale.ROOT).endsWith(suffix) ? name : name + suffix;
    }
}
