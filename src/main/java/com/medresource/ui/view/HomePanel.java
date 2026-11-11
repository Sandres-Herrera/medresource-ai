package com.medresource.ui.view;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Contenido de la pantalla de inicio: el logo que late, el nombre de la aplicación, la acción principal (nueva
 * simulación), las dos opciones rápidas como tarjetas, los datos del hospital estándar y el enlace para salir.
 *
 * <p>El fondo es azul noche con un resplandor verde agua detrás del logo y una línea fina de acento en el borde
 * de arriba. Los datos de las tarjetas (pacientes, horas, velocidad) salen de los casos que recibe, así que nunca
 * dicen algo distinto de lo que se simula. No abre ventanas ni simula nada: cada opción solo avisa a su
 * {@link Actions acción}.</p>
 */
public final class HomePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Lo que hace cada opción de la pantalla de inicio. */
    public record Actions(Runnable newSimulation, Runnable quickSimulation, Runnable demo, Runnable exit) {

        /**
         * Valida las acciones.
         *
         * @throws IllegalArgumentException si falta alguna
         */
        public Actions {
            if (newSimulation == null || quickSimulation == null || demo == null || exit == null) {
                throw new IllegalArgumentException("Todas las acciones de la pantalla de inicio son obligatorias.");
            }
        }
    }

    private final HomeButton newSimulation;
    private final ActionCard quick;
    private final ActionCard demo;
    private final HomeButton exit;
    private final List<String> chips = new ArrayList<>();

    /**
     * Arma la pantalla de inicio.
     *
     * @param hospital  hospital con el que se simulan las opciones rápidas, del que se muestran sus datos
     * @param quickCase caso de la simulación rápida
     * @param demoCase  caso de la demostración
     * @param speed     velocidad con la que empiezan las dos opciones rápidas
     * @param actions   lo que hace cada opción
     * @throws IllegalArgumentException si falta algún dato o la velocidad no es positiva
     */
    public HomePanel(HospitalConfig hospital, CaseConfig quickCase, CaseConfig demoCase, int speed, Actions actions) {
        if (hospital == null || quickCase == null || demoCase == null || actions == null) {
            throw new IllegalArgumentException("El hospital, los casos y las acciones son obligatorios.");
        }
        if (speed < 1) {
            throw new IllegalArgumentException("La velocidad debe ser positiva.");
        }
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(HomeTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(40, 50, 28, 50));

        newSimulation = new HomeButton(HomeButton.Style.PRIMARY, "Nueva simulación", HomeIcon.PLUS);
        newSimulation.addActionListener(event -> actions.newSimulation().run());
        quick = new ActionCard(HomeIcon.BOLT, HomeTheme.QUICK, HomeTheme.QUICK_TILE, "Simulación rápida",
                describe(quickCase, speed), null);
        quick.addActionListener(event -> actions.quickSimulation().run());
        demo = new ActionCard(HomeIcon.WARNING, HomeTheme.DANGER, HomeTheme.DANGER_TILE,
                "Demostración: " + demoCase.scenario().getDisplayName().toLowerCase(), describe(demoCase, speed),
                "DEMO");
        demo.addActionListener(event -> actions.demo().run());
        exit = new HomeButton(HomeButton.Style.LINK, "Salir", null);
        exit.addActionListener(event -> actions.exit().run());

        add(centered(new LogoBadge()));
        add(Box.createVerticalStrut(18));
        add(centered(title()));
        add(Box.createVerticalStrut(8));
        add(centered(label("Simulador de asignación de recursos hospitalarios con IA", Font.PLAIN, 14f,
                HomeTheme.MUTED)));
        add(Box.createVerticalStrut(30));
        add(centered(newSimulation));
        add(Box.createVerticalStrut(14));
        add(centered(quick));
        add(Box.createVerticalStrut(10));
        add(centered(demo));
        add(Box.createVerticalStrut(26));
        add(centered(label("HOSPITAL ESTÁNDAR", Font.BOLD, 11f, HomeTheme.FAINT)));
        add(Box.createVerticalStrut(8));
        add(centered(chipsOf(hospital)));
        add(Box.createVerticalStrut(22));
        add(centered(exit));
        add(Box.createVerticalStrut(4));
        add(centered(label("Universidad Popular del Cesar · Ingeniería de Sistemas", Font.PLAIN, 11f,
                HomeTheme.FAINT)));
    }

    /**
     * Describe un caso en una línea: pacientes, duración de las llegadas y velocidad.
     *
     * @param caseConfig caso
     * @param speed      velocidad con la que empieza
     * @return por ejemplo «60 pacientes · 2 horas · velocidad ×10»
     * @throws IllegalArgumentException si falta el caso
     */
    public static String describe(CaseConfig caseConfig, int speed) {
        if (caseConfig == null) {
            throw new IllegalArgumentException("El caso es obligatorio.");
        }
        int minutes = caseConfig.durationMinutes();
        String duration = minutes % 60 != 0 ? minutes + " min"
                : minutes / 60 + (minutes == 60 ? " hora" : " horas");
        return caseConfig.patientCount() + " pacientes · " + duration + " · velocidad ×" + speed;
    }

    /** Botón de nueva simulación. */
    HomeButton newSimulationButton() {
        return newSimulation;
    }

    /** Tarjeta de la simulación rápida. */
    ActionCard quickCard() {
        return quick;
    }

    /** Tarjeta de la demostración. */
    ActionCard demoCard() {
        return demo;
    }

    /** Enlace para salir. */
    HomeButton exitLink() {
        return exit;
    }

    /** Textos de las etiquetas con los datos del hospital, en orden. */
    List<String> chipTexts() {
        return List.copyOf(chips);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        surface.setColor(HomeTheme.BACKGROUND);
        surface.fillRect(0, 0, width, height);
        float radius = Math.max(1f, Math.min(width, 640) * 0.55f);
        Color clear = new Color(HomeTheme.GLOW.getRed(), HomeTheme.GLOW.getGreen(), HomeTheme.GLOW.getBlue(), 0);
        surface.setPaint(new RadialGradientPaint(new Point2D.Double(width / 2.0, 76), radius,
                new float[]{0f, 1f}, new Color[]{HomeTheme.GLOW, clear}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
        surface.fillRect(0, 0, width, height);
        surface.setPaint(new GradientPaint(0, 0, clear, width / 2f, 0, HomeTheme.ACCENT, true));
        surface.fillRect(0, 0, width, 2);
        surface.dispose();
    }

    private static JLabel title() {
        String accent = String.format("#%06X", HomeTheme.ACCENT.getRGB() & 0xFFFFFF);
        JLabel title = label("<html>MedResource <span style='color:" + accent + "'>AI</span></html>", Font.BOLD,
                38f, HomeTheme.TEXT);
        title.getAccessibleContext().setAccessibleName("MedResource AI");
        return title;
    }

    private static JLabel label(String text, int style, float size, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(HomeTheme.font(style, size));
        label.setForeground(color);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
        return label;
    }

    /** Fila con una etiqueta por cada dato del hospital. */
    private JPanel chipsOf(HospitalConfig hospital) {
        chips.add(hospital.hospitalizationBeds() + " camas");
        chips.add(hospital.icuBeds() + " UCI");
        chips.add(hospital.operatingRooms() + " quirófanos");
        chips.add(hospital.doctors() + " médicos");
        chips.add(hospital.nurses() + " enfermeros");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        row.setOpaque(false);
        for (String text : chips) {
            row.add(new Chip(text));
        }
        row.setMaximumSize(new Dimension(ActionCard.WIDTH, row.getPreferredSize().height));
        return row;
    }

    private static <T extends JComponent> T centered(T component) {
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
        return component;
    }

    /** Etiqueta pequeña con fondo redondeado. */
    private static final class Chip extends JLabel {

        private static final long serialVersionUID = 1L;

        Chip(String text) {
            super(text, SwingConstants.CENTER);
            setFont(HomeTheme.font(Font.PLAIN, 12f));
            setForeground(HomeTheme.MUTED);
            setBorder(BorderFactory.createEmptyBorder(5, 11, 5, 11));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D surface = (Graphics2D) graphics.create();
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D pill = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0,
                    getHeight(), getHeight());
            surface.setColor(HomeTheme.CHIP);
            surface.fill(pill);
            surface.setColor(HomeTheme.CARD_BORDER);
            surface.draw(pill);
            surface.dispose();
            super.paintComponent(graphics);
        }
    }
}
