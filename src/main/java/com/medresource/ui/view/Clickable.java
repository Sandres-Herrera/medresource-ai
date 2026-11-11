package com.medresource.ui.view;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Base de las piezas de la pantalla de inicio que se pueden pulsar: el botón principal, las tarjetas de opción y
 * el enlace de salir.
 *
 * <p>Se dibujan a mano, así que esta clase les da lo que un {@link javax.swing.JButton} trae hecho: avisa a sus
 * oyentes cuando se pulsa con el ratón (al soltar el botón dentro de la pieza) o con el teclado (Enter o espacio
 * cuando tiene el foco), muestra la mano al pasar el ratón y recuerda si el ratón está encima, si está presionada
 * y si tiene el foco, para que cada pieza se dibuje distinto en cada caso.</p>
 */
public abstract class Clickable extends JComponent {

    private static final long serialVersionUID = 1L;

    private final transient List<ActionListener> listeners = new ArrayList<>();
    private boolean hover;
    private boolean pressed;

    /**
     * Prepara la pieza.
     *
     * @param name nombre accesible, el que leen los lectores de pantalla
     * @throws IllegalArgumentException si falta el nombre
     */
    protected Clickable(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la pieza es obligatorio.");
        }
        setName(name);
        getAccessibleContext().setAccessibleName(name);
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                setHover(false);
                setPressed(false);
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (isEnabled()) {
                    setPressed(true);
                    requestFocusInWindow();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                boolean click = pressed && contains(event.getPoint());
                setPressed(false);
                if (click) {
                    doClick();
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER || event.getKeyCode() == KeyEvent.VK_SPACE) {
                    doClick();
                }
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent event) {
                repaint();
            }
        });
    }

    /** Datos de accesibilidad: la pieza se anuncia como un botón con su nombre. */
    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                private static final long serialVersionUID = 1L;

                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.PUSH_BUTTON;
                }
            };
        }
        return accessibleContext;
    }

    /**
     * Agrega un oyente que se avisa cada vez que se pulsa la pieza.
     *
     * @param listener oyente
     * @throws IllegalArgumentException si el oyente es nulo
     */
    public void addActionListener(ActionListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("El oyente es obligatorio.");
        }
        listeners.add(listener);
    }

    /** Pulsa la pieza: avisa a todos sus oyentes, en el orden en que se agregaron. Deshabilitada no hace nada. */
    public void doClick() {
        if (!isEnabled()) {
            return;
        }
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getName());
        for (ActionListener listener : List.copyOf(listeners)) {
            listener.actionPerformed(event);
        }
    }

    /** Indica si el ratón está encima de la pieza. */
    public boolean isHover() {
        return hover;
    }

    /** Indica si se está presionando la pieza con el ratón. */
    public boolean isPressed() {
        return pressed;
    }

    /**
     * Marca si el ratón está encima y vuelve a dibujar.
     *
     * @param hover {@code true} si el ratón está encima
     */
    public void setHover(boolean hover) {
        if (this.hover != hover) {
            this.hover = hover;
            repaint();
        }
    }

    private void setPressed(boolean pressed) {
        if (this.pressed != pressed) {
            this.pressed = pressed;
            repaint();
        }
    }
}
