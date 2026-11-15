package com.medresource.persistence;

import com.medresource.model.ApaDocument;
import com.medresource.model.ApaDocument.Block;
import com.medresource.model.ApaDocument.Figure;
import com.medresource.model.ApaDocument.Heading;
import com.medresource.model.ApaDocument.Paragraph;
import com.medresource.model.ApaDocument.Table;
import com.medresource.model.ApaReference;
import com.medresource.model.DocumentInfo;
import com.medresource.model.ExportFormat;
import com.medresource.persistence.PdfLineBreaker.Line;
import com.medresource.persistence.PdfLineBreaker.Piece;
import com.medresource.persistence.PdfLineBreaker.Run;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Escribe el reporte como PDF en normas APA 7.ª edición, con Apache PDFBox.
 *
 * <p>Aplica las mismas reglas que el documento de Word ({@link DocxReportWriter}): hoja carta, márgenes de
 * 2,54 cm, Times de 12 puntos a doble espacio, sangría de 1,27 cm, número de página arriba a la derecha, portada,
 * títulos de nivel 1 centrados y de nivel 2 a la izquierda, tablas sin líneas verticales (si una tabla pasa a otra
 * hoja, repite sus encabezados), figuras con su número, título y nota, y referencias en hoja aparte con sangría
 * francesa. Usa la letra Times que traen todos los lectores de PDF, así que no hace falta incluirla en el
 * archivo.</p>
 */
public final class PdfReportWriter implements ReportWriter {

    /** Margen de 2,54 cm (una pulgada), en puntos. */
    public static final float MARGIN = 72;
    /** Tamaño de la letra, en puntos. */
    public static final float FONT_SIZE = 12;
    /** Alto de una línea a doble espacio, en puntos. */
    public static final float LINE_HEIGHT = 24;
    /** Sangría de 1,27 cm (media pulgada), en puntos. */
    public static final float INDENT = 36;

    private static final PDRectangle PAGE = PDRectangle.LETTER;
    private static final float TEXT_WIDTH = PAGE.getWidth() - 2 * MARGIN;
    private static final float TOP = PAGE.getHeight() - MARGIN;
    private static final float BASELINE = FONT_SIZE * 0.9f;
    private static final float HEADER_BASELINE = PAGE.getHeight() - INDENT - BASELINE;
    private static final float TABLE_LINE = 14;
    private static final float CELL_PADDING = 4;
    private static final float RULE_WIDTH = 0.75f;
    private static final float LONG_CELL = 200;
    private static final float MAX_FIGURE_HEIGHT = 500;
    private static final float FIGURE_GAP = 6;

    @Override
    public ExportFormat format() {
        return ExportFormat.PDF;
    }

    @Override
    public void write(ApaDocument document, OutputStream out) throws IOException {
        if (document == null || out == null) {
            throw new IllegalArgumentException("El documento y el destino son obligatorios.");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PDDocument pdf = new PDDocument()) {
            Layout layout = new Layout(pdf);
            layout.cover(document.info());
            layout.newPage();
            layout.centered(document.info().title(), layout.bold);
            for (Block block : document.blocks()) {
                switch (block) {
                    case Heading heading -> layout.heading(heading);
                    case Paragraph paragraph -> layout.paragraph(paragraph);
                    case Table table -> layout.table(table);
                    case Figure figure -> layout.figure(figure);
                }
            }
            layout.references(document.references());
            layout.close();
            numberPages(pdf, layout.roman);
            describe(pdf, document.info());
            pdf.save(bytes);
        }
        out.write(bytes.toByteArray());
        out.flush();
    }

    /** Pone el número de cada hoja arriba a la derecha, en el encabezado. */
    private static void numberPages(PDDocument pdf, PDFont font) throws IOException {
        int number = 0;
        for (PDPage page : pdf.getPages()) {
            String text = String.valueOf(++number);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.beginText();
                stream.setFont(font, FONT_SIZE);
                stream.newLineAtOffset(MARGIN + TEXT_WIDTH - PdfLineBreaker.width(text, font, FONT_SIZE),
                        HEADER_BASELINE);
                stream.showText(text);
                stream.endText();
            }
        }
    }

    /** Guarda el título, los autores, el idioma y la fecha en las propiedades del PDF. */
    private static void describe(PDDocument pdf, DocumentInfo info) {
        PDDocumentInformation properties = pdf.getDocumentInformation();
        properties.setTitle(info.title());
        properties.setAuthor(info.authorsText());
        properties.setSubject("Reporte de fin de simulación");
        properties.setCreator("MedResource AI");
        properties.setCreationDate(GregorianCalendar.from(info.date().atStartOfDay(ZoneId.systemDefault())));
        pdf.getDocumentCatalog().setLanguage("es-CO");
    }

    /**
     * Acomoda el contenido en las hojas: lleva la altura por la que va y pasa a una hoja nueva cuando algo no cabe.
     */
    private static final class Layout {

        private final PDDocument pdf;
        private final PDFont roman = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
        private final PDFont italic = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
        private PDPageContentStream stream;
        private float y;

        Layout(PDDocument pdf) throws IOException {
            this.pdf = pdf;
            newPage();
        }

        void newPage() throws IOException {
            close();
            PDPage page = new PDPage(PAGE);
            pdf.addPage(page);
            stream = new PDPageContentStream(pdf, page);
            y = TOP;
        }

        void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        /** Pasa a una hoja nueva si no queda la altura pedida. */
        void ensure(float height) throws IOException {
            if (y - height < MARGIN && y < TOP) {
                newPage();
            }
        }

        // ---------- partes ----------

        void cover(DocumentInfo info) throws IOException {
            y -= 3 * LINE_HEIGHT;
            centered(info.title(), bold);
            y -= LINE_HEIGHT;
            for (String line : info.coverLines()) {
                centered(line, roman);
            }
        }

        void centered(String text, PDFont font) throws IOException {
            List<Line> lines = PdfLineBreaker.wrap(List.of(new Run(text, font)), FONT_SIZE, TEXT_WIDTH, TEXT_WIDTH);
            for (Line line : lines) {
                write(line, MARGIN + (TEXT_WIDTH - line.width()) / 2, LINE_HEIGHT);
            }
        }

        void heading(Heading heading) throws IOException {
            ensure(3 * LINE_HEIGHT);
            if (heading.level() == 1) {
                centered(heading.text(), bold);
            } else {
                lines(List.of(new Run(heading.text(), bold)), MARGIN, MARGIN);
            }
        }

        void paragraph(Paragraph paragraph) throws IOException {
            List<Run> runs = List.of(new Run(paragraph.text(), roman));
            if (paragraph.style() == ApaDocument.ParagraphStyle.BODY) {
                lines(runs, MARGIN + INDENT, MARGIN);
            } else {
                lines(runs, MARGIN + INDENT / 2, MARGIN + INDENT);
            }
        }

        void table(Table table) throws IOException {
            float[] widths = columnWidths(table);
            List<List<Line>> header = cells(table.headers(), widths);
            List<List<List<Line>>> rows = new ArrayList<>();
            for (List<String> row : table.rows()) {
                rows.add(cells(row, widths));
            }
            float headerHeight = height(header);
            float firstRow = rows.isEmpty() ? 0 : height(rows.getFirst());
            ensure(2 * LINE_HEIGHT + headerHeight + firstRow);
            lines(List.of(new Run(table.label(), bold)), MARGIN, MARGIN);
            lines(List.of(new Run(table.title(), italic)), MARGIN, MARGIN);
            tableTop(header, widths);
            for (List<List<Line>> row : rows) {
                if (y - height(row) < MARGIN) {
                    rule();
                    newPage();
                    tableTop(header, widths);
                }
                row(row, widths, false);
            }
            rule();
            y -= CELL_PADDING;
            note(table.note());
        }

        void figure(Figure figure) throws IOException {
            float width = TEXT_WIDTH;
            float height = width * figure.height() / figure.width();
            if (height > MAX_FIGURE_HEIGHT) {
                width = width * MAX_FIGURE_HEIGHT / height;
                height = MAX_FIGURE_HEIGHT;
            }
            ensure(2 * LINE_HEIGHT + height + FIGURE_GAP);
            lines(List.of(new Run(figure.label(), bold)), MARGIN, MARGIN);
            lines(List.of(new Run(figure.title(), italic)), MARGIN, MARGIN);
            PDImageXObject image = PDImageXObject.createFromByteArray(pdf, figure.png(), figure.label());
            stream.drawImage(image, MARGIN, y - height, width, height);
            y -= height + FIGURE_GAP;
            note(figure.note());
        }

        void note(String note) throws IOException {
            if (!note.isEmpty()) {
                lines(List.of(new Run("Nota.", italic), new Run(" " + note, roman)), MARGIN, MARGIN);
            }
        }

        void references(List<ApaReference> references) throws IOException {
            if (references.isEmpty()) {
                return;
            }
            newPage();
            centered("Referencias", bold);
            for (ApaReference reference : references) {
                lines(List.of(new Run(reference.lead(), roman), new Run(reference.title(), italic),
                        new Run(reference.tail(), roman)), MARGIN, MARGIN + INDENT);
            }
        }

        // ---------- tablas ----------

        /**
         * Reparte el ancho entre las columnas: cada una recibe al menos su palabra más larga y el resto se reparte
         * según el ancho de sus textos.
         */
        float[] columnWidths(Table table) throws IOException {
            int columns = table.headers().size();
            float[] minimum = new float[columns];
            float[] wanted = new float[columns];
            for (int i = 0; i < columns; i++) {
                List<String> texts = new ArrayList<>(List.of(table.headers().get(i)));
                for (List<String> row : table.rows()) {
                    texts.add(row.get(i));
                }
                for (String text : texts) {
                    String safe = PdfLineBreaker.safe(text, roman);
                    for (String word : safe.split("\\s+")) {
                        minimum[i] = Math.max(minimum[i], PdfLineBreaker.width(word, roman, FONT_SIZE));
                    }
                    wanted[i] = Math.max(wanted[i], Math.min(LONG_CELL,
                            PdfLineBreaker.width(safe, roman, FONT_SIZE)));
                }
                minimum[i] += 2 * CELL_PADDING;
                wanted[i] = Math.max(minimum[i], wanted[i] + 2 * CELL_PADDING);
            }
            float minimumTotal = 0;
            float extraTotal = 0;
            for (int i = 0; i < columns; i++) {
                minimumTotal += minimum[i];
                extraTotal += wanted[i] - minimum[i];
            }
            float free = TEXT_WIDTH - minimumTotal;
            float[] widths = new float[columns];
            for (int i = 0; i < columns; i++) {
                widths[i] = free <= 0 ? TEXT_WIDTH * minimum[i] / minimumTotal
                        : minimum[i] + (extraTotal == 0 ? free / columns
                        : free * (wanted[i] - minimum[i]) / extraTotal);
            }
            return widths;
        }

        private List<List<Line>> cells(List<String> texts, float[] widths) throws IOException {
            List<List<Line>> cells = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                float width = Math.max(1, widths[i] - 2 * CELL_PADDING);
                cells.add(PdfLineBreaker.wrap(List.of(new Run(texts.get(i), roman)), FONT_SIZE, width, width));
            }
            return cells;
        }

        private static float height(List<List<Line>> row) {
            int lines = row.stream().mapToInt(List::size).max().orElse(1);
            return lines * TABLE_LINE + 2 * CELL_PADDING;
        }

        private void tableTop(List<List<Line>> header, float[] widths) throws IOException {
            rule();
            row(header, widths, true);
            rule();
        }

        /** Dibuja una fila: la primera columna a la izquierda y las demás centradas; los encabezados, centrados. */
        private void row(List<List<Line>> cells, float[] widths, boolean header) throws IOException {
            float top = y;
            float x = MARGIN;
            for (int i = 0; i < cells.size(); i++) {
                float lineTop = top - CELL_PADDING;
                for (Line line : cells.get(i)) {
                    float lineX = header || i > 0 ? x + (widths[i] - line.width()) / 2 : x + CELL_PADDING;
                    draw(line, lineX, lineTop - BASELINE + 1);
                    lineTop -= TABLE_LINE;
                }
                x += widths[i];
            }
            y = top - height(cells);
        }

        private void rule() throws IOException {
            stream.setLineWidth(RULE_WIDTH);
            stream.moveTo(MARGIN, y);
            stream.lineTo(MARGIN + TEXT_WIDTH, y);
            stream.stroke();
        }

        // ---------- líneas ----------

        /** Parte los tramos en líneas y las escribe a doble espacio, con la primera línea en otra posición. */
        private void lines(List<Run> runs, float firstX, float otherX) throws IOException {
            List<Line> lines = PdfLineBreaker.wrap(runs, FONT_SIZE, MARGIN + TEXT_WIDTH - firstX,
                    MARGIN + TEXT_WIDTH - otherX);
            for (int i = 0; i < lines.size(); i++) {
                write(lines.get(i), i == 0 ? firstX : otherX, LINE_HEIGHT);
            }
        }

        private void write(Line line, float x, float height) throws IOException {
            if (y - height < MARGIN) {
                newPage();
            }
            draw(line, x, y - BASELINE);
            y -= height;
        }

        private void draw(Line line, float x, float baseline) throws IOException {
            for (Piece piece : line.pieces()) {
                stream.beginText();
                stream.setFont(piece.font(), FONT_SIZE);
                stream.newLineAtOffset(x + piece.x(), baseline);
                stream.showText(piece.text());
                stream.endText();
            }
        }
    }
}
