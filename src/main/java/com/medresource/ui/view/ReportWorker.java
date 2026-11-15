package com.medresource.ui.view;

import com.medresource.model.GeneratedReport;
import com.medresource.model.ReportProgress;
import com.medresource.services.management.ReportGenerator;
import com.medresource.services.management.ReportRequest;

import javax.swing.SwingWorker;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Genera el reporte en segundo plano, para que la ventana no se congele mientras tanto.
 *
 * <p>El avance, el reporte terminado y los errores llegan en el hilo de Swing, listos para mostrarse. Si se
 * cancela, la generación se detiene en cuanto puede (entre etapas o después de una simulación del «¿y si…?») y no
 * se avisa nada más.</p>
 */
public final class ReportWorker extends SwingWorker<GeneratedReport, ReportProgress> {

    private final ReportGenerator generator;
    private final ReportRequest request;
    private final Consumer<ReportProgress> onProgress;
    private final Consumer<GeneratedReport> onDone;
    private final Consumer<Throwable> onError;

    /**
     * Prepara la generación sin empezarla; empieza con {@link #execute()}.
     *
     * @param generator  generador del reporte
     * @param request    datos de la simulación que terminó
     * @param onProgress recibe el avance más reciente
     * @param onDone     recibe el reporte terminado
     * @param onError    recibe el error si la generación falla
     * @throws IllegalArgumentException si falta algún dato
     */
    public ReportWorker(ReportGenerator generator, ReportRequest request, Consumer<ReportProgress> onProgress,
                        Consumer<GeneratedReport> onDone, Consumer<Throwable> onError) {
        if (generator == null || request == null || onProgress == null || onDone == null || onError == null) {
            throw new IllegalArgumentException("Faltan datos para generar el reporte.");
        }
        this.generator = generator;
        this.request = request;
        this.onProgress = onProgress;
        this.onDone = onDone;
        this.onError = onError;
    }

    @Override
    protected GeneratedReport doInBackground() {
        return generator.generate(request, progress -> publish(progress), this::isCancelled);
    }

    /** Muestra solo el avance más reciente: los anteriores ya quedaron atrás. */
    @Override
    protected void process(List<ReportProgress> chunks) {
        if (!isCancelled() && !chunks.isEmpty()) {
            onProgress.accept(chunks.getLast());
        }
    }

    @Override
    protected void done() {
        if (isCancelled()) {
            return;
        }
        try {
            onDone.accept(get());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (!(cause instanceof CancellationException)) {
                onError.accept(cause);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
