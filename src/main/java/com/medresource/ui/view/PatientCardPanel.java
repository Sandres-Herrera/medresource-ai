package com.medresource.ui.view;

import com.medresource.ui.controller.PatientCard;
import com.medresource.ui.controller.SimulationClock;
import com.medresource.ui.render.Palette;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ficha del paciente seleccionado en el plano, para la columna derecha de la ventana de la simulación.
 *
 * <p>Muestra lo que dice la {@link PatientCard}: datos del paciente, enfermedad y síntomas, triage, qué está
 * haciendo, dónde está, cuánto lleva en el hospital, quién lo atiende, la barra de su salud (con el color de
 * {@link Palette#health(double)}) y, en tratamiento, la de su avance. Sin paciente, dice cómo elegir uno.</p>
 *
 * <p>Sus botones «Seguir con la cámara» y «Cerrar» no hacen nada solos: la ventana les pone su acción.</p>
 */
public final class PatientCardPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Ancho de la ficha en píxeles, para que la columna derecha no cambie de ancho con cada paciente. */
    public static final int WIDTH = 250;

    /** Texto que se ve cuando no hay paciente seleccionado. */
    public static final String HINT = "Haz clic en un paciente del plano para ver su ficha.";

    private static final int VALUE_WIDTH = 150;
    private static final String[] FIELDS = {"Paciente", "Edad y sexo", "Sangre", "Enfermedad", "Síntomas",
            "Triage", "Ahora", "Lugar", "Llegó", "Lo atienden"};

    private final JLabel hint = new JLabel("<html><div style='width:" + (WIDTH - 30) + "px'>" + HINT
            + "</div></html>");
    private final JPanel details = new JPanel(new GridBagLayout());
    private final transient Map<String, JLabel> values = new LinkedHashMap<>();
    private final JProgressBar health = new JProgressBar(0, 100);
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JToggleButton followButton = new JToggleButton("Seguir con la cámara");
    private final JButton closeButton = new JButton("Cerrar");
    private boolean showingCard;

    /** Crea la ficha vacía, con el texto que dice cómo elegir un paciente. */
    public PatientCardPanel() {
        super(new BorderLayout(0, 4));
        setBorder(BorderFactory.createTitledBorder("Ficha del paciente"));
        GridBagConstraints cell = new GridBagConstraints();
        cell.anchor = GridBagConstraints.NORTHWEST;
        cell.insets = new Insets(1, 2, 1, 4);
        int row = 0;
        for (String field : FIELDS) {
            JLabel value = new JLabel();
            value.setFont(value.getFont().deriveFont(Font.BOLD));
            values.put(field, value);
            addRow(field, value, row++, cell);
        }
        health.setStringPainted(true);
        progress.setStringPainted(true);
        addRow("Salud", health, row++, cell);
        addRow("Tratamiento", progress, row, cell);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.add(followButton);
        buttons.add(closeButton);
        add(hint, BorderLayout.NORTH);
        add(details, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        clear();
    }

    private void addRow(String field, Component value, int row, GridBagConstraints cell) {
        cell.gridy = row;
        cell.gridx = 0;
        cell.weightx = 0;
        cell.fill = GridBagConstraints.NONE;
        details.add(new JLabel(field + ":"), cell);
        cell.gridx = 1;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        details.add(value, cell);
    }

    /**
     * Muestra la ficha de un paciente.
     *
     * @param card ficha del paciente tal como se ve ahora
     * @throws IllegalArgumentException si la ficha es nula
     */
    public void showCard(PatientCard card) {
        if (card == null) {
            throw new IllegalArgumentException("La ficha es obligatoria.");
        }
        set("Paciente", card.code());
        set("Edad y sexo", card.ageAndSex());
        set("Sangre", card.bloodType().getLabel());
        set("Enfermedad", card.disease());
        set("Síntomas", card.symptoms().isEmpty() ? "Ninguno" : String.join(", ", card.symptoms()));
        set("Triage", card.triage() == null ? "Sin clasificar" : card.triage().getDisplayName());
        values.get("Triage").setForeground(card.triage() == null ? getForeground() : Palette.triage(card.triage()));
        set("Ahora", card.activity());
        set("Lugar", card.place() + (card.walking() ? " (va en camino)" : ""));
        set("Llegó", SimulationClock.formatTimeOfDay(card.arrivalMinute(), 0) + " · hace "
                + card.minutesInHospital() + " min");
        set("Lo atienden", card.caregivers().isEmpty() ? "Nadie en este momento"
                : String.join("<br>", card.caregivers()));
        health.setValue(card.healthPercent());
        health.setString(card.healthPercent() + " %");
        health.setForeground(Palette.health(card.health()));
        progress.setValue(card.progressPercent());
        progress.setString(card.inTreatment() ? card.progressPercent() + " %" : "No está en tratamiento");
        showingCard = true;
        showDetails(true, null);
    }

    /**
     * Avisa que el paciente seleccionado ya no está en el hospital.
     *
     * @param patientCode código del paciente
     */
    public void showGone(String patientCode) {
        showingCard = false;
        showDetails(false, patientCode + " ya salió del hospital. " + HINT);
    }

    /** Vuelve a la ficha vacía, con el texto que dice cómo elegir un paciente. */
    public void clear() {
        showingCard = false;
        followButton.setSelected(false);
        showDetails(false, HINT);
    }

    public boolean isShowingCard() {
        return showingCard;
    }

    public JToggleButton followButton() {
        return followButton;
    }

    public JButton closeButton() {
        return closeButton;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, super.getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /**
     * Texto que muestra un campo de la ficha, sin el formato.
     *
     * @param field nombre del campo, por ejemplo «Lugar»
     * @return texto del campo, o vacío si no existe
     */
    String valueOf(String field) {
        JLabel label = values.get(field);
        if (label == null) {
            return "";
        }
        return label.getText().replaceAll("<br>", "\n").replaceAll("<[^>]+>", "");
    }

    /** Texto de la indicación de arriba, sin el formato. */
    String hintText() {
        return hint.getText().replaceAll("<[^>]+>", "");
    }

    /** Barra de la salud del paciente. */
    JProgressBar healthBar() {
        return health;
    }

    /** Barra del avance de su tratamiento. */
    JProgressBar progressBar() {
        return progress;
    }

    private void set(String field, String text) {
        values.get(field).setText("<html><div style='width:" + VALUE_WIDTH + "px'>" + text + "</div></html>");
    }

    private void showDetails(boolean visible, String hintText) {
        details.setVisible(visible);
        followButton.setEnabled(visible);
        closeButton.setEnabled(visible || hintText != null && !HINT.equals(hintText));
        hint.setVisible(hintText != null);
        if (hintText != null) {
            hint.setText("<html><div style='width:" + (WIDTH - 30) + "px'>" + hintText + "</div></html>");
        }
        revalidate();
        repaint();
    }
}
