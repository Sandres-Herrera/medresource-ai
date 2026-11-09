package com.medresource.model;

/**
 * Un área del plano del hospital: una sala o zona rectangular con su título, como "Quirófano 2" o
 * "Hospitalización (30 camas)".
 *
 * <p>Se mide en casillas del plano. La primera fila del área es la del título; las filas de abajo son los
 * lugares donde van las camas, las sillas o los puestos. Así una vista dibuja el título arriba y los
 * lugares debajo sin calcular nada más. Un lugar se identifica con su {@link Position}.</p>
 *
 * <p>Un pasillo ({@link ZoneType#CORRIDOR}) no tiene fila de título: todas sus filas son lugares por donde
 * se camina, así que puede tener una sola fila.</p>
 *
 * @param zone  tipo de zona del área
 * @param title título que se escribe en la primera fila
 * @param col   primera columna del área, desde 0
 * @param row   primera fila del área (la del título), desde 0
 * @param cols  columnas del área, al menos 1
 * @param rows  filas del área contando la del título, al menos 2 (al menos 1 en un pasillo)
 */
public record Area(ZoneType zone, String title, int col, int row, int cols, int rows) {

    /**
     * Valida el área.
     *
     * @throws IllegalArgumentException si falta la zona o el título, si la posición es negativa, si tiene
     *                                  menos de una columna o si no le queda al menos una fila de lugares
     */
    public Area {
        if (zone == null) {
            throw new IllegalArgumentException("La zona del área es obligatoria.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("El título del área es obligatorio.");
        }
        if (col < 0 || row < 0) {
            throw new IllegalArgumentException("La columna y la fila del área no pueden ser negativas.");
        }
        if (cols < 1) {
            throw new IllegalArgumentException("El área debe tener al menos una columna.");
        }
        if (rows < (zone == ZoneType.CORRIDOR ? 1 : 2)) {
            throw new IllegalArgumentException("El área debe tener la fila del título y al menos una de lugares.");
        }
    }

    /** Indica si el área tiene fila de título; los pasillos no la tienen. */
    public boolean hasTitleRow() {
        return zone != ZoneType.CORRIDOR;
    }

    /** Filas de lugares: todas menos la del título, si la tiene. */
    public int slotRows() {
        return rows - firstSlotRowOffset();
    }

    /** Cantidad de lugares del área. */
    public int slotCount() {
        return cols * slotRows();
    }

    /** Primera columna que queda fuera del área, a su derecha. */
    public int right() {
        return col + cols;
    }

    /** Primera fila que queda fuera del área, debajo de ella. */
    public int bottom() {
        return row + rows;
    }

    /**
     * Devuelve un lugar del área, contando de izquierda a derecha y de arriba abajo.
     *
     * @param index número del lugar, desde 0
     * @return la casilla del lugar
     * @throws IllegalArgumentException si el número no corresponde a ningún lugar del área
     */
    public Position slot(int index) {
        if (index < 0 || index >= slotCount()) {
            throw new IllegalArgumentException("El área no tiene el lugar " + index + ".");
        }
        return new Position(col + index % cols, row + firstSlotRowOffset() + index / cols);
    }

    /**
     * Indica si una casilla es un lugar de esta área; la fila del título no cuenta.
     *
     * @param position casilla
     * @return {@code true} si está dentro del área y debajo del título (en un pasillo, en cualquier fila)
     */
    public boolean containsSlot(Position position) {
        return position != null && position.col() >= col && position.col() < right()
                && position.row() >= row + firstSlotRowOffset() && position.row() < bottom();
    }

    /**
     * Indica si dos áreas se pisan. Dos áreas pegadas por un borde no se pisan.
     *
     * @param other otra área
     * @return {@code true} si comparten alguna casilla, incluida la fila del título
     * @throws IllegalArgumentException si la otra área es nula
     */
    public boolean overlaps(Area other) {
        if (other == null) {
            throw new IllegalArgumentException("La otra área es obligatoria.");
        }
        return col < other.right() && other.col < right() && row < other.bottom() && other.row < bottom();
    }

    /** Filas que ocupa el título antes del primer lugar: una, o ninguna en un pasillo. */
    private int firstSlotRowOffset() {
        return hasTitleRow() ? 1 : 0;
    }
}
