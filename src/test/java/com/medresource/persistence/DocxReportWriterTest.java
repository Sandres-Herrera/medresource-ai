package com.medresource.persistence;

import com.medresource.model.ApaReference;
import com.medresource.model.ExportFormat;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del documento de Word del reporte: se abre el .docx como zip y se leen sus partes XML.
 */
class DocxReportWriterTest {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    @Test
    void hasAllThePartsOfAWordDocumentAndEveryXmlIsValid() throws Exception {
        Map<String, byte[]> parts = write();

        assertTrue(parts.keySet().containsAll(List.of("[Content_Types].xml", "_rels/.rels", "docProps/core.xml",
                "word/document.xml", "word/styles.xml", "word/settings.xml", "word/header1.xml",
                "word/_rels/document.xml.rels", "word/media/imagen1.png")));
        for (Map.Entry<String, byte[]> part : parts.entrySet()) {
            if (!part.getKey().endsWith(".png")) {
                assertFalse(parse(part.getValue()).getDocumentElement().getTagName().isEmpty(), part.getKey());
            }
        }
        assertArrayEquals(DocumentFixtures.png(), parts.get("word/media/imagen1.png"));
        assertTrue(text(parts, "docProps/core.xml").contains(DocumentFixtures.TITLE));
    }

    @Test
    void theCoverComesFirstAndTheTitleIsRepeatedOnTheSecondPage() throws Exception {
        List<String> paragraphs = paragraphTexts(write());
        List<String> written = paragraphs.stream().filter(text -> !text.isEmpty()).toList();

        assertEquals(DocumentFixtures.TITLE, written.getFirst());
        assertEquals(DocumentFixtures.info().coverLines(), written.subList(1, 6));
        assertEquals(DocumentFixtures.TITLE, written.get(6));
    }

    @Test
    void theBodyHasTheHeadingsTablesFiguresAndReferences() throws Exception {
        Map<String, byte[]> parts = write();
        List<String> paragraphs = paragraphTexts(parts);
        Document body = parse(parts.get("word/document.xml"));

        assertTrue(paragraphs.containsAll(List.of("Tabla 1", DocumentFixtures.TABLE_TITLE, "Nota. Cifras del simulador.",
                "Figura 1", DocumentFixtures.FIGURE_TITLE, "Referencias", DocumentFixtures.BODY,
                DocumentFixtures.SPECIAL, DocumentFixtures.ITEM, ApaReference.SIMULATOR.text())));
        assertEquals(List.of("Heading1", "Heading2", "Heading1"), attributes(body, "pStyle", "val"));
        assertEquals(1, body.getElementsByTagNameNS(W, "tbl").getLength());
        assertEquals(1, body.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/picture",
                "pic").getLength());
        assertTrue(text(parts, "word/_rels/document.xml.rels").contains("media/imagen1.png"));
    }

    @Test
    void followsTheApaPageAndFontRules() throws Exception {
        Map<String, byte[]> parts = write();
        Document body = parse(parts.get("word/document.xml"));
        Document styles = parse(parts.get("word/styles.xml"));
        Element margins = (Element) body.getElementsByTagNameNS(W, "pgMar").item(0);

        for (String side : List.of("top", "right", "bottom", "left")) {
            assertEquals("1440", margins.getAttributeNS(W, side), side);
        }
        assertEquals(List.of("Times New Roman"), attributes(styles, "rFonts", "ascii"));
        assertEquals("24", attributes(styles, "sz", "val").getFirst());
        assertEquals(List.of("480"), attributes(styles, "spacing", "line"));
        assertTrue(attributes(body, "ind", "firstLine").contains("720"));
        assertTrue(attributes(body, "ind", "hanging").contains("720"));
        assertEquals(2, body.getElementsByTagNameNS(W, "pageBreakBefore").getLength());
        assertTrue(text(parts, "word/header1.xml").contains("PAGE"));
        assertEquals(1, body.getElementsByTagNameNS(W, "headerReference").getLength());
    }

    @Test
    void theColumnsShareTheWidthWithoutCuttingWords() {
        int[] widths = DocxReportWriter.columnWidths(DocumentFixtures.document().tables().getFirst());

        assertEquals(DocxReportWriter.PAGE_WIDTH - 2 * DocxReportWriter.MARGIN, widths[0] + widths[1]);
        assertTrue(widths[0] > 0 && widths[1] > 0);
    }

    @Test
    void theTextIsEscapedAndControlCharactersAreRemoved() {
        assertEquals("a &lt;b&gt; &amp; &quot;c&quot;", DocxReportWriter.escape("a <b> & \"c\"\u0001"));
        assertTrue(DocxReportWriter.run("uno\ndos", true, false).contains("<w:br/>"));
        assertEquals("", DocxReportWriter.run("", false, false));
    }

    @Test
    void writesWordWithoutClosingTheStream() throws IOException {
        boolean[] closed = {false};
        OutputStream out = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };

        new DocxReportWriter().write(DocumentFixtures.document(), out);

        assertEquals(ExportFormat.WORD, new DocxReportWriter().format());
        assertFalse(closed[0]);
        assertThrows(IllegalArgumentException.class, () -> new DocxReportWriter().write(null, out));
        assertThrows(IllegalArgumentException.class,
                () -> new DocxReportWriter().write(DocumentFixtures.document(), (OutputStream) null));
    }

    // ---------- lectura del .docx ----------

    private static Map<String, byte[]> write() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DocxReportWriter().write(DocumentFixtures.document(), bytes);
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                parts.put(entry.getName(), zip.readAllBytes());
            }
        }
        return parts;
    }

    private static Document parse(byte[] xml) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static String text(Map<String, byte[]> parts, String name) {
        return new String(parts.get(name), StandardCharsets.UTF_8);
    }

    /** Texto de cada párrafo del cuerpo, juntando sus tramos. */
    private static List<String> paragraphTexts(Map<String, byte[]> parts) throws Exception {
        NodeList paragraphs = parse(parts.get("word/document.xml")).getElementsByTagNameNS(W, "p");
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < paragraphs.getLength(); i++) {
            NodeList runs = ((Element) paragraphs.item(i)).getElementsByTagNameNS(W, "t");
            StringBuilder text = new StringBuilder();
            for (int j = 0; j < runs.getLength(); j++) {
                text.append(runs.item(j).getTextContent());
            }
            texts.add(text.toString());
        }
        return texts;
    }

    private static List<String> attributes(Document document, String element, String attribute) {
        NodeList nodes = document.getElementsByTagNameNS(W, element);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = ((Element) nodes.item(i)).getAttributeNS(W, attribute);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }
}
