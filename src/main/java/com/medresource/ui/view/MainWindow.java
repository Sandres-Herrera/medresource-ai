package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Scenario;
import com.medresource.ui.controller.SimulationClock;
import com.medresource.ui.controller.SimulationController;
import com.medresource.ui.controller.SimulationSetup;
import com.medresource.ui.render.Renderer2D;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Ventana principal de MedResource AI: la pantalla de inicio ({@link HomePanel}), desde donde se configura una
 * simulación nueva o se lanza una rápida con el hospital estándar.
 *
 * <p>Lleva el logo de la aplicación como icono, empieza con el foco en «Nueva simulación» (Enter la abre) y se
 * cierra también con la tecla Escape. Se puede agrandar o maximizar: el contenido queda centrado. No se puede
 * achicar por debajo del tamaño en que todo se ve completo.</p>
 */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Duración de los casos de demostración: dos horas, para verlos completos en pocos minutos. */
    private static final int DEMO_DURATION_MINUTES = 120;

    /** Velocidad con la que empiezan las demostraciones. */
    private static final int DEMO_SPEED = 10;

    /** Caso de la simulación rápida: 60 pacientes en un día normal. */
    private static final CaseConfig QUICK_CASE = new CaseConfig(1L, Scenario.NORMAL, 60, DEMO_DURATION_MINUTES);

    /** Caso de la demostración: una catástrofe con 100 pacientes. */
    private static final CaseConfig DEMO_CASE = new CaseConfig(7L, Scenario.CATASTROPHE, CaseConfig.DEFAULT_PATIENTS,
            DEMO_DURATION_MINUTES);

    /** Crea la ventana principal. */
    public MainWindow() {
        super("MedResource AI");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setIconImages(List.of(LogoBadge.image(16), LogoBadge.image(32), LogoBadge.image(64)));
        HomePanel home = new HomePanel(HospitalConfig.standard(), QUICK_CASE, DEMO_CASE, DEMO_SPEED,
                new HomePanel.Actions(
                        () -> CaseConfigDialog.ask(this).ifPresent(setup -> open(setup, SimulationClock.DEFAULT_SPEED)),
                        () -> open(SimulationSetup.standard(QUICK_CASE), DEMO_SPEED),
                        () -> open(SimulationSetup.standard(DEMO_CASE), DEMO_SPEED),
                        this::dispose));
        home.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "salir");
        home.getActionMap().put("salir", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });
        setContentPane(home);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                home.newSimulationButton().requestFocusInWindow();
            }
        });
    }

    /**
     * Abre la ventana de simulación con la configuración elegida y la velocidad inicial. Si no se puede
     * preparar, avisa al usuario en lugar de abrirla.
     */
    private void open(SimulationSetup setup, int speed) {
        try {
            Renderer2D renderer = new Renderer2D();
            SimulationController controller = new SimulationController(setup, renderer);
            controller.setSpeed(speed);
            new SimulationWindow(controller, renderer).setVisible(true);
        } catch (IllegalStateException | IllegalArgumentException error) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "No se pudo iniciar la simulación",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
