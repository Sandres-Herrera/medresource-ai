package com.medresource.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los datos de la portada del reporte.
 */
class DocumentInfoTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 15);

    @Test
    void theDraftHasTheTeamAndTheUniversity() {
        DocumentInfo info = DocumentInfo.draft("Reporte", DATE);

        assertEquals(List.of("Santiago Herrera", "Omar Gámez"), info.authors());
        assertEquals("Universidad Popular del Cesar", info.institution());
        assertEquals("Ingeniería de Sistemas", info.program());
        assertEquals("", info.course());
        assertEquals("", info.teacher());
    }

    @Test
    void theAuthorsAreWrittenWithCommasAndAnY() {
        assertEquals("Ana", info(List.of("Ana")).authorsText());
        assertEquals("Ana y Luis", info(List.of("Ana", "Luis")).authorsText());
        assertEquals("Ana, Luis y Marta", info(List.of("Ana", "Luis", "Marta")).authorsText());
    }

    @Test
    void theAuthorsCanBeReadFromOneLine() {
        assertEquals(List.of("Ana Pérez", "Luis Díaz"), DocumentInfo.parseAuthors(" Ana Pérez , Luis Díaz "));
        assertEquals(List.of("Ana", "Luis", "Marta"), DocumentInfo.parseAuthors("Ana, Luis y Marta"));
        assertEquals(List.of("Yolanda Ruiz"), DocumentInfo.parseAuthors("Yolanda Ruiz"));
        assertTrue(DocumentInfo.parseAuthors("  ").isEmpty());
        assertTrue(DocumentInfo.parseAuthors(null).isEmpty());
    }

    @Test
    void theDateIsWrittenInSpanish() {
        assertEquals("15 de noviembre de 2026", DocumentInfo.draft("Reporte", DATE).dateText());
        assertEquals("1 de enero de 2027", DocumentInfo.draft("Reporte", LocalDate.of(2027, 1, 1)).dateText());
    }

    @Test
    void theCoverSkipsTheEmptyLines() {
        DocumentInfo full = new DocumentInfo("Reporte", List.of("Ana"), "Universidad", "Sistemas", "Programación III",
                "Docente Uno", DATE);
        DocumentInfo minimal = new DocumentInfo("Reporte", List.of("Ana"), "Universidad", " ", null, "", DATE);

        assertEquals(List.of("Ana", "Sistemas, Universidad", "Programación III", "Docente Uno",
                "15 de noviembre de 2026"), full.coverLines());
        assertEquals(List.of("Ana", "Universidad", "15 de noviembre de 2026"), minimal.coverLines());
    }

    @Test
    void theTextsAreTrimmed() {
        DocumentInfo info = new DocumentInfo("  Reporte  ", List.of(" Ana "), " Universidad ", " Sistemas ",
                " Curso ", " Docente ", DATE);

        assertEquals("Reporte", info.title());
        assertEquals(List.of("Ana"), info.authors());
        assertEquals("Universidad", info.institution());
        assertEquals("Sistemas", info.program());
        assertEquals("Curso", info.course());
        assertEquals("Docente", info.teacher());
    }

    @Test
    void wrongDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DocumentInfo.draft(" ", DATE));
        assertThrows(IllegalArgumentException.class, () -> DocumentInfo.draft("Reporte", null));
        assertThrows(IllegalArgumentException.class, () -> info(List.of()));
        assertThrows(IllegalArgumentException.class, () -> info(List.of("Ana", " ")));
        assertThrows(IllegalArgumentException.class, () -> info(null));
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentInfo("Reporte", List.of("Ana"), "", "", "", "", DATE));
    }

    private static DocumentInfo info(List<String> authors) {
        return new DocumentInfo("Reporte", authors, "Universidad", "", "", "", DATE);
    }
}
