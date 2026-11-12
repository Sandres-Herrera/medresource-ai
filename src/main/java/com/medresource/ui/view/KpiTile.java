package com.medresource.ui.view;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * Indicador grande de la ventana de la simulación: un nombre arriba y un número de color debajo, como
 * «En espera 3». Si tiene total, se ve al lado en gris: «Llegados 12 / 100».
 */
public final class KpiTile extends ThemedCard {

    private static final long serialVersionUID = 1L;

    private final JLabel name = new JLabel();
    private final JLabel value = new JLabel("0");
    private final JLabel total = new JLabel();

    /**
     * Crea el indicador en cero.
     *
     * @param title nombre del indicador
     * @param color color del número
     * @throws IllegalArgumentException si falta el nombre o el color
     */
    public KpiTile(String title, Color color) {
        super(new BorderLayout(0, 2), 10);
        if (title == null || title.isBlank() || color == null) {
            throw new IllegalArgumentException("El nombre y el color del indicador son obligatorios.");
        }
        name.setText(title);
        name.setFont(HomeTheme.font(Font.PLAIN, 12f));
        name.setForeground(HomeTheme.MUTED);
        value.setFont(HomeTheme.font(Font.BOLD, 22f));
        value.setForeground(color);
        total.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        total.setForeground(HomeTheme.MUTED);
        JPanel numbers = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        numbers.setOpaque(false);
        numbers.add(value);
        numbers.add(total);
        ((FlowLayout) numbers.getLayout()).setAlignOnBaseline(true);
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        add(name, BorderLayout.NORTH);
        add(numbers, BorderLayout.CENTER);
    }

    public String getTitle() {
        return name.getText();
    }

    /**
     * Muestra el número.
     *
     * @param number número del indicador
     */
    public void setValue(int number) {
        value.setText(String.valueOf(number));
    }

    /**
     * Muestra el número con su total al lado.
     *
     * @param number número del indicador
     * @param of     total
     */
    public void setValue(int number, int of) {
        value.setText(String.valueOf(number));
        total.setText("/ " + of);
    }

    /** Texto que se ve, por ejemplo «12 / 100» o «3». */
    public String text() {
        return total.getText().isEmpty() ? value.getText() : value.getText() + " " + total.getText();
    }

    /** Color del número. */
    public Color valueColor() {
        return value.getForeground();
    }
}
