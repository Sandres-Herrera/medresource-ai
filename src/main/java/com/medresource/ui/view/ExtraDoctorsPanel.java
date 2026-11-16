package com.medresource.ui.view;

import com.medresource.model.HospitalConfig;
import com.medresource.model.Specialty;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Lista de especialidades con un campo para elegir cuántos médicos extra hay de cada una. Los médicos extra se
 * suman a los médicos del hospital; sirven, por ejemplo, para probar el cirujano que recomendó un reporte.
 *
 * <p>No guarda nada: avisa cada cambio a quien la usa, que decide cuántos acepta (véase
 * {@link #showCount(Specialty, int)}).</p>
 */
public final class ExtraDoctorsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Ancho del campo de cada especialidad. */
    private static final int STEPPER_WIDTH = 104;

    private final Map<Specialty, Stepper> steppers = new EnumMap<>(Specialty.class);
    private transient BiConsumer<Specialty, Integer> changeAction = (specialty, count) -> { };

    /**
     * Crea la lista con las cantidades de un hospital.
     *
     * @param hospital hospital del que se toman los médicos extra
     * @throws IllegalArgumentException si es nulo
     */
    public ExtraDoctorsPanel(HospitalConfig hospital) {
        super(new BorderLayout(0, 8));
        if (hospital == null) {
            throw new IllegalArgumentException("La configuración del hospital es obligatoria.");
        }
        setBackground(HomeTheme.CARD);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(HomeTheme.CARD_BORDER),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        add(label("Médicos extra por especialidad (hasta " + HospitalConfig.MAX_EXTRA_DOCTORS + ")", Font.BOLD,
                12.5f, HomeTheme.TEXT), BorderLayout.NORTH);
        JPanel rows = new JPanel(new GridLayout(Specialty.values().length, 1, 0, 6));
        rows.setOpaque(false);
        for (Specialty specialty : Specialty.values()) {
            Stepper stepper = new Stepper(specialty.getDisplayName(), 0, HospitalConfig.MAX_EXTRA_DOCTORS, 1,
                    hospital.extraDoctorsOf(specialty));
            stepper.setPreferredSize(new Dimension(STEPPER_WIDTH, Stepper.HEIGHT));
            stepper.addChangeListener(count -> changeAction.accept(specialty, count));
            steppers.put(specialty, stepper);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.add(label(specialty.getDisplayName(), Font.PLAIN, 12.5f, HomeTheme.MUTED), BorderLayout.CENTER);
            row.add(stepper, BorderLayout.EAST);
            rows.add(row);
        }
        add(rows, BorderLayout.CENTER);
        add(label("Se suman a los médicos del hospital.", Font.PLAIN, 11.5f, HomeTheme.FAINT), BorderLayout.SOUTH);
    }

    /**
     * Cambia lo que se hace cuando el usuario cambia una cantidad.
     *
     * @param action recibe la especialidad y la cantidad pedida
     * @throws IllegalArgumentException si es nula
     */
    public void onChange(BiConsumer<Specialty, Integer> action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de cambio es obligatoria.");
        }
        this.changeAction = action;
    }

    /**
     * Muestra una cantidad sin avisar el cambio, por ejemplo la que quedó después de ajustarla al máximo.
     *
     * @param specialty especialidad
     * @param count     cantidad
     * @throws IllegalArgumentException si la especialidad es nula
     */
    public void showCount(Specialty specialty, int count) {
        Stepper stepper = stepper(specialty);
        BiConsumer<Specialty, Integer> action = changeAction;
        changeAction = (ignored, value) -> { };
        try {
            stepper.setValue(count);
        } finally {
            changeAction = action;
        }
    }

    /**
     * Campo de una especialidad.
     *
     * @param specialty especialidad
     * @return su campo
     * @throws IllegalArgumentException si la especialidad es nula
     */
    public Stepper stepper(Specialty specialty) {
        if (specialty == null) {
            throw new IllegalArgumentException("La especialidad es obligatoria.");
        }
        return steppers.get(specialty);
    }

    private static JLabel label(String text, int style, float size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(HomeTheme.font(style, size));
        label.setForeground(color);
        return label;
    }
}
