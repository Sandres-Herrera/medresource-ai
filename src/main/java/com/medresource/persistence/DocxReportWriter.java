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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Escribe el reporte como documento de Word (.docx) en normas APA 7.ª edición, sin librerías externas.
 *
 * <p>Un archivo .docx es un zip con partes XML (formato Office Open XML): el cuerpo ({@code word/document.xml}),
 * los estilos, el encabezado con el número de página, las imágenes y los datos del documento. Esta clase arma esas
 * partes a mano. Formato APA: hoja carta, márgenes de 2,54 cm, Times New Roman de 12 puntos, doble espacio,
 * sangría de 1,27 cm en la primera línea, número de página arriba a la derecha, portada en la primera hoja,
 * títulos de nivel 1 centrados en negrita y de nivel 2 a la izquierda en negrita, tablas sin líneas verticales y
 * referencias en hoja aparte con sangría francesa.</p>
 */
public final class DocxReportWriter implements ReportWriter {

    /** Ancho de la hoja carta, en veinteavos de punto. */
    public static final int PAGE_WIDTH = 12240;
    /** Alto de la hoja carta, en veinteavos de punto. */
    public static final int PAGE_HEIGHT = 15840;
    /** Margen de 2,54 cm (una pulgada), en veinteavos de punto. */
    public static final int MARGIN = 1440;
    /** Sangría de 1,27 cm (media pulgada), en veinteavos de punto. */
    public static final int INDENT = 720;
    /** Doble espacio, en la unidad de Word (240 es sencillo). */
    public static final int DOUBLE_SPACING = 480;
    /** Letra de 12 puntos, en medios puntos. */
    public static final int FONT_SIZE = 24;
    /** Letra del documento. */
    public static final String FONT = "Times New Roman";

    private static final int SINGLE_SPACING = 240;
    private static final int TEXT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final long EMU_PER_TWIP = 635;
    private static final long MAX_IMAGE_HEIGHT_EMU = 7_315_200;
    /** Ancho aproximado de una letra de Times New Roman de 12 puntos, en veinteavos de punto. */
    private static final double CHARACTER_WIDTH = 125;
    /** Largo desde el que un texto ya no pide más ancho: se parte en varias líneas. */
    private static final int LONG_TEXT = 30;

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture";
    private static final String PACKAGE_RELS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n";
    private static final String RULE = "w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"000000\"";

    @Override
    public ExportFormat format() {
        return ExportFormat.WORD;
    }

    @Override
    public void write(ApaDocument document, OutputStream out) throws IOException {
        if (document == null || out == null) {
            throw new IllegalArgumentException("El documento y el destino son obligatorios.");
        }
        List<Figure> figures = document.figures();
        LocalDateTime time = document.info().date().atStartOfDay();
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        put(zip, "[Content_Types].xml", contentTypes(), time);
        put(zip, "_rels/.rels", packageRelations(), time);
        put(zip, "docProps/core.xml", coreProperties(document.info()), time);
        put(zip, "word/document.xml", body(document), time);
        put(zip, "word/styles.xml", styles(), time);
        put(zip, "word/settings.xml", settings(), time);
        put(zip, "word/header1.xml", pageNumberHeader(), time);
        put(zip, "word/_rels/document.xml.rels", documentRelations(figures.size()), time);
        for (Figure figure : figures) {
            put(zip, "word/media/" + imageName(figure), figure.png(), time);
        }
        zip.finish();
        zip.flush();
    }

    // ---------- cuerpo ----------

    /**
     * Arma el cuerpo: portada, título otra vez arriba de la segunda hoja, los bloques y las referencias.
     *
     * @param document documento
     * @return XML de {@code word/document.xml}
     */
    static String body(ApaDocument document) {
        StringBuilder xml = new StringBuilder(HEADER);
        xml.append("<w:document xmlns:w=\"").append(W).append("\" xmlns:r=\"").append(R).append("\" xmlns:wp=\"")
                .append(WP).append("\" xmlns:a=\"").append(A).append("\" xmlns:pic=\"").append(PIC)
                .append("\"><w:body>");
        cover(xml, document.info());
        paragraph(xml, "<w:pageBreakBefore/><w:jc w:val=\"center\"/>", run(document.info().title(), true, false));
        for (Block block : document.blocks()) {
            switch (block) {
                case Heading heading -> paragraph(xml, "<w:pStyle w:val=\"Heading" + heading.level() + "\"/>",
                        run(heading.text(), false, false));
                case Paragraph text -> paragraph(xml, text.style() == ApaDocument.ParagraphStyle.BODY
                        ? "<w:ind w:firstLine=\"" + INDENT + "\"/>"
                        : "<w:ind w:left=\"" + INDENT + "\" w:hanging=\"" + INDENT / 2 + "\"/>",
                        run(text.text(), false, false));
                case Table table -> table(xml, table);
                case Figure figure -> figure(xml, figure);
            }
        }
        references(xml, document.references());
        xml.append("<w:sectPr><w:headerReference w:type=\"default\" r:id=\"rIdHeader\"/>")
                .append("<w:pgSz w:w=\"").append(PAGE_WIDTH).append("\" w:h=\"").append(PAGE_HEIGHT).append("\"/>")
                .append("<w:pgMar w:top=\"").append(MARGIN).append("\" w:right=\"").append(MARGIN)
                .append("\" w:bottom=\"").append(MARGIN).append("\" w:left=\"").append(MARGIN)
                .append("\" w:header=\"").append(INDENT).append("\" w:footer=\"").append(INDENT)
                .append("\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>");
        return xml.toString();
    }

    /** Portada APA de estudiante: tres líneas en blanco, el título en negrita y debajo los datos, centrados. */
    private static void cover(StringBuilder xml, DocumentInfo info) {
        for (int i = 0; i < 3; i++) {
            paragraph(xml, "<w:jc w:val=\"center\"/>", "");
        }
        paragraph(xml, "<w:jc w:val=\"center\"/>", run(info.title(), true, false));
        paragraph(xml, "<w:jc w:val=\"center\"/>", "");
        for (String line : info.coverLines()) {
            paragraph(xml, "<w:jc w:val=\"center\"/>", run(line, false, false));
        }
    }

    private static void table(StringBuilder xml, Table table) {
        paragraph(xml, "<w:keepNext/>", run(table.label(), true, false));
        paragraph(xml, "<w:keepNext/>", run(table.title(), false, true));
        int[] widths = columnWidths(table);
        xml.append("<w:tbl><w:tblPr><w:tblW w:w=\"").append(TEXT_WIDTH).append("\" w:type=\"dxa\"/>")
                .append("<w:tblBorders><w:top ").append(RULE).append("/><w:left w:val=\"nil\"/><w:bottom ")
                .append(RULE).append("/><w:right w:val=\"nil\"/><w:insideH w:val=\"nil\"/>")
                .append("<w:insideV w:val=\"nil\"/></w:tblBorders><w:tblLayout w:type=\"fixed\"/>")
                .append("<w:tblLook w:val=\"04A0\" w:firstRow=\"1\" w:lastRow=\"0\" w:firstColumn=\"0\" ")
                .append("w:lastColumn=\"0\" w:noHBand=\"1\" w:noVBand=\"1\"/></w:tblPr><w:tblGrid>");
        for (int width : widths) {
            xml.append("<w:gridCol w:w=\"").append(width).append("\"/>");
        }
        xml.append("</w:tblGrid>");
        row(xml, table.headers(), widths, true);
        for (List<String> cells : table.rows()) {
            row(xml, cells, widths, false);
        }
        xml.append("</w:tbl>");
        note(xml, table.note());
    }

    private static void row(StringBuilder xml, List<String> cells, int[] widths, boolean header) {
        xml.append("<w:tr><w:trPr><w:cantSplit/>").append(header ? "<w:tblHeader/>" : "").append("</w:trPr>");
        for (int i = 0; i < cells.size(); i++) {
            xml.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(widths[i]).append("\" w:type=\"dxa\"/>");
            if (header) {
                xml.append("<w:tcBorders><w:bottom ").append(RULE).append("/></w:tcBorders>");
            }
            xml.append("</w:tcPr>");
            String alignment = header || i > 0 ? "center" : "left";
            paragraph(xml, "<w:spacing w:line=\"" + SINGLE_SPACING + "\" w:lineRule=\"auto\"/><w:jc w:val=\""
                    + alignment + "\"/>", run(cells.get(i), false, false));
            xml.append("</w:tc>");
        }
        xml.append("</w:tr>");
    }

    /**
     * Reparte el ancho del texto entre las columnas.
     *
     * <p>Cada columna recibe al menos lo que ocupa su palabra más larga, para que ninguna palabra se parta, y el
     * resto del ancho se reparte según el largo de los textos (hasta {@value #LONG_TEXT} letras).</p>
     *
     * @param table tabla
     * @return ancho de cada columna en veinteavos de punto; suman el ancho del texto
     */
    static int[] columnWidths(Table table) {
        int columns = table.headers().size();
        double[] minimum = new double[columns];
        double[] wanted = new double[columns];
        for (int i = 0; i < columns; i++) {
            int word = longestWord(table.headers().get(i));
            int text = table.headers().get(i).length();
            for (List<String> row : table.rows()) {
                word = Math.max(word, longestWord(row.get(i)));
                text = Math.max(text, row.get(i).length());
            }
            minimum[i] = (word + 2) * CHARACTER_WIDTH;
            wanted[i] = Math.max(minimum[i], Math.min(text, LONG_TEXT) * CHARACTER_WIDTH);
        }
        double minimumTotal = 0;
        double extraTotal = 0;
        for (int i = 0; i < columns; i++) {
            minimumTotal += minimum[i];
            extraTotal += wanted[i] - minimum[i];
        }
        double free = TEXT_WIDTH - minimumTotal;
        int[] widths = new int[columns];
        int used = 0;
        for (int i = 0; i < columns; i++) {
            double width = free <= 0 ? TEXT_WIDTH * minimum[i] / minimumTotal
                    : minimum[i] + (extraTotal == 0 ? free / columns : free * (wanted[i] - minimum[i]) / extraTotal);
            widths[i] = i == columns - 1 ? TEXT_WIDTH - used : (int) width;
            used += widths[i];
        }
        return widths;
    }

    private static int longestWord(String text) {
        int longest = 0;
        for (String word : text.split("\\s+")) {
            longest = Math.max(longest, word.length());
        }
        return longest;
    }

    private static void figure(StringBuilder xml, Figure figure) {
        paragraph(xml, "<w:keepNext/>", run(figure.label(), true, false));
        paragraph(xml, "<w:keepNext/>", run(figure.title(), false, true));
        long width = (long) TEXT_WIDTH * EMU_PER_TWIP;
        long height = width * figure.height() / figure.width();
        if (height > MAX_IMAGE_HEIGHT_EMU) {
            width = width * MAX_IMAGE_HEIGHT_EMU / height;
            height = MAX_IMAGE_HEIGHT_EMU;
        }
        int id = figure.number();
        String drawing = "<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">"
                + "<wp:extent cx=\"" + width + "\" cy=\"" + height + "\"/>"
                + "<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>"
                + "<wp:docPr id=\"" + id + "\" name=\"" + escape(figure.label()) + "\" descr=\""
                + escape(figure.title()) + "\"/>"
                + "<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>"
                + "<a:graphic><a:graphicData uri=\"" + PIC + "\"><pic:pic><pic:nvPicPr>"
                + "<pic:cNvPr id=\"" + id + "\" name=\"" + imageName(figure) + "\"/><pic:cNvPicPr/></pic:nvPicPr>"
                + "<pic:blipFill><a:blip r:embed=\"" + imageRelation(figure.number()) + "\"/>"
                + "<a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
                + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + width + "\" cy=\"" + height + "\"/>"
                + "</a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>"
                + "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r>";
        paragraph(xml, "<w:keepNext/><w:spacing w:line=\"" + SINGLE_SPACING + "\" w:lineRule=\"auto\"/>",
                drawing);
        note(xml, figure.note());
    }

    /** Nota debajo de una tabla o figura: «Nota.» en cursiva y el texto, sin sangría. */
    private static void note(StringBuilder xml, String note) {
        if (!note.isEmpty()) {
            paragraph(xml, "", run("Nota.", false, true) + run(" " + note, false, false));
        }
    }

    /** «Referencias» en hoja aparte, centrado en negrita, y cada referencia con sangría francesa de 1,27 cm. */
    private static void references(StringBuilder xml, List<ApaReference> references) {
        if (references.isEmpty()) {
            return;
        }
        paragraph(xml, "<w:pageBreakBefore/><w:jc w:val=\"center\"/>", run("Referencias", true, false));
        for (ApaReference reference : references) {
            paragraph(xml, "<w:ind w:left=\"" + INDENT + "\" w:hanging=\"" + INDENT + "\"/>",
                    run(reference.lead(), false, false) + run(reference.title(), false, true)
                            + run(reference.tail(), false, false));
        }
    }

    /**
     * Agrega un párrafo con sus propiedades y su contenido.
     *
     * @param xml        destino
     * @param properties propiedades del párrafo, ya en el orden que pide Word; vacío si no tiene
     * @param content    tramos de texto o dibujos
     */
    private static void paragraph(StringBuilder xml, String properties, String content) {
        xml.append("<w:p>");
        if (!properties.isEmpty()) {
            xml.append("<w:pPr>").append(properties).append("</w:pPr>");
        }
        xml.append(content).append("</w:p>");
    }

    /**
     * Arma un tramo de texto; los saltos de línea del texto se vuelven saltos de línea de Word.
     *
     * @param text   texto
     * @param bold   en negrita
     * @param italic en cursiva
     * @return XML del tramo; vacío si el texto está vacío
     */
    static String run(String text, boolean bold, boolean italic) {
        if (text.isEmpty()) {
            return "";
        }
        StringBuilder xml = new StringBuilder("<w:r>");
        if (bold || italic) {
            xml.append("<w:rPr>").append(bold ? "<w:b/><w:bCs/>" : "").append(italic ? "<w:i/><w:iCs/>" : "")
                    .append("</w:rPr>");
        }
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                xml.append("<w:br/>");
            }
            xml.append("<w:t xml:space=\"preserve\">").append(escape(lines[i])).append("</w:t>");
        }
        return xml.append("</w:r>").toString();
    }

    /**
     * Escapa el texto para XML y quita los caracteres de control que XML no admite.
     *
     * @param text texto
     * @return texto seguro dentro de un elemento o atributo
     */
    static String escape(String text) {
        StringBuilder safe = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            switch (character) {
                case '&' -> safe.append("&amp;");
                case '<' -> safe.append("&lt;");
                case '>' -> safe.append("&gt;");
                case '"' -> safe.append("&quot;");
                default -> {
                    if (character >= 0x20 || character == '\t') {
                        safe.append(character);
                    }
                }
            }
        }
        return safe.toString();
    }

    // ---------- otras partes ----------

    private static String styles() {
        return HEADER + "<w:styles xmlns:w=\"" + W + "\"><w:docDefaults><w:rPrDefault><w:rPr>"
                + "<w:rFonts w:ascii=\"" + FONT + "\" w:hAnsi=\"" + FONT + "\" w:eastAsia=\"" + FONT + "\" w:cs=\""
                + FONT + "\"/><w:sz w:val=\"" + FONT_SIZE + "\"/><w:szCs w:val=\"" + FONT_SIZE + "\"/>"
                + "<w:lang w:val=\"es-CO\" w:eastAsia=\"es-CO\" w:bidi=\"ar-SA\"/></w:rPr></w:rPrDefault>"
                + "<w:pPrDefault><w:pPr><w:spacing w:after=\"0\" w:line=\"" + DOUBLE_SPACING
                + "\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/>"
                + "<w:qFormat/></w:style>"
                + heading(1, "<w:jc w:val=\"center\"/>") + heading(2, "")
                + "</w:styles>";
    }

    private static String heading(int level, String alignment) {
        return "<w:style w:type=\"paragraph\" w:styleId=\"Heading" + level + "\"><w:name w:val=\"heading " + level
                + "\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/><w:pPr><w:keepNext/>"
                + "<w:keepLines/>" + alignment + "<w:outlineLvl w:val=\"" + (level - 1) + "\"/></w:pPr><w:rPr><w:b/>"
                + "<w:bCs/></w:rPr></w:style>";
    }

    private static String settings() {
        return HEADER + "<w:settings xmlns:w=\"" + W + "\"><w:defaultTabStop w:val=\"" + INDENT + "\"/>"
                + "<w:characterSpacingControl w:val=\"doNotCompress\"/><w:compat><w:compatSetting "
                + "w:name=\"compatibilityMode\" w:uri=\"http://schemas.microsoft.com/office/word\" w:val=\"15\"/>"
                + "</w:compat></w:settings>";
    }

    /** Encabezado con el número de página arriba a la derecha, en todas las hojas. */
    private static String pageNumberHeader() {
        return HEADER + "<w:hdr xmlns:w=\"" + W + "\" xmlns:r=\"" + R + "\"><w:p><w:pPr><w:spacing w:line=\""
                + SINGLE_SPACING + "\" w:lineRule=\"auto\"/><w:jc w:val=\"right\"/></w:pPr>"
                + "<w:fldSimple w:instr=\" PAGE \"><w:r><w:t>1</w:t></w:r></w:fldSimple></w:p></w:hdr>";
    }

    private static String contentTypes() {
        String main = "application/vnd.openxmlformats-officedocument.wordprocessingml.";
        return HEADER + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"" + main + "document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"" + main + "styles+xml\"/>"
                + "<Override PartName=\"/word/settings.xml\" ContentType=\"" + main + "settings+xml\"/>"
                + "<Override PartName=\"/word/header1.xml\" ContentType=\"" + main + "header+xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/></Types>";
    }

    private static String packageRelations() {
        return HEADER + "<Relationships xmlns=\"" + PACKAGE_RELS + "\">"
                + "<Relationship Id=\"rId1\" Type=\"" + R + "/officeDocument\" Target=\"word/document.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"" + PACKAGE_RELS + "/metadata/core-properties\" "
                + "Target=\"docProps/core.xml\"/></Relationships>";
    }

    private static String documentRelations(int images) {
        List<String> relations = new ArrayList<>(List.of(
                relation("rIdStyles", "styles", "styles.xml"),
                relation("rIdSettings", "settings", "settings.xml"),
                relation("rIdHeader", "header", "header1.xml")));
        for (int number = 1; number <= images; number++) {
            relations.add(relation(imageRelation(number), "image", "media/imagen" + number + ".png"));
        }
        return HEADER + "<Relationships xmlns=\"" + PACKAGE_RELS + "\">" + String.join("", relations)
                + "</Relationships>";
    }

    private static String relation(String id, String type, String target) {
        return "<Relationship Id=\"" + id + "\" Type=\"" + R + "/" + type + "\" Target=\"" + target + "\"/>";
    }

    private static String coreProperties(DocumentInfo info) {
        return HEADER + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/"
                + "core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">"
                + "<dc:title>" + escape(info.title()) + "</dc:title>"
                + "<dc:creator>" + escape(info.authorsText()) + "</dc:creator>"
                + "<dc:language>es-CO</dc:language></cp:coreProperties>";
    }

    private static String imageName(Figure figure) {
        return "imagen" + figure.number() + ".png";
    }

    private static String imageRelation(int number) {
        return "rIdImagen" + number;
    }

    private static void put(ZipOutputStream zip, String name, String content, LocalDateTime time) throws IOException {
        put(zip, name, content.getBytes(StandardCharsets.UTF_8), time);
    }

    private static void put(ZipOutputStream zip, String name, byte[] content, LocalDateTime time) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTimeLocal(time);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }
}
