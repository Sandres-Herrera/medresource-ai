package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.GeneratedReport;
import com.medresource.model.NarrativeSource;
import com.medresource.model.ReportNarrative;
import com.medresource.model.ReportSection;
import com.medresource.model.ReviewCase;
import com.medresource.model.RunMetrics;
import com.medresource.model.SimulationReport;
import com.medresource.model.WhatIfResult;

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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Contenido de la ventana del reporte, con el tema oscuro: una tarjeta por sección, de arriba abajo.
 *
 * <p>Arriba van el título, los datos del caso, quién redactó el texto (con la nota de por qué, si se usaron las
 * plantillas) y cuatro indicadores. Después, el resumen; cada sección con su texto y su gráfica
 * ({@link ReportCharts}); las recomendaciones con lo que mejora cada una; la conclusión y los supuestos del
 * simulador. Los casos para revisar tienen el botón «Ver en el plano», que selecciona al paciente en la ventana de
 * la simulación.</p>
 *
 * <p>Se acomoda al ancho de la ventana y crece hacia abajo; la ventana le pone la barra de desplazamiento.</p>
 */
public final class ReportPanel extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    /** Título del reporte. */
    public static final String TITLE = "Reporte de la simulación";
    /** Título de la tarjeta del resumen. */
    public static final String SUMMARY = "Resumen";
    /** Título de la tarjeta de las recomendaciones. */
    public static final String RECOMMENDATIONS = "Recomendaciones";
    /** Título de la tarjeta de la conclusión. */
    public static final String CONCLUSION = "Conclusión";
    /** Título de la tarjeta de los supuestos. */
    public static final String ASSUMPTIONS = "Supuestos y límites del simulador";
    /** Texto del botón de cada caso para revisar. */
    public static final String SHOW_CASE = "Ver en el plano";
    /** Texto cuando ninguna alternativa mejora el hospital. */
    public static final String NO_RECOMMENDATIONS =
            "Ninguno de los cambios que se probaron mejora el hospital de forma clara.";

    private static final int GAP = 12;
    private static final int UNIT_INCREMENT = 24;
    private static final int CARD_PADDING = 18;

    private final transient GeneratedReport generated;
    private final Map<String, JTextArea> texts = new LinkedHashMap<>();
    private final Map<String, JComponent> charts = new LinkedHashMap<>();
    private final List<HomeButton> caseButtons = new ArrayList<>();
    private final List<JTextArea> paragraphs = new ArrayList<>();
    private final List<String> titles = new ArrayList<>();
    private final JLabel source = new JLabel();
    private transient Consumer<String> caseAction = code -> { };
    private int textWidth;
    private int recommendationCount;

    /**
     * Arma el reporte.
     *
     * @param generated reporte terminado
     * @throws IllegalArgumentException si falta el reporte
     */
    public ReportPanel(GeneratedReport generated) {
        if (generated == null) {
            throw new IllegalArgumentException("El reporte es obligatorio.");
        }
        this.generated = generated;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(HomeTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 20, 20));
        ReportNarrative narrative = generated.narrative();
        addBlock(header());
        addBlock(indicators(generated.report().metrics()));
        addBlock(card(SUMMARY, narrative.summary(), null, null));
        for (ReportSection section : ReportSection.values()) {
            Optional<String> text = narrative.sectionText(section);
            Optional<JComponent> chart = ReportCharts.chartFor(section, generated);
            JComponent extra = section == ReportSection.CASES ? cases(generated.report().cases()) : null;
            if (text.isPresent() || chart.isPresent() || extra != null) {
                addBlock(card(section.getTitle(), text.orElse(null), chart.orElse(null), extra));
            }
        }
        addBlock(recommendations());
        addBlock(card(CONCLUSION, narrative.conclusion(), null, null));
        addBlock(card(ASSUMPTIONS, bullets(generated.report().assumptions()), null, null));
    }

    // ---------- armado ----------

    private void addBlock(JComponent block) {
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (getComponentCount() > 0) {
            add(Box.createVerticalStrut(GAP));
        }
        add(block);
    }

    private JComponent header() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(TITLE);
        title.setFont(HomeTheme.font(Font.BOLD, 22f));
        title.setForeground(HomeTheme.TEXT);
        JLabel subtitle = new JLabel(caseText(generated.report()));
        subtitle.setFont(HomeTheme.font(Font.PLAIN, 13f));
        subtitle.setForeground(HomeTheme.MUTED);
        ReportNarrative narrative = generated.narrative();
        source.setText(sourceText(narrative));
        source.setFont(HomeTheme.font(Font.BOLD, 12.5f));
        source.setForeground(narrative.source() == NarrativeSource.AI ? HomeTheme.ACCENT : HomeTheme.AMBER);
        for (JComponent line : List.<JComponent>of(title, subtitle, source)) {
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.add(line);
            header.add(Box.createVerticalStrut(4));
        }
        return header;
    }

    private JComponent indicators(RunMetrics metrics) {
        JPanel row = new JPanel(new GridLayout(1, 4, GAP, 0));
        row.setOpaque(false);
        row.add(tile("Pacientes", String.valueOf(metrics.patients()), HomeTheme.TEXT));
        row.add(tile("Fallecidos", metrics.deaths() + " (" + ReportFormat.percent(metrics.mortalityRate()) + ")",
                HomeTheme.RED));
        row.add(tile("Espera promedio", ReportFormat.decimal(metrics.averageWaitMinutes()) + " min",
                HomeTheme.AMBER));
        row.add(tile("Espera máxima", generated.report().maxWaitMinutes() + " min", HomeTheme.ORANGE));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static JComponent tile(String name, String value, Color color) {
        ThemedCard tile = new ThemedCard(new BorderLayout(0, 2), 10);
        tile.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(HomeTheme.font(Font.PLAIN, 12f));
        nameLabel.setForeground(HomeTheme.MUTED);
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(HomeTheme.font(Font.BOLD, 20f));
        valueLabel.setForeground(color);
        tile.add(nameLabel, BorderLayout.NORTH);
        tile.add(valueLabel, BorderLayout.CENTER);
        return tile;
    }

    /**
     * Arma una tarjeta con su título y, si los tiene, su texto, su gráfica y algo más debajo.
     */
    private ThemedCard card(String title, String text, JComponent chart, JComponent extra) {
        ThemedCard card = new ThemedCard(new BorderLayout(0, 10), CARD_PADDING);
        titles.add(title);
        JLabel heading = new JLabel(title);
        heading.setFont(HomeTheme.font(Font.BOLD, 15f));
        heading.setForeground(HomeTheme.TEXT);
        card.add(heading, BorderLayout.NORTH);
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        List<JComponent> parts = new ArrayList<>();
        if (text != null) {
            JTextArea area = paragraph(text);
            texts.put(title, area);
            paragraphs.add(area);
            parts.add(area);
        }
        if (chart != null) {
            charts.put(title, chart);
            parts.add(chart);
        }
        if (extra != null) {
            parts.add(extra);
        }
        for (JComponent part : parts) {
            part.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (body.getComponentCount() > 0) {
                body.add(Box.createVerticalStrut(10));
            }
            body.add(part);
        }
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private static JTextArea paragraph(String text) {
        JTextArea area = new JTextArea();
        // El cursor no sigue al texto: si lo hiciera, el reporte se abriría desplazado hasta el último párrafo.
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        area.setText(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setFont(HomeTheme.font(Font.PLAIN, 14f));
        area.setForeground(HomeTheme.TEXT);
        area.setCaretColor(HomeTheme.TEXT);
        area.setSelectionColor(HomeTheme.SELECTED);
        return area;
    }

    private JComponent cases(List<ReviewCase> reviewCases) {
        if (reviewCases.isEmpty()) {
            return null;
        }
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (ReviewCase reviewCase : reviewCases) {
            JPanel row = new JPanel(new BorderLayout(12, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
            JLabel what = new JLabel(caseTitle(reviewCase));
            what.setFont(HomeTheme.font(Font.BOLD, 13f));
            what.setForeground(HomeTheme.triageColor(reviewCase.triage()));
            JLabel why = new JLabel(caseDetail(reviewCase));
            why.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
            why.setForeground(HomeTheme.MUTED);
            JPanel lines = new JPanel(new GridLayout(2, 1, 0, 2));
            lines.setOpaque(false);
            lines.add(what);
            lines.add(why);
            HomeButton button = new HomeButton(HomeButton.Style.LINK, SHOW_CASE, null);
            String code = reviewCase.patientCode();
            button.addActionListener(event -> caseAction.accept(code));
            caseButtons.add(button);
            JPanel buttonBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            buttonBox.setOpaque(false);
            buttonBox.add(button);
            row.add(lines, BorderLayout.CENTER);
            row.add(buttonBox, BorderLayout.EAST);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            list.add(row);
        }
        return list;
    }

    private JComponent recommendations() {
        List<WhatIfResult> results = generated.whatIf().recommendations();
        if (results.isEmpty()) {
            return card(RECOMMENDATIONS, NO_RECOMMENDATIONS, null, null);
        }
        List<String> explanations = generated.narrative().recommendations();
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (int i = 0; i < results.size(); i++) {
            WhatIfResult result = results.get(i);
            JLabel name = new JLabel((i + 1) + ". " + result.label());
            name.setFont(HomeTheme.font(Font.BOLD, 14f));
            name.setForeground(HomeTheme.TEXT);
            JLabel effect = new JLabel(effectText(result));
            effect.setFont(HomeTheme.font(Font.BOLD, 12.5f));
            effect.setForeground(HomeTheme.GREEN);
            List<JComponent> lines = new ArrayList<>(List.of(name, effect));
            if (i < explanations.size()) {
                JTextArea explanation = paragraph(explanations.get(i));
                texts.put(recommendationTitle(i + 1), explanation);
                paragraphs.add(explanation);
                lines.add(explanation);
            }
            if (i > 0) {
                list.add(Box.createVerticalStrut(14));
            }
            for (JComponent line : lines) {
                line.setAlignmentX(Component.LEFT_ALIGNMENT);
                list.add(line);
                list.add(Box.createVerticalStrut(3));
            }
        }
        recommendationCount = results.size();
        return card(RECOMMENDATIONS, null, null, list);
    }

    /**
     * Título con el que se busca el texto de una recomendación ({@link #textOf(String)}).
     *
     * @param number número de la recomendación, desde 1
     * @return por ejemplo «Recomendación 1»
     */
    public static String recommendationTitle(int number) {
        return "Recomendación " + number;
    }

    // ---------- textos ----------

    /**
     * Escribe los datos del caso: escenario, estrategia, semilla, pacientes y horas de llegadas.
     *
     * @param report reporte
     * @return por ejemplo «Catástrofe · Prioridad clínica (triage) · Semilla 7 · 100 pacientes · llegadas
     *         durante 8 h»
     */
    static String caseText(SimulationReport report) {
        CaseConfig config = report.config();
        return config.scenario().getDisplayName() + " · " + report.metrics().strategyName() + " · Semilla "
                + config.seed() + " · " + config.patientCount() + " pacientes · llegadas durante "
                + ReportFormat.decimal(config.durationMinutes() / 60.0) + " h";
    }

    /**
     * Escribe quién redactó el texto y, si se usaron las plantillas, por qué.
     *
     * @param narrative texto del reporte
     * @return por ejemplo «Redactado por IA» o «Generado automáticamente · No hay clave de la IA»
     */
    static String sourceText(ReportNarrative narrative) {
        String label = narrative.source().getLabel();
        return narrative.note() == null ? label : label + " · " + narrative.note();
    }

    /**
     * Escribe lo que mejora una recomendación: las muertes que evita y los minutos de espera que ahorra.
     *
     * @param result alternativa recomendada
     * @return por ejemplo «evita 0,8 muertes por cada 100 pacientes · 12,5 min menos de espera»; si no hay
     *         ninguna de las dos cosas, «mejora el hospital»
     */
    static String effectText(WhatIfResult result) {
        List<String> parts = new ArrayList<>();
        if (result.lowersMortality()) {
            parts.add("evita " + ReportCharts.deaths(result.deathsAvoidedPer100()) + " por cada 100 pacientes");
        }
        if (result.waitChange().mean() < 0) {
            parts.add(ReportFormat.decimal(-result.waitChange().mean()) + " min menos de espera");
        }
        return parts.isEmpty() ? "mejora el hospital" : String.join(" · ", parts);
    }

    private static String caseTitle(ReviewCase reviewCase) {
        return reviewCase.patientCode() + " · " + reviewCase.triage().getDisplayName() + " · "
                + reviewCase.status().getDisplayName();
    }

    private static String caseDetail(ReviewCase reviewCase) {
        return reviewCase.reason().getDisplayName() + " · esperó " + reviewCase.waitMinutes()
                + " min · momento clave " + ReportFormat.clock(reviewCase.focusMinute());
    }

    private static String bullets(List<String> lines) {
        return lines.isEmpty() ? "Sin supuestos registrados." : "• " + String.join("\n• ", lines);
    }

    // ---------- acciones y consultas ----------

    /**
     * Indica qué hacer al pulsar «Ver en el plano» en un caso para revisar.
     *
     * @param action recibe el código del paciente
     * @throws IllegalArgumentException si es nula
     */
    public void onShowCase(Consumer<String> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de los casos es obligatoria.");
        }
        this.caseAction = action;
    }

    /**
     * Devuelve los títulos de las tarjetas, en el orden en que se ven.
     *
     * @return títulos
     */
    public List<String> cardTitles() {
        return List.copyOf(titles);
    }

    /**
     * Busca el texto de una tarjeta.
     *
     * @param title título de la tarjeta
     * @return su texto; vacío si no tiene
     */
    public Optional<String> textOf(String title) {
        return Optional.ofNullable(texts.get(title)).map(JTextArea::getText);
    }

    /**
     * Busca la gráfica de una tarjeta.
     *
     * @param title título de la tarjeta
     * @return su gráfica; vacío si no tiene
     */
    public Optional<JComponent> chartOf(String title) {
        return Optional.ofNullable(charts.get(title));
    }

    /**
     * Devuelve los botones «Ver en el plano», uno por caso para revisar, en orden.
     *
     * @return botones
     */
    public List<HomeButton> caseButtons() {
        return List.copyOf(caseButtons);
    }

    /** Cantidad de recomendaciones que se muestran. */
    public int recommendationCount() {
        return recommendationCount;
    }

    /** Texto de quién redactó el reporte, como se ve arriba. */
    public String sourceLabel() {
        return source.getText();
    }

    // ---------- ancho de los textos ----------

    /**
     * Calcula el alto con los textos partidos en líneas al ancho que tendrá el panel: el de la ventana si ya está
     * dentro de una, o el suyo propio.
     */
    @Override
    public Dimension getPreferredSize() {
        Container parent = getParent();
        fitTexts(parent != null && parent.getWidth() > 0 ? parent.getWidth() : getWidth());
        return super.getPreferredSize();
    }

    @Override
    public void doLayout() {
        fitTexts(getWidth());
        super.doLayout();
    }

    /**
     * Le da a cada texto el ancho de su tarjeta, para que su alto sea el de las líneas en que se parte, e invalida
     * las tarjetas que lo contienen para que vuelvan a medirse. Solo hace algo si el ancho cambió.
     *
     * @param panelWidth ancho del panel
     */
    private void fitTexts(int panelWidth) {
        Insets insets = getInsets();
        int width = panelWidth - insets.left - insets.right - 2 * CARD_PADDING;
        if (width <= 0 || width == textWidth) {
            return;
        }
        textWidth = width;
        for (JTextArea paragraph : paragraphs) {
            paragraph.setSize(width, Short.MAX_VALUE);
            for (Component part = paragraph; part != null && part != this; part = part.getParent()) {
                part.invalidate();
            }
        }
        invalidate();
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
