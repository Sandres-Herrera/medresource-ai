package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.Scenario;
import com.medresource.ui.controller.SimulationController;
import com.medresource.ui.controller.SimulationSetup;
import com.medresource.ui.render.Palette;
import com.medresource.ui.render.Renderer2D;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Ventana principal de MedResource AI: desde aquí se configura una simulación nueva o se lanza una rápida
 * con el hospital estándar.
 */
public final class MainWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Crea la ventana principal. */
    public MainWindow() {
        super("MedResource AI");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Palette.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));

        JLabel title = new JLabel("MedResource AI", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 30f));
        title.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("Simulador de asignación de recursos hospitalarios con IA",
                SwingConstants.CENTER);
        subtitle.setForeground(new Color(0xB0, 0xBE, 0xC5));

        JButton configure = button("Nueva simulación…");
        configure.addActionListener(event -> CaseConfigDialog.ask(this).ifPresent(this::open));
        JButton quick = button("Simulación rápida (hospital estándar, 60 pacientes)");
        quick.addActionListener(event -> open(SimulationSetup.standard(
                new CaseConfig(1L, Scenario.NORMAL, 60, CaseConfig.DEFAULT_DURATION_MINUTES))));
        JButton catastrophe = button("Demostración: catástrofe (100 pacientes)");
        catastrophe.addActionListener(event -> open(SimulationSetup.standard(CaseConfig.of(7L,
                Scenario.CATASTROPHE))));
        JButton exit = button("Salir");
        exit.addActionListener(event -> dispose());

        for (Component part : new Component[]{title, subtitle}) {
            ((JLabel) part).setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(part);
            content.add(Box.createVerticalStrut(8));
        }
        content.add(Box.createVerticalStrut(20));
        for (JButton button : new JButton[]{configure, quick, catastrophe, exit}) {
            content.add(button);
            content.add(Box.createVerticalStrut(10));
        }
        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(380, 38));
        button.setPreferredSize(new Dimension(380, 38));
        return button;
    }

    /**
     * Abre la ventana de simulación con la configuración elegida. Si la estrategia necesita un modelo que no
     * existe, avisa al usuario en lugar de abrirla.
     */
    private void open(SimulationSetup setup) {
        try {
            Renderer2D renderer = new Renderer2D();
            new SimulationWindow(new SimulationController(setup, renderer), renderer).setVisible(true);
        } catch (IllegalStateException | IllegalArgumentException error) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "No se pudo iniciar la simulación",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
