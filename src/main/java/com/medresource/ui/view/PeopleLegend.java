package com.medresource.ui.view;

import com.medresource.model.TriageLevel;
import com.medresource.ui.render.IsoPalette;
import com.medresource.ui.render.Palette;

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
 * Tarjeta «Quién es quién» de la ventana de la simulación: el color de cada persona del plano y la función que
 * cumple, con una figura pequeña como las del plano.
 *
 * <p>Los pacientes llevan el color de su nivel de triage (del I, el más urgente, al V), gris claro si todavía no
 * tienen triage y gris oscuro si fallecieron. El personal lleva un gorro: el médico, bata blanca; el enfermero,
 * uniforme verde agua; la enfermera de triage, uniforme lila; y el camillero, que busca a los pacientes en la sala de
 * espera y empuja las camillas, uniforme gris azulado; y el vigilante de la salida, azul oscuro.</p>
 */
public final class PeopleLegend extends ThemedCard {

    private static final long serialVersionUID = 1L;

    /** Título de la tarjeta. */
    public static final String TITLE = "Quién es quién";
    /** Texto de los pacientes sin triage. */
    public static final String PENDING = "Sin clasificar";
    /** Texto de los pacientes fallecidos. */
    public static final String DECEASED = "Fallecido";
    /** Texto del médico. */
    public static final String DOCTOR = "Médico";
    /** Texto del enfermero. */
    public static final String NURSE = "Enfermero";
    /** Texto de la enfermera de triage. */
    public static final String TRIAGE_NURSE = "Enf. de triage";
    /** Texto del camillero. */
    public static final String PORTER = "Camillero";
    /** Texto del vigilante de la salida. */
    public static final String GUARD = "Vigilante";

    private final List<String> entries = new ArrayList<>();
    private final List<FigureIcon> figures = new ArrayList<>();

    /** Crea la leyenda. */
    public PeopleLegend() {
        super(new BorderLayout(0, 8), 14);
        JLabel title = new JLabel(TITLE);
        title.setFont(HomeTheme.font(Font.BOLD, 14f));
        title.setForeground(HomeTheme.TEXT);
        JPanel groups = new JPanel(new BorderLayout(0, 8));
        groups.setOpaque(false);
        JPanel patients = grid();
        for (TriageLevel level : TriageLevel.values()) {
            patients.add(entry(shortName(level), Palette.triage(level), null));
        }
        patients.add(entry(PENDING, Palette.UNCLASSIFIED, null));
        patients.add(entry(DECEASED, Palette.DECEASED, null));
        JPanel staff = grid();
        staff.add(entry(DOCTOR, Palette.DOCTOR_COAT, IsoPalette.DOCTOR_CAP));
        staff.add(entry(NURSE, Palette.NURSE_UNIFORM, IsoPalette.cap(Palette.NURSE_UNIFORM)));
        staff.add(entry(TRIAGE_NURSE, Palette.TRIAGE_NURSE, IsoPalette.cap(Palette.TRIAGE_NURSE)));
        staff.add(entry(PORTER, Palette.PORTER_UNIFORM, IsoPalette.cap(Palette.PORTER_UNIFORM)));
        staff.add(entry(GUARD, Palette.GUARD_UNIFORM, IsoPalette.cap(Palette.GUARD_UNIFORM)));
        groups.add(section("Pacientes · color del triage", patients), BorderLayout.NORTH);
        groups.add(section("Personal · con gorro", staff), BorderLayout.CENTER);
        add(title, BorderLayout.NORTH);
        add(groups, BorderLayout.CENTER);
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

    /** Textos de la leyenda, en orden: primero los pacientes y después el personal. */
    public List<String> entries() {
        return List.copyOf(entries);
    }

    /** Figuras de la leyenda, en el mismo orden que sus textos. */
    public List<FigureIcon> figures() {
        return List.copyOf(figures);
    }

    private static JPanel grid() {
        JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
        grid.setOpaque(false);
        return grid;
    }

    private static JPanel section(String text, JPanel grid) {
        JPanel section = new JPanel(new BorderLayout(0, 4));
        section.setOpaque(false);
        JLabel label = new JLabel(text);
        label.setFont(HomeTheme.font(Font.BOLD, 11.5f));
        label.setForeground(HomeTheme.MUTED);
        section.add(label, BorderLayout.NORTH);
        section.add(grid, BorderLayout.CENTER);
        return section;
    }

    private JPanel entry(String text, Color body, Color cap) {
        entries.add(text);
        FigureIcon figure = new FigureIcon(body, cap);
        figures.add(figure);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);
        row.add(figure);
        JLabel label = new JLabel(text);
        label.setFont(HomeTheme.font(Font.PLAIN, 12f));
        label.setForeground(HomeTheme.TEXT);
        row.add(label);
        return row;
    }
}
