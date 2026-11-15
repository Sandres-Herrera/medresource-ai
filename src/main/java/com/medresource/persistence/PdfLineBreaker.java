package com.medresource.persistence;

import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parte un texto en líneas que caben en un ancho, usando las medidas reales de la letra del PDF.
 *
 * <p>El texto puede tener tramos con letras distintas (normal, negrita, cursiva), por ejemplo una referencia con
 * el título en cursiva. Corta entre palabras; solo si una palabra sola no cabe (un enlace largo) la corta por
 * letras. Un salto de línea del texto siempre empieza una línea nueva.</p>
 */
final class PdfLineBreaker {

    /** Letras que la letra estándar del PDF no tiene y cómo se escriben en su lugar. */
    private static final Map<Character, String> REPLACEMENTS = Map.of(
            '≥', ">=", '≤', "<=", '→', "->", '−', "-", ' ', " ", ' ', " ", '\t', " ");

    /**
     * Tramo de texto con su letra.
     *
     * @param text texto
     * @param font letra
     */
    record Run(String text, PDFont font) {
    }

    /**
     * Pedazo de una línea ya ubicado: su texto, su letra y dónde empieza.
     *
     * @param text texto, ya con solo letras que la letra puede escribir
     * @param font letra
     * @param x    distancia desde el comienzo de la línea, en puntos
     */
    record Piece(String text, PDFont font, float x) {
    }

    /**
     * Línea terminada.
     *
     * @param pieces pedazos, de izquierda a derecha
     * @param width  ancho total en puntos
     */
    record Line(List<Piece> pieces, float width) {

        /**
         * Junta el texto de la línea.
         *
         * @return texto de todos los pedazos
         */
        String text() {
            StringBuilder text = new StringBuilder();
            pieces.forEach(piece -> text.append(piece.text()));
            return text.toString();
        }
    }

    private final float size;
    private final float firstWidth;
    private final float otherWidth;
    private final List<Line> lines = new ArrayList<>();
    private final List<Piece> pieces = new ArrayList<>();
    private float x;

    private PdfLineBreaker(float size, float firstWidth, float otherWidth) {
        this.size = size;
        this.firstWidth = firstWidth;
        this.otherWidth = otherWidth;
    }

    /**
     * Parte los tramos en líneas.
     *
     * @param runs       tramos de texto, en orden
     * @param size       tamaño de la letra en puntos
     * @param firstWidth ancho disponible para la primera línea
     * @param otherWidth ancho disponible para las demás
     * @return líneas; al menos una (vacía si no hay texto)
     * @throws IOException              si no se pudo medir la letra
     * @throws IllegalArgumentException si faltan los tramos o los anchos o el tamaño no son positivos
     */
    static List<Line> wrap(List<Run> runs, float size, float firstWidth, float otherWidth) throws IOException {
        if (runs == null || size <= 0 || firstWidth <= 0 || otherWidth <= 0) {
            throw new IllegalArgumentException("Faltan los tramos o los anchos no son positivos.");
        }
        PdfLineBreaker breaker = new PdfLineBreaker(size, firstWidth, otherWidth);
        for (Run run : runs) {
            breaker.add(safe(run.text(), run.font()), run.font());
        }
        breaker.endLine();
        return breaker.lines;
    }

    /**
     * Cambia las letras que la letra del PDF no puede escribir: algunas por su equivalente y las demás por «?».
     *
     * @param text texto
     * @param font letra
     * @return texto que la letra puede escribir
     * @throws IOException si no se pudo consultar la letra
     */
    static String safe(String text, PDFont font) throws IOException {
        StringBuilder safe = new StringBuilder(text.length());
        for (char character : text.toCharArray()) {
            if (character == '\n') {
                safe.append(character);
                continue;
            }
            String replacement = REPLACEMENTS.get(character);
            if (replacement != null) {
                safe.append(replacement);
            } else if (canWrite(font, String.valueOf(character))) {
                safe.append(character);
            } else {
                safe.append('?');
            }
        }
        return safe.toString();
    }

    /**
     * Mide el ancho de un texto.
     *
     * @param text texto que la letra puede escribir
     * @param font letra
     * @param size tamaño en puntos
     * @return ancho en puntos
     * @throws IOException si no se pudo medir
     */
    static float width(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(text) / 1000 * size;
    }

    private static boolean canWrite(PDFont font, String character) throws IOException {
        try {
            font.encode(character);
            return true;
        } catch (IllegalArgumentException missing) {
            return false;
        }
    }

    private void add(String text, PDFont font) throws IOException {
        String[] paragraphs = text.split("\n", -1);
        for (int p = 0; p < paragraphs.length; p++) {
            if (p > 0) {
                endLine();
            }
            for (String word : paragraphs[p].split("(?<= )")) {
                addWord(word, font);
            }
        }
    }

    /** Agrega una palabra con su espacio final; si no cabe, empieza una línea nueva. */
    private void addWord(String word, PDFont font) throws IOException {
        if (word.isEmpty()) {
            return;
        }
        String visible = word.stripTrailing();
        if (x > 0 && x + width(visible, font, size) > available()) {
            endLine();
            word = word.stripLeading();
            if (word.isEmpty()) {
                return;
            }
        }
        if (x == 0 && width(word.stripTrailing(), font, size) > available()) {
            addLongWord(word, font);
            return;
        }
        place(word, font);
    }

    /** Corta por letras una palabra que no cabe sola en la línea, como un enlace largo. */
    private void addLongWord(String word, PDFont font) throws IOException {
        StringBuilder part = new StringBuilder();
        for (char character : word.toCharArray()) {
            if (part.length() > 0 && x + width(part.toString() + character, font, size) > available()) {
                place(part.toString(), font);
                endLine();
                part.setLength(0);
            }
            part.append(character);
        }
        if (part.length() > 0) {
            place(part.toString(), font);
        }
    }

    private void place(String text, PDFont font) throws IOException {
        if (!pieces.isEmpty() && pieces.getLast().font() == font) {
            Piece last = pieces.removeLast();
            pieces.add(new Piece(last.text() + text, font, last.x()));
        } else {
            pieces.add(new Piece(text, font, x));
        }
        x += width(text, font, size);
    }

    private void endLine() throws IOException {
        List<Piece> trimmed = new ArrayList<>(pieces);
        float width = 0;
        while (!trimmed.isEmpty()) {
            Piece last = trimmed.removeLast();
            String text = last.text().stripTrailing();
            if (!text.isEmpty()) {
                trimmed.add(new Piece(text, last.font(), last.x()));
                width = last.x() + width(text, last.font(), size);
                break;
            }
        }
        lines.add(new Line(List.copyOf(trimmed), width));
        pieces.clear();
        x = 0;
    }

    private float available() {
        return lines.isEmpty() ? firstWidth : otherWidth;
    }
}
