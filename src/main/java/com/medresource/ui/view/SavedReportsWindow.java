package com.medresource.ui.view;

import com.medresource.model.SavedReport;
import com.medresource.model.SavedReportSummary;
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
 * Ventana con los reportes guardados en la base de datos ({@link SavedReportsPanel}).
 *
 * <p>Al abrirse consulta la base de datos en segundo plano ({@link ArchiveWorker}). «Abrir» trae el reporte
 * completo y lo muestra en su propia ventana ({@link ReportWindow#showingSaved(SavedReport)}), donde se puede
 * descargar. Si el equipo no tiene configurada la base de datos o la consulta falla, la ventana lo explica.</p>
 */
public final class SavedReportsWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Título de la ventana. */
    public static final String TITLE = "MedResource AI — Reportes guardados";
    /** Mensaje cuando el equipo no tiene configurada la base de datos. */
    public static final String NO_DATABASE_TEXT = "Este equipo no tiene configurada la base de datos "
            + "(falta src/main/resources/db.properties), así que no hay reportes guardados.";
    /** Mensaje cuando la consulta falla, antes del detalle. */
    public static final String ERROR_TEXT = "No se pudieron leer los reportes guardados: ";

    private static final Dimension SIZE = new Dimension(1120, 640);
    private static final Dimension MIN_SIZE = new Dimension(720, 420);

    private final SavedReportsPanel panel = new SavedReportsPanel();
    private final transient Supplier<Optional<ReportArchive>> archives;
    private transient ReportArchive archive;

    private SavedReportsWindow(Supplier<Optional<ReportArchive>> archives) {
        super(TITLE);
        this.archives = archives;
        setContentPane(panel);
        setIconImages(List.of(LogoBadge.image(16), LogoBadge.image(32), LogoBadge.image(64)));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(ScreenFit.fit(MIN_SIZE, usable));
        setBounds(ScreenFit.centered(ScreenFit.fit(SIZE, usable), usable));
        panel.onOpen(this::open);
    }

    /**
     * Abre la ventana y empieza a buscar los reportes.
     *
     * @param archives da el archivo de reportes, o vacío si no hay base de datos; se pide en segundo plano
     * @return la ventana ya visible
     * @throws IllegalArgumentException si es nulo
     */
    public static SavedReportsWindow open(Supplier<Optional<ReportArchive>> archives) {
        if (archives == null) {
            throw new IllegalArgumentException("El archivo de reportes es obligatorio.");
        }
        SavedReportsWindow window = new SavedReportsWindow(archives);
        window.load();
        window.setVisible(true);
        return window;
    }

    /** Busca los reportes en segundo plano. */
    private void load() {
        panel.showLoading();
        new ArchiveWorker<>(() -> archives.get().map(found -> new Loaded(found, found.summaries())), loaded -> {
            if (loaded.isEmpty()) {
                panel.showNotice(NO_DATABASE_TEXT);
                return;
            }
            archive = loaded.get().archive();
            panel.showReports(loaded.get().summaries());
        }, error -> panel.showError(ERROR_TEXT + detail(error))).execute();
    }

    /** Trae el reporte completo en segundo plano y lo muestra. */
    private void open(int id) {
        if (archive == null) {
            return;
        }
        ReportArchive current = archive;
        new ArchiveWorker<>(() -> current.find(id), found -> {
            if (found.isPresent()) {
                ReportWindow.showingSaved(found.get());
            } else {
                panel.showError("El reporte " + id + " ya no está guardado. Cierra y vuelve a abrir esta ventana.");
            }
        }, error -> panel.showError("No se pudo abrir el reporte " + id + ": " + detail(error))).execute();
    }

    private static String detail(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    /**
     * Devuelve la lista que muestra la ventana.
     *
     * @return el panel de la lista
     */
    public SavedReportsPanel getPanel() {
        return panel;
    }

    /** El archivo con el que se leyó la lista y los reportes que tenía. */
    private record Loaded(ReportArchive archive, List<SavedReportSummary> summaries) {
    }
}
