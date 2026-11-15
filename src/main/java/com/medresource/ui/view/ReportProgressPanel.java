package com.medresource.ui.view;

import com.medresource.model.ReportProgress;
import com.medresource.model.ReportStage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.EnumMap;
import java.util.Map;

/**
 * Pantalla de espera mientras se genera el reporte: las etapas una debajo de otra, la barra de avance, el
 * porcentaje y cuánto falta.
 *
 * <p>Las etapas terminadas llevan un visto verde, la actual un punto de acento y las que faltan un círculo gris.
 * La barra nunca retrocede, aunque la estimación del tiempo cambie. El botón «Cancelar» detiene la generación;
 * si algo falla, la pantalla muestra el error y el botón pasa a decir «Cerrar».</p>
 */
public final class ReportProgressPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Título de la pantalla. */
    public static final String TITLE = "Generando el reporte";
    /** Texto bajo el título cuando hay IA. */
    public static final String AI_TEXT = "El programa calcula todos los números y la IA (Gemini) redacta el texto.";
    /** Texto bajo el título cuando no hay IA. */
    public static final String TEMPLATE_TEXT = "No hay clave de la IA: el texto se redactará con plantillas.";
    /** Texto del botón para detener la generación. */
    public static final String CANCEL = "Cancelar";
    /** Texto del botón cuando la generación falló. */
    public static final String CLOSE = "Cerrar";
    /** Ancho de la tarjeta. */
    public static final int CARD_WIDTH = 540;

    private static final int ICON_SIZE = 18;

    /** Cómo va cada etapa. */
    public enum StageState {
        /** Ya terminó. */
        DONE,
        /** Es la que se está haciendo. */
        CURRENT,
        /** Todavía no empieza. */
        PENDING
    }

    private final JLabel subtitle = new JLabel();
    private final Map<ReportStage, JLabel> stageLabels = new EnumMap<>(ReportStage.class);
    private final Map<ReportStage, StageState> states = new EnumMap<>(ReportStage.class);
    private final MeterBar bar = new MeterBar(HomeTheme.ACCENT);
    private final JLabel percent = new JLabel("0 %");
    private final JLabel remaining = new JLabel(ReportFormat.CALCULATING);
    private final HomeButton cancelButton = new HomeButton(HomeButton.Style.OUTLINE, CANCEL, HomeIcon.CLOSE);
    private boolean failed;

    /**
     * Crea la pantalla en la primera etapa, sin avance.
     *
     * @param usesAi {@code true} si el texto lo redactará la IA
     */
    public ReportProgressPanel(boolean usesAi) {
        super(new GridBagLayout());
        setBackground(HomeTheme.BACKGROUND);
        subtitle.setText(usesAi ? AI_TEXT : TEMPLATE_TEXT);
        ThemedCard card = new ThemedCard(new BorderLayout(0, 18), 24);
        card.add(header(), BorderLayout.NORTH);
        card.add(stages(), BorderLayout.CENTER);
        card.add(footer(), BorderLayout.SOUTH);
        card.setPreferredSize(new Dimension(CARD_WIDTH, card.getPreferredSize().height));
        add(card);
        show(ReportProgress.start());
    }

    private JPanel header() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(TITLE);
        title.setFont(HomeTheme.font(Font.BOLD, 20f));
        title.setForeground(HomeTheme.TEXT);
        subtitle.setFont(HomeTheme.font(Font.PLAIN, 13f));
        subtitle.setForeground(HomeTheme.MUTED);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(6));
        header.add(subtitle);
        return header;
    }

    private JPanel stages() {
        JPanel list = new JPanel(new GridLayout(ReportStage.count(), 1, 0, 8));
        list.setOpaque(false);
        for (ReportStage stage : ReportStage.values()) {
            JLabel label = new JLabel(stage.getLabel());
            label.setIconTextGap(10);
            stageLabels.put(stage, label);
            list.add(label);
        }
        return list;
    }

    private JPanel footer() {
        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        JPanel barRow = new JPanel(new BorderLayout(12, 0));
        barRow.setOpaque(false);
        percent.setFont(HomeTheme.font(Font.BOLD, 13f));
        percent.setForeground(HomeTheme.TEXT);
        barRow.add(bar, BorderLayout.CENTER);
        barRow.add(percent, BorderLayout.EAST);
        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);
        remaining.setFont(HomeTheme.font(Font.PLAIN, 13f));
        remaining.setForeground(HomeTheme.MUTED);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(cancelButton);
        bottom.add(remaining, BorderLayout.CENTER);
        bottom.add(buttonRow, BorderLayout.EAST);
        footer.add(barRow, BorderLayout.NORTH);
        footer.add(bottom, BorderLayout.SOUTH);
        return footer;
    }

    /**
     * Muestra el avance: qué etapas terminaron, la barra (que nunca retrocede), el porcentaje y cuánto falta.
     * Después de un error ya no cambia nada.
     *
     * @param progress avance de la generación
     * @throws IllegalArgumentException si falta el avance
     */
    public void show(ReportProgress progress) {
        if (progress == null) {
            throw new IllegalArgumentException("El avance es obligatorio.");
        }
        if (failed) {
            return;
        }
        for (ReportStage stage : ReportStage.values()) {
            StageState state = stage.compareTo(progress.stage()) < 0 ? StageState.DONE
                    : stage == progress.stage() ? StageState.CURRENT : StageState.PENDING;
            states.put(stage, state);
            JLabel label = stageLabels.get(stage);
            label.setIcon(new StageIcon(state));
            label.setFont(HomeTheme.font(state == StageState.CURRENT ? Font.BOLD : Font.PLAIN, 14f));
            label.setForeground(state == StageState.PENDING ? HomeTheme.FAINT : HomeTheme.TEXT);
        }
        bar.setFraction(Math.max(bar.getFraction(), progress.fraction()));
        percent.setText(Math.round(100 * bar.getFraction()) + " %");
        remaining.setText(ReportFormat.remaining(progress));
    }

    /**
     * Muestra que la generación falló: el mensaje en rojo y el botón «Cerrar».
     *
     * @param message qué pasó
     * @throws IllegalArgumentException si el mensaje está vacío
     */
    public void showError(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("El mensaje del error es obligatorio.");
        }
        failed = true;
        remaining.setText(message);
        remaining.setForeground(HomeTheme.DANGER);
        cancelButton.setText(CLOSE);
    }

    /**
     * Indica qué hace el botón de abajo: cancelar o, después de un error, cerrar.
     *
     * @param action acción del botón
     * @throws IllegalArgumentException si es nula
     */
    public void onCancel(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción del botón es obligatoria.");
        }
        cancelButton.addActionListener(event -> action.run());
    }

    /**
     * Dice cómo va una etapa.
     *
     * @param stage etapa
     * @return terminada, actual o pendiente
     */
    public StageState stateOf(ReportStage stage) {
        return states.get(stage);
    }

    public double getFraction() {
        return bar.getFraction();
    }

    public String percentText() {
        return percent.getText();
    }

    public String remainingText() {
        return remaining.getText();
    }

    public String subtitleText() {
        return subtitle.getText();
    }

    public HomeButton cancelButton() {
        return cancelButton;
    }

    public boolean hasFailed() {
        return failed;
    }

    /** Círculo de cada etapa: visto verde si terminó, punto de acento si es la actual, aro gris si falta. */
    private record StageIcon(StageState state) implements Icon {

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D surface = (Graphics2D) graphics.create();
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Ellipse2D circle = new Ellipse2D.Double(x + 1, y + 1, ICON_SIZE - 2.0, ICON_SIZE - 2.0);
            switch (state) {
                case DONE -> {
                    surface.setColor(HomeTheme.GREEN);
                    surface.fill(circle);
                    Path2D check = new Path2D.Double();
                    check.moveTo(x + 5, y + 9.5);
                    check.lineTo(x + 8, y + 12.5);
                    check.lineTo(x + 13.5, y + 6);
                    surface.setColor(HomeTheme.BACKGROUND);
                    surface.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    surface.draw(check);
                }
                case CURRENT -> {
                    surface.setColor(HomeTheme.ACCENT);
                    surface.setStroke(new BasicStroke(2f));
                    surface.draw(circle);
                    surface.fill(new Ellipse2D.Double(x + 5.5, y + 5.5, ICON_SIZE - 11.0, ICON_SIZE - 11.0));
                }
                case PENDING -> {
                    surface.setColor(HomeTheme.FAINT);
                    surface.setStroke(new BasicStroke(1.5f));
                    surface.draw(circle);
                }
            }
            surface.dispose();
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE;
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE;
        }
    }

    /** Color del texto que dice cuánto falta, para las pruebas. */
    Color remainingColor() {
        return remaining.getForeground();
    }
}
