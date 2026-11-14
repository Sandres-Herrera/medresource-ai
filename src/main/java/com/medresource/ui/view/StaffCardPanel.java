package com.medresource.ui.view;

import com.medresource.ui.controller.StaffCard;
import com.medresource.ui.render.Palette;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ficha de un médico o de un enfermero en la columna derecha de la ventana de la simulación: su código, su
 * especialidad (o «Enfermería»), lo que está haciendo y la lista de los pacientes que ha atendido, que va creciendo a
 * medida que atiende a más. Cada paciente de la lista se puede elegir con un clic, y la equis cierra la ficha.
 *
 * <p>Empieza oculta; {@link #showCard} la muestra y la actualiza en cada cuadro sin rehacerla si no cambió.</p>
 */
public final class StaffCardPanel extends ThemedCard {

    private static final long serialVersionUID = 1L;

    /** Ancho de la ficha: el mismo de la ficha del paciente. */
    public static final int WIDTH = PatientCardPanel.WIDTH;
    /** Pacientes que se muestran como mucho en la lista, los más recientes. */
    public static final int MAX_ROWS = 12;

    private final JLabel titleLabel = label("", Font.BOLD, 15f, HomeTheme.TEXT);
    private final JLabel subtitleLabel = label("", Font.PLAIN, 12.5f, HomeTheme.MUTED);
    private final IconButton close = new IconButton(HomeIcon.CLOSE, "Cerrar la ficha", 24);
    private final JPanel body = new JPanel();
    private final List<String> rows = new ArrayList<>();
    private transient Consumer<String> patientAction = code -> { };
    private transient Runnable closeAction = () -> { };
    private String staffCode;
    private String shownKey = "";

    /** Crea la ficha, oculta hasta que se muestre a alguien. */
    public StaffCardPanel() {
        super(new BorderLayout(0, 8), 14);
        setHighlighted(true);
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        close.addActionListener(event -> {
            clear();
            closeAction.run();
        });
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        JPanel titles = new JPanel(new BorderLayout(0, 2));
        titles.setOpaque(false);
        titles.add(titleLabel, BorderLayout.NORTH);
        titles.add(subtitleLabel, BorderLayout.CENTER);
        header.add(titles, BorderLayout.CENTER);
        JPanel closeHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeHolder.setOpaque(false);
        closeHolder.add(close);
        header.add(closeHolder, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        setVisible(false);
    }

    /**
     * Cambia lo que se hace al hacer clic en un paciente de la lista.
     *
     * @param action recibe el código del paciente
     * @throws IllegalArgumentException si es nula
     */
    public void onPatientClicked(Consumer<String> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción del paciente es obligatoria.");
        }
        this.patientAction = action;
    }

    /**
     * Cambia lo que se hace al cerrar la ficha con la equis.
     *
     * @param action acción
     * @throws IllegalArgumentException si es nula
     */
    public void onClose(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de cerrar es obligatoria.");
        }
        this.closeAction = action;
    }

    /**
     * Muestra la ficha de un médico o de un enfermero; si es la misma que ya se ve, no la rehace.
     *
     * @param card ficha tal como se ve ahora
     * @throws IllegalArgumentException si es nula
     */
    public void showCard(StaffCard card) {
        if (card == null) {
            throw new IllegalArgumentException("La ficha es obligatoria.");
        }
        staffCode = card.code();
        setVisible(true);
        String key = card.toString();
        if (key.equals(shownKey)) {
            return;
        }
        shownKey = key;
        titleLabel.setText((card.isDoctor() ? "Médico " : "Enfermería · ") + card.code());
        subtitleLabel.setText(card.isDoctor() ? card.role() : "Enfermero o enfermera");
        body.removeAll();
        rows.clear();
        Color roleColor = card.isDoctor() ? Palette.specialty(card.specialty()) : Palette.NURSE_UNIFORM;
        body.add(left(Chip.tinted(card.role(), roleColor)));
        body.add(Box.createVerticalStrut(5));
        body.add(left(new Chip(card.activity(), HomeTheme.ACCENT, HomeTheme.SELECTED, null)));
        body.add(Box.createVerticalStrut(10));
        body.add(left(label("Pacientes atendidos (" + card.attended().size() + ")", Font.BOLD, 12.5f,
                HomeTheme.TEXT)));
        body.add(Box.createVerticalStrut(4));
        if (card.attended().isEmpty()) {
            body.add(left(label("Todavía no ha atendido a nadie.", Font.PLAIN, 12f, HomeTheme.MUTED)));
        }
        List<StaffCard.Attended> attended = card.attended();
        for (StaffCard.Attended patient : attended.subList(Math.max(0, attended.size() - MAX_ROWS),
                attended.size())) {
            String text = patient.patientCode() + " · min " + patient.minute() + " · " + patient.status();
            rows.add(text);
            body.add(left(patientRow(patient, text)));
            body.add(Box.createVerticalStrut(2));
        }
        if (attended.size() > MAX_ROWS) {
            body.add(left(label("y " + (attended.size() - MAX_ROWS) + " más antes", Font.PLAIN, 11.5f,
                    HomeTheme.FAINT)));
        }
        revalidate();
        repaint();
    }

    /** Oculta la ficha. */
    public void clear() {
        staffCode = null;
        shownKey = "";
        rows.clear();
        body.removeAll();
        setVisible(false);
    }

    /** Código del médico o enfermero que se muestra, o nulo si la ficha está oculta. */
    public String getStaffCode() {
        return staffCode;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, super.getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(WIDTH, getPreferredSize().height);
    }

    // ---------- para las pruebas ----------

    /** Título que se ve arriba, por ejemplo «Médico D03». */
    String titleText() {
        return titleLabel.getText();
    }

    /** Subtítulo: la especialidad del médico. */
    String subtitleText() {
        return subtitleLabel.getText();
    }

    /** Líneas de la lista de pacientes, por ejemplo «P012 · min 14 · En tratamiento». */
    List<String> rowTexts() {
        return List.copyOf(rows);
    }

    /** Hace lo mismo que un clic en el paciente de la lista. */
    void clickPatient(String patientCode) {
        patientAction.accept(patientCode);
    }

    /** Hace lo mismo que la equis. */
    void clickClose() {
        close.doClick();
    }

    // ---------- armado ----------

    /** Renglón de un paciente: un punto del color de su triage y el texto; un clic lo elige. */
    private JComponent patientRow(StaffCard.Attended patient, String text) {
        Color dot = HomeTheme.triageColor(patient.triage());
        JLabel row = label("<html><span style='color:" + hex(dot) + "'>●</span>&nbsp;" + text + "</html>",
                Font.PLAIN, 12f, HomeTheme.MUTED);
        row.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setToolTipText("Ver la ficha de " + patient.patientCode());
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                patientAction.accept(patient.patientCode());
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                row.setForeground(HomeTheme.TEXT);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                row.setForeground(HomeTheme.MUTED);
            }
        });
        return row;
    }

    private static JComponent left(JComponent component) {
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        holder.setOpaque(false);
        holder.setAlignmentX(Component.LEFT_ALIGNMENT);
        holder.add(component);
        return holder;
    }

    private static JLabel label(String text, int style, float size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(HomeTheme.font(style, size));
        label.setForeground(color);
        return label;
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
