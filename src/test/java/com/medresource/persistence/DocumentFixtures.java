package com.medresource.persistence;

import com.medresource.model.ApaDocument;
import com.medresource.model.ApaDocument.Figure;
import com.medresource.model.ApaDocument.Heading;
import com.medresource.model.ApaDocument.Paragraph;
import com.medresource.model.ApaDocument.Table;
import com.medresource.model.ApaReference;
import com.medresource.model.DocumentInfo;
import com.medresource.model.NarrativeSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Documento APA de ejemplo para probar los escritores de Word y de PDF, con un bloque de cada clase.
 */
final class DocumentFixtures {

    static final String TITLE = "Reporte de prueba: escenario Catástrofe";
    static final String TABLE_TITLE = "Indicadores principales";
    static final String FIGURE_TITLE = "Espera por nivel de triage";
    static final String BODY = "La espera promedio fue de 58,9 min y fallecieron 13 pacientes (13 %).";
    static final String ITEM = "1. Un médico más: evita 0,7 muertes por cada 100 pacientes.";
    static final String SPECIAL = "Símbolos <&> «¿y si…?» — ± 2 ≥ 1";
    static final int FIGURE_WIDTH = 400;
    static final int FIGURE_HEIGHT = 120;

    private DocumentFixtures() {
    }

    static DocumentInfo info() {
        return new DocumentInfo(TITLE, List.of("Santiago Herrera", "Omar Gámez"), "Universidad Popular del Cesar",
                "Ingeniería de Sistemas", "Programación III", "Docente del curso", LocalDate.of(2026, 11, 15));
    }

    static ApaDocument document() {
        return new ApaDocument(info(), List.of(
                Paragraph.body("Introducción del reporte " + ApaReference.SIMULATOR.parenthetical() + "."),
                new Heading(1, "Resumen de la simulación"),
                Paragraph.body(BODY),
                new Table(1, TABLE_TITLE, List.of("Indicador", "Valor"),
                        List.of(List.of("Pacientes", "100"), List.of("Fallecidos", "13 (13 %)")),
                        "Cifras del simulador."),
                new Heading(2, "Esperas por nivel de triage"),
                Paragraph.body(SPECIAL),
                new Figure(1, FIGURE_TITLE, png(), FIGURE_WIDTH, FIGURE_HEIGHT, "Datos de la simulación."),
                new Heading(1, "Recomendaciones"),
                Paragraph.item(ITEM)),
                ApaReference.forReport(NarrativeSource.TEMPLATE));
    }

    /**
     * Arma un documento largo, para probar que pasa a varias hojas.
     *
     * @param paragraphs cantidad de párrafos
     * @return documento con muchos párrafos
     */
    static ApaDocument longDocument(int paragraphs) {
        List<ApaDocument.Block> blocks = new ArrayList<>();
        blocks.add(new Heading(1, "Resultados"));
        for (int i = 1; i <= paragraphs; i++) {
            blocks.add(Paragraph.body("Párrafo " + i + ". " + BODY.repeat(4)));
        }
        return new ApaDocument(info(), blocks, ApaReference.forReport(NarrativeSource.AI));
    }

    static byte[] png() {
        BufferedImage image = new BufferedImage(FIGURE_WIDTH, FIGURE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D surface = image.createGraphics();
        surface.setColor(Color.WHITE);
        surface.fillRect(0, 0, FIGURE_WIDTH, FIGURE_HEIGHT);
        surface.setColor(Color.RED);
        surface.fillRect(20, 40, 300, 30);
        surface.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bytes);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return bytes.toByteArray();
    }
}
