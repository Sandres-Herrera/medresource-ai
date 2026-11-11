package com.medresource.ui.view;

import com.medresource.model.RunMetrics;
import com.medresource.model.SimulationEvent;
import com.medresource.model.TriageLevel;
import com.medresource.ui.controller.EventCategory;
import com.medresource.ui.controller.EventFilter;
import com.medresource.ui.controller.EventLog;
import com.medresource.ui.controller.LiveCounters;
import com.medresource.ui.controller.PatientCard;
import com.medresource.ui.controller.SimulationClock;
import com.medresource.ui.controller.SimulationController;
import com.medresource.ui.render.Palette;
import com.medresource.ui.render.Renderer2D;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Ventana de la simulación 2D: el plano con los pacientes moviéndose, los controles del reloj, los contadores
 * en vivo, la barra de progreso y el registro de eventos.
 *
 * <p>El reloj corre en tiempo real (×1) o más rápido (×2, ×5, ×10) y muestra el día y la hora con segundos.
 * El botón «Saltar al siguiente evento» adelanta los ratos en que no pasa nada.</p>
 *
 * <p>Al hacer clic sobre un paciente del plano se ve su ficha en la columna derecha, que se actualiza en cada
 * cuadro; desde la ficha se le puede seguir con la cámara. El registro de eventos se puede filtrar por grupo de
 * eventos y dejar solo los del paciente seleccionado: al cambiar el filtro se vuelven a mostrar los eventos
 * anteriores que lo cumplen.</p>
 *
 * <p>Solo muestra lo que le da el {@link SimulationController} y le pasa las acciones del usuario; no tiene
 * reglas de simulación.</p>
 */
public final class SimulationWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final int FRAME_MILLIS = 33;
    private static final int MAX_LOG_LINES = 400;

    private final transient SimulationController controller;
    private final HospitalMapPanel mapPanel;
    private final JButton playButton = new JButton("Iniciar");
    private final JButton stepButton = new JButton("Paso (+1 min)");
    private final JButton skipButton = new JButton("Saltar al siguiente evento");
    private final JButton resetButton = new JButton("Reiniciar");
    private final JButton captureButton = new JButton("Guardar captura");
    private final JComboBox<String> speedBox = new JComboBox<>();
    private final JLabel clockLabel = new JLabel();
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JTextArea log = new JTextArea();
    private final transient EventLog eventLog = new EventLog();
    private final JComboBox<EventCategory> categoryBox = new JComboBox<>(EventCategory.values());
    private final JCheckBox onlySelectedBox = new JCheckBox("Solo el paciente seleccionado");
    private final PatientCardPanel cardPanel = new PatientCardPanel();
    private final transient Map<String, JLabel> counterLabels = new LinkedHashMap<>();
    private final Timer timer;
    private long lastFrameNanos;
    private boolean summaryShown;

    /**
     * Crea la ventana con una simulación lista para empezar.
     *
     * @param controller controlador de la simulación
     * @param renderer   renderizador del plano (el mismo que usa el controlador)
     * @throws IllegalArgumentException si alguno es nulo
     */
    public SimulationWindow(SimulationController controller, Renderer2D renderer) {
        super("MedResource AI — Simulación 2D");
        if (controller == null || renderer == null) {
            throw new IllegalArgumentException("El controlador y el renderizador son obligatorios.");
        }
        this.controller = controller;
        this.mapPanel = new HospitalMapPanel(controller.getLayout(), renderer);
        this.timer = new Timer(FRAME_MILLIS, event -> onFrame());
        mapPanel.setSelectionListener(code -> onSelectionChanged());
        cardPanel.followButton().addActionListener(event ->
                mapPanel.setFollowing(cardPanel.followButton().isSelected()));
        cardPanel.closeButton().addActionListener(event -> {
            mapPanel.select(null);
            onSelectionChanged();
        });
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildToolBar(), BorderLayout.NORTH);
        JSplitPane center = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mapPanel, buildLogPanel());
        center.setResizeWeight(0.8);
        add(center, BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);
        add(progressBar, BorderLayout.SOUTH);
        progressBar.setStringPainted(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                timer.stop();
            }
        });
        refresh();
        pack();
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        timer.start();
    }

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        for (int speed : SimulationClock.SPEEDS) {
            speedBox.addItem("×" + speed);
        }
        speedBox.setSelectedIndex(SimulationClock.SPEEDS.indexOf(controller.getSpeed()));
        speedBox.setMaximumSize(speedBox.getPreferredSize());
        speedBox.setToolTipText("×1 es tiempo real: un segundo en pantalla es un segundo en el hospital.");
        speedBox.addActionListener(event ->
                controller.setSpeed(SimulationClock.SPEEDS.get(speedBox.getSelectedIndex())));
        playButton.addActionListener(event -> togglePlay());
        stepButton.addActionListener(event -> {
            controller.stepOnce();
            refresh();
        });
        skipButton.addActionListener(event -> {
            controller.skipToNextEvent();
            lastFrameNanos = System.nanoTime();
            refresh();
        });
        resetButton.addActionListener(event -> restart());
        captureButton.addActionListener(event -> saveCapture());
        bar.add(playButton);
        bar.add(stepButton);
        bar.add(skipButton);
        bar.add(resetButton);
        bar.addSeparator();
        bar.add(new JLabel("Velocidad: "));
        bar.add(speedBox);
        bar.addSeparator();
        bar.add(captureButton);
        bar.add(Box.createHorizontalGlue());
        clockLabel.setFont(clockLabel.getFont().deriveFont(Font.BOLD, 14f));
        bar.add(clockLabel);
        bar.add(Box.createHorizontalStrut(8));
        return bar;
    }

    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JLabel info = new JLabel("<html><b>" + controller.getSetup().caseConfig().scenario().getDisplayName()
                + "</b><br>" + controller.getStrategyName() + "<br>Semilla "
                + controller.getSetup().caseConfig().seed() + "</html>");
        side.add(info);
        side.add(Box.createVerticalStrut(8));
        cardPanel.setAlignmentX(LEFT_ALIGNMENT);
        side.add(cardPanel);
        side.add(Box.createVerticalStrut(8));
        side.add(counterSection("Pacientes", "Llegados", "En espera", "En tratamiento", "En UCI",
                "Alta", "Remitidos", "Fallecidos", "Sin atender"));
        side.add(Box.createVerticalStrut(8));
        side.add(counterSection("Recursos libres", "Camas", "Camas UCI", "Quirófanos", "Médicos",
                "Enfermeros", "Sangre (unidades)", "Morgue (ocupada)"));
        side.add(Box.createVerticalStrut(8));
        side.add(buildLegend());
        side.add(Box.createVerticalGlue());
        return side;
    }

    private JPanel counterSection(String title, String... names) {
        JPanel panel = new JPanel(new GridLayout(names.length, 2, 8, 2));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        for (String name : names) {
            JLabel value = new JLabel("0", JLabel.RIGHT);
            value.setFont(value.getFont().deriveFont(Font.BOLD));
            counterLabels.put(name, value);
            panel.add(new JLabel(name));
            panel.add(value);
        }
        return panel;
    }

    private JPanel buildLegend() {
        JPanel legend = new JPanel(new GridLayout(TriageLevel.values().length, 1, 0, 2));
        legend.setBorder(BorderFactory.createTitledBorder("Triage"));
        for (TriageLevel level : TriageLevel.values()) {
            JLabel label = new JLabel("■ " + level.getDisplayName());
            label.setForeground(Palette.triage(level));
            legend.add(label);
        }
        return legend;
    }

    private JPanel buildLogPanel() {
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(log);
        scroll.setPreferredSize(new Dimension(600, 130));
        categoryBox.setToolTipText("Muestra solo un grupo de eventos.");
        categoryBox.addActionListener(event -> showLog());
        onlySelectedBox.setToolTipText("Muestra solo la historia del paciente cuya ficha se ve.");
        onlySelectedBox.setEnabled(false);
        onlySelectedBox.addActionListener(event -> showLog());
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filters.add(new JLabel("Mostrar:"));
        filters.add(categoryBox);
        filters.add(onlySelectedBox);
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Registro de eventos"));
        panel.add(filters, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void togglePlay() {
        if (controller.isRunning()) {
            controller.pause();
        } else {
            lastFrameNanos = System.nanoTime();
            controller.play();
        }
        updateButtons();
    }

    private void restart() {
        controller.reset();
        mapPanel.showLayout(controller.getLayout());
        mapPanel.select(null);
        eventLog.clear();
        log.setText("");
        onSelectionChanged();
        summaryShown = false;
        refresh();
    }

    private void onFrame() {
        long now = System.nanoTime();
        double elapsed = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1_000_000.0;
        lastFrameNanos = now;
        if (controller.isRunning()) {
            controller.advance(elapsed);
            refresh();
        }
        if (controller.isFinished() && !summaryShown) {
            summaryShown = true;
            refresh();
            showSummary();
        }
    }

    private void refresh() {
        mapPanel.showStaff(controller.staffMarkers());
        mapPanel.showPatients(controller.markers(), controller.overflow());
        LiveCounters counters = controller.counters();
        set("Llegados", counters.arrived() + " / " + counters.totalPatients());
        set("En espera", counters.waiting());
        set("En tratamiento", counters.inTreatment());
        set("En UCI", counters.inIcu());
        set("Alta", counters.discharged());
        set("Remitidos", counters.referred());
        set("Fallecidos", counters.deceased());
        set("Sin atender", counters.unattended());
        set("Camas", counters.freeBeds() + " / " + counters.totalBeds());
        set("Camas UCI", counters.freeIcuBeds() + " / " + counters.totalIcuBeds());
        set("Quirófanos", counters.freeOperatingRooms() + " / " + counters.totalOperatingRooms());
        set("Médicos", counters.freeDoctors() + " / " + counters.totalDoctors());
        set("Enfermeros", counters.freeNurses() + " / " + counters.totalNurses());
        set("Sangre (unidades)", counters.bloodUnits());
        set("Morgue (ocupada)", counters.morgueOccupancy() + " / " + counters.morgueCapacity());
        counterLabels.get("Fallecidos").setForeground(counters.deceased() > 0 ? new Color(0xC6, 0x28, 0x28)
                : getForeground());
        clockLabel.setText(SimulationClock.format(controller.getMinute(), controller.getSecond())
                + "   (minuto " + counters.minute() + ")");
        progressBar.setValue(counters.progressPercent());
        progressBar.setString(counters.finished() + " de " + counters.totalPatients()
                + " pacientes terminaron su caso (" + counters.progressPercent() + " %)");
        appendEvents();
        updateCard();
        updateButtons();
    }

    private void set(String name, Object value) {
        counterLabels.get(name).setText(String.valueOf(value));
    }

    /** Anota los eventos nuevos en el registro y escribe los que cumplen el filtro. */
    private void appendEvents() {
        StringBuilder text = new StringBuilder();
        String now = SimulationClock.formatTimeOfDay(controller.getMinute(), controller.getSecond());
        EventFilter filter = currentFilter();
        for (SimulationEvent event : controller.drainNewEvents()) {
            EventLog.Entry entry = eventLog.add(now, event);
            if (filter.accepts(event)) {
                text.append(entry.line()).append(System.lineSeparator());
            }
        }
        if (text.isEmpty()) {
            return;
        }
        log.append(text.toString());
        int extra = log.getLineCount() - MAX_LOG_LINES;
        if (extra > 0) {
            try {
                log.replaceRange("", 0, log.getLineEndOffset(extra - 1));
            } catch (BadLocationException error) {
                log.setText("");
            }
        }
        log.setCaretPosition(log.getDocument().getLength());
    }

    /** Filtro que eligió el usuario: el grupo de eventos y, si lo pidió, solo el paciente seleccionado. */
    private EventFilter currentFilter() {
        EventCategory category = (EventCategory) categoryBox.getSelectedItem();
        String patient = onlySelectedBox.isSelected() ? mapPanel.getSelected().orElse(null) : null;
        return new EventFilter(category == null ? EventCategory.ALL : category, patient);
    }

    /** Vuelve a escribir el registro con los eventos anteriores que cumplen el filtro actual. */
    private void showLog() {
        StringBuilder text = new StringBuilder();
        for (EventLog.Entry entry : eventLog.entries(currentFilter(), MAX_LOG_LINES)) {
            text.append(entry.line()).append(System.lineSeparator());
        }
        log.setText(text.toString());
        log.setCaretPosition(log.getDocument().getLength());
    }

    /** Cuando cambia el paciente seleccionado: su ficha, la cámara y el registro si se filtra por paciente. */
    private void onSelectionChanged() {
        Optional<String> selected = mapPanel.getSelected();
        onlySelectedBox.setEnabled(selected.isPresent());
        if (selected.isEmpty()) {
            onlySelectedBox.setSelected(false);
            mapPanel.setFollowing(false);
            cardPanel.clear();
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
            cardPanel.showCard(card.get());
        } else if (cardPanel.isShowingCard() || mapPanel.isFollowing()) {
            mapPanel.setFollowing(false);
            cardPanel.followButton().setSelected(false);
            cardPanel.showGone(selected.get());
        }
    }

    private void updateButtons() {
        boolean finished = controller.isFinished();
        playButton.setText(controller.isRunning() ? "Pausar" : "Iniciar");
        playButton.setEnabled(!finished);
        stepButton.setEnabled(!finished && !controller.isRunning());
        skipButton.setEnabled(!finished);
    }

    private void showSummary() {
        controller.metrics().ifPresent(metrics -> JOptionPane.showMessageDialog(this, summaryText(metrics),
                "Simulación terminada", JOptionPane.INFORMATION_MESSAGE));
    }

    private String summaryText(RunMetrics metrics) {
        return String.format("Estrategia: %s%n%nPacientes: %d%nSobrevivientes: %d%nFallecidos: %d (%.1f %%)%n"
                        + "Muertes evitables: %d%nRemitidos: %d%nSin atender: %d%n"
                        + "Espera promedio: %.1f min%nEspera promedio de urgentes: %.1f min%n"
                        + "Años de vida salvados: %.0f%nMinutos simulados: %d",
                metrics.strategyName(), metrics.patients(), metrics.survivors(), metrics.deaths(),
                100 * metrics.mortalityRate(), metrics.avoidableDeaths(), metrics.referred(), metrics.unattended(),
                metrics.averageWaitMinutes(), metrics.urgentAverageWaitMinutes(), metrics.lifeYearsSaved(),
                controller.getMinute());
    }

    private void saveCapture() {
        boolean wasRunning = controller.isRunning();
        controller.pause();
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Imagen PNG", "png"));
        chooser.setSelectedFile(new File("simulacion-minuto-" + controller.getMinute() + ".png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
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
