package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.Hospital;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Scenario;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.ClinicalPriorityStrategy;
import com.medresource.ui.controller.ScenePlayer;
import com.medresource.ui.render.Renderer2D;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;

/**
 * Vista previa de la simulación animada: corre un caso completo sobre el plano del hospital estándar.
 *
 * <p>Es un programa de prueba para ver a los pacientes moverse antes de que exista la ventana de simulación
 * de la aplicación. Usa la estrategia de prioridad clínica. Sin argumentos corre el escenario {@code NORMAL}
 * con 60 pacientes y la semilla 1; los argumentos opcionales son, en orden, el escenario ({@code NORMAL},
 * {@code HIGH_DEMAND}, {@code CATASTROPHE} o {@code UNEXPECTED_EMERGENCIES}), la cantidad de pacientes y la
 * semilla (por ejemplo {@code CATASTROPHE 100 7}).</p>
 */
public final class AnimationDemo {

    private static final int DEFAULT_PATIENTS = 60;
    private static final long DEFAULT_SEED = 1L;
    private static final int FRAME_MILLIS = 30;
    private static final int FRAMES_PER_MINUTE = 4;

    private AnimationDemo() {
    }

    /**
     * Abre la ventana y empieza la animación.
     *
     * @param args escenario, pacientes y semilla, todos opcionales
     */
    public static void main(String[] args) {
        CaseConfig config;
        try {
            config = caseFrom(args);
        } catch (IllegalArgumentException error) {
            System.err.println(error.getMessage());
            return;
        }
        Hospital hospital = new HospitalFactory().create(HospitalConfig.standard());
        HospitalLayout layout = new LayoutFactory().create(hospital);
        Renderer2D renderer = new Renderer2D();
        SimulationEngine engine = new SimulationEngine(new CaseGenerator().generate(config),
                new HospitalFactory().create(HospitalConfig.standard()), new ClinicalPriorityStrategy());
        ScenePlayer player = new ScenePlayer(engine, layout, renderer);
        SwingUtilities.invokeLater(() -> show(config, layout, renderer, player));
    }

    private static void show(CaseConfig config, HospitalLayout layout, Renderer2D renderer, ScenePlayer player) {
        HospitalMapPanel panel = new HospitalMapPanel(layout, renderer);
        JLabel status = new JLabel(" ", SwingConstants.CENTER);
        JFrame frame = new JFrame("MedResource AI — simulación animada (vista previa)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(panel, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        int[] frameInMinute = {0};
        Timer timer = new Timer(FRAME_MILLIS, null);
        timer.addActionListener(event -> {
            if (frameInMinute[0] == 0 && !player.step()) {
                timer.stop();
                status.setText(config.scenario().getDisplayName() + " · terminó en el minuto " + player.getMinute());
                return;
            }
            frameInMinute[0]++;
            player.animate(Math.min(1.0, frameInMinute[0] / (double) FRAMES_PER_MINUTE));
            panel.showPatients(player.markers(), player.overflow());
            status.setText(config.scenario().getDisplayName() + " · minuto " + player.getMinute());
            if (frameInMinute[0] >= FRAMES_PER_MINUTE) {
                frameInMinute[0] = 0;
            }
        });
        timer.start();
    }

    /**
     * Interpreta los argumentos de la línea de comandos.
     *
     * @param args escenario, pacientes y semilla, todos opcionales y en ese orden
     * @return configuración del caso (480 minutos de duración)
     * @throws IllegalArgumentException si hay más de tres argumentos, si el escenario no existe, si algún
     *                                  número no es entero o si algún valor está fuera de su rango
     */
    static CaseConfig caseFrom(String[] args) {
        if (args.length > 3) {
            throw new IllegalArgumentException("Use hasta 3 argumentos: escenario, pacientes y semilla.");
        }
        Scenario scenario = Scenario.NORMAL;
        if (args.length >= 1) {
            try {
                scenario = Scenario.valueOf(args[0].trim().toUpperCase());
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("El escenario no existe: " + args[0]
                        + ". Use NORMAL, HIGH_DEMAND, CATASTROPHE o UNEXPECTED_EMERGENCIES.");
            }
        }
        int patients = args.length >= 2 ? parseInt(args[1], "El número de pacientes") : DEFAULT_PATIENTS;
        long seed = args.length >= 3 ? parseInt(args[2], "La semilla") : DEFAULT_SEED;
        return new CaseConfig(seed, scenario, patients, CaseConfig.DEFAULT_DURATION_MINUTES);
    }

    private static int parseInt(String text, String what) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(what + " no es un número entero: " + text);
        }
    }
}