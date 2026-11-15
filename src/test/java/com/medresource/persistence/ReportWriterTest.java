package com.medresource.persistence;

import com.medresource.model.ApaDocument;
import com.medresource.model.DocumentInfo;
import com.medresource.model.ExportFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la escritura del reporte en un archivo.
 */
class ReportWriterTest {

    private static final ApaDocument DOCUMENT = new ApaDocument(
            DocumentInfo.draft("Reporte", LocalDate.of(2026, 11, 15)), List.of(), List.of());

    /** Escritor de prueba que escribe un texto fijo o falla a mitad. */
    private static ReportWriter writer(boolean fails) {
        return new ReportWriter() {
            @Override
            public ExportFormat format() {
                return ExportFormat.PDF;
            }

            @Override
            public void write(ApaDocument document, OutputStream out) throws IOException {
                out.write("nuevo".getBytes(StandardCharsets.UTF_8));
                if (fails) {
                    throw new IOException("Disco lleno");
                }
            }
        };
    }

    @Test
    void writesTheFileAndCreatesItsFolder() throws IOException {
        Path folder = Files.createTempDirectory("reportes");
        Path file = folder.resolve("nueva/reporte.pdf");

        writer(false).write(DOCUMENT, file);

        assertEquals("nuevo", Files.readString(file));
        assertEquals(List.of(file), files(file.getParent()));
    }

    @Test
    void ifItFailsTheOldFileStaysAndNoTemporaryFileIsLeft() throws IOException {
        Path folder = Files.createTempDirectory("reportes");
        Path file = folder.resolve("reporte.pdf");
        Files.writeString(file, "anterior");

        assertThrows(IOException.class, () -> writer(true).write(DOCUMENT, file));

        assertEquals("anterior", Files.readString(file));
        assertEquals(List.of(file), files(folder));
    }

    @Test
    void wrongDataIsRejected() {
        Path file = Path.of("reporte.pdf");

        assertThrows(IllegalArgumentException.class, () -> writer(false).write(null, file));
        assertThrows(IllegalArgumentException.class, () -> writer(false).write(DOCUMENT, (Path) null));
    }

    private static List<Path> files(Path folder) throws IOException {
        try (Stream<Path> list = Files.list(folder)) {
            return list.toList();
        }
    }
}
