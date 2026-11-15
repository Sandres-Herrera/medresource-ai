package com.medresource.ui.view;

import com.medresource.model.DocumentInfo;
import com.medresource.model.ExportFormat;

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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Formulario para descargar el reporte: el formato (Word o PDF) y los datos de la portada APA.
 *
 * <p>Arriba van dos tarjetas para elegir el formato; debajo, los campos de la portada (título, autores separados
 * por comas, institución, programa, curso y docente) y la fecha del día. Al pulsar «Descargar» revisa que estén el
 * título, al menos un autor y la institución; si falta algo, lo dice en rojo y no sigue. Escape cancela.</p>
 */
public final class DownloadPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Título del formulario. */
    public static final String TITLE = "Descargar el reporte";
    /** Aviso cuando faltan datos obligatorios de la portada. */
    public static final String MISSING_DATA = "Escribe el título, al menos un autor y la institución.";

    private final LocalDate date;
    private final String dateText;
    private final Map<ExportFormat, ChoiceTile> formatTiles = new EnumMap<>(ExportFormat.class);
    private final ThemedTextField title;
    private final ThemedTextField authors;
    private final ThemedTextField institution;
    private final ThemedTextField program;
    private final ThemedTextField course;
    private final ThemedTextField teacher;
    private final JLabel error = new JLabel(" ");
    private final IconButton close = new IconButton(HomeIcon.CLOSE, "Cerrar", 30);
    private final HomeButton cancel = new HomeButton(HomeButton.Style.LINK, "Cancelar", null);
    private final HomeButton download = new HomeButton(HomeButton.Style.COMPACT, "Descargar", HomeIcon.DOWNLOAD);
    private ExportFormat format;
    private transient Consumer<Choice> downloadAction = choice -> { };
    private transient Runnable cancelAction = () -> { };

    /**
     * Lo que eligió el usuario.
     *
     * @param format formato del archivo
     * @param info   datos de la portada
     */
    public record Choice(ExportFormat format, DocumentInfo info) {
    }

    /**
     * Crea el formulario con los datos propuestos.
     *
     * @param draft  datos de la portada que se muestran al abrir
     * @param format formato elegido al abrir
     * @throws IllegalArgumentException si falta alguno
     */
    public DownloadPanel(DocumentInfo draft, ExportFormat format) {
        super(new BorderLayout());
        if (draft == null || format == null) {
            throw new IllegalArgumentException("Los datos propuestos y el formato son obligatorios.");
        }
        this.date = draft.date();
        this.dateText = draft.dateText();
        this.format = format;
        title = new ThemedTextField("Título", draft.title());
        authors = new ThemedTextField("Autores", String.join(", ", draft.authors()));
        institution = new ThemedTextField("Institución", draft.institution());
        program = new ThemedTextField("Programa", draft.program());
        course = new ThemedTextField("Curso", draft.course());
        teacher = new ThemedTextField("Docente", draft.teacher());
        setBackground(HomeTheme.BACKGROUND);
        setBorder(BorderFactory.createLineBorder(HomeTheme.CARD_BORDER));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        wireActions();
        refresh();
    }

    /**
     * Cambia lo que se hace al pulsar «Descargar» con los datos completos.
     *
     * @param action recibe el formato y la portada
     * @throws IllegalArgumentException si es nula
     */
    public void onDownload(Consumer<Choice> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de descargar es obligatoria.");
        }
        this.downloadAction = action;
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

    /**
     * Lee lo que escribió el usuario. Si falta algo obligatorio, muestra el aviso en rojo.
     *
     * @return el formato y la portada; vacío si falta el título, los autores o la institución
     */
    public Optional<Choice> choice() {
        try {
            DocumentInfo info = new DocumentInfo(title.getText(), DocumentInfo.parseAuthors(authors.getText()),
                    institution.getText(), program.getText(), course.getText(), teacher.getText(), date);
            error.setText(" ");
            return Optional.of(new Choice(format, info));
        } catch (IllegalArgumentException missing) {
            error.setText(MISSING_DATA);
            return Optional.empty();
        }
    }

    public ExportFormat getFormat() {
        return format;
    }

    /**
     * Elige un formato y marca su tarjeta.
     *
     * @param format formato nuevo
     * @throws IllegalArgumentException si es nulo
     */
    public void setFormat(ExportFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("El formato es obligatorio.");
        }
        this.format = format;
        refresh();
    }

    public ChoiceTile formatTile(ExportFormat format) {
        return formatTiles.get(format);
    }

    public ThemedTextField titleField() {
        return title;
    }

    public ThemedTextField authorsField() {
        return authors;
    }

    public ThemedTextField institutionField() {
        return institution;
    }

    public ThemedTextField programField() {
        return program;
    }

    public ThemedTextField courseField() {
        return course;
    }

    public ThemedTextField teacherField() {
        return teacher;
    }

    public HomeButton downloadButton() {
        return download;
    }

    public HomeButton cancelButton() {
        return cancel;
    }

    public IconButton closeButton() {
        return close;
    }

    public String errorText() {
        return error.getText().strip();
    }

    // ---------- armado ----------

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(16, 20, 10, 14));
        JPanel titles = column();
        titles.add(label(TITLE, Font.BOLD, 19f, HomeTheme.TEXT));
        titles.add(Box.createVerticalStrut(2));
        titles.add(label("Documento en normas APA 7.ª edición (versión de estudiante)", Font.PLAIN, 12.5f,
                HomeTheme.MUTED));
        header.add(titles, BorderLayout.CENTER);
        JPanel corner = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        corner.setOpaque(false);
        corner.add(close);
        header.add(corner, BorderLayout.EAST);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = column();
        body.setBorder(BorderFactory.createEmptyBorder(4, 20, 14, 20));
        ThemedCard formats = new ThemedCard(new BorderLayout(0, 12), 14);
        formats.add(ThemedCard.heading(HomeIcon.DOCUMENT, "Formato", HomeTheme.ACCENT), BorderLayout.NORTH);
        JPanel tiles = new JPanel(new GridLayout(1, ExportFormat.values().length, 8, 0));
        tiles.setOpaque(false);
        for (ExportFormat option : ExportFormat.values()) {
            ChoiceTile tile = new ChoiceTile(HomeIcon.DOCUMENT, option == ExportFormat.WORD ? HomeTheme.BLUE
                    : HomeTheme.RED, option.getLabel(), option.getDetail());
            formatTiles.put(option, tile);
            tiles.add(tile);
        }
        formats.add(tiles, BorderLayout.CENTER);
        ThemedCard cover = new ThemedCard(new BorderLayout(0, 12), 14);
        cover.add(ThemedCard.heading(HomeIcon.USERS, "Portada", HomeTheme.ACCENT), BorderLayout.NORTH);
        JPanel fields = new JPanel(new GridLayout(0, 2, 12, 10));
        fields.setOpaque(false);
        fields.add(labeled("Título", title));
        fields.add(labeled("Autores (separados por comas)", authors));
        fields.add(labeled("Institución", institution));
        fields.add(labeled("Programa", program));
        fields.add(labeled("Curso", course));
        fields.add(labeled("Docente", teacher));
        JPanel coverContent = new JPanel(new BorderLayout(0, 10));
        coverContent.setOpaque(false);
        coverContent.add(fields, BorderLayout.CENTER);
        coverContent.add(label("Fecha: " + dateText
                + " · El programa, el curso y el docente pueden quedar vacíos.", Font.PLAIN, 11.5f, HomeTheme.FAINT),
                BorderLayout.SOUTH);
        cover.add(coverContent, BorderLayout.CENTER);
        formats.setAlignmentX(Component.LEFT_ALIGNMENT);
        cover.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(formats);
        body.add(Box.createVerticalStrut(12));
        body.add(cover);
        return body;
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
        error.setFont(HomeTheme.font(Font.BOLD, 12.5f));
        error.setForeground(HomeTheme.DANGER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(download);
        footer.add(error, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void wireActions() {
        formatTiles.forEach((option, tile) -> tile.addActionListener(event -> setFormat(option)));
        download.addActionListener(event -> choice().ifPresent(chosen -> downloadAction.accept(chosen)));
        cancel.addActionListener(event -> cancelAction.run());
        close.addActionListener(event -> cancelAction.run());
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelar");
        getActionMap().put("cancelar", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                cancelAction.run();
            }
        });
    }

    private void refresh() {
        formatTiles.forEach((option, tile) -> tile.setSelected(option == format));
    }

    // ---------- piezas ----------

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

    /** Columna con el nombre del campo arriba y el campo debajo. */
    private static JComponent labeled(String text, ThemedTextField field) {
        JPanel column = new JPanel(new BorderLayout(0, 4));
        column.setOpaque(false);
        column.add(label(text, Font.PLAIN, 12f, HomeTheme.MUTED), BorderLayout.NORTH);
        column.add(field, BorderLayout.CENTER);
        return column;
    }
}
