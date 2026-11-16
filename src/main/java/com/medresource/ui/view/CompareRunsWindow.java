package com.medresource.ui.view;

import com.medresource.model.SavedReportSummary;
import com.medresource.services.management.ComparisonArchive;
import com.medresource.services.management.ReportArchive;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Ventana «Comparar simulaciones» ({@link CompareRunsPanel}), que se abre desde el inicio.
 *
 * <p>Al abrirse busca en segundo plano las ejecuciones guardadas ({@link ArchiveWorker}). «Comparar» calcula la
 * comparación, también en segundo plano, y la muestra en su propia ventana ({@link ComparisonWindow}), donde se
 * puede guardar. Si el equipo no tiene configurada la base de datos o la consulta falla, la ventana lo explica.</p>
 */
public final class CompareRunsWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Título de la ventana. */
    public static final String TITLE = "MedResource AI — Comparar simulaciones";
    /** Mensaje cuando el equipo no tiene configurada la base de datos. */
    public static final String NO_DATABASE_TEXT = "Este equipo no tiene configurada la base de datos "
            + "(falta src/main/resources/db.properties), así que no hay ejecuciones guardadas para comparar.";
    /** Mensaje cuando la consulta falla, antes del detalle. */
    public static final String ERROR_TEXT = "No se pudieron leer las ejecuciones guardadas: ";

    private static final Dimension SIZE = new Dimension(1040, 680);
    private static final Dimension MIN_SIZE = new Dimension(760, 460);

    private final CompareRunsPanel panel = new CompareRunsPanel();
    private final transient Supplier<Optional<ReportArchive>> reportArchives;
    private final transient Supplier<Optional<ComparisonArchive>> comparisonArchives;
    private transient ComparisonArchive comparisons;

    private CompareRunsWindow(Supplier<Optional<ReportArchive>> reportArchives,
                              Supplier<Optional<ComparisonArchive>> comparisonArchives) {
        super(TITLE);
        this.reportArchives = reportArchives;
        this.comparisonArchives = comparisonArchives;
        setContentPane(panel);
        setIconImages(List.of(LogoBadge.image(16), LogoBadge.image(32), LogoBadge.image(64)));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(ScreenFit.fit(MIN_SIZE, usable));
        setBounds(ScreenFit.centered(ScreenFit.fit(SIZE, usable), usable));
        panel.onCompare(this::compare);
    }

    /**
     * Abre la ventana y empieza a buscar las ejecuciones guardadas.
     *
     * @param reportArchives     da el archivo de reportes, o vacío si no hay base de datos; se pide en segundo plano
     * @param comparisonArchives da el archivo de comparaciones, o vacío si no hay base de datos
     * @return la ventana ya visible
     * @throws IllegalArgumentException si falta algún dato
     */
    public static CompareRunsWindow open(Supplier<Optional<ReportArchive>> reportArchives,
                                         Supplier<Optional<ComparisonArchive>> comparisonArchives) {
        if (reportArchives == null || comparisonArchives == null) {
            throw new IllegalArgumentException("Faltan los archivos de reportes y de comparaciones.");
        }
        CompareRunsWindow window = new CompareRunsWindow(reportArchives, comparisonArchives);
        window.load();
        window.setVisible(true);
        return window;
    }

    /** Busca las ejecuciones y prepara el archivo de comparaciones en segundo plano. */
    private void load() {
        panel.showLoading();
        new ArchiveWorker<>(this::loadInBackground, loaded -> {
            if (loaded.isEmpty()) {
                panel.showNotice(NO_DATABASE_TEXT);
                return;
            }
            comparisons = loaded.get().comparisons();
            panel.showRuns(loaded.get().summaries());
        }, error -> panel.showError(ERROR_TEXT + detail(error))).execute();
    }

    private Optional<Loaded> loadInBackground() {
        Optional<ReportArchive> reports = reportArchives.get();
        Optional<ComparisonArchive> archive = comparisonArchives.get();
        if (reports.isEmpty() || archive.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Loaded(archive.get(), reports.get().summaries()));
    }

    /** Compara las dos ejecuciones en segundo plano y abre la comparación. */
    private void compare(int runA, int runB) {
        if (comparisons == null) {
            return;
        }
        ComparisonArchive current = comparisons;
        panel.showComparing();
        new ArchiveWorker<>(() -> current.compare(runA, runB), comparison -> {
            panel.showCompared();
            ComparisonWindow.showing(comparison, current);
        }, error -> panel.showCompareError("No se pudo comparar: " + detail(error))).execute();
    }

    private static String detail(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    /**
     * Devuelve el formulario de la ventana.
     *
     * @return el formulario
     */
    public CompareRunsPanel getPanel() {
        return panel;
    }

    /** El archivo de comparaciones y las ejecuciones guardadas. */
    private record Loaded(ComparisonArchive comparisons, List<SavedReportSummary> summaries) {
    }
}
