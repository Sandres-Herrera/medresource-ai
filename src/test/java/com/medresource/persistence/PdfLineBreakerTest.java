package com.medresource.persistence;

import com.medresource.persistence.PdfLineBreaker.Line;
import com.medresource.persistence.PdfLineBreaker.Run;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del corte del texto en líneas para el PDF.
 */
class PdfLineBreakerTest {

    private static final PDType1Font ROMAN = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final PDType1Font ITALIC = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
    private static final float SIZE = 12;

    @Test
    void aShortTextFitsInOneLine() throws IOException {
        List<Line> lines = PdfLineBreaker.wrap(List.of(new Run("Hola mundo ", ROMAN)), SIZE, 200, 200);

        assertEquals(1, lines.size());
        assertEquals("Hola mundo", lines.getFirst().text());
        assertEquals(PdfLineBreaker.width("Hola mundo", ROMAN, SIZE), lines.getFirst().width(), 0.01);
    }

    @Test
    void aLongTextIsCutBetweenWordsAndEveryLineFits() throws IOException {
        String text = "La espera promedio fue de 58,9 min y fallecieron 13 pacientes en el escenario de catástrofe. "
                .repeat(4);

        List<Line> lines = PdfLineBreaker.wrap(List.of(new Run(text, ROMAN)), SIZE, 150, 250);

        assertTrue(lines.size() > 3);
        assertTrue(lines.getFirst().width() <= 150);
        StringBuilder joined = new StringBuilder();
        for (Line line : lines) {
            assertTrue(line.width() <= 250, line.text());
            assertTrue(!line.text().startsWith(" ") && !line.text().endsWith(" "), line.text());
            joined.append(line.text()).append(' ');
        }
        assertEquals(text.strip(), joined.toString().strip());
    }

    @Test
    void theRunsKeepTheirFontAndPosition() throws IOException {
        List<Line> lines = PdfLineBreaker.wrap(List.of(new Run("Autor. (2020). ", ROMAN),
                new Run("Título del libro", ITALIC), new Run(". Editorial.", ROMAN)), SIZE, 400, 400);

        Line line = lines.getFirst();
        assertEquals(3, line.pieces().size());
        assertSame(ITALIC, line.pieces().get(1).font());
        assertEquals(PdfLineBreaker.width("Autor. (2020). ", ROMAN, SIZE), line.pieces().get(1).x(), 0.01);
        assertEquals("Autor. (2020). Título del libro. Editorial.", line.text());
    }

    @Test
    void aWordLongerThanTheLineIsCutByLetters() throws IOException {
        String link = "https://www.minsalud.gov.co/Normatividad_Nuevo/Resoluci%C3%B3n%205596%20de%202015.pdf";

        List<Line> lines = PdfLineBreaker.wrap(List.of(new Run(link, ROMAN)), SIZE, 120, 120);

        assertTrue(lines.size() > 1);
        assertEquals(link, String.join("", lines.stream().map(Line::text).toList()));
        lines.forEach(line -> assertTrue(line.width() <= 120, line.text()));
    }

    @Test
    void aLineBreakInTheTextStartsANewLine() throws IOException {
        List<Line> lines = PdfLineBreaker.wrap(List.of(new Run("uno\ndos", ROMAN)), SIZE, 300, 300);

        assertEquals(List.of("uno", "dos"), lines.stream().map(Line::text).toList());
    }

    @Test
    void theLettersThePdfFontDoesNotHaveAreReplaced() throws IOException {
        assertEquals("«¿y si…?» — ± ñ", PdfLineBreaker.safe("«¿y si…?» — ± ñ", ROMAN));
        assertEquals(">= 2 -> 3 ?", PdfLineBreaker.safe("≥ 2 → 3 ☺", ROMAN));
    }

    @Test
    void wrongDataIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PdfLineBreaker.wrap(null, SIZE, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> PdfLineBreaker.wrap(List.of(), 0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> PdfLineBreaker.wrap(List.of(), SIZE, 0, 10));
    }
}
