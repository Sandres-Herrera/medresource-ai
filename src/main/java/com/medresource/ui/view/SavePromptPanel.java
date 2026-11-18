package com.medresource.ui.view;

import com.medresource.ui.view.ReportSaving.Status;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;

/**
 * Franja del reporte que pregunta si se guarda la simulación en el historial y, después, cómo va ese guardado
 * ({@link ReportSaving}).
 *
 * <p>Cuando el reporte está listo pregunta «¿Deseas guardar esta simulación en el historial?» con dos botones:
 * «Guardar simulación» y «Continuar sin guardar». Si se continúa sin guardar, la franja lo recuerda y deja el botón
 * «Guardar simulación» para hacerlo más tarde. Mientras se guarda no hay botones, así que no se puede empezar un
 * segundo guardado. Al final dice con qué número de ejecución quedó, o por qué no se pudo (y deja reintentar).
 * Mientras el reporte se genera, la franja no se ve.</p>
 *
 * <p>Se actualiza sola con cada cambio de la decisión; al cerrar el reporte hay que llamar a {@link #detach()} para
 * que deje de escucharla.</p>
 */
public final class SavePromptPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Pregunta que se hace al terminar la simulación. */
    public static final String QUESTION = "¿Deseas guardar esta simulación en el historial?";
    /** Explicación debajo de la pregunta. */
    public static final String QUESTION_DETAIL = "Se guardan el caso, la ejecución y el reporte. Si no, igual puedes "
            + "ver y descargar el reporte.";
    /** Texto del botón que guarda. */
    public static final String SAVE = "Guardar simulación";
    /** Texto del botón que sigue sin guardar. */
    public static final String SKIP = "Continuar sin guardar";
    /** Aviso cuando se eligió no guardar. */
    public static final String SKIPPED_TEXT = "Esta simulación no está en el historial. Puedes guardarla cuando "
            + "quieras.";
    /** Aviso mientras se guarda. */
    public static final String SAVING_TEXT = "Guardando la simulación en la base de datos…";
    /** Aviso cuando el equipo no tiene base de datos. */
    public static final String NO_DATABASE_TEXT = "Este equipo no tiene configurada la base de datos "
            + "(falta src/main/resources/db.properties): la simulación no se puede guardar.";
    /** Comienzo del aviso cuando falla el guardado, antes del motivo. */
    public static final String ERROR_TEXT = "No se pudo guardar la simulación: ";

    private final transient ReportSaving saving;
    private final transient Runnable listener = this::refresh;
    private final JLabel title = new JLabel();
    private final JLabel detail = new JLabel();
    private final HomeButton save = new HomeButton(HomeButton.Style.COMPACT, SAVE, HomeIcon.DOWNLOAD);
    private final HomeButton skip = new HomeButton(HomeButton.Style.OUTLINE, SKIP, null);

    /**
     * Crea la franja y la deja mostrando el estado actual de la decisión.
     *
     * @param saving decisión de la simulación
     * @throws IllegalArgumentException si es nula
     */
    public SavePromptPanel(ReportSaving saving) {
        super(new BorderLayout(16, 0));
        if (saving == null) {
            throw new IllegalArgumentException("La decisión de guardar es obligatoria.");
        }
        this.saving = saving;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        JPanel texts = new JPanel();
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.setOpaque(false);
        title.setFont(HomeTheme.font(Font.BOLD, 14f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        detail.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        texts.add(Box.createVerticalGlue());
        texts.add(title);
        texts.add(detail);
        texts.add(Box.createVerticalGlue());
        add(texts, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(skip);
        buttons.add(save);
        add(buttons, BorderLayout.EAST);
        save.addActionListener(event -> saving.save());
        skip.addActionListener(event -> saving.skip());
        saving.addListener(listener);
        refresh();
    }

    /** Deja de escuchar la decisión, por ejemplo al cerrar el reporte. */
    public void detach() {
        saving.removeListener(listener);
    }

    /** Muestra el estado actual de la decisión. */
    private void refresh() {
        Status status = saving.status();
        setVisible(status != Status.WAITING_REPORT);
        save.setVisible(saving.canSave());
        skip.setVisible(status == Status.UNDECIDED);
        switch (status) {
            case WAITING_REPORT, UNDECIDED -> show(QUESTION, HomeTheme.TEXT, QUESTION_DETAIL);
            case SKIPPED -> show(SKIPPED_TEXT, HomeTheme.MUTED, "");
            case SAVING -> show(SAVING_TEXT, HomeTheme.MUTED, "");
            case SAVED -> show("Simulación guardada en el historial como la ejecución #"
                    + saving.saved().map(saved -> saved.resultId()).orElse(0) + ".", HomeTheme.ACCENT,
                    "Ábrela en «Reportes guardados» o compárala en «Comparar simulaciones».");
            case FAILED -> show(ERROR_TEXT + saving.error().orElse("error desconocido"), HomeTheme.DANGER,
                    "Revisa la conexión con Oracle y vuelve a intentarlo.");
            case NO_DATABASE -> show(NO_DATABASE_TEXT, HomeTheme.MUTED, "");
        }
        revalidate();
        repaint();
    }

    private void show(String titleText, Color color, String detailText) {
        title.setText(titleText);
        title.setForeground(color);
        detail.setText(detailText.isEmpty() ? " " : detailText);
        detail.setToolTipText(detailText.isEmpty() ? null : detailText);
        detail.setForeground(HomeTheme.MUTED);
        detail.setVisible(!detailText.isEmpty());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Status status = saving.status();
        boolean asking = status == Status.UNDECIDED;
        graphics.setColor(asking ? HomeTheme.QUICK_TILE : HomeTheme.BACKGROUND);
        graphics.fillRect(0, 0, getWidth(), getHeight());
        graphics.setColor(asking ? HomeTheme.ACCENT : HomeTheme.DIVIDER);
        graphics.fillRect(0, getHeight() - 1, getWidth(), 1);
        if (asking) {
            graphics.fillRect(0, 0, 3, getHeight());
        }
    }

    // ---------- consultas ----------

    public HomeButton saveButton() {
        return save;
    }

    public HomeButton skipButton() {
        return skip;
    }

    public String titleText() {
        return title.getText();
    }

    public Color titleColor() {
        return title.getForeground();
    }

    /**
     * Devuelve la explicación que se ve debajo del título.
     *
     * @return la explicación; vacía si no hay
     */
    public String detailText() {
        return detail.isVisible() ? detail.getText() : "";
    }
}
