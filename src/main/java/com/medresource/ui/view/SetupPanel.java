package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.Scenario;
import com.medresource.ui.controller.HospitalField;
import com.medresource.ui.controller.HospitalPreset;
import com.medresource.ui.controller.SetupDraft;
import com.medresource.ui.controller.SimulationClock;
import com.medresource.ui.controller.SimulationRequest;
import com.medresource.ui.controller.StrategyOption;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Contenido del formulario de una simulación nueva, con el tema oscuro de la pantalla de inicio.
 *
 * <p>Arriba, el título y la equis de cerrar. A la izquierda, el <b>hospital</b> (tamaños pequeño, estándar y
 * grande, y cada recurso con su campo de «−» y «+») y la <b>velocidad inicial</b>. A la derecha, el
 * <b>escenario</b> (cuatro opciones con su color, que dicen cómo llegan los pacientes con el tiempo elegido;
 * pacientes, tiempo de llegadas y semilla, con un dado que sortea otra) y la <b>estrategia</b>. Abajo, un resumen
 * que se actualiza con cada cambio (ritmo de llegada, tamaño del hospital y cuánto dura en pantalla), «Cancelar» e
 * «Iniciar simulación».</p>
 *
 * <p>Lo elegido vive en un {@link SetupDraft}: el panel solo le pasa los cambios y muestra lo que el borrador
 * describe, así que nunca se puede iniciar una simulación con un valor inválido. Escape cancela. Quien lo usa
 * decide qué hacer al iniciar ({@link #onStart}) y al cancelar ({@link #onCancel}).</p>
 */
public final class SetupPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Título del formulario. */
    public static final String TITLE = "Nueva simulación";

    private static final int GAP = 12;
    private static final int STEPPER_WIDTH = 112;

    private final transient SetupDraft draft;
    private final transient Random random;
    private final Map<HospitalPreset, Pill> presetPills = new EnumMap<>(HospitalPreset.class);
    private final Map<HospitalField, Stepper> resourceSteppers = new EnumMap<>(HospitalField.class);
    private final Map<Scenario, ChoiceTile> scenarioTiles = new EnumMap<>(Scenario.class);
    private final Map<StrategyOption, ChoiceTile> strategyTiles = new EnumMap<>(StrategyOption.class);
    private final Map<Integer, Pill> speedPills = new LinkedHashMap<>();
    private final Stepper patients;
    private final Stepper duration;
    private final Stepper seed;
    private final IconButton dice = new IconButton(HomeIcon.DICE, "Sortear otra semilla", 28);
    private final IconButton close = new IconButton(HomeIcon.CLOSE, "Cerrar", 30);
    private final HomeButton cancel = new HomeButton(HomeButton.Style.LINK, "Cancelar", null);
    private final HomeButton start = new HomeButton(HomeButton.Style.COMPACT, "Iniciar simulación", HomeIcon.PLAY);
    private final JLabel summary = new JLabel();
    private transient Consumer<SimulationRequest> startAction = request -> { };
    private transient Runnable cancelAction = () -> { };

    /**
     * Crea el formulario con lo que tiene el borrador.
     *
     * @param draft  borrador de la simulación, que el formulario va cambiando
     * @param random generador con el que el dado sortea semillas
     * @throws IllegalArgumentException si alguno es nulo
     */
    public SetupPanel(SetupDraft draft, Random random) {
        super(new BorderLayout());
        if (draft == null || random == null) {
            throw new IllegalArgumentException("El borrador y el generador de números al azar son obligatorios.");
        }
        this.draft = draft;
        this.random = random;
        this.patients = new Stepper("Pacientes", CaseConfig.MIN_PATIENTS,
                CaseConfig.MAX_PATIENTS, 10, draft.getPatients());
        this.duration = new Stepper("Llegadas", CaseConfig.MIN_DURATION_MINUTES,
                CaseConfig.MAX_DURATION_MINUTES, 30, draft.getDurationMinutes());
        this.seed = new Stepper("Semilla", 0, 999_999_999, 1, (int) Math.min(999_999_999L, draft.getSeed()));
        setBackground(HomeTheme.BACKGROUND);
        setBorder(BorderFactory.createLineBorder(HomeTheme.CARD_BORDER));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        wireActions();
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelar");
        getActionMap().put("cancelar", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                cancelAction.run();
            }
        });
        refresh();
    }

    /**
     * Cambia lo que se hace al pulsar «Iniciar simulación».
     *
     * @param action recibe el pedido con lo elegido
     * @throws IllegalArgumentException si es nula
     */
    public void onStart(Consumer<SimulationRequest> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de iniciar es obligatoria.");
        }
        this.startAction = action;
    }

    /**
     * Cambia lo que se hace al cancelar (con «Cancelar», la equis o Escape).
     *
     * @param action acción
     * @throws IllegalArgumentException si es nula
     */
    public void onCancel(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de cancelar es obligatoria.");
        }
        this.cancelAction = action;
    }

    // ---------- armado ----------

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(16, 20, 10, 14));
        JPanel titles = column();
        titles.add(label(TITLE, Font.BOLD, 19f, HomeTheme.TEXT));
        titles.add(Box.createVerticalStrut(2));
        titles.add(label("Configura el hospital, el caso y la estrategia", Font.PLAIN, 12.5f, HomeTheme.MUTED));
        header.add(titles, BorderLayout.CENTER);
        JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        corner.setOpaque(false);
        corner.add(close);
        header.add(corner, BorderLayout.EAST);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(4, 20, 14, 20));
        GridBagConstraints at = new GridBagConstraints();
        at.fill = GridBagConstraints.BOTH;
        at.weighty = 1;
        at.insets = new Insets(0, 0, 0, GAP);
        at.weightx = 0.42;
        body.add(stack(buildHospitalCard(), buildSpeedCard()), at);
        at.gridx = 1;
        at.weightx = 0.58;
        at.insets = new Insets(0, 0, 0, 0);
        body.add(stack(buildScenarioCard(), buildStrategyCard()), at);
        return body;
    }

    /** Columna con una tarjeta arriba y otra que ocupa el resto del alto. */
    private static JComponent stack(JComponent top, JComponent rest) {
        JPanel column = new JPanel(new BorderLayout(0, GAP));
        column.setOpaque(false);
        column.add(top, BorderLayout.NORTH);
        column.add(rest, BorderLayout.CENTER);
        return column;
    }

    private JComponent buildHospitalCard() {
        Section card = new Section(HomeIcon.HOSPITAL, "Hospital", new BorderLayout(0, 10));
        JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pills.setOpaque(false);
        for (HospitalPreset preset : HospitalPreset.values()) {
            Pill pill = new Pill(preset.getDisplayName());
            presetPills.put(preset, pill);
            pills.add(pill);
        }
        JPanel rows = new JPanel(new GridLayout(HospitalField.values().length, 1, 0, 6));
        rows.setOpaque(false);
        for (HospitalField field : HospitalField.values()) {
            Stepper stepper = new Stepper(field.getDisplayName(), field.getMin(), field.getMax(), 1,
                    field.valueIn(draft.getHospital()));
            resourceSteppers.put(field, stepper);
            rows.add(labeledRow(field.getDisplayName(), stepper));
        }
        card.content().add(pills, BorderLayout.NORTH);
        card.content().add(rows, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildSpeedCard() {
        Section card = new Section(HomeIcon.CLOCK, "Velocidad inicial", new BorderLayout(0, 6));
        JPanel pills = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pills.setOpaque(false);
        for (int speed : SimulationClock.SPEEDS) {
            Pill pill = new Pill("×" + speed);
            speedPills.put(speed, pill);
            pills.add(pill);
        }
        JPanel stack = column();
        pills.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(pills);
        stack.add(Box.createVerticalStrut(10));
        stack.add(label("×1 es tiempo real: un segundo en pantalla es un segundo", Font.PLAIN, 11.5f,
                HomeTheme.FAINT));
        stack.add(label("en el hospital. Se puede cambiar mientras corre.", Font.PLAIN, 11.5f, HomeTheme.FAINT));
        card.content().add(stack, BorderLayout.NORTH);
        return card;
    }

    private JComponent buildScenarioCard() {
        Section card = new Section(HomeIcon.PULSE, "Escenario", new BorderLayout(0, 12));
        JPanel tiles = new JPanel(new GridLayout(2, 2, 8, 8));
        tiles.setOpaque(false);
        for (Scenario scenario : Scenario.values()) {
            ChoiceTile tile = new ChoiceTile(scenarioIcon(scenario), HomeTheme.scenarioColor(scenario),
                    scenario.getDisplayName(), draft.arrivalsOf(scenario));
            scenarioTiles.put(scenario, tile);
            tiles.add(tile);
        }
        JPanel fields = new JPanel(new GridLayout(1, 3, 8, 0));
        fields.setOpaque(false);
        fields.add(labeledColumn("Pacientes", patients));
        fields.add(labeledColumn("Llegadas (min)", duration));
        JPanel seedRow = new JPanel(new BorderLayout(4, 0));
        seedRow.setOpaque(false);
        seedRow.add(seed, BorderLayout.CENTER);
        seedRow.add(dice, BorderLayout.EAST);
        fields.add(labeledColumn("Semilla", seedRow));
        seed.field().setColumns(5);
        JPanel stack = new JPanel(new BorderLayout(0, 14));
        stack.setOpaque(false);
        stack.add(tiles, BorderLayout.NORTH);
        stack.add(fields, BorderLayout.CENTER);
        card.content().add(stack, BorderLayout.NORTH);
        return card;
    }

    private JComponent buildStrategyCard() {
        Section card = new Section(HomeIcon.BRANCH, "Estrategia", new BorderLayout());
        JPanel tiles = new JPanel(new GridLayout(StrategyOption.values().length, 1, 0, 8));
        tiles.setOpaque(false);
        for (StrategyOption option : StrategyOption.values()) {
            ChoiceTile tile = new ChoiceTile(strategyIcon(option), HomeTheme.ACCENT, option.getDisplayName(),
                    option.getDescription());
            strategyTiles.put(option, tile);
            tiles.add(tile);
        }
        card.content().add(tiles, BorderLayout.NORTH);
        return card;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0)) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void paintComponent(Graphics graphics) {
                graphics.setColor(HomeTheme.DIVIDER);
                graphics.fillRect(20, 0, getWidth() - 40, 1);
            }
        };
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(12, 20, 14, 20));
        JPanel info = new JPanel(new GridBagLayout());
        info.setOpaque(false);
        GridBagConstraints at = new GridBagConstraints();
        at.anchor = GridBagConstraints.WEST;
        at.insets = new Insets(0, 0, 0, 8);
        info.add(new IconLabel(HomeIcon.INFO, 16, HomeTheme.MUTED), at);
        summary.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        summary.setForeground(HomeTheme.MUTED);
        at.weightx = 1;
        at.insets = new Insets(0, 0, 0, 0);
        info.add(summary, at);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(start);
        footer.add(info, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void wireActions() {
        presetPills.forEach((preset, pill) -> pill.addActionListener(event -> {
            draft.setHospital(preset.config());
            resourceSteppers.forEach((field, stepper) -> stepper.setValue(field.valueIn(draft.getHospital())));
            refresh();
        }));
        resourceSteppers.forEach((field, stepper) -> stepper.addChangeListener(value -> {
            draft.setResource(field, value);
            refresh();
        }));
        scenarioTiles.forEach((scenario, tile) -> tile.addActionListener(event -> {
            draft.setScenario(scenario);
            refresh();
        }));
        strategyTiles.forEach((option, tile) -> tile.addActionListener(event -> {
            draft.setStrategy(option);
            refresh();
        }));
        speedPills.forEach((speed, pill) -> pill.addActionListener(event -> {
            draft.setSpeed(speed);
            refresh();
        }));
        patients.addChangeListener(value -> {
            draft.setPatients(value);
            refresh();
        });
        duration.addChangeListener(value -> {
            draft.setDurationMinutes(value);
            refresh();
        });
        seed.addChangeListener(value -> {
            draft.setSeed(value);
            refresh();
        });
        dice.addActionListener(event -> seed.setValue((int) draft.rollSeed(random)));
        close.addActionListener(event -> cancelAction.run());
        cancel.addActionListener(event -> cancelAction.run());
        start.addActionListener(event -> {
            patients.commitText();
            duration.commitText();
            seed.commitText();
            resourceSteppers.values().forEach(Stepper::commitText);
            startAction.accept(draft.toRequest());
        });
    }

    /** Muestra lo que tiene el borrador: qué está elegido, cómo llegan los pacientes y el resumen. */
    private void refresh() {
        presetPills.forEach((preset, pill) -> pill.setSelected(draft.getPreset().orElse(null) == preset));
        scenarioTiles.forEach((scenario, tile) -> {
            tile.setSelected(scenario == draft.getScenario());
            tile.setDetail(draft.arrivalsOf(scenario));
        });
        strategyTiles.forEach((option, tile) -> tile.setSelected(option == draft.getStrategy()));
        speedPills.forEach((speed, pill) -> pill.setSelected(speed == draft.getSpeed()));
        summary.setText(draft.summary());
    }

    // ---------- piezas para las pruebas y para la ventana ----------

    public Pill presetPill(HospitalPreset preset) {
        return presetPills.get(preset);
    }

    public Stepper resourceStepper(HospitalField field) {
        return resourceSteppers.get(field);
    }

    public ChoiceTile scenarioTile(Scenario scenario) {
        return scenarioTiles.get(scenario);
    }

    public ChoiceTile strategyTile(StrategyOption option) {
        return strategyTiles.get(option);
    }

    public Pill speedPill(int speed) {
        return speedPills.get(speed);
    }

    public Stepper patientsStepper() {
        return patients;
    }

    public Stepper durationStepper() {
        return duration;
    }

    public Stepper seedStepper() {
        return seed;
    }

    public IconButton diceButton() {
        return dice;
    }

    public IconButton closeButton() {
        return close;
    }

    public HomeButton cancelButton() {
        return cancel;
    }

    public HomeButton startButton() {
        return start;
    }

    /** Texto del resumen de abajo. */
    public String summaryText() {
        return summary.getText();
    }

    // ---------- ayudas ----------

    private static HomeIcon scenarioIcon(Scenario scenario) {
        return switch (scenario) {
            case NORMAL -> HomeIcon.HEART;
            case HIGH_DEMAND -> HomeIcon.USERS;
            case CATASTROPHE -> HomeIcon.WARNING;
            case UNEXPECTED_EMERGENCIES -> HomeIcon.BOLT;
        };
    }

    private static HomeIcon strategyIcon(StrategyOption option) {
        return switch (option) {
            case CLINICAL_PRIORITY -> HomeIcon.STETHOSCOPE;
            case FIFO -> HomeIcon.LIST;
            case RANDOM -> HomeIcon.DICE;
        };
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, int style, float size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(HomeTheme.font(style, size));
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** Fila con el nombre a la izquierda y el campo, de ancho fijo, a la derecha. */
    private static JComponent labeledRow(String text, Stepper stepper) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(label(text, Font.PLAIN, 13f, HomeTheme.MUTED), BorderLayout.CENTER);
        stepper.setPreferredSize(new Dimension(STEPPER_WIDTH, Stepper.HEIGHT));
        row.add(stepper, BorderLayout.EAST);
        return row;
    }

    /** Columna con el nombre arriba y el campo debajo. */
    private static JComponent labeledColumn(String text, JComponent field) {
        JPanel column = new JPanel(new BorderLayout(0, 4));
        column.setOpaque(false);
        column.add(label(text, Font.PLAIN, 12f, HomeTheme.MUTED), BorderLayout.NORTH);
        column.add(field, BorderLayout.CENTER);
        return column;
    }

    /** Tarjeta de una sección: icono y título arriba y el contenido debajo. */
    private static final class Section extends ThemedCard {

        private static final long serialVersionUID = 1L;

        private final JPanel content;

        Section(HomeIcon icon, String title, LayoutManager contentLayout) {
            super(new BorderLayout(0, 12), 14);
            content = new JPanel(contentLayout);
            content.setOpaque(false);
            add(heading(icon, title, HomeTheme.ACCENT), BorderLayout.NORTH);
            add(content, BorderLayout.CENTER);
        }

        JPanel content() {
            return content;
        }
    }
}
