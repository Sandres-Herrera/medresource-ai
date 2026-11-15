package com.medresource.ui.view;

import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Hace en segundo plano una operación con el archivo de reportes (guardar, listar o abrir), para que la ventana no
 * se congele mientras se espera a la base de datos.
 *
 * <p>El resultado o el error llegan en el hilo de Swing, listos para mostrarse.</p>
 *
 * @param <T> lo que devuelve la operación
 */
public final class ArchiveWorker<T> extends SwingWorker<T, Void> {

    private final Supplier<T> task;
    private final Consumer<T> onDone;
    private final Consumer<Throwable> onError;

    /**
     * Prepara la operación sin empezarla; empieza con {@link #execute()}.
     *
     * @param task    operación con la base de datos
     * @param onDone  recibe lo que devolvió la operación
     * @param onError recibe el error si la operación falla
     * @throws IllegalArgumentException si falta algún dato
     */
    public ArchiveWorker(Supplier<T> task, Consumer<T> onDone, Consumer<Throwable> onError) {
        if (task == null || onDone == null || onError == null) {
            throw new IllegalArgumentException("Faltan datos para usar el archivo de reportes.");
        }
        this.task = task;
        this.onDone = onDone;
        this.onError = onError;
    }

    @Override
    protected T doInBackground() {
        return task.get();
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
