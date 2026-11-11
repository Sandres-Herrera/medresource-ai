package com.medresource.ui.view;

import com.medresource.ui.controller.SetupDraft;
import com.medresource.ui.controller.SimulationRequest;

import javax.swing.JDialog;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;
import java.util.Random;

/**
 * Diálogo donde el usuario arma una simulación nueva: el hospital, el caso, la estrategia y la velocidad inicial.
 *
 * <p>Muestra el {@link SetupPanel} con el tema oscuro de la pantalla de inicio, sin el marco de Windows: el
 * título y la equis de cerrar son parte del formulario, y el diálogo se mueve arrastrando su parte de arriba.
 * Cabe en una pantalla de 1366 × 768 ({@link ScreenFit}). Las reglas siguen en {@link SetupDraft} y en los
 * registros del modelo, así que lo que devuelve siempre es válido.</p>
 */
public final class CaseConfigDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Alto de la franja de arriba que sirve para arrastrar el diálogo. */
    private static final int DRAG_HEIGHT = 70;

    private transient SimulationRequest result;
    private Point dragStart;

    private CaseConfigDialog(Window owner) {
        super(owner, SetupPanel.TITLE, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        SetupPanel panel = new SetupPanel(new SetupDraft(), new Random());
        panel.onStart(request -> {
            result = request;
            dispose();
        });
        panel.onCancel(this::dispose);
        setContentPane(panel);
        enableDragging(panel);
        pack();
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Rectangle bounds = ScreenFit.centered(getSize(), usable);
        if (owner != null && owner.isShowing()) {
            setSize(bounds.getSize());
            setLocationRelativeTo(owner);
        } else {
            setBounds(bounds);
        }
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                panel.startButton().requestFocusInWindow();
            }
        });
    }

    /**
     * Muestra el diálogo y espera a que el usuario termine.
     *
     * @param owner ventana dueña del diálogo (puede ser nula)
     * @return la configuración y la velocidad elegidas, o vacío si el usuario canceló
     */
    public static Optional<SimulationRequest> ask(Window owner) {
        CaseConfigDialog dialog = new CaseConfigDialog(owner);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    /** Permite mover el diálogo arrastrando la franja de arriba del formulario. */
    private void enableDragging(SetupPanel panel) {
        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragStart = event.getY() <= DRAG_HEIGHT ? event.getPoint() : null;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragStart != null) {
                    Point screen = event.getLocationOnScreen();
                    setLocation(screen.x - dragStart.x, screen.y - dragStart.y);
                }
            }
        };
        panel.addMouseListener(drag);
        panel.addMouseMotionListener(drag);
    }
}
