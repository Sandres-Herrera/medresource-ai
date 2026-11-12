package com.medresource.ui.view;

import com.medresource.model.TriageLevel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Tarjeta «Triage» de la ventana de la simulación: el color de cada nivel en dos columnas, más el de los
 * pacientes que todavía no tienen triage.
 */
public final class TriageLegend extends ThemedCard {

    private static final long serialVersionUID = 1L;

    /** Texto del color de los pacientes sin triage. */
    public static final String PENDING = "Sin clasificar";

    private final List<String> entries = new ArrayList<>();

    /** Crea la leyenda. */
    public TriageLegend() {
        super(new BorderLayout(0, 8), 14);
        JLabel title = new JLabel("Triage");
        title.setFont(HomeTheme.font(Font.BOLD, 14f));
        title.setForeground(HomeTheme.TEXT);
        JPanel grid = new JPanel(new GridLayout(0, 2, 10, 4));
        grid.setOpaque(false);
        for (TriageLevel level : TriageLevel.values()) {
            grid.add(entry(shortName(level), HomeTheme.triageColor(level)));
        }
        grid.add(entry(PENDING, HomeTheme.PENDING));
        add(title, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
    }

    /**
     * Nombre corto de un nivel, por ejemplo «III · Urgente».
     *
     * @param level nivel de triage
     * @return su nombre corto
     * @throws IllegalArgumentException si es nulo
     */
    public static String shortName(TriageLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("El nivel de triage es obligatorio.");
        }
        String name = level.getDisplayName();
        int dash = name.indexOf('—');
        return level.name() + " · " + (dash < 0 ? name : name.substring(dash + 1).trim());
    }

    /** Textos de la leyenda, en orden. */
    public List<String> entries() {
        return List.copyOf(entries);
    }

    private JPanel entry(String text, Color color) {
        entries.add(text);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        row.add(new Dot(color));
        JLabel label = new JLabel(text);
        label.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        label.setForeground(HomeTheme.TEXT);
        row.add(label);
        return row;
    }
}
