package com.medresource.ui.view;

import com.medresource.model.SavedReportSummary;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Formulario para elegir las dos ejecuciones guardadas que se comparan.
 *
 * <p>A la izquierda están todas las ejecuciones guardadas; la elegida es la de referencia (A). A la derecha solo
 * aparecen las otras ejecuciones del mismo caso, porque solo así la comparación es justa: los mismos pacientes
 * llegando a la misma hora. Si el caso no tiene otra, el panel explica cómo conseguirla. «Comparar» se activa cuando
 * están elegidas las dos y solo avisa a quien lo usa.</p>
 */
public final class CompareRunsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Texto del botón que compara. */
    public static final String COMPARE = "Comparar";
    /** Mensaje mientras se consulta la base de datos. */
    public static final String LOADING_TEXT = "Buscando las ejecuciones guardadas…";
    /** Mensaje cuando todavía no hay ejecuciones. */
    public static final String EMPTY_TEXT = "Todavía no hay ejecuciones guardadas. Cada simulación que termina con "
            + "su reporte queda guardada y se puede comparar aquí.";
    /** Mensaje de la columna B mientras no se elige la ejecución A. */
    public static final String CHOOSE_A_TEXT = "Elige primero la ejecución de referencia.";
    /** Indicación mientras falta elegir alguna de las dos. */
    public static final String HINT_TEXT = "Elige las dos ejecuciones del mismo caso que quieres comparar.";

    private static final int MESSAGE_WIDTH = 360;
    private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("d/MM HH:mm");

    private final JPanel columnA = column();
    private final JPanel columnB = column();
    private final JLabel status = new JLabel(HINT_TEXT);
    private final HomeButton compare = new HomeButton(HomeButton.Style.COMPACT, COMPARE, HomeIcon.BRANCH);
    private final Map<Integer, SavedReportSummary> runs = new LinkedHashMap<>();
    private final Map<Integer, ChoiceTile> tilesA = new LinkedHashMap<>();
    private final Map<Integer, ChoiceTile> tilesB = new LinkedHashMap<>();
    private final JLabel messageA = message();
    private final JLabel messageB = message();
    private transient BiConsumer<Integer, Integer> compareAction = (a, b) -> { };
    private Integer runA;
    private Integer runB;

    /** Crea el formulario mostrando que se están buscando las ejecuciones. */
    public CompareRunsPanel() {
        super(new BorderLayout(0, 14));
        setBackground(HomeTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(22, 26, 18, 26));
        add(header(), BorderLayout.NORTH);
        JPanel columns = new JPanel(new GridLayout(1, 2, 16, 0));
        columns.setOpaque(false);
        columns.add(card(HomeIcon.TARGET, "Ejecución de referencia (A)", columnA));
        columns.add(card(HomeIcon.BRANCH, "Ejecución que se compara (B)", columnB));
        add(columns, BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        compare.addActionListener(event -> {
            if (runA != null && runB != null) {
                compareAction.accept(runA, runB);
            }
        });
        showLoading();
    }

    private static JComponent header() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JComponent title = ThemedCard.heading(HomeIcon.BRANCH, "Comparar simulaciones", HomeTheme.ACCENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Compara dos ejecuciones del mismo caso para ver qué cambió entre ellas y si "
                + "los resultados mejoraron.");
        subtitle.setFont(HomeTheme.font(Font.PLAIN, 13f));
        subtitle.setForeground(HomeTheme.MUTED);
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(subtitle);
        return header;
    }

    private static JPanel column() {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        return column;
    }

    private static JLabel message() {
        JLabel message = new JLabel();
        message.setFont(HomeTheme.font(Font.PLAIN, 13.5f));
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        return message;
    }

    private static JComponent card(HomeIcon icon, String title, JPanel column) {
        ThemedCard card = new ThemedCard(new BorderLayout(0, 12), 16);
        card.add(ThemedCard.heading(icon, title, HomeTheme.ACCENT), BorderLayout.NORTH);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(column, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        DarkScrollBarUI.apply(scroll);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JComponent footer() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        status.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        status.setForeground(HomeTheme.MUTED);
        footer.add(status, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(compare);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    // ---------- estados ----------

    /** Muestra que se están buscando las ejecuciones. */
    public void showLoading() {
        showMessage(LOADING_TEXT, HomeTheme.MUTED);
    }

    /**
     * Muestra un aviso en lugar de las ejecuciones, por ejemplo que el equipo no tiene base de datos.
     *
     * @param text el aviso
     */
    public void showNotice(String text) {
        showMessage(text, HomeTheme.MUTED);
    }

    /**
     * Muestra un error en lugar de las ejecuciones.
     *
     * @param text explicación del problema
     */
    public void showError(String text) {
        showMessage(text, HomeTheme.DANGER);
    }

    private void showMessage(String text, Color color) {
        runs.clear();
        runA = null;
        runB = null;
        fill(columnA, tilesA, List.of(), messageA, text, color);
        fill(columnB, tilesB, List.of(), messageB, "", HomeTheme.MUTED);
        updateCompare();
    }

    /**
     * Muestra las ejecuciones guardadas, sin ninguna elegida; si no hay ninguna, lo dice.
     *
     * @param summaries reportes guardados, de los que se toma cada ejecución una vez
     * @throws IllegalArgumentException si la lista es nula
     */
    public void showRuns(List<SavedReportSummary> summaries) {
        if (summaries == null) {
            throw new IllegalArgumentException("La lista de ejecuciones es obligatoria.");
        }
        if (summaries.isEmpty()) {
            showMessage(EMPTY_TEXT, HomeTheme.MUTED);
            return;
        }
        runs.clear();
        for (SavedReportSummary summary : summaries) {
            runs.putIfAbsent(summary.resultId(), summary);
        }
        runA = null;
        runB = null;
        fill(columnA, tilesA, List.copyOf(runs.values()), messageA, "", HomeTheme.MUTED);
        tilesA.forEach((id, tile) -> tile.addActionListener(event -> selectA(id)));
        fill(columnB, tilesB, List.of(), messageB, CHOOSE_A_TEXT, HomeTheme.MUTED);
        updateCompare();
    }

    private void fill(JPanel column, Map<Integer, ChoiceTile> tiles, List<SavedReportSummary> shown, JLabel message,
                      String text, Color color) {
        column.removeAll();
        tiles.clear();
        if (shown.isEmpty()) {
            message.setText("<html><body style='width:" + MESSAGE_WIDTH + "px'>" + text + "</body></html>");
            message.setForeground(color);
            column.add(message);
        }
        for (SavedReportSummary summary : shown) {
            ChoiceTile tile = new ChoiceTile(HomeIcon.PULSE, HomeTheme.scenarioColor(summary.config().scenario()),
                    title(summary), detail(summary));
            tile.setAlignmentX(Component.LEFT_ALIGNMENT);
            tiles.put(summary.resultId(), tile);
            column.add(tile);
            column.add(Box.createVerticalStrut(8));
        }
        column.revalidate();
        column.repaint();
    }

    /** Título de una ejecución: «Ejecución #9 · caso #3», con la ejecución en que se basa si la tiene. */
    static String title(SavedReportSummary summary) {
        return "Ejecución #" + summary.resultId() + " · caso #" + summary.caseId()
                + summary.basedOn().map(base -> " · de #" + base).orElse("");
    }

    /** Detalle de una ejecución: escenario, estrategia, fallecidos y cuándo se guardó. */
    static String detail(SavedReportSummary summary) {
        return summary.config().scenario().getDisplayName() + " · " + summary.strategyName() + " · "
                + summary.deaths() + (summary.deaths() == 1 ? " fallecido" : " fallecidos") + " · "
                + SAVED_AT.format(summary.savedAt());
    }

    // ---------- elegir ----------

    /**
     * Elige la ejecución de referencia; la columna B pasa a mostrar las otras ejecuciones de su caso.
     *
     * @param id número de la ejecución
     * @throws IllegalArgumentException si no está en la lista
     */
    public void selectA(int id) {
        SavedReportSummary chosen = runs.get(id);
        if (chosen == null) {
            throw new IllegalArgumentException("La ejecución " + id + " no está en la lista.");
        }
        runA = id;
        runB = null;
        tilesA.forEach((each, tile) -> tile.setSelected(each == id));
        List<SavedReportSummary> partners = runs.values().stream()
                .filter(other -> other.caseId() == chosen.caseId() && other.resultId() != id).toList();
        fill(columnB, tilesB, partners, messageB, "El caso #" + chosen.caseId() + " solo tiene esta ejecución "
                + "guardada. Para tener otra con la que compararla, abre su reporte en «Reportes guardados» y usa "
                + "«Reutilizar caso» o «Probar recomendaciones».", HomeTheme.AMBER);
        tilesB.forEach((each, tile) -> tile.addActionListener(event -> selectB(each)));
        updateCompare();
    }

    /**
     * Elige la ejecución que se compara con la de referencia.
     *
     * @param id número de la ejecución
     * @throws IllegalArgumentException si no es otra ejecución del caso de la de referencia
     */
    public void selectB(int id) {
        if (!tilesB.containsKey(id)) {
            throw new IllegalArgumentException("La ejecución " + id + " no se puede comparar con la elegida.");
        }
        runB = id;
        tilesB.forEach((each, tile) -> tile.setSelected(each == id));
        updateCompare();
    }

    private void updateCompare() {
        boolean ready = runA != null && runB != null;
        compare.setEnabled(ready);
        showStatus(ready ? "Se comparará la ejecución #" + runA + " con la #" + runB + "." : HINT_TEXT,
                HomeTheme.MUTED);
    }

    /**
     * Cambia lo que se hace al pulsar «Comparar».
     *
     * @param action recibe la ejecución de referencia y la que se compara
     * @throws IllegalArgumentException si es nula
     */
    public void onCompare(BiConsumer<Integer, Integer> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de comparar es obligatoria.");
        }
        this.compareAction = action;
    }

    /** Muestra que se está comparando y desactiva el botón. */
    public void showComparing() {
        compare.setEnabled(false);
        showStatus("Comparando las ejecuciones…", HomeTheme.MUTED);
    }

    /** Vuelve a dejar comparar, por ejemplo cuando la comparación ya se abrió en su ventana. */
    public void showCompared() {
        updateCompare();
    }

    /**
     * Muestra por qué no se pudo comparar y deja volver a intentarlo.
     *
     * @param text explicación
     */
    public void showCompareError(String text) {
        compare.setEnabled(runA != null && runB != null);
        showStatus(text, HomeTheme.DANGER);
    }

    private void showStatus(String text, Color color) {
        status.setText(text);
        status.setForeground(color);
    }

    // ---------- consultas ----------

    public HomeButton compareButton() {
        return compare;
    }

    public String statusText() {
        return status.getText();
    }

    public Color statusColor() {
        return status.getForeground();
    }

    public Optional<Integer> selectedA() {
        return Optional.ofNullable(runA);
    }

    public Optional<Integer> selectedB() {
        return Optional.ofNullable(runB);
    }

    /**
     * Ejecuciones que se pueden elegir como referencia, en orden.
     *
     * @return sus números
     */
    public List<Integer> runsA() {
        return new ArrayList<>(tilesA.keySet());
    }

    /**
     * Ejecuciones que se pueden comparar con la de referencia, en orden.
     *
     * @return sus números; vacía si no se eligió la de referencia o su caso no tiene otra
     */
    public List<Integer> runsB() {
        return new ArrayList<>(tilesB.keySet());
    }

    /**
     * Opción de la columna A de una ejecución.
     *
     * @param id número de la ejecución
     * @return su opción, o nula si no está
     */
    public ChoiceTile tileA(int id) {
        return tilesA.get(id);
    }

    /**
     * Opción de la columna B de una ejecución.
     *
     * @param id número de la ejecución
     * @return su opción, o nula si no está
     */
    public ChoiceTile tileB(int id) {
        return tilesB.get(id);
    }

    /**
     * Mensaje de la columna A, cuando se ve en lugar de las ejecuciones.
     *
     * @return el mensaje, o vacío si se ven las ejecuciones
     */
    public String messageA() {
        return columnA.isAncestorOf(messageA) ? messageA.getText().replaceAll("<[^>]+>", "") : "";
    }

    /**
     * Mensaje de la columna B, cuando se ve en lugar de las ejecuciones.
     *
     * @return el mensaje, o vacío si se ven las ejecuciones
     */
    public String messageB() {
        return columnB.isAncestorOf(messageB) ? messageB.getText().replaceAll("<[^>]+>", "") : "";
    }

    public Color messageAColor() {
        return messageA.getForeground();
    }
}
