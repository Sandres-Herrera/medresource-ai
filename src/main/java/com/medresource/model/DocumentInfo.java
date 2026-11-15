package com.medresource.model;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Datos de la portada del reporte en normas APA 7.ª edición (versión de estudiante): título, autores, institución,
 * programa, curso, docente y fecha.
 *
 * <p>El título, al menos un autor, la institución y la fecha son obligatorios. El programa, el curso y el docente
 * pueden quedar vacíos: en ese caso esa línea no aparece en la portada.</p>
 *
 * @param title       título del documento
 * @param authors     autores, en el orden en que aparecen
 * @param institution universidad o institución
 * @param program     programa académico; vacío si no se pone
 * @param course      curso; vacío si no se pone
 * @param teacher     docente; vacío si no se pone
 * @param date        fecha del documento
 */
public record DocumentInfo(String title, List<String> authors, String institution, String program, String course,
                           String teacher, LocalDate date) {

    /** Institución que se propone por defecto. */
    public static final String DEFAULT_INSTITUTION = "Universidad Popular del Cesar";
    /** Programa que se propone por defecto. */
    public static final String DEFAULT_PROGRAM = "Ingeniería de Sistemas";
    /** Autores que se proponen por defecto: el equipo del proyecto. */
    public static final List<String> DEFAULT_AUTHORS = List.of("Santiago Herrera", "Omar Gámez");

    private static final String[] MONTHS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
        "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    /**
     * Valida los datos, quita los espacios de los extremos y guarda una copia inmutable de los autores.
     *
     * @throws IllegalArgumentException si falta el título, algún autor, la institución o la fecha
     */
    public DocumentInfo {
        if (isBlank(title) || isBlank(institution) || date == null) {
            throw new IllegalArgumentException("El título, la institución y la fecha son obligatorios.");
        }
        if (authors == null || authors.isEmpty() || authors.stream().anyMatch(DocumentInfo::isBlank)) {
            throw new IllegalArgumentException("Hace falta al menos un autor y ninguno puede estar vacío.");
        }
        title = title.strip();
        authors = authors.stream().map(String::strip).toList();
        institution = institution.strip();
        program = clean(program);
        course = clean(course);
        teacher = clean(teacher);
    }

    /**
     * Crea los datos que se proponen para un reporte nuevo: los autores del equipo, la Universidad Popular del
     * Cesar e Ingeniería de Sistemas, sin curso ni docente.
     *
     * @param title título propuesto
     * @param date  fecha del documento
     * @return datos de la portada
     * @throws IllegalArgumentException si falta el título o la fecha
     */
    public static DocumentInfo draft(String title, LocalDate date) {
        return new DocumentInfo(title, DEFAULT_AUTHORS, DEFAULT_INSTITUTION, DEFAULT_PROGRAM, "", "", date);
    }

    /**
     * Separa una lista de autores escrita en una sola línea, separados por comas o por «y».
     *
     * @param text por ejemplo «Santiago Herrera, Omar Gámez» o «Santiago Herrera y Omar Gámez»
     * @return autores sin espacios de sobra; vacía si el texto está vacío
     */
    public static List<String> parseAuthors(String text) {
        if (isBlank(text)) {
            return List.of();
        }
        return Arrays.stream(text.split(",|\\s+y\\s+"))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .toList();
    }

    /**
     * Escribe los autores como van en la portada en español: separados por comas y el último con «y».
     *
     * @return por ejemplo «Santiago Herrera y Omar Gámez»
     */
    public String authorsText() {
        if (authors.size() == 1) {
            return authors.getFirst();
        }
        return String.join(", ", authors.subList(0, authors.size() - 1)) + " y " + authors.getLast();
    }

    /**
     * Escribe la fecha como se lee en español.
     *
     * @return por ejemplo «15 de noviembre de 2026»
     */
    public String dateText() {
        return date.getDayOfMonth() + " de " + MONTHS[date.getMonthValue() - 1] + " de " + date.getYear();
    }

    /**
     * Devuelve las líneas de la portada debajo del título, sin las que están vacías.
     *
     * <p>El orden es el de APA 7 para estudiantes: autores; programa e institución; curso; docente; fecha.</p>
     *
     * @return líneas de la portada
     */
    public List<String> coverLines() {
        String affiliation = program.isEmpty() ? institution : program + ", " + institution;
        return Stream.of(authorsText(), affiliation, course, teacher, dateText())
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private static String clean(String text) {
        return text == null ? "" : text.strip();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
