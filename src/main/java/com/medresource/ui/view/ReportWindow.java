package com.medresource.ui.view;

import com.medresource.model.ApaDocument;
import com.medresource.model.DocumentInfo;
import com.medresource.model.ExportFormat;
import com.medresource.model.GeneratedReport;
import com.medresource.model.ReportProgress;
import com.medresource.model.SavedReport;
import com.medresource.persistence.CoverPreferences;
import com.medresource.persistence.DocxReportWriter;
import com.medresource.persistence.PdfReportWriter;
import com.medresource.persistence.ReportWriter;
import com.medresource.services.management.ReportArchive;
import com.medresource.services.management.ReportGenerator;
import com.medresource.services.management.ReportRequest;
import com.medresource.ui.view.DownloadPanel.Choice;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Ventana del reporte de fin de simulación.
 *
 * <p>Mientras el reporte se genera muestra la pantalla de espera ({@link ReportProgressPanel}): las etapas, la
 * barra y cuánto falta. La generación corre en segundo plano ({@link ReportWorker}); entre un aviso y otro, la
 * ventana sigue descontando el tiempo cada cuarto de segundo, por ejemplo mientras espera la respuesta de la IA.
 * Al terminar, la misma ventana pasa al reporte ({@link ReportPanel}) con su barra de desplazamiento.</p>
 *
 * <p>Cerrar la ventana o pulsar «Cancelar» detiene la generación. Un reporte ya generado se puede volver a abrir
 * sin generarlo de nuevo ({@link #showing(GeneratedReport, Consumer)}).</p>
 *
 * <p>Arriba del reporte va la barra de descarga: «Descargar» pide el formato y la portada ({@link DownloadDialog}),
 * propone dónde guardarlo con un nombre como «Reporte_catastrofe_semilla7_2026-11-15.pdf» y lo escribe en segundo
 * plano ({@link ExportWorker}) en normas APA ({@link ApaReportBuilder}). Al terminar, la barra dice dónde quedó y
 * ofrece «Abrir archivo» y «Abrir carpeta». Los datos de la portada se recuerdan para la próxima vez
 * ({@link CoverPreferences}).</p>
 *
 * <p>Cuando el reporte termina de generarse se guarda en la base de datos ({@link ReportArchive}), en segundo plano
 * ({@link ArchiveWorker}); la barra dice si quedó guardado y, si no, al pasar el ratón por el aviso explica por qué.
 * Si el equipo no tiene configurada la base de datos, el reporte se ve y se descarga igual. Un reporte guardado se
 * abre con {@link #showingSaved(SavedReport)}.</p>
 */
public final class ReportWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Título de la ventana. */
    public static final String TITLE = "MedResource AI — Reporte de la simulación";
    /** Mensaje cuando la generación falla, antes del detalle. */
    public static final String ERROR_TEXT = "No se pudo generar el reporte: ";
    /** Texto del botón para descargar el reporte. */
    public static final String DOWNLOAD = "Descargar";
    /** Mensaje cuando no se pudo guardar el archivo, antes del detalle. */
    public static final String SAVE_ERROR_TEXT = "No se pudo guardar el reporte: ";
    /** Mensaje cuando el reporte quedó guardado en la base de datos. */
    public static final String ARCHIVED_TEXT = "Guardado en la base de datos";
    /** Mensaje cuando el reporte no se pudo guardar en la base de datos; el detalle va en la ayuda emergente. */
    public static final String ARCHIVE_ERROR_TEXT = "No se guardó en la base de datos";
    /** Mensaje cuando el equipo no tiene configurada la base de datos. */
    public static final String NO_DATABASE_TEXT = "Sin base de datos configurada: el reporte no se guardó";
    /** Texto del botón que vuelve a simular el mismo caso. */
    public static final String RERUN = "Reutilizar caso";
    /** Texto del botón que vuelve a simular el mismo caso con las recomendaciones del reporte. */
    public static final String TRY_RECOMMENDATIONS = "Probar recomendaciones";

    private static final int TICK_MILLIS = 250;
    private static final Dimension SIZE = new Dimension(1100, 760);
    private static final Dimension MIN_SIZE = new Dimension(760, 520);
    private static final String PROGRESS_CARD = "progreso";
    private static final String REPORT_CARD = "reporte";
    private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("d/MM/yyyy 'a las' HH:mm");

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private transient Consumer<String> onShowCase;
    private transient ReportWorker worker;
    private transient ReportProgressPanel progressPanel;
    private transient ReportProgress lastProgress;
    private long lastProgressNanos;
    private final Timer ticker = new Timer(TICK_MILLIS, event -> tick());
    private transient GeneratedReport generated;
    private final HomeButton download = new HomeButton(HomeButton.Style.OUTLINE, DOWNLOAD, HomeIcon.DOWNLOAD);
    private final HomeButton openFile = new HomeButton(HomeButton.Style.LINK, "Abrir archivo", null);
    private final HomeButton openFolder = new HomeButton(HomeButton.Style.LINK, "Abrir carpeta", null);
    private final JLabel saveStatus = new JLabel("Descarga el reporte en Word o PDF, en normas APA.");
    private final JLabel archiveStatus = new JLabel(" ");
    private transient Consumer<Boolean> rerunAction;
    private final HomeButton rerun = new HomeButton(HomeButton.Style.OUTLINE, RERUN, HomeIcon.PLAY);
    private final HomeButton tryRecommendations =
            new HomeButton(HomeButton.Style.OUTLINE, TRY_RECOMMENDATIONS, HomeIcon.PLAY);
    private final transient CoverPreferences coverMemory = CoverPreferences.forUser();
    private transient Path savedFile;
    private File lastFolder;

    private ReportWindow(Consumer<String> onShowCase) {
        super(TITLE);
        if (onShowCase == null) {
            throw new IllegalArgumentException("La acción de los casos para revisar es obligatoria.");
        }
        this.onShowCase = onShowCase;
        content.setBackground(HomeTheme.BACKGROUND);
        setContentPane(content);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(ScreenFit.fit(MIN_SIZE, usable));
        setBounds(ScreenFit.centered(ScreenFit.fit(SIZE, usable), usable));
    }

    /**
     * Abre la ventana y empieza a generar el reporte.
     *
     * @param generator   generador del reporte
     * @param request     datos de la simulación que terminó
     * @param onShowCase  qué hacer al pulsar «Ver en el plano»: recibe el código del paciente
     * @param onGenerated recibe el reporte cuando está listo, para poder volver a abrirlo sin generarlo otra vez
     * @return la ventana ya visible
     * @throws IllegalArgumentException si falta algún dato
     */
    public static ReportWindow generating(ReportGenerator generator, ReportRequest request,
                                          Consumer<String> onShowCase, Consumer<GeneratedReport> onGenerated) {
        return generating(generator, Optional::empty, request, onShowCase, onGenerated);
    }

    /**
     * Abre la ventana, empieza a generar el reporte y, cuando termina, lo guarda en la base de datos.
     *
     * @param generator   generador del reporte
     * @param archive     da el archivo de reportes, o vacío si no hay base de datos; se pide en segundo plano
     * @param request     datos de la simulación que terminó
     * @param onShowCase  qué hacer al pulsar «Ver en el plano»: recibe el código del paciente
     * @param onGenerated recibe el reporte cuando está listo, para poder volver a abrirlo sin generarlo otra vez
     * @return la ventana ya visible
     * @throws IllegalArgumentException si falta algún dato
     */
    public static ReportWindow generating(ReportGenerator generator, Supplier<Optional<ReportArchive>> archive,
                                          ReportRequest request, Consumer<String> onShowCase,
                                          Consumer<GeneratedReport> onGenerated) {
        if (generator == null || archive == null || request == null || onGenerated == null) {
            throw new IllegalArgumentException("Faltan datos para generar el reporte.");
        }
        ReportWindow window = new ReportWindow(onShowCase);
        window.start(generator, request, report -> {
            onGenerated.accept(report);
            window.archive(archive, request, report);
        });
        window.setVisible(true);
        return window;
    }

    /**
     * Abre la ventana con un reporte que ya se generó.
     *
     * @param generated  reporte terminado
     * @param onShowCase qué hacer al pulsar «Ver en el plano»: recibe el código del paciente
     * @return la ventana ya visible
     * @throws IllegalArgumentException si falta algún dato
     */
    public static ReportWindow showing(GeneratedReport generated, Consumer<String> onShowCase) {
        if (generated == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        ReportWindow window = new ReportWindow(onShowCase);
        window.showReport(generated);
        window.setVisible(true);
        return window;
    }

    /**
     * Abre la ventana con un reporte guardado en la base de datos. «Ver en el plano» no está disponible, porque la
     * simulación ya no está abierta: la barra explica cómo volver a verla.
     *
     * @param saved reporte guardado
     * @return la ventana ya visible
     * @throws IllegalArgumentException si es nulo
     */
    public static ReportWindow showingSaved(SavedReport saved) {
        return showingSaved(saved, null);
    }

    /**
     * Abre la ventana con un reporte guardado y, arriba, los botones «Reutilizar caso» y «Probar recomendaciones»,
     * que vuelven a simular el mismo caso (los mismos pacientes) para compararlo después con la ejecución
     * original. «Probar recomendaciones» solo está activo si el reporte recomendó algo.
     *
     * @param saved  reporte guardado
     * @param onRerun recibe {@code false} al pulsar «Reutilizar caso» y {@code true} al pulsar «Probar
     *                recomendaciones»; si es nulo, los botones no se muestran
     * @return la ventana ya visible
     * @throws IllegalArgumentException si falta el reporte
     */
    public static ReportWindow showingSaved(SavedReport saved, Consumer<Boolean> onRerun) {
        if (saved == null || !saved.isSaved()) {
            throw new IllegalArgumentException("El reporte guardado es obligatorio.");
        }
        ReportWindow window = new ReportWindow(code -> { });
        window.rerunAction = onRerun;
        if (onRerun != null) {
            window.rerun.addActionListener(event -> onRerun.accept(false));
            window.tryRecommendations.addActionListener(event -> onRerun.accept(true));
            boolean recommends = !saved.report().whatIf().recommendations().isEmpty();
            window.tryRecommendations.setEnabled(recommends);
            window.tryRecommendations.setToolTipText(recommends
                    ? "Abre «Nueva simulación» con el mismo caso y los cambios recomendados ya puestos"
                    : "Este reporte no recomendó cambios");
            window.rerun.setToolTipText("Abre «Nueva simulación» con el mismo caso, el mismo hospital y la misma "
                    + "estrategia");
        }
        window.onShowCase = code -> window.explainCase(code, saved);
        window.showReport(saved.report());
        window.showArchiveStatus("Guardado el " + SAVED_AT.format(saved.savedAt()), HomeTheme.MUTED);
        window.setVisible(true);
        return window;
    }

    /**
     * Devuelve el reporte que muestra la ventana.
     *
     * @return el reporte; vacío si todavía se está generando
     */
    public Optional<GeneratedReport> getGenerated() {
        return Optional.ofNullable(generated);
    }

    private void start(ReportGenerator generator, ReportRequest request, Consumer<GeneratedReport> onGenerated) {
        progressPanel = new ReportProgressPanel(generator.usesAi());
        progressPanel.onCancel(this::dispose);
        content.add(progressPanel, PROGRESS_CARD);
        cards.show(content, PROGRESS_CARD);
        worker = new ReportWorker(generator, request, this::onProgress, report -> {
            onGenerated.accept(report);
            showReport(report);
        }, this::onError);
        ticker.start();
        worker.execute();
    }

    /** Guarda el avance nuevo y lo muestra. */
    private void onProgress(ReportProgress progress) {
        lastProgress = progress;
        lastProgressNanos = System.nanoTime();
        progressPanel.show(progress);
    }

    /** Entre un aviso y otro, sigue descontando el tiempo que falta. */
    private void tick() {
        if (lastProgress != null && progressPanel != null) {
            progressPanel.show(lastProgress.after((System.nanoTime() - lastProgressNanos) / 1_000_000));
        }
    }

    private void onError(Throwable error) {
        ticker.stop();
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        progressPanel.showError(ERROR_TEXT + detail);
    }

    /** Guarda en segundo plano el reporte recién generado y deja en la barra cómo terminó. */
    private void archive(Supplier<Optional<ReportArchive>> archive, ReportRequest request, GeneratedReport report) {
        showArchiveStatus("Guardando en la base de datos…", HomeTheme.MUTED);
        new ArchiveWorker<>(() -> archive.get().map(found -> found.save(request, report)), saved -> {
            if (saved.isPresent()) {
                showArchiveStatus(ARCHIVED_TEXT, HomeTheme.ACCENT);
            } else {
                showArchiveStatus(NO_DATABASE_TEXT, HomeTheme.MUTED);
            }
        }, error -> {
            showArchiveStatus(ARCHIVE_ERROR_TEXT, HomeTheme.DANGER);
            archiveStatus.setToolTipText(detail(error));
        }).execute();
    }

    /** En un reporte guardado no hay plano: explica cómo volver a ver al paciente. */
    private void explainCase(String patientCode, SavedReport saved) {
        showStatus("Para ver a " + patientCode + " en el plano, vuelve a simular el escenario "
                + saved.report().report().config().scenario().getDisplayName() + " con la semilla "
                + saved.report().report().config().seed() + ".", HomeTheme.MUTED);
    }

    private static String detail(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    /** Cambia la pantalla de espera por el reporte. */
    private void showReport(GeneratedReport report) {
        ticker.stop();
        generated = report;
        ReportPanel panel = new ReportPanel(report);
        panel.onShowCase(onShowCase);
        JScrollPane scroll = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(HomeTheme.BACKGROUND);
        DarkScrollBarUI.apply(scroll);
        JPanel withBar = new JPanel(new BorderLayout());
        withBar.setOpaque(false);
        withBar.add(buildDownloadBar(), BorderLayout.NORTH);
        withBar.add(scroll, BorderLayout.CENTER);
        content.add(withBar, REPORT_CARD);
        cards.show(content, REPORT_CARD);
        content.revalidate();
        content.repaint();
    }

    // ---------- descarga ----------

    /** Barra de arriba del reporte: el estado de la descarga con sus enlaces a la izquierda y «Descargar». */
    private JPanel buildDownloadBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0)) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics graphics) {
                graphics.setColor(HomeTheme.CARD);
                graphics.fillRect(0, 0, getWidth(), getHeight());
                graphics.setColor(HomeTheme.DIVIDER);
                graphics.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        bar.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        saveStatus.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        showStatus(saveStatus.getText(), HomeTheme.MUTED);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(saveStatus);
        left.add(openFile);
        left.add(openFolder);
        openFile.setVisible(false);
        openFolder.setVisible(false);
        bar.add(left, BorderLayout.CENTER);
        archiveStatus.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(archiveStatus);
        rerun.setVisible(rerunAction != null);
        tryRecommendations.setVisible(rerunAction != null);
        right.add(rerun);
        right.add(tryRecommendations);
        right.add(download);
        bar.add(right, BorderLayout.EAST);
        download.addActionListener(event -> download());
        openFile.addActionListener(event -> open(savedFile));
        openFolder.addActionListener(event -> open(savedFile.getParent()));
        return bar;
    }

    /** Pide el formato y la portada, luego dónde guardarlo, y lo escribe en segundo plano. */
    private void download() {
        if (generated == null) {
            return;
        }
        DocumentInfo draft = coverMemory.apply(DocumentInfo.draft(ApaReportBuilder.defaultTitle(generated),
                LocalDate.now()));
        Optional<Choice> choice = DownloadDialog.ask(this, draft, coverMemory.format(ExportFormat.PDF));
        if (choice.isEmpty()) {
            return;
        }
        ExportFormat format = choice.get().format();
        DocumentInfo info = choice.get().info();
        coverMemory.save(info, format);
        Optional<Path> file = askFile(format, info.date());
        if (file.isEmpty()) {
            return;
        }
        ApaDocument document = ApaReportBuilder.build(generated, info);
        ReportWriter writer = format == ExportFormat.WORD ? new DocxReportWriter() : new PdfReportWriter();
        download.setEnabled(false);
        openFile.setVisible(false);
        openFolder.setVisible(false);
        showStatus("Guardando el reporte en " + format.getLabel() + "…", HomeTheme.MUTED);
        new ExportWorker(writer, document, file.get(), this::onSaved, this::onSaveError).execute();
    }

    /** Muestra el selector de archivos con un nombre propuesto; si el archivo ya existe, pide confirmar. */
    private Optional<Path> askFile(ExportFormat format, LocalDate date) {
        JFileChooser chooser = new JFileChooser(lastFolder);
        chooser.setDialogTitle("Guardar el reporte en " + format.getLabel());
        chooser.setFileFilter(new FileNameExtensionFilter(format.getLabel(), format.getExtension()));
        String name = format.fileName(generated.report().config().scenario(), generated.report().config().seed(),
                date);
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), name));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        File chosen = chooser.getSelectedFile();
        Path file = chosen.toPath().resolveSibling(format.withExtension(chosen.getName()));
        lastFolder = file.toFile().getParentFile();
        if (Files.exists(file) && JOptionPane.showConfirmDialog(this, "Ya existe «" + file.getFileName()
                        + "». ¿Quieres reemplazarlo?", "Reemplazar el archivo", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    private void onSaved(Path file) {
        savedFile = file;
        download.setEnabled(true);
        showStatus("Reporte guardado: " + file.getFileName(), HomeTheme.ACCENT);
        boolean canOpen = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
        openFile.setVisible(canOpen);
        openFolder.setVisible(canOpen);
    }

    private void onSaveError(Throwable error) {
        download.setEnabled(true);
        showStatus(SAVE_ERROR_TEXT + detail(error), HomeTheme.DANGER);
    }

    /** Abre un archivo o una carpeta con el programa que el sistema tenga para eso. */
    private void open(Path path) {
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException | RuntimeException error) {
            showStatus("No se pudo abrir «" + path.getFileName() + "».", HomeTheme.DANGER);
        }
    }

    private void showStatus(String text, Color color) {
        saveStatus.setText(text);
        saveStatus.setForeground(color);
    }

    private void showArchiveStatus(String text, Color color) {
        archiveStatus.setText(text);
        archiveStatus.setForeground(color);
    }

    /** Además de cerrar la ventana, detiene la generación si todavía no terminó. */
    @Override
    public void dispose() {
        ticker.stop();
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        super.dispose();
    }
}
