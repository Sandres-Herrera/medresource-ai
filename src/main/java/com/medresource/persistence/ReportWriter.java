package com.medresource.persistence;

import com.medresource.model.ApaDocument;
import com.medresource.model.ExportFormat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Escribe el reporte, ya armado como documento APA, en un archivo de un formato (Word o PDF).
 *
 * <p>Cada formato tiene su escritor; todos reciben el mismo {@link ApaDocument}.</p>
 */
public interface ReportWriter {

    /**
     * Indica qué formato escribe.
     *
     * @return formato del archivo
     */
    ExportFormat format();

    /**
     * Escribe el documento en un flujo de bytes. No cierra el flujo.
     *
     * @param document documento que se escribe
     * @param out      destino
     * @throws IOException              si no se pudo escribir
     * @throws IllegalArgumentException si falta el documento o el destino
     */
    void write(ApaDocument document, OutputStream out) throws IOException;

    /**
     * Escribe el documento en un archivo.
     *
     * <p>Primero lo escribe en un archivo temporal de la misma carpeta y solo al final lo pone en su lugar; así, si
     * algo falla a mitad, no queda un archivo dañado ni se pierde el que ya existía.</p>
     *
     * @param document documento que se escribe
     * @param file     archivo de destino; si ya existe, se reemplaza
     * @throws IOException              si no se pudo escribir
     * @throws IllegalArgumentException si falta el documento o el archivo
     */
    default void write(ApaDocument document, Path file) throws IOException {
        if (document == null || file == null) {
            throw new IllegalArgumentException("El documento y el archivo son obligatorios.");
        }
        Path folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        Path temporary = folder.resolve(".reporte-" + UUID.randomUUID() + ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW)) {
                write(document, out);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
