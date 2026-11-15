package com.medresource.ui.view;

import com.medresource.model.GeneratedReport;
import com.medresource.model.ReportProgress;
import com.medresource.services.management.ReportGenerator;
import com.medresource.services.management.ReportRequest;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.Optional;
import java.util.function.Consumer;

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
 */
public final class ReportWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Título de la ventana. */
    public static final String TITLE = "MedResource AI — Reporte de la simulación";
    /** Mensaje cuando la generación falla, antes del detalle. */
    public static final String ERROR_TEXT = "No se pudo generar el reporte: ";

    private static final int TICK_MILLIS = 250;
    private static final Dimension SIZE = new Dimension(1100, 760);
    private static final Dimension MIN_SIZE = new Dimension(760, 520);
    private static final String PROGRESS_CARD = "progreso";
    private static final String REPORT_CARD = "reporte";

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final transient Consumer<String> onShowCase;
    private transient ReportWorker worker;
    private transient ReportProgressPanel progressPanel;
    private transient ReportProgress lastProgress;
    private long lastProgressNanos;
    private final Timer ticker = new Timer(TICK_MILLIS, event -> tick());
    private transient GeneratedReport generated;

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
        if (generator == null || request == null || onGenerated == null) {
            throw new IllegalArgumentException("Faltan datos para generar el reporte.");
        }
        ReportWindow window = new ReportWindow(onShowCase);
        window.start(generator, request, onGenerated);
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
        content.add(scroll, REPORT_CARD);
        cards.show(content, REPORT_CARD);
        content.revalidate();
        content.repaint();
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
