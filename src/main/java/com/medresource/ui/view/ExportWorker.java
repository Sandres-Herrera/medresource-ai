package com.medresource.ui.view;

import com.medresource.model.ApaDocument;
import com.medresource.persistence.ReportWriter;

import javax.swing.SwingWorker;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Escribe el archivo del reporte (Word o PDF) en segundo plano, para que la ventana no se congele mientras tanto.
 *
 * <p>El archivo guardado o el error llegan en el hilo de Swing, listos para mostrarse.</p>
 */
public final class ExportWorker extends SwingWorker<Path, Void> {

    private final ReportWriter writer;
    private final ApaDocument document;
    private final Path file;
    private final Consumer<Path> onDone;
    private final Consumer<Throwable> onError;

    /**
     * Prepara la escritura.
     *
     * @param writer   escritor del formato elegido
     * @param document documento que se escribe
     * @param file     archivo de destino
     * @param onDone   recibe el archivo guardado, en el hilo de Swing
     * @param onError  recibe el error si no se pudo guardar, en el hilo de Swing
     * @throws IllegalArgumentException si falta alguno
     */
    public ExportWorker(ReportWriter writer, ApaDocument document, Path file, Consumer<Path> onDone,
                        Consumer<Throwable> onError) {
        if (writer == null || document == null || file == null || onDone == null || onError == null) {
            throw new IllegalArgumentException("Faltan datos para guardar el reporte.");
        }
        this.writer = writer;
        this.document = document;
        this.file = file;
        this.onDone = onDone;
        this.onError = onError;
    }

    @Override
    protected Path doInBackground() throws Exception {
        writer.write(document, file);
        return file;
    }

    @Override
    protected void done() {
        if (isCancelled()) {
            return;
        }
        try {
            onDone.accept(get());
        } catch (ExecutionException e) {
            onError.accept(e.getCause() == null ? e : e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
