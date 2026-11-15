package com.medresource.persistence;

import com.medresource.model.ApaDocument;
import com.medresource.model.ApaDocument.Table;
import com.medresource.model.ApaReference;
import com.medresource.model.ExportFormat;
import com.medresource.model.NarrativeSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del PDF del reporte: se vuelve a abrir con PDFBox y se lee su texto hoja por hoja.
 */
class PdfReportWriterTest {

    @Test
    void theCoverTheBodyAndTheReferencesGoInTheirOwnPages() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(write(DocumentFixtures.document()))) {
            List<String> pages = pageTexts(pdf);

            assertEquals(3, pages.size());
            assertTrue(pages.get(0).contains(DocumentFixtures.TITLE));
            for (String line : DocumentFixtures.info().coverLines()) {
                assertTrue(pages.get(0).contains(line), line);
            }
            assertTrue(pages.get(1).contains(DocumentFixtures.TITLE));
            assertTrue(pages.get(2).contains("Referencias"));
            assertTrue(pages.get(2).contains("Herrera, S. y Gámez, O. (2026)."));
        }
    }

    @Test
    void theBodyHasTheTextTheTableAndTheFigure() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(write(DocumentFixtures.document()))) {
            String body = pageTexts(pdf).get(1);

            for (String text : List.of("Resumen de la simulación", DocumentFixtures.BODY, "Tabla 1",
                    DocumentFixtures.TABLE_TITLE, "Indicador", "Fallecidos", "13 (13 %)", "Nota. Cifras del simulador.",
                    "Figura 1", DocumentFixtures.FIGURE_TITLE, DocumentFixtures.ITEM)) {
                assertTrue(body.contains(text), text);
            }
            assertTrue(body.contains("Símbolos <&> «¿y si…?» — ± 2 >= 1"));
            assertEquals(1, images(pdf.getPage(1)));
        }
    }

    @Test
    void everyPageHasItsNumberAtTheTop() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(write(DocumentFixtures.longDocument(40)))) {
            List<String> pages = pageTexts(pdf);

            assertTrue(pages.size() > 4);
            for (int i = 0; i < pages.size(); i++) {
                assertEquals(String.valueOf(i + 1), pages.get(i).strip().lines().findFirst().orElse(""));
            }
            assertTrue(pages.getLast().contains("Referencias"));
            assertTrue(pages.getLast().contains("Google. (s.f.)."));
        }
    }

    @Test
    void aLongTableRepeatsItsHeadersOnTheNextPage() throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i <= 70; i++) {
            rows.add(List.of("Paciente " + i, String.valueOf(i)));
        }
        ApaDocument document = new ApaDocument(DocumentFixtures.info(),
                List.of(new Table(1, "Muchos pacientes", List.of("Encabezado A", "Encabezado B"), rows, "")),
                ApaReference.forReport(NarrativeSource.TEMPLATE));

        try (PDDocument pdf = Loader.loadPDF(write(document))) {
            List<String> pages = pageTexts(pdf);

            long withHeader = pages.stream().filter(page -> page.contains("Encabezado A")).count();
            assertTrue(withHeader >= 2);
            assertTrue(String.join("\n", pages).contains("Paciente 70"));
        }
    }

    @Test
    void savesTheTitleTheAuthorsAndTheLanguage() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(write(DocumentFixtures.document()))) {
            assertEquals(DocumentFixtures.TITLE, pdf.getDocumentInformation().getTitle());
            assertEquals("Santiago Herrera y Omar Gámez", pdf.getDocumentInformation().getAuthor());
            assertEquals("es-CO", pdf.getDocumentCatalog().getLanguage());
        }
    }

    @Test
    void writesThePdfWithoutClosingTheStream() throws IOException {
        boolean[] closed = {false};
        OutputStream out = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };

        new PdfReportWriter().write(DocumentFixtures.document(), out);

        assertEquals(ExportFormat.PDF, new PdfReportWriter().format());
        assertFalse(closed[0]);
        assertThrows(IllegalArgumentException.class, () -> new PdfReportWriter().write(null, out));
        assertThrows(IllegalArgumentException.class,
                () -> new PdfReportWriter().write(DocumentFixtures.document(), (OutputStream) null));
    }

    private static byte[] write(ApaDocument document) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PdfReportWriter().write(document, bytes);
        return bytes.toByteArray();
    }

    private static List<String> pageTexts(PDDocument pdf) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            pages.add(stripper.getText(pdf).replaceAll("\\s+\\n", "\n").replace("\r", ""));
        }
        return pages;
    }

    private static int images(PDPage page) throws IOException {
        int images = 0;
        for (COSName name : page.getResources().getXObjectNames()) {
            if (page.getResources().getXObject(name) instanceof PDImageXObject) {
                images++;
            }
        }
        return images;
    }
}
