package com.medresource.ui.view;

import com.medresource.model.GeneratedReport;
import com.medresource.model.SavedReport;
import com.medresource.services.management.ReportArchive;
import com.medresource.services.management.ReportRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * La decisión de guardar en el historial (la base de datos) una simulación que terminó, y cómo va ese guardado.
 *
 * <p>Hay una por simulación y la conserva la ventana de la simulación, así que la decisión sigue igual aunque se
 * cierre y se vuelva a abrir el reporte. Nada se guarda solo: el guardado empieza únicamente con {@link #save()}, en
 * segundo plano ({@link ArchiveWorker}), y mientras está en curso no se puede empezar otro. Quien muestra el estado
 * se entera de cada cambio con {@link #addListener(Runnable)}.</p>
 *
 * <p>Los estados van así: {@link Status#WAITING_REPORT} mientras se genera el reporte; {@link Status#UNDECIDED}
 * cuando está listo y falta la respuesta; {@link Status#SKIPPED} si se eligió continuar sin guardar;
 * {@link Status#SAVING} mientras se guarda; y al final {@link Status#SAVED}, {@link Status#FAILED} (se puede volver
 * a intentar) o {@link Status#NO_DATABASE} si el equipo no tiene configurada la base de datos.</p>
 */
public final class ReportSaving {

    /** En qué va el guardado de la simulación. */
    public enum Status {
        /** El reporte todavía se está generando: no hay nada que guardar. */
        WAITING_REPORT,
        /** El reporte está listo y falta decidir si se guarda. */
        UNDECIDED,
        /** Se eligió continuar sin guardar; se puede guardar más tarde. */
        SKIPPED,
        /** Se está guardando en segundo plano. */
        SAVING,
        /** Quedó guardada en la base de datos. */
        SAVED,
        /** No se pudo guardar; se puede volver a intentar. */
        FAILED,
        /** El equipo no tiene configurada la base de datos. */
        NO_DATABASE
    }

    /** Ejecuta el guardado y avisa cómo terminó. */
    @FunctionalInterface
    interface Runner {

        /**
         * Ejecuta el guardado.
         *
         * @param task   guarda y devuelve lo guardado, o vacío si no hay base de datos
         * @param done   recibe lo que devolvió el guardado
         * @param failed recibe el error si el guardado falla
         */
        void run(Supplier<Optional<SavedReport>> task, Consumer<Optional<SavedReport>> done,
                 Consumer<Throwable> failed);
    }

    private final Function<GeneratedReport, Optional<SavedReport>> saver;
    private final Runner runner;
    private final List<Runnable> listeners = new ArrayList<>();
    private GeneratedReport report;
    private Status status = Status.WAITING_REPORT;
    private SavedReport saved;
    private String error;

    /**
     * Prepara la decisión de una simulación que terminó, sin guardar nada todavía.
     *
     * @param archives da el archivo de reportes, o vacío si no hay base de datos; se pide al guardar, en segundo
     *                 plano
     * @param request  datos de la simulación que terminó
     * @throws IllegalArgumentException si falta algún dato
     */
    public ReportSaving(Supplier<Optional<ReportArchive>> archives, ReportRequest request) {
        this(saverFor(archives, request), (task, done, failed) -> new ArchiveWorker<>(task, done, failed).execute());
    }

    /**
     * Prepara la decisión con otra forma de guardar y de ejecutar el guardado (para las pruebas).
     *
     * @param saver  guarda el reporte y devuelve lo guardado, o vacío si no hay base de datos
     * @param runner ejecuta el guardado
     * @throws IllegalArgumentException si falta algún dato
     */
    ReportSaving(Function<GeneratedReport, Optional<SavedReport>> saver, Runner runner) {
        if (saver == null || runner == null) {
            throw new IllegalArgumentException("Faltan datos para guardar la simulación.");
        }
        this.saver = saver;
        this.runner = runner;
    }

    private static Function<GeneratedReport, Optional<SavedReport>> saverFor(
            Supplier<Optional<ReportArchive>> archives, ReportRequest request) {
        if (archives == null || request == null) {
            throw new IllegalArgumentException("Faltan datos para guardar la simulación.");
        }
        return generated -> archives.get().map(archive -> archive.save(request, generated));
    }

    /**
     * Avisa que el reporte ya está listo: desde ahora se puede decidir si se guarda. Solo cuenta la primera vez.
     *
     * @param generated reporte terminado
     * @throws IllegalArgumentException si es nulo
     */
    public void reportReady(GeneratedReport generated) {
        if (generated == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        if (status != Status.WAITING_REPORT) {
            return;
        }
        report = generated;
        change(Status.UNDECIDED);
    }

    /**
     * Empieza a guardar la simulación en segundo plano, si se puede: hace falta el reporte, y no se empieza si ya
     * se está guardando, si ya quedó guardada o si no hay base de datos.
     *
     * @return {@code true} si empezó a guardar
     */
    public boolean save() {
        if (!canSave()) {
            return false;
        }
        GeneratedReport toSave = report;
        error = null;
        change(Status.SAVING);
        runner.run(() -> saver.apply(toSave), result -> {
            if (result.isPresent()) {
                saved = result.get();
                change(Status.SAVED);
            } else {
                change(Status.NO_DATABASE);
            }
        }, failure -> {
            error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            change(Status.FAILED);
        });
        return true;
    }

    /**
     * Elige continuar sin guardar. Solo cambia algo si todavía no se había decidido; la simulación se puede guardar
     * más tarde con {@link #save()}.
     */
    public void skip() {
        if (status == Status.UNDECIDED) {
            change(Status.SKIPPED);
        }
    }

    /**
     * Dice si ahora se puede empezar a guardar.
     *
     * @return {@code true} si el reporte está listo y no se está guardando, ni está guardada, ni falta la base de
     *         datos
     */
    public boolean canSave() {
        return status == Status.UNDECIDED || status == Status.SKIPPED || status == Status.FAILED;
    }

    /**
     * Agrega a quien quiere enterarse de cada cambio; se le avisa en el hilo de la interfaz.
     *
     * @param listener a quien avisar
     * @throws IllegalArgumentException si es nulo
     */
    public void addListener(Runnable listener) {
        if (listener == null) {
            throw new IllegalArgumentException("A quién avisar es obligatorio.");
        }
        listeners.add(listener);
    }

    /**
     * Deja de avisar a alguien, por ejemplo al cerrar el reporte.
     *
     * @param listener a quien ya no avisar
     */
    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void change(Status next) {
        status = next;
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }

    public Status status() {
        return status;
    }

    /**
     * Devuelve lo que quedó guardado.
     *
     * @return el reporte guardado, con su ejecución; vacío si todavía no se guardó
     */
    public Optional<SavedReport> saved() {
        return Optional.ofNullable(saved);
    }

    /**
     * Devuelve por qué falló el último guardado.
     *
     * @return el motivo; vacío si no falló
     */
    public Optional<String> error() {
        return Optional.ofNullable(status == Status.FAILED ? error : null);
    }

    /**
     * Devuelve el reporte de la simulación.
     *
     * @return el reporte; vacío mientras se genera
     */
    public Optional<GeneratedReport> report() {
        return Optional.ofNullable(report);
    }
}
