package com.medresource.ui.view;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Campo de un número entero con un botón «−» a la izquierda y uno «+» a la derecha, para el formulario de una
 * simulación nueva.
 *
 * <p>El número nunca sale de su rango: los botones suben o bajan un paso y se deshabilitan en los extremos, las
 * flechas del teclado y la rueda del ratón también cambian el número, y en el campo solo se pueden escribir
 * dígitos; lo escrito se toma al pulsar Enter o al salir del campo y se ajusta al rango (si se deja vacío, vuelve
 * el número anterior). Cada vez que el número cambia, avisa a sus oyentes.</p>
 */
public final class Stepper extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Alto del campo. */
    public static final int HEIGHT = 28;

    private static final int ARC = 8;
    private static final int BUTTON = 22;

    private final int min;
    private final int max;
    private final int step;
    private final IconButton minus;
    private final IconButton plus;
    private final JTextField field = new JTextField();
    private final transient List<IntConsumer> listeners = new ArrayList<>();
    private int value;

    /**
     * Crea el campo.
     *
     * @param name  nombre del número, para los botones («Bajar camas», «Subir camas»)
     * @param min   menor número permitido
     * @param max   mayor número permitido
     * @param step  cuánto sube o baja cada botón, al menos 1
     * @param value número inicial; se ajusta al rango
     * @throws IllegalArgumentException si falta el nombre, si el mínimo es mayor que el máximo o el paso no es
     *                                  positivo
     */
    public Stepper(String name, int min, int max, int step, int value) {
        super(new BorderLayout(2, 0));
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del campo es obligatorio.");
        }
        if (min > max) {
            throw new IllegalArgumentException("El mínimo no puede ser mayor que el máximo.");
        }
        if (step < 1) {
            throw new IllegalArgumentException("El paso debe ser positivo.");
        }
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = clamp(value);
        setName(name);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        minus = new IconButton(HomeIcon.MINUS, "Bajar " + name.toLowerCase(), BUTTON);
        plus = new IconButton(HomeIcon.PLUS, "Subir " + name.toLowerCase(), BUTTON);
        minus.addActionListener(event -> setValue(this.value - step));
        plus.addActionListener(event -> setValue(this.value + step));
        styleField(name);
        add(minus, BorderLayout.WEST);
        add(field, BorderLayout.CENTER);
        add(plus, BorderLayout.EAST);
        addMouseWheelListener(event -> setValue(this.value - event.getWheelRotation() * step));
        showValue();
    }

    private void styleField(String name) {
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setForeground(HomeTheme.TEXT);
        field.setCaretColor(HomeTheme.ACCENT);
        field.setSelectionColor(HomeTheme.SELECTED);
        field.setSelectedTextColor(HomeTheme.TEXT);
        field.setFont(HomeTheme.font(Font.BOLD, 13f));
        field.setHorizontalAlignment(JTextField.CENTER);
        field.setColumns(Math.max(2, String.valueOf(max).length()));
        field.getAccessibleContext().setAccessibleName(name);
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DigitsOnly());
        field.addActionListener(event -> commitText());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                field.selectAll();
                repaint();
            }

            @Override
            public void focusLost(FocusEvent event) {
                commitText();
                repaint();
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_UP) {
                    setValue(value + step);
                } else if (event.getKeyCode() == KeyEvent.VK_DOWN) {
                    setValue(value - step);
                }
            }
        });
    }

    public int getValue() {
        return value;
    }

    /**
     * Cambia el número, ajustado al rango, y avisa a los oyentes si cambió.
     *
     * @param newValue número pedido
     */
    public void setValue(int newValue) {
        int clamped = clamp(newValue);
        boolean changed = clamped != value;
        value = clamped;
        showValue();
        if (changed) {
            for (IntConsumer listener : List.copyOf(listeners)) {
                listener.accept(value);
            }
        }
    }

    /**
     * Agrega un oyente que recibe el número nuevo cada vez que cambia.
     *
     * @param listener oyente
     * @throws IllegalArgumentException si es nulo
     */
    public void addChangeListener(IntConsumer listener) {
        if (listener == null) {
            throw new IllegalArgumentException("El oyente es obligatorio.");
        }
        listeners.add(listener);
    }

    /** Toma lo escrito en el campo: lo ajusta al rango, o vuelve al número anterior si quedó vacío. */
    public void commitText() {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            showValue();
            return;
        }
        try {
            setValue(Integer.parseInt(text));
        } catch (NumberFormatException tooLong) {
            setValue(max);
        }
    }

    /** Campo donde se escribe el número. */
    public JTextField field() {
        return field;
    }

    /** Botón que baja el número. */
    public IconButton minusButton() {
        return minus;
    }

    /** Botón que sube el número. */
    public IconButton plusButton() {
        return plus;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width, HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
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

    private void showValue() {
        field.setText(String.valueOf(value));
        minus.setEnabled(value > min);
        plus.setEnabled(value < max);
    }

    private int clamp(int number) {
        return Math.max(min, Math.min(max, number));
    }

    /** Deja escribir solo dígitos, y como mucho diez. */
    private static final class DigitsOnly extends DocumentFilter {

        private static final int MAX_DIGITS = 10;

        @Override
        public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes)
                throws BadLocationException {
            replace(bypass, offset, 0, text, attributes);
        }

        @Override
        public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
                throws BadLocationException {
            String digits = text == null ? "" : text.replaceAll("\\D", "");
            int room = MAX_DIGITS - (bypass.getDocument().getLength() - length);
            if (room > 0 || digits.isEmpty()) {
                super.replace(bypass, offset, length, digits.substring(0, Math.min(digits.length(),
                        Math.max(0, room))), attributes);
            }
        }
    }
}
