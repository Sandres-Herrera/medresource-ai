package com.medresource.ui.view;

import com.medresource.model.GeneratedReport;
import com.medresource.model.HospitalLayout;
import com.medresource.model.SimulationEvent;
import com.medresource.services.management.ReportArchive;
import com.medresource.services.management.ReportGenerator;
import com.medresource.ui.controller.EventCategory;
import com.medresource.ui.controller.EventFilter;
import com.medresource.ui.controller.EventLog;
import com.medresource.ui.controller.HospitalPreset;
import com.medresource.ui.controller.LiveCounters;
import com.medresource.ui.controller.PatientCard;
import com.medresource.ui.controller.ReplayBuffer;
import com.medresource.ui.controller.SimulationClock;
import com.medresource.ui.controller.SimulationController;
import com.medresource.ui.render.PlanAlert;
import com.medresource.ui.render.Renderer2D;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Ventana de la simulación 2D, con el tema oscuro de la pantalla de inicio: un centro de control alrededor del
 * plano.
 *
 * <p>Arriba, la barra con la marca, los datos del caso, los controles (reproducir o pausar, siguiente evento,
 * volver a empezar, velocidad ×1 a ×10, captura) y el reloj; debajo, seis indicadores: llegados, en espera, en
 * tratamiento, en UCI, altas y fallecidos. En el centro, el plano con su marco de acento; a la derecha, la ficha
 * del paciente seleccionado, los recursos libres y la leyenda de triage. Abajo, el registro de eventos con sus
 * filtros y el progreso del caso.</p>
 *
 * <p>Al hacer clic en un paciente del plano, o en una fila del registro, se selecciona: su ficha se actualiza en
 * cada cuadro, sus filas del registro se resaltan y la cámara lo puede seguir. Cabe en una pantalla de
 * 1366 × 768 ({@link ScreenFit}): el plano llena todo su recuadro, lo que no cabe se ve arrastrándolo, y la
 * columna derecha tiene su propia barra. La rueda del ratón acerca el plano, y el botón de arriba a la derecha de su
 * marco lo abre a pantalla completa ({@link MapFullscreen}) con los controles de la simulación. A pantalla completa
 * se ve todo el hospital de una vez, sin arrastrar, y la rueda lo acerca; al salir, el plano vuelve a su marco,
 * llenándolo otra vez, con la misma selección. El selector del marco del plano ({@link ViewToggle}) cambia entre la
 * vista 2D y la 2.5D en cualquier momento, sin detener la simulación; la tarjeta «Quién es quién»
 * ({@link PeopleLegend}) dice qué función cumple cada persona según su color.</p>
 *
 * <p>Al hacer clic en un médico o un enfermero se abre su ficha ({@link StaffCardPanel}) con su especialidad y los
 * pacientes que ha atendido; un clic en uno de ellos lo selecciona. Los avisos del plano (fallecidos, llegadas
 * masivas, UCI llena, falta de sangre) se ven como íconos sobre su lugar. «Repetir 30 s» detiene la simulación y
 * vuelve a mostrar los últimos 30 segundos que se vieron, con el cartel «Repetición»; al terminar (o con «Volver al
 * presente») la simulación sigue donde estaba. A pantalla completa, al pasar el ratón sobre una cama, un quirófano o
 * un consultorio aparece quién está ahí, y el botón «Seguir» hace que la cámara siga al paciente elegido.</p>
 *
 * <p>Al terminar la simulación se abre la ventana del reporte ({@link ReportWindow}), que primero muestra cómo va su
 * generación y después el reporte; el botón «Ver reporte», junto al progreso del caso, lo vuelve a abrir sin
 * generarlo otra vez. «Ver en el plano», en un caso para revisar, selecciona a ese paciente aquí: su ficha y su
 * registro de eventos cuentan lo que le pasó. Volver a empezar cierra el reporte.</p>
 *
 * <p>Solo muestra lo que le da el {@link SimulationController} y le pasa las acciones del usuario; no tiene
 * reglas de simulación.</p>
 */
public final class SimulationWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Título de la ventana: la misma para la vista 2D y la 2.5D. */
    public static final String TITLE = "MedResource AI — Simulación";
    /** Cartel que se ve sobre el plano durante la repetición. */
    public static final String REPLAY_BANNER = "Repetición · últimos 30 s";
    /** Texto del botón que vuelve a abrir el reporte cuando la simulación terminó. */
    public static final String REPORT_BUTTON = "Ver reporte";

    private static final int FRAME_MILLIS = 33;
    private static final int GAP = 10;
    /** Tamaño mínimo de la ventana, si la pantalla lo permite. */
    private static final Dimension MIN_SIZE = new Dimension(1000, 640);
    /** Alto que pide la columna derecha; el resto lo recorre su barra de desplazamiento. */
    private static final int SIDE_PREFERRED_HEIGHT = 300;

    private final transient SimulationController controller;
    private final HospitalMapPanel mapPanel;
    private final MapFrame mapFrame;
    private final SimulationTopBar topBar;
    private final KpiTile arrived = new KpiTile("Llegados", HomeTheme.TEXT);
    private final KpiTile waiting = new KpiTile("En espera", HomeTheme.AMBER);
    private final KpiTile treating = new KpiTile("En tratamiento", HomeTheme.ACCENT);
    private final KpiTile inIcu = new KpiTile("En UCI", HomeTheme.ROSE);
    private final KpiTile discharged = new KpiTile("Altas", HomeTheme.GREEN);
    private final KpiTile deceased = new KpiTile("Fallecidos", HomeTheme.RED);
    private final PatientCardPanel cardPanel = new PatientCardPanel();
    private final StaffCardPanel staffCardPanel = new StaffCardPanel();
    private final ResourcesPanel resourcesPanel = new ResourcesPanel();
    private final EventLogPanel logPanel = new EventLogPanel();
    private final MeterBar progressBar = new MeterBar(HomeTheme.ACCENT);
    private final JLabel progressText = new JLabel();
    private final HomeButton reportButton = new HomeButton(HomeButton.Style.OUTLINE, REPORT_BUTTON, HomeIcon.LIST);
    private final transient EventLog eventLog = new EventLog();
    private final Timer timer;
    private long lastFrameNanos;
    private boolean reportOpened;
    private transient ReportWindow reportWindow;
    private transient GeneratedReport generatedReport;
    private transient ReportSaving reportSaving;
    private transient MapFullscreen fullscreen;
    private transient FullscreenBar fullscreenBar;
    private final transient ReplayBuffer replayBuffer = new ReplayBuffer();
    private transient ReplayBuffer.Replay replay;
    private long replayStartNanos;
    private boolean resumeAfterReplay;
    private transient List<PlanAlert> liveAlerts = List.of();


    /**
     * Crea la ventana con una simulación lista para empezar.
     *
     * @param controller controlador de la simulación
     * @param renderer   renderizador del plano (el mismo que usa el controlador)
     * @throws IllegalArgumentException si alguno es nulo
     */
    public SimulationWindow(SimulationController controller, Renderer2D renderer) {
        super(TITLE);
        if (controller == null || renderer == null) {
            throw new IllegalArgumentException("El controlador y el renderizador son obligatorios.");
        }
        this.controller = controller;
        this.mapPanel = new HospitalMapPanel(controller.getLayout(), renderer);
        mapPanel.setCovering(true);
        mapPanel.setAnimated(true);
        mapPanel.setPlaceInfo(controller::placeInfo);
        this.mapFrame = new MapFrame(mapPanel, planText(controller));
        this.topBar = new SimulationTopBar(controller.getSetup().caseConfig().scenario().getDisplayName() + " · "
                + controller.getStrategyName() + " · Semilla " + controller.getSetup().caseConfig().seed());
        this.timer = new Timer(FRAME_MILLIS, event -> onFrame());
        wireActions();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                timer.stop();
                closeFullscreen();
                closeReport();
            }
        });
        topBar.setSpeed(controller.getSpeed());
        refresh();
        pack();
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(ScreenFit.fit(MIN_SIZE, usable));
        setBounds(ScreenFit.centered(getSize(), usable));
        timer.start();
    }

    /**
     * Texto del tamaño del plano para su marco, por ejemplo «estándar · 24 × 21 m · clic en un paciente o un
     * médico · arrastra para moverlo · rueda para acercar · clic derecho para girar».
     *
     * @param controller controlador de la simulación
     * @return el texto
     */
    static String planText(SimulationController controller) {
        HospitalLayout layout = controller.getLayout();
        String size = HospitalPreset.matching(controller.getSetup().hospital())
                .map(preset -> preset.getDisplayName().toLowerCase(Locale.ROOT)).orElse("personalizado");
        return size + " · " + layout.getCols() + " × " + layout.getRows()
                + " m · clic en un paciente o un médico · arrastra para moverlo · rueda para acercar"
                + " · clic derecho para girar";
    }

    // ---------- armado ----------

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, GAP));
        content.setBackground(HomeTheme.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        content.add(column(topBar, kpiRow()), BorderLayout.NORTH);
        JPanel middle = new JPanel(new BorderLayout(GAP, 0));
        middle.setOpaque(false);
        middle.add(mapFrame, BorderLayout.CENTER);
        middle.add(buildSideScroll(), BorderLayout.EAST);
        content.add(middle, BorderLayout.CENTER);
        content.add(column(logPanel, progressRow()), BorderLayout.SOUTH);
        return content;
    }

    private JComponent kpiRow() {
        JPanel row = new JPanel(new GridLayout(1, 6, GAP, 0));
        row.setOpaque(false);
        for (KpiTile tile : List.of(arrived, waiting, treating, inIcu, discharged, deceased)) {
            row.add(tile);
        }
        return row;
    }

    /** Columna derecha con su propia barra de desplazamiento, para que no estire la ventana hacia abajo. */
    private JScrollPane buildSideScroll() {
        JPanel side = new JPanel();
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        for (JComponent card : List.<JComponent>of(cardPanel, staffCardPanel, resourcesPanel, new PeopleLegend())) {
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setMaximumSize(new Dimension(PatientCardPanel.WIDTH, Integer.MAX_VALUE));
            side.add(card);
            side.add(Box.createVerticalStrut(GAP));
        }
        JScrollPane scroll = new JScrollPane(side, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        DarkScrollBarUI.apply(scroll);
        scroll.setPreferredSize(new Dimension(PatientCardPanel.WIDTH + DarkScrollBarUI.THICKNESS + 4,
                SIDE_PREFERRED_HEIGHT));
        return scroll;
    }

    private JComponent progressRow() {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        JLabel label = new JLabel("Progreso del caso");
        label.setFont(HomeTheme.font(Font.PLAIN, 12f));
        label.setForeground(HomeTheme.MUTED);
        progressText.setFont(HomeTheme.font(Font.PLAIN, 12f));
        progressText.setForeground(HomeTheme.MUTED);
        JPanel end = new JPanel(new BorderLayout(12, 0));
        end.setOpaque(false);
        end.add(progressText, BorderLayout.CENTER);
        end.add(reportButton, BorderLayout.EAST);
        row.add(label, BorderLayout.WEST);
        row.add(progressBar, BorderLayout.CENTER);
        row.add(end, BorderLayout.EAST);
        return row;
    }

    private static JPanel column(JComponent top, JComponent bottom) {
        JPanel column = new JPanel(new BorderLayout(0, GAP));
        column.setOpaque(false);
        column.add(top, BorderLayout.NORTH);
        column.add(bottom, BorderLayout.CENTER);
        return column;
    }

    private void wireActions() {
        topBar.playButton().addActionListener(event -> togglePlay());
        topBar.skipButton().addActionListener(event -> skipToNextEvent());
        topBar.replayButton().addActionListener(event -> toggleReplay());
        topBar.restartButton().addActionListener(event -> restart());
        topBar.captureButton().addActionListener(event -> saveCapture());
        reportButton.addActionListener(event -> openReport());
        topBar.onSpeed(this::changeSpeed);
        mapFrame.onFullscreen(this::openFullscreen);
        mapPanel.setSelectionListener(code -> onSelectionChanged());
        mapPanel.setStaffListener(this::showStaffCard);
        mapPanel.setFollowListener(following -> {
            cardPanel.setFollowing(false);
            showFollowing(null);
        });
        staffCardPanel.onPatientClicked(code -> {
            mapPanel.select(code);
            onSelectionChanged();
        });
        logPanel.onPatientClicked(code -> {
            mapPanel.select(code);
            onSelectionChanged();
        });
        logPanel.onFilterChanged(this::showLog);
        cardPanel.onFollow(following -> {
            mapPanel.setFollowing(following);
            showFollowing(following ? mapPanel.getSelected().orElse(null) : null);
        });
        cardPanel.onClose(() -> {
            mapPanel.select(null);
            onSelectionChanged();
        });
    }

    // ---------- acciones ----------

    private void skipToNextEvent() {
        stopReplay();
        controller.skipToNextEvent();
        lastFrameNanos = System.nanoTime();
        refresh();
    }

    private void changeSpeed(int speed) {
        controller.setSpeed(speed);
        topBar.setSpeed(speed);
        if (fullscreenBar != null) {
            fullscreenBar.setSpeed(speed);
        }
    }

    /** Muestra a quién sigue la cámara en el marco del plano y, si está abierta, en la pantalla completa. */
    private void showFollowing(String patientCode) {
        mapFrame.showFollowing(patientCode);
        if (fullscreenBar != null) {
            fullscreenBar.showFollowing(patientCode);
        }
    }

    /** Abre el plano a pantalla completa, con los controles de la simulación arriba. */
    private void openFullscreen() {
        if (fullscreen != null) {
            return;
        }
        FullscreenBar bar = new FullscreenBar();
        bar.playButton().addActionListener(event -> togglePlay());
        bar.skipButton().addActionListener(event -> skipToNextEvent());
        bar.followButton().addActionListener(event -> toggleFollow());
        bar.replayButton().addActionListener(event -> toggleReplay());
        bar.setReplaying(replay != null);
        bar.setFollowEnabled(mapPanel.getSelected().isPresent());
        bar.onSpeed(this::changeSpeed);
        bar.setSpeed(controller.getSpeed());
        bar.showFollowing(mapPanel.isFollowing() ? mapPanel.getSelected().orElse(null) : null);
        fullscreenBar = bar;
        mapPanel.setCovering(false);
        mapPanel.resetZoom();
        mapPanel.setHoverEnabled(true);
        fullscreen = new MapFullscreen(this, bar, mapPanel);
        fullscreen.onClose(() -> {
            fullscreen = null;
            fullscreenBar = null;
            mapPanel.setHoverEnabled(false);
            mapPanel.setCovering(true);
            mapPanel.resetZoom();
            mapFrame.restoreMap();
        });
        refresh();
        fullscreen.setVisible(true);
        mapPanel.requestFocusInWindow();
    }

    /** Cierra la pantalla completa si está abierta; el plano vuelve a su marco. */
    private void closeFullscreen() {
        if (fullscreen != null) {
            fullscreen.close();
        }
    }

    /** Hace que la cámara siga al paciente elegido o deje de seguirlo, como el botón de su ficha. */
    private void toggleFollow() {
        Optional<String> selected = mapPanel.getSelected();
        if (selected.isEmpty()) {
            return;
        }
        boolean following = !mapPanel.isFollowing();
        mapPanel.setFollowing(following);
        cardPanel.setFollowing(following);
        showFollowing(following ? selected.get() : null);
    }

    /** Abre la ficha de un médico o de un enfermero al hacerle clic en el plano. */
    private void showStaffCard(String staffCode) {
        controller.staffCard(staffCode).ifPresent(staffCardPanel::showCard);
    }

    /** Empieza la repetición de los últimos segundos o, si ya se está viendo, vuelve al presente. */
    private void toggleReplay() {
        if (replay != null) {
            stopReplay();
            return;
        }
        if (!replayBuffer.canReplay()) {
            return;
        }
        resumeAfterReplay = controller.isRunning();
        controller.pause();
        replay = replayBuffer.start();
        replayStartNanos = System.nanoTime();
        mapPanel.setBanner(REPLAY_BANNER);
        showReplaying(true);
        updateButtons();
    }

    /** Muestra el cuadro de la repetición que toca ahora; al llegar al final, vuelve al presente. */
    private void advanceReplay(long now) {
        double seconds = (now - replayStartNanos) / 1e9;
        if (replay.isOver(seconds)) {
            stopReplay();
            return;
        }
        ReplayBuffer.Frame frame = replay.frameAt(seconds);
        mapPanel.showStaff(frame.staff());
        mapPanel.showPorters(frame.porters());
        mapPanel.showPatients(frame.patients(), frame.overflow());
        mapPanel.showAlerts(frame.alerts());
        topBar.showTime(frame.minute(), frame.second());
        if (fullscreenBar != null) {
            fullscreenBar.showTime(frame.minute(), frame.second());
        }
    }

    /** Termina la repetición: el plano vuelve a lo que pasa ahora y la simulación sigue si estaba corriendo. */
    private void stopReplay() {
        if (replay == null) {
            return;
        }
        replay = null;
        mapPanel.setBanner(null);
        showReplaying(false);
        refresh();
        if (resumeAfterReplay) {
            lastFrameNanos = System.nanoTime();
            controller.play();
        }
        updateButtons();
    }

    private void showReplaying(boolean replaying) {
        topBar.setReplaying(replaying);
        if (fullscreenBar != null) {
            fullscreenBar.setReplaying(replaying);
        }
    }

    /** Indica si se está viendo la repetición de los últimos segundos. */
    public boolean isReplaying() {
        return replay != null;
    }

    private void togglePlay() {
        if (replay != null) {
            stopReplay();
            return;
        }
        if (controller.isRunning()) {
            controller.pause();
        } else {
            lastFrameNanos = System.nanoTime();
            controller.play();
        }
        updateButtons();
    }

    private void restart() {
        replay = null;
        mapPanel.setBanner(null);
        showReplaying(false);
        replayBuffer.clear();
        staffCardPanel.clear();
        closeReport();
        generatedReport = null;
        reportSaving = null;
        controller.reset();
        mapPanel.showLayout(controller.getLayout());
        mapPanel.select(null);
        eventLog.clear();
        logPanel.showEntries(List.of());
        onSelectionChanged();
        reportOpened = false;
        refresh();
    }

    private void onFrame() {
        long now = System.nanoTime();
        double elapsed = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1_000_000.0;
        lastFrameNanos = now;
        if (replay != null) {
            advanceReplay(now);
            return;
        }
        if (controller.isRunning()) {
            controller.advance(elapsed);
            refresh();
            replayBuffer.record(elapsed / 1000.0, controller.getMinute(), controller.getSecond(),
                    controller.markers(), controller.staffMarkers(), controller.porterMarkers(),
                    controller.overflow(), liveAlerts);
        }
        if (controller.isFinished() && !reportOpened) {
            reportOpened = true;
            refresh();
            closeFullscreen();
            openReport();
        }
    }

    private void refresh() {
        mapPanel.showStaff(controller.staffMarkers());
        mapPanel.showPorters(controller.porterMarkers());
        mapPanel.showPatients(controller.markers(), controller.overflow());
        LiveCounters counters = controller.counters();
        arrived.setValue(counters.arrived(), counters.totalPatients());
        waiting.setValue(counters.waiting());
        treating.setValue(counters.inTreatment());
        inIcu.setValue(counters.inIcu());
        discharged.setValue(counters.discharged());
        deceased.setValue(counters.deceased());
        resourcesPanel.show(counters);
        topBar.showTime(controller.getMinute(), controller.getSecond());
        if (fullscreenBar != null) {
            fullscreenBar.showTime(controller.getMinute(), controller.getSecond());
        }
        progressBar.setFraction(counters.progressPercent() / 100.0);
        progressText.setText(counters.finished() + " de " + counters.totalPatients()
                + " pacientes terminaron su caso (" + counters.progressPercent() + " %)");
        appendEvents();
        liveAlerts = controller.alerts();
        mapPanel.showAlerts(liveAlerts);
        updateCard();
        updateStaffCard();
        updateButtons();
    }

    /** Pone en la ficha del personal, si está abierta, lo que se ve ahora del médico o enfermero. */
    private void updateStaffCard() {
        String code = staffCardPanel.getStaffCode();
        if (code != null) {
            controller.staffCard(code).ifPresent(staffCardPanel::showCard);
        }
    }

    /** Anota los eventos nuevos en el registro y muestra los que cumplen el filtro. */
    private void appendEvents() {
        String now = SimulationClock.formatTimeOfDay(controller.getMinute(), controller.getSecond());
        EventFilter filter = currentFilter();
        List<EventLog.Entry> shown = new ArrayList<>();
        for (SimulationEvent event : controller.drainNewEvents()) {
            EventLog.Entry entry = eventLog.add(now, event);
            if (filter.accepts(event)) {
                shown.add(entry);
            }
        }
        logPanel.appendEntries(shown);
    }

    /** Filtro que eligió el usuario en el registro. */
    private EventFilter currentFilter() {
        return logPanel.filter(mapPanel.getSelected().orElse(null));
    }

    /** Vuelve a mostrar en el registro los eventos anteriores que cumplen el filtro actual. */
    private void showLog() {
        logPanel.showEntries(eventLog.entries(currentFilter(), EventLogPanel.MAX_ROWS));
    }

    /** Cuando cambia el paciente seleccionado: su ficha, la cámara y el registro. */
    private void onSelectionChanged() {
        Optional<String> selected = mapPanel.getSelected();
        if (fullscreenBar != null) {
            fullscreenBar.setFollowEnabled(selected.isPresent());
        }
        logPanel.setOnlySelectedEnabled(selected.isPresent());
        logPanel.setSelectedPatient(selected.orElse(null));
        mapPanel.setFollowing(false);
        showFollowing(null);
        if (selected.isEmpty()) {
            cardPanel.clear();
        } else {
            cardPanel.setFollowing(false);
        }
        updateCard();
        showLog();
    }

    /** Pone en la ficha lo que se ve ahora del paciente seleccionado, o avisa que ya salió del hospital. */
    private void updateCard() {
        Optional<String> selected = mapPanel.getSelected();
        if (selected.isEmpty()) {
            return;
        }
        Optional<PatientCard> card = controller.patientCard(selected.get());
        if (card.isPresent()) {
            cardPanel.showCard(card.get(), eventLog.entries(new EventFilter(EventCategory.ALL, selected.get()),
                    PatientCardPanel.TRAIL_SIZE));
        } else if (cardPanel.isShowingCard() || mapPanel.isFollowing() || !cardPanel.isHighlighted()) {
            mapPanel.setFollowing(false);
            showFollowing(null);
            cardPanel.showGone(selected.get());
        }
    }

    private void updateButtons() {
        boolean finished = controller.isFinished();
        topBar.setRunning(controller.isRunning());
        topBar.playButton().setEnabled(!finished);
        topBar.skipButton().setEnabled(!finished);
        boolean canReplay = replay != null || replayBuffer.canReplay();
        topBar.replayButton().setEnabled(canReplay);
        reportButton.setVisible(finished);
        if (fullscreenBar != null) {
            fullscreenBar.setRunning(controller.isRunning());
            fullscreenBar.playButton().setEnabled(!finished);
            fullscreenBar.skipButton().setEnabled(!finished);
            fullscreenBar.replayButton().setEnabled(canReplay);
        }
    }

    /**
     * Abre el reporte: lo trae al frente si ya está abierto, lo vuelve a mostrar si ya se generó o, si no, empieza
     * a generarlo. Al terminar, el reporte pregunta si se guarda la simulación en el historial; esa decisión se
     * guarda aquí ({@link ReportSaving}), así que se conserva aunque se cierre y se vuelva a abrir el reporte. Solo
     * hace algo cuando la simulación terminó.
     */
    private void openReport() {
        if (reportWindow != null && reportWindow.isDisplayable()) {
            reportWindow.toFront();
            return;
        }
        if (generatedReport != null && reportSaving != null) {
            reportWindow = ReportWindow.showing(generatedReport, reportSaving, this::showCase);
            return;
        }
        controller.reportRequest().ifPresent(request -> {
            reportSaving = new ReportSaving(ReportArchive::fromConfiguration, request);
            reportWindow = ReportWindow.generating(ReportGenerator.fromConfiguration(), reportSaving, request,
                    this::showCase, report -> generatedReport = report);
        });
    }

    /** Cierra el reporte si está abierto; si todavía se estaba generando, la generación se detiene. */
    private void closeReport() {
        if (reportWindow != null) {
            reportWindow.dispose();
            reportWindow = null;
        }
    }

    /**
     * Muestra un paciente de los casos para revisar: lo selecciona en el plano, deja en el registro solo su historia
     * y trae esta ventana al frente.
     */
    private void showCase(String patientCode) {
        toFront();
        mapPanel.select(patientCode);
        onSelectionChanged();
        logPanel.choose(EventCategory.ALL);
        logPanel.showOnlySelected();
    }

    private void saveCapture() {
        boolean wasRunning = controller.isRunning();
        controller.pause();
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Imagen PNG", "png"));
        chooser.setSelectedFile(new File("simulacion-minuto-" + controller.getMinute() + ".png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                file = new File(file.getParentFile(), file.getName() + ".png");
            }
            try {
                ImageIO.write(mapPanel.toImage(Math.max(1, mapPanel.getWidth()),
                        Math.max(1, mapPanel.getHeight())), "png", file);
            } catch (IOException error) {
                JOptionPane.showMessageDialog(this, "No se pudo guardar la imagen: " + error.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        if (wasRunning) {
            lastFrameNanos = System.nanoTime();
            controller.play();
        }
        updateButtons();
    }
}
