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
import java.awt.Dimension;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Lista de los reportes guardados en la base de datos, del más reciente al más antiguo.
 *
 * <p>Cada fila dice el número de la ejecución (y en cuál se basa, si se volvió a simular un caso guardado), el
 * caso, cuándo se guardó, el escenario, la semilla y los pacientes, los fallecidos, la estrategia y quién redactó
 * el texto, y tiene los botones «Abrir» y «Eliminar». Mientras se consulta o se borra en la base de datos, si no
 * hay reportes o si la consulta falla, en lugar de la lista se ve un mensaje.</p>
 *
 * <p>«Eliminar» no borra nada por sí mismo: avisa a quien maneja la lista ({@link #onDelete}), que debe preguntar
 * antes ({@link #deleteQuestion}) y luego borrar.</p>
 */
public final class SavedReportsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Títulos de las columnas, en el orden en que se ven. */
    public static final List<String> COLUMNS = List.of("Ejecución", "Caso", "Guardado", "Escenario", "Semilla",
            "Pacientes", "Fallecidos", "Estrategia", "Texto");
    /** Texto del botón que abre el reporte de cada fila. */
    public static final String OPEN = "Abrir";
    /** Texto del botón que elimina el reporte de cada fila. */
    public static final String DELETE = "Eliminar";
    /** Mensaje mientras se consulta la base de datos. */
    public static final String LOADING_TEXT = "Buscando los reportes guardados…";
    /** Mensaje cuando todavía no hay reportes. */
    public static final String EMPTY_TEXT = "Todavía no hay reportes guardados. Cada reporte que generes al terminar "
            + "una simulación se guarda aquí.";

    private static final int[] WIDTHS = {100, 60, 120, 120, 60, 70, 100, 180, 110};
    private static final int SCENARIO_COLUMN = 3;
    private static final int ROW_HEIGHT = 34;
    private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("d/MM/yyyy HH:mm");

    private final JLabel message = new JLabel();
    private final JPanel rows = new JPanel();
    private final List<List<String>> texts = new ArrayList<>();
    private final List<HomeButton> openButtons = new ArrayList<>();
    private final List<HomeButton> deleteButtons = new ArrayList<>();
    private transient IntConsumer onOpen = id -> { };
    private transient Consumer<SavedReportSummary> onDelete = summary -> { };

    /** Crea el panel mostrando que se están buscando los reportes. */
    public SavedReportsPanel() {
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
        JComponent title = ThemedCard.heading(HomeIcon.DOCUMENT, "Reportes guardados", HomeTheme.ACCENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = new JLabel("Abre un reporte para verlo tal como se generó y descargarlo en Word o PDF, "
                + "o elimínalo si ya no lo necesitas.");
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
     * @param action recibe el identificador del reporte
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
     * @param action recibe el reporte de la fila, tal como se ve en la lista
     * @throws IllegalArgumentException si es nula
     */
    public void onDelete(Consumer<SavedReportSummary> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de eliminar es obligatoria.");
        }
        this.onDelete = action;
    }

    /**
     * Pregunta que se hace antes de eliminar un reporte: dice qué más se borra con él y que no se puede deshacer.
     *
     * @param summary reporte que se va a eliminar
     * @return la pregunta, en varias líneas
     * @throws IllegalArgumentException si falta el reporte
     */
    public static String deleteQuestion(SavedReportSummary summary) {
        if (summary == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        return "¿Eliminar el reporte de la ejecución #" + summary.resultId() + " ("
                + summary.config().scenario().getDisplayName() + ", semilla " + summary.config().seed() + ")?\n\n"
                + "También se borran la simulación, con el resultado de cada paciente, y las comparaciones que la "
                + "usan.\nLas simulaciones que se hicieron a partir de ella se conservan.\n\n"
                + "Esto no se puede deshacer.";
    }

    /**
     * Muestra que se está eliminando un reporte.
     *
     * @param summary reporte que se está eliminando
     * @throws IllegalArgumentException si falta el reporte
     */
    public void showDeleting(SavedReportSummary summary) {
        if (summary == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        showMessage("Eliminando el reporte de la ejecución #" + summary.resultId() + "…", HomeTheme.MUTED);
    }

    /** Muestra que se están buscando los reportes. */
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
     * Muestra los reportes; si no hay ninguno, lo dice.
     *
     * @param summaries reportes, en el orden en que se verán
     * @throws IllegalArgumentException si la lista es nula
     */
    public void showReports(List<SavedReportSummary> summaries) {
        if (summaries == null) {
            throw new IllegalArgumentException("La lista de reportes es obligatoria.");
        }
        if (summaries.isEmpty()) {
            showMessage(EMPTY_TEXT, HomeTheme.MUTED);
            return;
        }
        clear();
        rows.add(headerRow());
        rows.add(Box.createVerticalStrut(6));
        for (SavedReportSummary summary : summaries) {
            rows.add(row(summary));
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

    private JComponent row(SavedReportSummary summary) {
        List<String> values = List.of(runLabel(summary), "#" + summary.caseId(), SAVED_AT.format(summary.savedAt()),
                summary.config().scenario().getDisplayName(), String.valueOf(summary.config().seed()),
                String.valueOf(summary.config().patientCount()),
                summary.deaths() + " (" + ReportFormat.percent(summary.mortalityRate()) + ")",
                summary.strategyName(), summary.source().getLabel());
        texts.add(values);
        ThemedCard card = new ThemedCard(new BorderLayout(), 0);
        card.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 12));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel line = line();
        for (int i = 0; i < values.size(); i++) {
            Color color = i == SCENARIO_COLUMN ? HomeTheme.scenarioColor(summary.config().scenario())
                    : i == 0 ? HomeTheme.ACCENT : HomeTheme.TEXT;
            line.add(cell(values.get(i), i, color, i == SCENARIO_COLUMN || i == 0 ? Font.BOLD : Font.PLAIN, 13f));
        }
        line.add(Box.createHorizontalGlue());
        HomeButton open = new HomeButton(HomeButton.Style.OUTLINE, OPEN, HomeIcon.DOCUMENT);
        open.addActionListener(event -> onOpen.accept(summary.id()));
        openButtons.add(open);
        line.add(open);
        line.add(Box.createHorizontalStrut(8));
        HomeButton delete = new HomeButton(HomeButton.Style.OUTLINE, DELETE, HomeIcon.TRASH);
        delete.setToolTipText("Eliminar este reporte y su simulación");
        delete.addActionListener(event -> onDelete.accept(summary));
        deleteButtons.add(delete);
        line.add(delete);
        card.add(line, BorderLayout.CENTER);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    /** Número de la ejecución y, si se basa en otra, cuál: «#8» o «#8 · de #5». */
    static String runLabel(SavedReportSummary summary) {
        return "#" + summary.resultId() + summary.basedOn().map(base -> " · de #" + base).orElse("");
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
     * Devuelve cuántos reportes se ven en la lista.
     *
     * @return reportes visibles; 0 si se ve un mensaje
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
