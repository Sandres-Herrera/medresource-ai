package com.medresource.model;

/**
 * Una casilla del plano del hospital, identificada por su columna y su fila.
 *
 * <p>La columna crece hacia la derecha y la fila hacia abajo; la casilla (0, 0) es la esquina superior
 * izquierda. Es una posición del plano, no una coordenada de pantalla: cada vista la convierte a píxeles
 * a su manera.</p>
 *
 * @param col columna de la casilla, desde 0
 * @param row fila de la casilla, desde 0
 */
public record Position(int col, int row) {

    /**
     * Valida la posición.
     *
     * @throws IllegalArgumentException si la columna o la fila son negativas
     */
    public Position {
        if (col < 0 || row < 0) {
            throw new IllegalArgumentException("La columna y la fila no pueden ser negativas.");
        }
    }

    /**
     * Calcula la distancia a otra casilla contando solo pasos horizontales y verticales.
     *
     * @param other otra casilla
     * @return número de pasos entre las dos casillas
     * @throws IllegalArgumentException si la otra casilla es nula
     */
    public int stepsTo(Position other) {
        if (other == null) {
            throw new IllegalArgumentException("La otra posición es obligatoria.");
        }
        return Math.abs(col - other.col) + Math.abs(row - other.row);
    }
}
