package com.medresource.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Documento en normas APA 7.ª edición (versión de estudiante), sin depender del formato del archivo.
 *
 * <p>Tiene los datos de la portada, el cuerpo como una lista de bloques (títulos, párrafos, tablas y figuras) y las
 * referencias. Los escritores de Word y de PDF reciben este mismo documento, así los dos archivos dicen lo mismo y
 * solo cambia cómo se dibuja: la portada, el número de página, los márgenes, la letra y el interlineado los pone
 * cada escritor según APA.</p>
 *
 * @param info       datos de la portada
 * @param blocks     cuerpo del documento, en orden
 * @param references referencias, en el orden en que se listan
 */
public record ApaDocument(DocumentInfo info, List<Block> blocks, List<ApaReference> references) {

    /** Parte del cuerpo del documento. */
    public sealed interface Block permits Heading, Paragraph, Table, Figure {
    }

    /** Cómo se acomoda un párrafo. */
    public enum ParagraphStyle {
        /** Párrafo normal: sangría de 1,27 cm en la primera línea. */
        BODY,
        /** Elemento de una lista: todo el párrafo con sangría y la primera línea un poco más afuera. */
        ITEM
    }

    /**
     * Título de una parte del documento.
     *
     * @param level nivel APA: 1 (centrado y en negrita) o 2 (a la izquierda y en negrita)
     * @param text  texto del título
     */
    public record Heading(int level, String text) implements Block {

        /**
         * Valida el nivel y el texto.
         *
         * @throws IllegalArgumentException si el nivel no es 1 ni 2 o si el texto está vacío
         */
        public Heading {
            if (level < 1 || level > 2) {
                throw new IllegalArgumentException("El nivel del título debe ser 1 o 2.");
            }
            requireText(text, "El título no puede estar vacío.");
        }
    }

    /**
     * Párrafo de texto.
     *
     * @param text  texto del párrafo
     * @param style cómo se acomoda
     */
    public record Paragraph(String text, ParagraphStyle style) implements Block {

        /**
         * Valida el texto y el estilo.
         *
         * @throws IllegalArgumentException si el texto está vacío o falta el estilo
         */
        public Paragraph {
            requireText(text, "El párrafo no puede estar vacío.");
            if (style == null) {
                throw new IllegalArgumentException("El estilo del párrafo es obligatorio.");
            }
        }

        /**
         * Crea un párrafo normal.
         *
         * @param text texto del párrafo
         * @return párrafo con sangría en la primera línea
         */
        public static Paragraph body(String text) {
            return new Paragraph(text, ParagraphStyle.BODY);
        }

        /**
         * Crea un elemento de lista.
         *
         * @param text texto del elemento, con su número o viñeta al comienzo
         * @return párrafo con estilo de lista
         */
        public static Paragraph item(String text) {
            return new Paragraph(text, ParagraphStyle.ITEM);
        }
    }

    /**
     * Tabla con su número, su título y una nota debajo.
     *
     * @param number  número de la tabla, desde 1
     * @param title   título, que va en cursiva
     * @param headers encabezados de las columnas
     * @param rows    filas, cada una con un texto por columna
     * @param note    nota que va debajo; vacía si no lleva
     */
    public record Table(int number, String title, List<String> headers, List<List<String>> rows, String note)
            implements Block {

        /**
         * Valida la tabla y guarda copias inmutables.
         *
         * @throws IllegalArgumentException si el número no es positivo, si falta el título o algún encabezado,
         *                                  o si alguna fila no tiene un texto por columna
         */
        public Table {
            if (number < 1) {
                throw new IllegalArgumentException("El número de la tabla debe ser positivo.");
            }
            requireText(title, "La tabla necesita un título.");
            if (headers == null || headers.isEmpty() || headers.stream().anyMatch(ApaDocument::isBlank)) {
                throw new IllegalArgumentException("La tabla necesita sus encabezados.");
            }
            int columns = headers.size();
            if (rows == null || rows.stream().anyMatch(row -> row == null || row.size() != columns
                    || row.stream().anyMatch(Objects::isNull))) {
                throw new IllegalArgumentException("Cada fila debe tener un texto por columna.");
            }
            headers = List.copyOf(headers);
            rows = rows.stream().map(List::copyOf).toList();
            note = note == null ? "" : note.strip();
        }

        /**
         * Devuelve la etiqueta de la tabla.
         *
         * @return por ejemplo «Tabla 1»
         */
        public String label() {
            return "Tabla " + number;
        }
    }

    /**
     * Figura (una gráfica) guardada como imagen PNG, con su número, su título y una nota debajo.
     *
     * @param number número de la figura, desde 1
     * @param title  título, que va en cursiva
     * @param png    imagen en formato PNG
     * @param width  ancho de la imagen en píxeles
     * @param height alto de la imagen en píxeles
     * @param note   nota que va debajo; vacía si no lleva
     */
    public record Figure(int number, String title, byte[] png, int width, int height, String note)
            implements Block {

        /**
         * Valida la figura y guarda una copia de la imagen.
         *
         * @throws IllegalArgumentException si el número o el tamaño no son positivos, si falta el título o si la
         *                                  imagen está vacía
         */
        public Figure {
            if (number < 1 || width < 1 || height < 1) {
                throw new IllegalArgumentException("El número y el tamaño de la figura deben ser positivos.");
            }
            requireText(title, "La figura necesita un título.");
            if (png == null || png.length == 0) {
                throw new IllegalArgumentException("La figura necesita su imagen.");
            }
            png = png.clone();
            note = note == null ? "" : note.strip();
        }

        /**
         * Devuelve una copia de la imagen, para que nadie cambie la de la figura.
         *
         * @return bytes del PNG
         */
        @Override
        public byte[] png() {
            return png.clone();
        }

        /**
         * Devuelve la etiqueta de la figura.
         *
         * @return por ejemplo «Figura 2»
         */
        public String label() {
            return "Figura " + number;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Figure figure && number == figure.number && width == figure.width
                    && height == figure.height && title.equals(figure.title) && note.equals(figure.note)
                    && Arrays.equals(png, figure.png);
        }

        @Override
        public int hashCode() {
            return Objects.hash(number, title, width, height, note) * 31 + Arrays.hashCode(png);
        }

        @Override
        public String toString() {
            return "Figure[" + label() + ", " + title + ", " + width + "x" + height + "]";
        }
    }

    /**
     * Valida el documento y guarda copias inmutables.
     *
     * @throws IllegalArgumentException si falta la portada, algún bloque o las referencias, o si las tablas o las
     *                                  figuras no están numeradas 1, 2, 3… en el orden en que aparecen
     */
    public ApaDocument {
        if (info == null || blocks == null || references == null || blocks.stream().anyMatch(Objects::isNull)
                || references.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Faltan partes del documento.");
        }
        blocks = List.copyOf(blocks);
        references = List.copyOf(references);
        checkNumbers(blocks.stream().filter(Table.class::isInstance).mapToInt(b -> ((Table) b).number()).toArray(),
                "Las tablas");
        checkNumbers(blocks.stream().filter(Figure.class::isInstance).mapToInt(b -> ((Figure) b).number()).toArray(),
                "Las figuras");
    }

    /**
     * Devuelve las tablas del documento, en orden.
     *
     * @return tablas
     */
    public List<Table> tables() {
        return blocks.stream().filter(Table.class::isInstance).map(Table.class::cast).toList();
    }

    /**
     * Devuelve las figuras del documento, en orden.
     *
     * @return figuras
     */
    public List<Figure> figures() {
        return blocks.stream().filter(Figure.class::isInstance).map(Figure.class::cast).toList();
    }

    private static void checkNumbers(int[] numbers, String what) {
        for (int i = 0; i < numbers.length; i++) {
            if (numbers[i] != i + 1) {
                throw new IllegalArgumentException(what + " deben ir numeradas 1, 2, 3… en orden.");
            }
        }
    }

    private static void requireText(String text, String message) {
        if (isBlank(text)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
