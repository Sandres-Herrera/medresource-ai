package com.medresource.ui.view;

import com.medresource.model.IndicatorComparison;
import com.medresource.model.RunComparison;
import com.medresource.model.SavedComparison;
import com.medresource.services.management.RunComparator;

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
import java.awt.Dimension;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Lista de las comparaciones guardadas en la base de datos, de la más reciente a la más antigua.
 *
 * <p>Cada fila dice el número de la comparación, el caso, cuándo se guardó, qué ejecuciones compara, el tipo
 * (estrategias, recursos o mixta), las estrategias y cómo cambiaron los fallecidos, y tiene los botones «Abrir» y
 * «Eliminar». Mientras se consulta o se borra en la base de datos, si no hay comparaciones o si la consulta falla, en
 * lugar de la lista se ve un mensaje.</p>
 *
 * <p>«Eliminar» no borra nada por sí mismo: avisa a quien maneja la lista ({@link #onDelete}), que debe preguntar
 * antes ({@link #deleteQuestion}) y luego borrar.</p>
 */
public final class SavedComparisonsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Títulos de las columnas, en el orden en que se ven. */
    public static final List<String> COLUMNS = List.of("Comparación", "Caso", "Guardada", "Ejecuciones", "Tipo",
            "Estrategias", "Fallecidos");
    /** Texto del botón que abre la comparación de cada fila. */
    public static final String OPEN = "Abrir";
    /** Texto del botón que elimina la comparación de cada fila. */
    public static final String DELETE = "Eliminar";
    /** Mensaje mientras se consulta la base de datos. */
    public static final String LOADING_TEXT = "Buscando las comparaciones guardadas…";
    /** Mensaje cuando todavía no hay comparaciones. */
    public static final String EMPTY_TEXT = "Todavía no hay comparaciones guardadas. Compara dos simulaciones del "
            + "mismo caso desde «Comparar simulaciones» en el inicio y pulsa «Guardar comparación».";

    private static final int[] WIDTHS = {110, 60, 120, 110, 110, 300, 90};
    private static final int TYPE_COLUMN = 4;
    private static final int ROW_HEIGHT = 34;
    private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("d/MM/yyyy HH:mm");

    private final JLabel message = new JLabel();
    private final JPanel rows = new JPanel();
    private final List<List<String>> texts = new ArrayList<>();
    private final List<HomeButton> openButtons = new ArrayList<>();
    private final List<HomeButton> deleteButtons = new ArrayList<>();
    private transient IntConsumer onOpen = id -> { };
    private transient Consumer<SavedComparison> onDelete = saved -> { };

    /** Crea el panel mostrando que se están buscando las comparaciones. */
    public SavedComparisonsPanel() {
        super(new BorderLayout(0, 14));
        setBackground(HomeTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        add(header(), BorderLayout.NORTH);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(rows, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(HomeTheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        DarkScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);
        message.setFont(HomeTheme.font(Font.PLAIN, 14f));
        showLoading();
    }

    private static JComponent header() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        JComponent title = ThemedCard.heading(HomeIcon.BRANCH, "Comparaciones guardadas", HomeTheme.ACCENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Abre una comparación para ver qué cambió entre las dos ejecuciones y cómo "
                + "cambiaron sus resultados, o elimínala si ya no la necesitas.");
        subtitle.setFont(HomeTheme.font(Font.PLAIN, 13f));
        subtitle.setForeground(HomeTheme.MUTED);
        subtitle.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 0));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(subtitle);
        return header;
    }

    /**
     * Indica qué hacer al pulsar «Abrir».
     *
     * @param action recibe el identificador de la comparación
     * @throws IllegalArgumentException si es nula
     */
    public void onOpen(IntConsumer action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de abrir es obligatoria.");
        }
        this.onOpen = action;
    }

    /**
     * Indica qué hacer al pulsar «Eliminar».
     *
     * @param action recibe la comparación de la fila, tal como se ve en la lista
     * @throws IllegalArgumentException si es nula
     */
    public void onDelete(Consumer<SavedComparison> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de eliminar es obligatoria.");
        }
        this.onDelete = action;
    }

    /**
     * Pregunta que se hace antes de eliminar una comparación: dice que las simulaciones se conservan y que no se
     * puede deshacer.
     *
     * @param saved comparación que se va a eliminar
     * @return la pregunta, en varias líneas
     * @throws IllegalArgumentException si falta la comparación
     */
    public static String deleteQuestion(SavedComparison saved) {
        if (saved == null) {
            throw new IllegalArgumentException("La comparación es obligatoria.");
        }
        RunComparison comparison = saved.comparison();
        return "¿Eliminar la comparación #" + saved.id() + " (ejecuciones #" + comparison.runA() + " vs #"
                + comparison.runB() + ")?\n\n"
                + "Las dos simulaciones y sus reportes se conservan: puedes volver a compararlas cuando quieras.\n\n"
                + "Esto no se puede deshacer.";
    }

    /**
     * Muestra que se está eliminando una comparación.
     *
     * @param saved comparación que se está eliminando
     * @throws IllegalArgumentException si falta la comparación
     */
    public void showDeleting(SavedComparison saved) {
        if (saved == null) {
            throw new IllegalArgumentException("La comparación es obligatoria.");
        }
        showMessage("Eliminando la comparación #" + saved.id() + "…", HomeTheme.MUTED);
    }

    /** Muestra que se están buscando las comparaciones. */
    public void showLoading() {
        showMessage(LOADING_TEXT, HomeTheme.MUTED);
    }

    /**
     * Muestra un aviso en lugar de la lista, por ejemplo que el equipo no tiene base de datos.
     *
     * @param text el aviso
     */
    public void showNotice(String text) {
        showMessage(text, HomeTheme.MUTED);
    }

    /**
     * Muestra un error en lugar de la lista.
     *
     * @param text explicación del problema
     */
    public void showError(String text) {
        showMessage(text, HomeTheme.DANGER);
    }

    /**
     * Muestra las comparaciones; si no hay ninguna, lo dice.
     *
     * @param comparisons comparaciones, en el orden en que se verán
     * @throws IllegalArgumentException si la lista es nula
     */
    public void showComparisons(List<SavedComparison> comparisons) {
        if (comparisons == null) {
            throw new IllegalArgumentException("La lista de comparaciones es obligatoria.");
        }
        if (comparisons.isEmpty()) {
            showMessage(EMPTY_TEXT, HomeTheme.MUTED);
            return;
        }
        clear();
        rows.add(headerRow());
        rows.add(Box.createVerticalStrut(6));
        for (SavedComparison saved : comparisons) {
            rows.add(row(saved));
            rows.add(Box.createVerticalStrut(8));
        }
        refresh();
    }

    private void showMessage(String text, Color color) {
        clear();
        message.setText("<html>" + text + "</html>");
        message.setForeground(color);
        message.setAlignmentX(Component.LEFT_ALIGNMENT);
        rows.add(message);
        refresh();
    }

    private void clear() {
        rows.removeAll();
        texts.clear();
        openButtons.clear();
        deleteButtons.clear();
    }

    private void refresh() {
        rows.revalidate();
        rows.repaint();
    }

    private static JComponent headerRow() {
        JPanel row = line();
        row.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        for (int i = 0; i < COLUMNS.size(); i++) {
            row.add(cell(COLUMNS.get(i).toUpperCase(), i, HomeTheme.FAINT, Font.BOLD, 11.5f));
        }
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private JComponent row(SavedComparison saved) {
        RunComparison comparison = saved.comparison();
        List<String> values = List.of("#" + saved.id(), "#" + comparison.caseId(), SAVED_AT.format(saved.savedAt()),
                "#" + comparison.runA() + " vs #" + comparison.runB(), comparison.type().getDisplayName(),
                strategies(comparison), deaths(comparison));
        texts.add(values);
        ThemedCard card = new ThemedCard(new BorderLayout(), 0);
        card.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 12));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel line = line();
        for (int i = 0; i < values.size(); i++) {
            Color color = i == 0 ? HomeTheme.ACCENT : i == TYPE_COLUMN ? HomeTheme.VIOLET : HomeTheme.TEXT;
            line.add(cell(values.get(i), i, color, i == 0 || i == TYPE_COLUMN ? Font.BOLD : Font.PLAIN, 13f));
        }
        line.add(Box.createHorizontalGlue());
        HomeButton open = new HomeButton(HomeButton.Style.OUTLINE, OPEN, HomeIcon.DOCUMENT);
        open.addActionListener(event -> onOpen.accept(saved.id()));
        openButtons.add(open);
        line.add(open);
        line.add(Box.createHorizontalStrut(8));
        HomeButton delete = new HomeButton(HomeButton.Style.OUTLINE, DELETE, HomeIcon.TRASH);
        delete.setToolTipText("Eliminar esta comparación; las simulaciones se conservan");
        delete.addActionListener(event -> onDelete.accept(saved));
        deleteButtons.add(delete);
        line.add(delete);
        card.add(line, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    /** Las estrategias de las dos ejecuciones, o una sola si es la misma: «FIFO → Prioridad clínica». */
    static String strategies(RunComparison comparison) {
        return comparison.strategyA().equals(comparison.strategyB()) ? comparison.strategyA()
                : comparison.strategyA() + " → " + comparison.strategyB();
    }

    /** Cómo cambiaron los fallecidos: «8 → 4», o «—» si la comparación no los tiene. */
    static String deaths(RunComparison comparison) {
        return comparison.indicator(RunComparator.DEATHS).map(SavedComparisonsPanel::change).orElse("—");
    }

    private static String change(IndicatorComparison indicator) {
        return ReportFormat.decimal(indicator.valueA()) + " → " + ReportFormat.decimal(indicator.valueB());
    }

    private static JPanel line() {
        JPanel line = new JPanel();
        line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
        line.setOpaque(false);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    private static JLabel cell(String text, int column, Color color, int style, float size) {
        JLabel cell = new JLabel(text);
        cell.setFont(HomeTheme.font(style, size));
        cell.setForeground(color);
        cell.setToolTipText(text);
        Dimension fixed = new Dimension(WIDTHS[column], ROW_HEIGHT);
        cell.setPreferredSize(fixed);
        cell.setMinimumSize(fixed);
        cell.setMaximumSize(fixed);
        return cell;
    }

    // ---------- consultas ----------

    /**
     * Devuelve cuántas comparaciones se ven en la lista.
     *
     * @return comparaciones visibles; 0 si se ve un mensaje
     */
    public int rowCount() {
        return texts.size();
    }

    /**
     * Devuelve los textos de una fila, en el orden de {@link #COLUMNS}.
     *
     * @param index posición de la fila, desde 0
     * @return textos de la fila
     */
    public List<String> rowTexts(int index) {
        return texts.get(index);
    }

    /**
     * Devuelve el botón «Abrir» de una fila.
     *
     * @param index posición de la fila, desde 0
     * @return el botón
     */
    public HomeButton openButton(int index) {
        return openButtons.get(index);
    }

    /**
     * Devuelve el botón «Eliminar» de una fila.
     *
     * @param index posición de la fila, desde 0
     * @return el botón
     */
    public HomeButton deleteButton(int index) {
        return deleteButtons.get(index);
    }

    /**
     * Devuelve el mensaje que se ve en lugar de la lista.
     *
     * @return el mensaje, o vacío si se ve la lista
     */
    public String messageText() {
        return rows.isAncestorOf(message) ? message.getText().replaceAll("<[^>]+>", "") : "";
    }

    /**
     * Devuelve el color del mensaje.
     *
     * @return color del mensaje
     */
    public Color messageColor() {
        return message.getForeground();
    }
}
