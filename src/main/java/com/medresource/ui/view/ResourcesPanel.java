package com.medresource.ui.view;

import com.medresource.ui.controller.LiveCounters;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tarjeta «Recursos libres» de la ventana de la simulación: cuántas camas, camas de UCI, quirófanos, médicos,
 * enfermeros y cupos de morgue quedan libres de cuántos hay, cada uno con su barra, y las unidades de sangre.
 *
 * <p>La barra es verde agua mientras sobra, naranja cuando ya se usan tres de cada cuatro y roja cuando no queda
 * ninguno ({@link #barColor(int, int)}).</p>
 */
public final class ResourcesPanel extends ThemedCard {

    private static final long serialVersionUID = 1L;

    /** Recursos que tienen barra, en el orden en que se muestran. */
    public static final List<String> RESOURCES = List.of("Camas", "Camas UCI", "Quirófanos", "Médicos",
            "Enfermeros", "Morgue (libre)");
    /** Nombre de la fila de la sangre, que no tiene barra. */
    public static final String BLOOD = "Sangre (unidades)";
    /** Desde esta parte usada la barra se pone naranja. */
    public static final double BUSY_SHARE = 0.75;

    private final Map<String, JLabel> values = new LinkedHashMap<>();
    private final Map<String, MeterBar> bars = new LinkedHashMap<>();

    /** Crea la tarjeta con todo en cero. */
    public ResourcesPanel() {
        super(new BorderLayout(0, 8), 14);
        JLabel title = new JLabel("Recursos libres");
        title.setFont(HomeTheme.font(Font.BOLD, 14f));
        title.setForeground(HomeTheme.TEXT);
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        for (String resource : RESOURCES) {
            rows.add(row(resource));
            MeterBar bar = new MeterBar(HomeTheme.ACCENT);
            bar.setAlignmentX(Component.LEFT_ALIGNMENT);
            bars.put(resource, bar);
            rows.add(Box.createVerticalStrut(3));
            rows.add(bar);
            rows.add(Box.createVerticalStrut(8));
        }
        rows.add(row(BLOOD));
        add(title, BorderLayout.NORTH);
        add(rows, BorderLayout.CENTER);
    }

    private JPanel row(String resource) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel name = new JLabel(resource);
        name.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        name.setForeground(HomeTheme.MUTED);
        JLabel value = new JLabel("0");
        value.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        value.setForeground(HomeTheme.TEXT);
        values.put(resource, value);
        row.add(name, BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /**
     * Muestra los recursos libres de un momento de la simulación.
     *
     * @param counters contadores en vivo
     * @throws IllegalArgumentException si son nulos
     */
    public void show(LiveCounters counters) {
        if (counters == null) {
            throw new IllegalArgumentException("Los contadores son obligatorios.");
        }
        set("Camas", counters.freeBeds(), counters.totalBeds());
        set("Camas UCI", counters.freeIcuBeds(), counters.totalIcuBeds());
        set("Quirófanos", counters.freeOperatingRooms(), counters.totalOperatingRooms());
        set("Médicos", counters.freeDoctors(), counters.totalDoctors());
        set("Enfermeros", counters.freeNurses(), counters.totalNurses());
        set("Morgue (libre)", counters.morgueCapacity() - counters.morgueOccupancy(), counters.morgueCapacity());
        values.get(BLOOD).setText(String.valueOf(counters.bloodUnits()));
    }

    private void set(String resource, int free, int total) {
        values.get(resource).setText(free + " / " + total);
        MeterBar bar = bars.get(resource);
        bar.setFraction(total == 0 ? 0 : free / (double) total);
        bar.setColor(barColor(free, total));
    }

    /**
     * Color de la barra de un recurso: rojo si no queda ninguno, naranja si ya se usa {@value #BUSY_SHARE} o más,
     * verde agua si no.
     *
     * @param free  cuántos quedan libres
     * @param total cuántos hay
     * @return el color de la barra
     */
    public static Color barColor(int free, int total) {
        if (free <= 0) {
            return HomeTheme.RED;
        }
        double used = total == 0 ? 1.0 : 1.0 - free / (double) total;
        return used >= BUSY_SHARE ? HomeTheme.ORANGE : HomeTheme.ACCENT;
    }

    /**
     * Texto que se ve en una fila, por ejemplo «28 / 30».
     *
     * @param resource nombre de la fila
     * @return el texto, o vacío si no existe
     */
    public String valueOf(String resource) {
        JLabel label = values.get(resource);
        return label == null ? "" : label.getText();
    }

    /**
     * Barra de un recurso.
     *
     * @param resource nombre del recurso
     * @return la barra, o nula si no tiene
     */
    public MeterBar barOf(String resource) {
        return bars.get(resource);
    }
}
