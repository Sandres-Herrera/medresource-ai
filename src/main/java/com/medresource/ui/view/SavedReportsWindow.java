package com.medresource.ui.view;

import com.medresource.model.SavedComparison;
import com.medresource.model.SavedReport;
import com.medresource.model.SavedReportSummary;
import com.medresource.services.management.ComparisonArchive;
import com.medresource.services.management.ReportArchive;
import com.medresource.ui.controller.SetupDraft;
import com.medresource.ui.controller.SimulationRequest;
import com.medresource.ui.view.SavedResultsPanel.Tab;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ventana con lo guardado en la base de datos, en dos pestañas ({@link SavedResultsPanel}): los reportes de las
 * simulaciones y las comparaciones.
 *
 * <p>Al abrirse consulta la base de datos en segundo plano ({@link ArchiveWorker}); la lista de comparaciones se
 * vuelve a leer cada vez que se elige su pestaña, para que aparezcan las que se acaban de guardar. «Abrir» muestra
 * el reporte ({@link ReportWindow}) o la comparación ({@link ComparisonWindow}) en su propia ventana.</p>
 *
 * <p>Desde un reporte abierto se puede volver a simular su caso, con los mismos pacientes: «Reutilizar caso» repite
 * el hospital y la estrategia, y «Probar recomendaciones» les aplica las recomendaciones del reporte. En ambos casos
 * se abre el formulario de la simulación con el caso fijo para cambiar lo que se quiera probar, y la simulación
 * empieza como cualquier otra. Si el equipo no tiene configurada la base de datos o la consulta falla, la ventana lo
 * explica.</p>
 *
 * <p>«Eliminar» pregunta primero, diciendo qué más se borra; si el usuario confirma, borra el reporte con su
 * simulación en segundo plano ({@link ReportArchive#delete}) y vuelve a leer las dos listas, porque también se borran
 * las comparaciones que usaban esa simulación. En la pestaña de comparaciones, «Eliminar» borra solo la comparación
 * ({@link ComparisonArchive#delete}); las simulaciones y sus reportes se conservan. Si la base de datos rechaza un
 * borrado, lo explica y no se pierde nada.</p>
 */
public final class SavedReportsWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Título de la ventana. */
    public static final String TITLE = "MedResource AI — Reportes guardados";
    /** Mensaje cuando el equipo no tiene configurada la base de datos. */
    public static final String NO_DATABASE_TEXT = "Este equipo no tiene configurada la base de datos "
            + "(falta src/main/resources/db.properties), así que no hay reportes guardados.";
    /** Mensaje de la pestaña de comparaciones cuando el equipo no tiene configurada la base de datos. */
    public static final String NO_DATABASE_COMPARISONS_TEXT = "Este equipo no tiene configurada la base de datos "
            + "(falta src/main/resources/db.properties), así que no hay comparaciones guardadas.";
    /** Mensaje cuando la consulta falla, antes del detalle. */
    public static final String ERROR_TEXT = "No se pudieron leer los reportes guardados: ";
    /** Mensaje cuando la consulta de comparaciones falla, antes del detalle. */
    public static final String COMPARISONS_ERROR_TEXT = "No se pudieron leer las comparaciones guardadas: ";
    /** Título de la pregunta antes de eliminar un reporte. */
    public static final String DELETE_TITLE = "Eliminar reporte guardado";
    /** Título del aviso cuando no se pudo eliminar un reporte. */
    public static final String DELETE_ERROR_TITLE = "No se pudo eliminar el reporte";
    /** Título de la pregunta antes de eliminar una comparación. */
    public static final String DELETE_COMPARISON_TITLE = "Eliminar comparación guardada";
    /** Título del aviso cuando no se pudo eliminar una comparación. */
    public static final String DELETE_COMPARISON_ERROR_TITLE = "No se pudo eliminar la comparación";
    /** Botón que cancela la eliminación; es el que queda elegido al abrir la pregunta. */
    public static final String CANCEL = "Cancelar";

    private static final Dimension SIZE = new Dimension(1240, 640);
    private static final Dimension MIN_SIZE = new Dimension(720, 420);

    private final SavedResultsPanel results = new SavedResultsPanel();
    private final transient Supplier<Optional<ReportArchive>> reportArchives;
    private final transient Supplier<Optional<ComparisonArchive>> comparisonArchives;
    private final transient Consumer<SimulationRequest> onSimulate;
    private transient ReportArchive archive;
    private transient ComparisonArchive comparisons;

    private SavedReportsWindow(Supplier<Optional<ReportArchive>> reportArchives,
                               Supplier<Optional<ComparisonArchive>> comparisonArchives,
                               Consumer<SimulationRequest> onSimulate) {
        super(TITLE);
        this.reportArchives = reportArchives;
        this.comparisonArchives = comparisonArchives;
        this.onSimulate = onSimulate;
        setContentPane(results);
        setIconImages(List.of(LogoBadge.image(16), LogoBadge.image(32), LogoBadge.image(64)));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(ScreenFit.fit(MIN_SIZE, usable));
        setBounds(ScreenFit.centered(ScreenFit.fit(SIZE, usable), usable));
        results.reports().onOpen(this::openReport);
        results.reports().onDelete(this::deleteReport);
        results.comparisons().onOpen(this::openComparison);
        results.comparisons().onDelete(this::deleteComparison);
        results.onTab(tab -> {
            if (tab == Tab.COMPARISONS) {
                loadComparisons();
            }
        });
    }

    /**
     * Abre la ventana y empieza a buscar los reportes y las comparaciones.
     *
     * @param reportArchives     da el archivo de reportes, o vacío si no hay base de datos; se pide en segundo plano
     * @param comparisonArchives da el archivo de comparaciones, o vacío si no hay base de datos
     * @param onSimulate         empieza la simulación nueva que el usuario preparó desde un reporte guardado
     * @return la ventana ya visible
     * @throws IllegalArgumentException si falta algún dato
     */
    public static SavedReportsWindow open(Supplier<Optional<ReportArchive>> reportArchives,
                                          Supplier<Optional<ComparisonArchive>> comparisonArchives,
                                          Consumer<SimulationRequest> onSimulate) {
        if (reportArchives == null || comparisonArchives == null || onSimulate == null) {
            throw new IllegalArgumentException("Faltan datos para abrir los reportes guardados.");
        }
        SavedReportsWindow window = new SavedReportsWindow(reportArchives, comparisonArchives, onSimulate);
        window.loadReports();
        window.loadComparisons();
        window.setVisible(true);
        return window;
    }

    // ---------- simulaciones ----------

    /** Busca los reportes en segundo plano. */
    private void loadReports() {
        SavedReportsPanel panel = results.reports();
        panel.showLoading();
        new ArchiveWorker<>(() -> reportArchives.get().map(found -> new Loaded(found, found.summaries())), loaded -> {
            if (loaded.isEmpty()) {
                panel.showNotice(NO_DATABASE_TEXT);
                return;
            }
            archive = loaded.get().archive();
            panel.showReports(loaded.get().summaries());
        }, error -> panel.showError(ERROR_TEXT + detail(error))).execute();
    }

    /** Trae el reporte completo en segundo plano y lo muestra. */
    private void openReport(int id) {
        if (archive == null) {
            return;
        }
        ReportArchive current = archive;
        SavedReportsPanel panel = results.reports();
        new ArchiveWorker<>(() -> current.find(id), found -> {
            if (found.isPresent()) {
                SavedReport saved = found.get();
                ReportWindow.showingSaved(saved, withRecommendations -> rerun(current, saved, withRecommendations));
            } else {
                panel.showError("El reporte " + id + " ya no está guardado. Cierra y vuelve a abrir esta ventana.");
            }
        }, error -> panel.showError("No se pudo abrir el reporte " + id + ": " + detail(error))).execute();
    }

    /** Pregunta antes de eliminar un reporte y, si el usuario confirma, lo borra en segundo plano. */
    private void deleteReport(SavedReportSummary summary) {
        if (archive == null || !confirmDelete(DELETE_TITLE, SavedReportsPanel.deleteQuestion(summary))) {
            return;
        }
        ReportArchive current = archive;
        SavedReportsPanel panel = results.reports();
        panel.showDeleting(summary);
        new ArchiveWorker<>(() -> {
            current.delete(summary);
            return current.summaries();
        }, remaining -> {
            panel.showReports(remaining);
            loadComparisons();
        }, error -> {
            JOptionPane.showMessageDialog(this, "No se pudo eliminar el reporte de la ejecución #"
                    + summary.resultId() + ": " + detail(error), DELETE_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            loadReports();
        }).execute();
    }

    /** Muestra la pregunta con «Eliminar» y «Cancelar»; «Cancelar» queda elegido para no borrar por accidente. */
    private boolean confirmDelete(String title, String question) {
        Object[] options = {SavedReportsPanel.DELETE, CANCEL};
        int answer = JOptionPane.showOptionDialog(this, question, title, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return answer == 0;
    }

    /**
     * Prepara en segundo plano la simulación nueva del caso del reporte, abre el formulario con el caso fijo y,
     * si el usuario la confirma, la empieza.
     */
    private void rerun(ReportArchive current, SavedReport saved, boolean withRecommendations) {
        Window owner = focusedOr(this);
        new ArchiveWorker<>(() -> current.rerunPlan(saved, withRecommendations), plan -> {
            SetupDraft draft = new SetupDraft();
            draft.applyRerun(plan);
            CaseConfigDialog.ask(owner, draft).ifPresent(onSimulate);
        }, error -> JOptionPane.showMessageDialog(owner, detail(error), "No se pudo preparar la simulación",
                JOptionPane.ERROR_MESSAGE)).execute();
    }

    private static Window focusedOr(Window fallback) {
        Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return focused == null ? fallback : focused;
    }

    // ---------- comparaciones ----------

    /** Busca las comparaciones en segundo plano. */
    private void loadComparisons() {
        SavedComparisonsPanel panel = results.comparisons();
        panel.showLoading();
        new ArchiveWorker<>(() -> comparisonArchives.get().map(found -> new LoadedComparisons(found, found.saved())),
                loaded -> {
                    if (loaded.isEmpty()) {
                        panel.showNotice(NO_DATABASE_COMPARISONS_TEXT);
                        return;
                    }
                    comparisons = loaded.get().archive();
                    panel.showComparisons(loaded.get().comparisons());
                }, error -> panel.showError(COMPARISONS_ERROR_TEXT + detail(error))).execute();
    }

    /** Trae la comparación guardada en segundo plano y la muestra. */
    private void openComparison(int id) {
        if (comparisons == null) {
            return;
        }
        ComparisonArchive current = comparisons;
        SavedComparisonsPanel panel = results.comparisons();
        new ArchiveWorker<>(() -> current.find(id), found -> {
            if (found.isPresent()) {
                ComparisonWindow.showingSaved(found.get());
            } else {
                panel.showError("La comparación " + id + " ya no está guardada. Vuelve a elegir la pestaña.");
            }
        }, error -> panel.showError("No se pudo abrir la comparación " + id + ": " + detail(error))).execute();
    }

    /** Pregunta antes de eliminar una comparación y, si el usuario confirma, la borra en segundo plano. */
    private void deleteComparison(SavedComparison saved) {
        if (comparisons == null
                || !confirmDelete(DELETE_COMPARISON_TITLE, SavedComparisonsPanel.deleteQuestion(saved))) {
            return;
        }
        ComparisonArchive current = comparisons;
        SavedComparisonsPanel panel = results.comparisons();
        panel.showDeleting(saved);
        new ArchiveWorker<>(() -> {
            current.delete(saved.id());
            return current.saved();
        }, panel::showComparisons, error -> {
            JOptionPane.showMessageDialog(this, "No se pudo eliminar la comparación #" + saved.id() + ": "
                    + detail(error), DELETE_COMPARISON_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            loadComparisons();
        }).execute();
    }

    private static String detail(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    /**
     * Devuelve el contenido de la ventana, con sus dos pestañas.
     *
     * @return el panel con las pestañas
     */
    public SavedResultsPanel getResults() {
        return results;
    }

    /** El archivo con el que se leyó la lista y los reportes que tenía. */
    private record Loaded(ReportArchive archive, List<SavedReportSummary> summaries) {
    }

    /** El archivo con el que se leyeron las comparaciones y las que tenía. */
    private record LoadedComparisons(ComparisonArchive archive, List<SavedComparison> comparisons) {
    }
}
