package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Scenario;
import com.medresource.ui.controller.SimulationSetup;
import com.medresource.ui.controller.StrategyOption;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Optional;

/**
 * Diálogo donde el usuario configura el hospital, el caso y la estrategia antes de simular.
 *
 * <p>Los rangos de cada campo son los de {@link HospitalConfig} y {@link CaseConfig}; las reglas siguen
 * viviendo en esos registros, y si algún valor no es válido se muestra su mensaje.</p>
 */
public final class CaseConfigDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JSpinner beds = spinner(1, HospitalConfig.MAX_BEDS);
    private final JSpinner icuBeds = spinner(1, HospitalConfig.MAX_ICU_BEDS);
    private final JSpinner operatingRooms = spinner(1, HospitalConfig.MAX_OPERATING_ROOMS);
    private final JSpinner doctors = spinner(1, HospitalConfig.MAX_DOCTORS);
    private final JSpinner nurses = spinner(1, HospitalConfig.MAX_NURSES);
    private final JSpinner blood = spinner(0, HospitalConfig.MAX_BLOOD_UNITS_PER_TYPE);
    private final JSpinner morgue = spinner(1, HospitalConfig.MAX_MORGUE_CAPACITY);
    private final JComboBox<Scenario> scenario = new JComboBox<>(Scenario.values());
    private final JSpinner seed = spinner(0, Integer.MAX_VALUE);
    private final JSpinner patients = spinner(CaseConfig.MIN_PATIENTS, CaseConfig.MAX_PATIENTS);
    private final JSpinner duration = spinner(CaseConfig.MIN_DURATION_MINUTES, CaseConfig.MAX_DURATION_MINUTES);
    private final JComboBox<StrategyOption> strategy = new JComboBox<>(StrategyOption.values());
    private transient SimulationSetup result;

    private CaseConfigDialog(Window owner) {
        super(owner, "Nueva simulación", ModalityType.APPLICATION_MODAL);
        scenario.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                Object text = value instanceof Scenario chosen ? chosen.getDisplayName() : value;
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints at = new GridBagConstraints();
        at.insets = new Insets(3, 6, 3, 6);
        at.fill = GridBagConstraints.HORIZONTAL;
        at.gridy = 0;
        form.add(section("Hospital"), full(at));
        row(form, at, "Camas de hospitalización", beds);
        row(form, at, "Camas de UCI", icuBeds);
        row(form, at, "Quirófanos", operatingRooms);
        row(form, at, "Médicos", doctors);
        row(form, at, "Enfermeros", nurses);
        row(form, at, "Sangre por tipo (unidades)", blood);
        row(form, at, "Cupos de la morgue", morgue);
        JButton standard = new JButton("Hospital estándar");
        standard.addActionListener(event -> showHospital(HospitalConfig.standard()));
        at.gridy++;
        form.add(standard, full(at));
        at.gridy++;
        form.add(section("Caso"), full(at));
        row(form, at, "Escenario", scenario);
        row(form, at, "Semilla", seed);
        row(form, at, "Pacientes", patients);
        row(form, at, "Duración de llegadas (min)", duration);
        at.gridy++;
        form.add(section("Estrategia"), full(at));
        row(form, at, "Estrategia de asignación", strategy);

        JButton cancel = new JButton("Cancelar");
        cancel.addActionListener(event -> dispose());
        JButton accept = new JButton("Simular");
        accept.addActionListener(event -> accept());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(accept);
        getRootPane().setDefaultButton(accept);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        showHospital(HospitalConfig.standard());
        showCase(CaseConfig.of(1L, Scenario.NORMAL));
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /**
     * Muestra el diálogo y espera a que el usuario termine.
     *
     * @param owner ventana dueña del diálogo (puede ser nula)
     * @return la configuración elegida, o vacío si el usuario canceló
     */
    public static Optional<SimulationSetup> ask(Window owner) {
        CaseConfigDialog dialog = new CaseConfigDialog(owner);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    private void accept() {
        try {
            HospitalConfig hospital = new HospitalConfig(value(beds), value(icuBeds), value(operatingRooms),
                    value(doctors), value(nurses), value(blood), value(morgue));
            CaseConfig caseConfig = new CaseConfig(value(seed), (Scenario) scenario.getSelectedItem(),
                    value(patients), value(duration));
            result = new SimulationSetup(hospital, caseConfig, (StrategyOption) strategy.getSelectedItem());
            dispose();
        } catch (IllegalArgumentException error) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "Datos no válidos",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showHospital(HospitalConfig config) {
        beds.setValue(config.hospitalizationBeds());
        icuBeds.setValue(config.icuBeds());
        operatingRooms.setValue(config.operatingRooms());
        doctors.setValue(config.doctors());
        nurses.setValue(config.nurses());
        blood.setValue(config.bloodUnitsPerType());
        morgue.setValue(config.morgueCapacity());
    }

    private void showCase(CaseConfig config) {
        scenario.setSelectedItem(config.scenario());
        seed.setValue((int) config.seed());
        patients.setValue(config.patientCount());
        duration.setValue(config.durationMinutes());
    }

    private static JSpinner spinner(int min, int max) {
        return new JSpinner(new SpinnerNumberModel(min, min, max, 1));
    }

    private static int value(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private static JLabel section(String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        return label;
    }

    private static void row(JPanel form, GridBagConstraints at, String text, JComponent field) {
        at.gridy++;
        at.gridx = 0;
        at.gridwidth = 1;
        at.weightx = 0;
        form.add(new JLabel(text), at);
        at.gridx = 1;
        at.weightx = 1;
        form.add(field, at);
    }

    private static GridBagConstraints full(GridBagConstraints at) {
        at.gridx = 0;
        at.gridwidth = 2;
        return at;
    }
}
