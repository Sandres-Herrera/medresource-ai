package com.medresource.ui.view;

import com.medresource.model.IndicatorComparison;
import com.medresource.model.RunComparison;
import com.medresource.model.SettingChange;
import com.medresource.model.Trend;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.text.DefaultCaret;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Muestra la comparación de dos ejecuciones del mismo caso: qué cambió, sus indicadores lado a lado con la
 * diferencia y si mejoró, la conclusión y las limitaciones.
 *
 * <p>Abajo tiene el botón «Guardar comparación», que solo avisa a quien lo usa; ver la comparación no guarda
 * nada. Una comparación que ya está guardada se muestra sin el botón, con la fecha en que se guardó.</p>
 */
public final class ComparisonPanel extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    /** Texto del botón que guarda la comparación. */
    public static final String SAVE = "Guardar comparación";
    /** Columnas de la tabla de indicadores, sin las dos de las ejecuciones. */
    public static final String INDICATOR = "Indicador";
    /** Columna de la diferencia. */
    public static final String DIFFERENCE = "Diferencia";
    /** Columna del resultado. */
    public static final String RESULT = "Resultado";

    private static final int GAP = 14;
    private static final int UNIT_INCREMENT = 16;

    private final transient RunComparison comparison;
    private final HomeButton save = new HomeButton(HomeButton.Style.COMPACT, SAVE, HomeIcon.DOWNLOAD);
    private final JLabel status = new JLabel(" ");
    private final List<List<String>> table = new ArrayList<>();
    private final List<Color> resultColors = new ArrayList<>();
    private transient Runnable saveAction = () -> { };

    /**
     * Crea el panel.
     *
     * @param comparison comparación que se muestra
     * @throws IllegalArgumentException si es nula
     */
    public ComparisonPanel(RunComparison comparison) {
        super(new BorderLayout(0, GAP));
        if (comparison == null) {
            throw new IllegalArgumentException("La comparación es obligatoria.");
        }
        this.comparison = comparison;
        setBackground(HomeTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));
        add(header(), BorderLayout.NORTH);
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.add(changesCard());
        body.add(Box.createVerticalStrut(GAP));
        body.add(indicatorsCard());
        body.add(Box.createVerticalStrut(GAP));
        body.add(textCard(HomeIcon.TARGET, "Conclusión", List.of(comparison.conclusion()), HomeTheme.TEXT));
        body.add(Box.createVerticalStrut(GAP));
        body.add(textCard(HomeIcon.WARNING, "Limitaciones", comparison.limitations().stream()
                .map(text -> "• " + text).toList(), HomeTheme.MUTED));
        add(body, BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        save.addActionListener(event -> saveAction.run());
    }

    private JComponent header() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JComponent title = ThemedCard.heading(HomeIcon.BRANCH, comparison.title(), HomeTheme.ACCENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Caso #" + comparison.caseId() + " · Tipo: " + comparison.type().getDisplayName()
                + ". " + comparison.type().getDescription());
        subtitle.setFont(HomeTheme.font(Font.PLAIN, 13f));
        subtitle.setForeground(HomeTheme.MUTED);
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(subtitle);
        return header;
    }

    private JComponent changesCard() {
        ThemedCard card = card(HomeIcon.LIST, "Qué cambió de la ejecución #" + comparison.runA() + " a la #"
                + comparison.runB());
        JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
        rows.setOpaque(false);
        for (SettingChange change : comparison.changes()) {
            JLabel line = new JLabel(change.describe());
            line.setFont(HomeTheme.font(Font.PLAIN, 13.5f));
            line.setForeground(HomeTheme.TEXT);
            rows.add(line);
        }
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private JComponent indicatorsCard() {
        ThemedCard card = card(HomeIcon.PULSE, "Indicadores");
        JPanel grid = new JPanel(new GridLayout(0, 5, 10, 6));
        grid.setOpaque(false);
        List<String> headers = List.of(INDICATOR, "Ejecución #" + comparison.runA(),
                "Ejecución #" + comparison.runB(), DIFFERENCE, RESULT);
        for (String text : headers) {
            grid.add(cell(text.toUpperCase(), HomeTheme.FAINT, Font.BOLD, 11.5f));
        }
        grid.add(cell("Estrategia", HomeTheme.MUTED, Font.PLAIN, 12.5f));
        grid.add(cell(comparison.strategyA(), HomeTheme.MUTED, Font.PLAIN, 12.5f));
        grid.add(cell(comparison.strategyB(), HomeTheme.MUTED, Font.PLAIN, 12.5f));
        grid.add(cell("", HomeTheme.MUTED, Font.PLAIN, 12.5f));
        grid.add(cell("", HomeTheme.MUTED, Font.PLAIN, 12.5f));
        for (IndicatorComparison indicator : comparison.indicators()) {
            List<String> row = List.of(indicator.name(), value(indicator.valueA(), indicator.unit()),
                    value(indicator.valueB(), indicator.unit()), difference(indicator),
                    indicator.trend().getDisplayName());
            table.add(row);
            Color color = colorOf(indicator.trend());
            resultColors.add(color);
            grid.add(cell(row.get(0), HomeTheme.TEXT, Font.BOLD, 13.5f));
            grid.add(cell(row.get(1), HomeTheme.TEXT, Font.PLAIN, 13.5f));
            grid.add(cell(row.get(2), HomeTheme.TEXT, Font.PLAIN, 13.5f));
            grid.add(cell(row.get(3), color, Font.BOLD, 13.5f));
            grid.add(cell(row.get(4), color, Font.BOLD, 13.5f));
        }
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    private JComponent textCard(HomeIcon icon, String title, List<String> lines, Color color) {
        ThemedCard card = card(icon, title);
        JPanel texts = new JPanel(new GridLayout(0, 1, 0, 6));
        texts.setOpaque(false);
        for (String line : lines) {
            texts.add(paragraph(line, color));
        }
        card.add(texts, BorderLayout.CENTER);
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
        buttons.add(save);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private static ThemedCard card(HomeIcon icon, String title) {
        ThemedCard card = new ThemedCard(new BorderLayout(0, 10), 16);
        card.add(ThemedCard.heading(icon, title, HomeTheme.ACCENT), BorderLayout.NORTH);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }

    private static JLabel cell(String text, Color color, int style, float size) {
        JLabel cell = new JLabel(text);
        cell.setFont(HomeTheme.font(style, size));
        cell.setForeground(color);
        cell.setToolTipText(text.isEmpty() ? null : text);
        return cell;
    }

    private static JTextArea paragraph(String text, Color color) {
        JTextArea area = new JTextArea(text);
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setFont(HomeTheme.font(Font.PLAIN, 13.5f));
        area.setForeground(color);
        return area;
    }

    /** Un valor con su unidad, por ejemplo «35,5 min», «4 %» o «92». */
    static String value(double number, String unit) {
        String text = ReportFormat.decimal(number);
        return unit.isEmpty() ? text : text + " " + unit;
    }

    /** La diferencia con su signo y, si se puede, la variación porcentual: «−4 (−50 %)» o «+3». */
    static String difference(IndicatorComparison indicator) {
        double change = indicator.difference();
        String sign = change < 0 ? "−" : change > 0 ? "+" : "";
        String unit = switch (indicator.unit()) {
            case "" -> "";
            case "%" -> " pts";
            default -> " " + indicator.unit();
        };
        String text = sign + ReportFormat.decimal(Math.abs(change)) + unit;
        if (change != 0 && indicator.relativeChangePercent().isPresent() && !indicator.unit().equals("%")) {
            double percent = indicator.relativeChangePercent().getAsDouble();
            text += " (" + (percent < 0 ? "−" : "+") + ReportFormat.decimal(Math.abs(percent)) + " %)";
        }
        return text;
    }

    private static Color colorOf(Trend trend) {
        return switch (trend) {
            case IMPROVED -> HomeTheme.GREEN;
            case WORSENED -> HomeTheme.DANGER;
            case UNCHANGED -> HomeTheme.MUTED;
        };
    }

    // ---------- guardar ----------

    /**
     * Cambia lo que se hace al pulsar «Guardar comparación».
     *
     * @param action acción
     * @throws IllegalArgumentException si es nula
     */
    public void onSave(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de guardar es obligatoria.");
        }
        this.saveAction = action;
    }

    /** Muestra que se está guardando y desactiva el botón para no guardar dos veces. */
    public void showSaving() {
        save.setEnabled(false);
        showStatus("Guardando la comparación…", HomeTheme.MUTED);
    }

    /**
     * Muestra que la comparación quedó guardada y deja el botón desactivado.
     *
     * @param text por ejemplo «Guardada como comparación #3»
     */
    public void showSaved(String text) {
        save.setEnabled(false);
        showStatus(text, HomeTheme.ACCENT);
    }

    /**
     * Muestra por qué no se pudo guardar y deja volver a intentarlo.
     *
     * @param text explicación
     */
    public void showSaveError(String text) {
        save.setEnabled(true);
        showStatus(text, HomeTheme.DANGER);
    }

    /**
     * Quita el botón de guardar, para una comparación que ya está guardada, y muestra un texto en su lugar.
     *
     * @param text por ejemplo «Guardada el 16/11/2026 11:55»
     */
    public void showAlreadySaved(String text) {
        save.setVisible(false);
        showStatus(text, HomeTheme.MUTED);
    }

    private void showStatus(String text, Color color) {
        status.setText(text);
        status.setForeground(color);
    }

    // ---------- consultas ----------

    public HomeButton saveButton() {
        return save;
    }

    public String statusText() {
        return status.getText();
    }

    public Color statusColor() {
        return status.getForeground();
    }

    /**
     * Textos de una fila de la tabla de indicadores: indicador, valor A, valor B, diferencia y resultado.
     *
     * @param index posición, desde 0
     * @return los textos
     */
    public List<String> indicatorRow(int index) {
        return table.get(index);
    }

    /**
     * Color del resultado de una fila: verde si mejoró, rojo si empeoró y gris si quedó igual.
     *
     * @param index posición, desde 0
     * @return el color
     */
    public Color resultColor(int index) {
        return resultColors.get(index);
    }

    public int indicatorCount() {
        return table.size();
    }

    // ---------- Scrollable: toma el ancho de la ventana y crece hacia abajo ----------

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return UNIT_INCREMENT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(UNIT_INCREMENT, visible.height - UNIT_INCREMENT);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
