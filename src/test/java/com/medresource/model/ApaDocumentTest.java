package com.medresource.model;

import com.medresource.model.ApaDocument.Block;
import com.medresource.model.ApaDocument.Figure;
import com.medresource.model.ApaDocument.Heading;
import com.medresource.model.ApaDocument.Paragraph;
import com.medresource.model.ApaDocument.ParagraphStyle;
import com.medresource.model.ApaDocument.Table;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del documento en normas APA.
 */
class ApaDocumentTest {

    private static final DocumentInfo INFO = DocumentInfo.draft("Reporte", LocalDate.of(2026, 11, 15));
    private static final byte[] PNG = {1, 2, 3};

    @Test
    void keepsTheBlocksInOrderAndFindsTheTablesAndFigures() {
        Table table = table(1);
        Figure figure = figure(1);
        List<Block> blocks = List.of(new Heading(1, "Resumen"), Paragraph.body("Texto."), table, figure);

        ApaDocument document = new ApaDocument(INFO, blocks, List.of(ApaReference.SIMULATOR));

        assertEquals(blocks, document.blocks());
        assertEquals(List.of(table), document.tables());
        assertEquals(List.of(figure), document.figures());
        assertEquals("Tabla 1", table.label());
        assertEquals("Figura 1", figure.label());
    }

    @Test
    void theListsCannotBeChangedFromOutside() {
        List<Block> blocks = new ArrayList<>(List.of(Paragraph.body("Texto.")));
        ApaDocument document = new ApaDocument(INFO, blocks, List.of());

        blocks.add(Paragraph.body("Otro."));

        assertEquals(1, document.blocks().size());
        assertThrows(UnsupportedOperationException.class, () -> document.blocks().add(Paragraph.body("Más.")));
    }

    @Test
    void theParagraphsAreNormalOrListItems() {
        assertEquals(ParagraphStyle.BODY, Paragraph.body("Texto.").style());
        assertEquals(ParagraphStyle.ITEM, Paragraph.item("1. Uno").style());
    }

    @Test
    void theTablesAndFiguresMustBeNumberedInOrder() {
        assertThrows(IllegalArgumentException.class,
                () -> new ApaDocument(INFO, List.of(table(2)), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ApaDocument(INFO, List.of(figure(1), figure(1)), List.of()));
        assertEquals(2, new ApaDocument(INFO, List.of(table(1), figure(1), table(2)), List.of()).tables().size());
    }

    @Test
    void theImageOfAFigureCannotBeChangedFromOutside() {
        byte[] png = {1, 2, 3};
        Figure figure = new Figure(1, "Gráfica", png, 10, 5, null);

        png[0] = 9;
        figure.png()[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, figure.png());
        assertEquals("", figure.note());
        assertEquals(figure, new Figure(1, "Gráfica", new byte[]{1, 2, 3}, 10, 5, ""));
        assertNotEquals(figure, new Figure(1, "Gráfica", new byte[]{1, 2, 4}, 10, 5, ""));
    }

    @Test
    void wrongHeadingsAndParagraphsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Heading(3, "Título"));
        assertThrows(IllegalArgumentException.class, () -> new Heading(1, " "));
        assertThrows(IllegalArgumentException.class, () -> Paragraph.body(""));
        assertThrows(IllegalArgumentException.class, () -> new Paragraph("Texto", null));
    }

    @Test
    void wrongTablesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Table(0, "T", List.of("A"), List.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> new Table(1, " ", List.of("A"), List.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> new Table(1, "T", List.of(), List.of(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> new Table(1, "T", List.of("A", "B"), List.of(List.of("solo uno")), ""));
    }

    @Test
    void wrongFiguresAndDocumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Figure(1, "F", new byte[0], 10, 5, ""));
        assertThrows(IllegalArgumentException.class, () -> new Figure(1, "F", PNG, 0, 5, ""));
        assertThrows(IllegalArgumentException.class, () -> new Figure(1, "", PNG, 10, 5, ""));
        assertThrows(IllegalArgumentException.class, () -> new ApaDocument(null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ApaDocument(INFO, null, List.of()));
    }

    private static Table table(int number) {
        return new Table(number, "Indicadores", List.of("Indicador", "Valor"), List.of(List.of("Pacientes", "100")),
                "Nota de la tabla.");
    }

    private static Figure figure(int number) {
        return new Figure(number, "Gráfica", PNG, 10, 5, "Nota de la figura.");
    }
}
