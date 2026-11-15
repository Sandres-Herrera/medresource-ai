package com.medresource.ui.view;

import com.medresource.model.ApaDocument;
import com.medresource.model.DocumentInfo;
import com.medresource.model.ExportFormat;
import com.medresource.persistence.ReportWriter;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la escritura del reporte en segundo plano.
 */
class ExportWorkerTest {

    private static final long WAIT_SECONDS = 30;
    private static final ApaDocument DOCUMENT = new ApaDocument(
            DocumentInfo.draft("Reporte", LocalDate.of(2026, 11, 15)), List.of(), List.of());

    /** Escritor de prueba que escribe un texto fijo o falla. */
    private static ReportWriter writer(boolean fails) {
        return new ReportWriter() {
            @Override
            public ExportFormat format() {
                return ExportFormat.PDF;
            }

            @Override
            public void write(ApaDocument document, OutputStream out) throws IOException {
                if (fails) {
                    throw new IOException("El archivo está abierto en otro programa.");
                }
                out.write("reporte".getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    @Test
    void writesTheFileAndDeliversItInTheSwingThread() throws Exception {
        Path file = Files.createTempDirectory("reportes").resolve("reporte.pdf");
        AtomicReference<Path> saved = new AtomicReference<>();
        AtomicReference<Boolean> inSwing = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        new ExportWorker(writer(false), DOCUMENT, file, path -> {
            inSwing.set(SwingUtilities.isEventDispatchThread());
            saved.set(path);
            finished.countDown();
        }, error -> finished.countDown()).execute();

        assertTrue(finished.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(file, saved.get());
        assertTrue(inSwing.get());
        assertEquals("reporte", Files.readString(file));
    }

    @Test
    void anErrorIsDeliveredInsteadOfTheFile() throws Exception {
        Path file = Files.createTempDirectory("reportes").resolve("reporte.pdf");
        AtomicReference<Path> saved = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        new ExportWorker(writer(true), DOCUMENT, file, path -> {
            saved.set(path);
            finished.countDown();
        }, failure -> {
            error.set(failure);
            finished.countDown();
        }).execute();

        assertTrue(finished.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertNull(saved.get());
        assertInstanceOf(IOException.class, error.get());
        assertEquals("El archivo está abierto en otro programa.", error.get().getMessage());
    }

    @Test
    void wrongDataIsRejected() {
        Path file = Path.of("reporte.pdf");

        assertThrows(IllegalArgumentException.class,
                () -> new ExportWorker(null, DOCUMENT, file, path -> { }, error -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new ExportWorker(writer(false), DOCUMENT, null, path -> { }, error -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> new ExportWorker(writer(false), DOCUMENT, file, null, error -> { }));
    }
}
