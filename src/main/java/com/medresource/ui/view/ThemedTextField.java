package com.medresource.ui.view;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Campo de texto de una línea con el tema oscuro: fondo de campo, borde fino que se pinta con el color de acento
 * mientras se escribe en él.
 */
public final class ThemedTextField extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Alto del campo. */
    public static final int HEIGHT = 32;

    private static final int ARC = 8;

    private final JTextField field = new JTextField();

    /**
     * Crea el campo.
     *
     * @param name nombre accesible, el que leen los lectores de pantalla
     * @param text texto inicial; vacío si es nulo
     * @throws IllegalArgumentException si falta el nombre
     */
    public ThemedTextField(String name, String text) {
        super(new BorderLayout());
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del campo es obligatorio.");
        }
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setForeground(HomeTheme.TEXT);
        field.setCaretColor(HomeTheme.ACCENT);
        field.setSelectionColor(HomeTheme.SELECTED);
        field.setSelectedTextColor(HomeTheme.TEXT);
        field.setFont(HomeTheme.font(Font.PLAIN, 13f));
        setText(text);
        field.getAccessibleContext().setAccessibleName(name);
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent event) {
                repaint();
            }
        });
        add(field, BorderLayout.CENTER);
    }

    public JTextField field() {
        return field;
    }

    /**
     * Devuelve el texto escrito, sin los espacios de los extremos.
     *
     * @return texto
     */
    public String getText() {
        return field.getText().strip();
    }

    /**
     * Cambia el texto del campo y deja el cursor al comienzo, para que un texto largo se lea desde el principio.
     *
     * @param text texto nuevo; vacío si es nulo
     */
    public void setText(String text) {
        field.setText(text == null ? "" : text);
        field.setCaretPosition(0);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Math.max(120, super.getPreferredSize().width), HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, ARC, ARC);
        surface.setColor(HomeTheme.FIELD);
        surface.fill(box);
        surface.setColor(field.isFocusOwner() ? HomeTheme.ACCENT : HomeTheme.CARD_BORDER);
        surface.draw(box);
        surface.dispose();
    }
}
