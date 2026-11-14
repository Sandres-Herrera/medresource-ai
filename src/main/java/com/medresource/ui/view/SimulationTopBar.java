package com.medresource.ui.view;

import com.medresource.ui.controller.SimulationClock;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.function.IntConsumer;

/**
 * Barra de arriba de la ventana de la simulación, con el tema oscuro.
 *
 * <p>A la izquierda, la marca «MedResource AI» y los datos del caso (escenario, estrategia y semilla). A la
 * derecha, los controles: reproducir o pausar (botón de acento), «Siguiente evento», «Repetir 30 s» (volver a ver lo
 * último que pasó), volver a empezar, las velocidades ×1, ×2, ×5 y ×10, guardar una captura y el reloj con la hora y
 * el día.</p>
 *
 * <p>No tiene reglas: la ventana les pone acción a los botones y le dice qué mostrar.</p>
 */
public final class SimulationTopBar extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Alto de los botones de la barra. */
    public static final int BUTTON_SIZE = 34;
    /** Texto del botón que vuelve a mostrar los últimos segundos. */
    public static final String REPLAY_TEXT = "Repetir 30 s";
    /** Texto del mismo botón mientras se ve la repetición: vuelve a lo que pasa ahora. */
    public static final String LIVE_TEXT = "Volver al presente";

    private final IconButton play = new IconButton(HomeIcon.PLAY, "Reproducir", BUTTON_SIZE);
    private final HomeButton skip = new HomeButton(HomeButton.Style.OUTLINE, "Siguiente evento", HomeIcon.SKIP);
    private final HomeButton replay = new HomeButton(HomeButton.Style.OUTLINE, REPLAY_TEXT, HomeIcon.CLOCK);
    private final IconButton restart = new IconButton(HomeIcon.RESTART, "Volver a empezar", BUTTON_SIZE);
    private final IconButton capture = new IconButton(HomeIcon.CAMERA, "Guardar captura del plano", BUTTON_SIZE);
    private final SpeedSegment speeds = new SpeedSegment();
    private final JLabel meta = new JLabel();
    private final JLabel clock = new JLabel("00:00:00");
    private final JLabel day = new JLabel("Día 1 · minuto 0");
    private boolean running;

    /**
     * Crea la barra.
     *
     * @param caseText datos del caso, por ejemplo «Hospital normal · Prioridad clínica (triage) · Semilla 1»
     * @throws IllegalArgumentException si falta el texto
     */
    public SimulationTopBar(String caseText) {
        super(new BorderLayout(12, 0));
        if (caseText == null || caseText.isBlank()) {
            throw new IllegalArgumentException("Los datos del caso son obligatorios.");
        }
        setOpaque(false);
        meta.setText(caseText);
        meta.setFont(HomeTheme.font(Font.PLAIN, 12.5f));
        meta.setForeground(HomeTheme.MUTED);
        meta.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, HomeTheme.CARD_BORDER),
                BorderFactory.createEmptyBorder(0, 10, 0, 0)));
        play.setFilled(true);
        restart.setOutlined(true);
        capture.setOutlined(true);
        add(brand(), BorderLayout.WEST);
        add(controls(), BorderLayout.EAST);
    }

    private JComponent brand() {
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brand.setOpaque(false);
        brand.add(new LogoTile());
        JLabel name = new JLabel("<html>MedResource <span style='color:#2DD4BF'>AI</span></html>");
        name.setFont(HomeTheme.font(Font.BOLD, 15f));
        name.setForeground(HomeTheme.TEXT);
        brand.add(name);
        brand.add(meta);
        JPanel holder = new JPanel(new GridLayout(1, 1));
        holder.setOpaque(false);
        holder.add(brand);
        return center(holder);
    }

    private JComponent controls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        controls.setOpaque(false);
        controls.add(play);
        controls.add(skip);
        controls.add(replay);
        controls.add(restart);
        controls.add(speeds);
        controls.add(capture);
        controls.add(clockColumn());
        return center(controls);
    }

    private JComponent clockColumn() {
        clock.setFont(new Font(Font.MONOSPACED, Font.BOLD, 21));
        clock.setForeground(HomeTheme.TEXT);
        clock.setHorizontalAlignment(JLabel.RIGHT);
        day.setFont(HomeTheme.font(Font.PLAIN, 11f));
        day.setForeground(HomeTheme.MUTED);
        day.setHorizontalAlignment(JLabel.RIGHT);
        JPanel column = new JPanel(new GridLayout(2, 1, 0, 0));
        column.setOpaque(false);
        column.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        column.add(clock);
        column.add(day);
        return column;
    }

    /** Envuelve un panel para que quede centrado en el alto de la barra. */
    private static JComponent center(JComponent component) {
        JPanel holder = new JPanel(new GridBagLayout());
        holder.setOpaque(false);
        holder.add(component);
        return holder;
    }

    /**
     * Cambia lo que se hace al elegir una velocidad.
     *
     * @param action recibe la velocidad elegida
     * @throws IllegalArgumentException si es nula
     */
    public void onSpeed(IntConsumer action) {
        speeds.onSpeed(action);
    }

    /**
     * Marca la velocidad elegida.
     *
     * @param speed una de {@link SimulationClock#SPEEDS}
     * @throws IllegalArgumentException si no es una de ellas
     */
    public void setSpeed(int speed) {
        speeds.setSpeed(speed);
    }

    /**
     * Muestra si la simulación está corriendo: el botón principal pasa a «Pausar» o a «Reproducir».
     *
     * @param running {@code true} si está corriendo
     */
    public void setRunning(boolean running) {
        this.running = running;
        play.setIcon(running ? HomeIcon.PAUSE : HomeIcon.PLAY);
        play.setToolTipText(running ? "Pausar" : "Reproducir");
        play.getAccessibleContext().setAccessibleName(running ? "Pausar" : "Reproducir");
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Muestra la hora de la simulación.
     *
     * @param minute minuto simulado
     * @param second segundo dentro del minuto
     */
    public void showTime(int minute, int second) {
        clock.setText(SimulationClock.formatTimeOfDay(minute, second));
        String full = SimulationClock.format(minute, second);
        day.setText(full.substring(0, full.indexOf(" · ")) + " · minuto " + minute);
    }

    public IconButton playButton() {
        return play;
    }

    public HomeButton skipButton() {
        return skip;
    }

    public IconButton restartButton() {
        return restart;
    }

    /** Botón «Repetir 30 s»; durante la repetición dice «Volver al presente». */
    public HomeButton replayButton() {
        return replay;
    }

    /**
     * Cambia el botón de la repetición según si se está viendo una.
     *
     * @param replaying {@code true} mientras se ve la repetición
     */
    public void setReplaying(boolean replaying) {
        replay.setText(replaying ? LIVE_TEXT : REPLAY_TEXT);
    }

    public IconButton captureButton() {
        return capture;
    }

    /**
     * Pastilla de una velocidad.
     *
     * @param speed velocidad
     * @return su pastilla, o nula si no existe
     */
    public Pill speedPill(int speed) {
        return speeds.pill(speed);
    }

    /** Texto del reloj, por ejemplo «00:20:12». */
    public String clockText() {
        return clock.getText();
    }

    /** Texto bajo el reloj, por ejemplo «Día 1 · minuto 20». */
    public String dayText() {
        return day.getText();
    }

    /** Datos del caso que se ven junto a la marca. */
    public String caseText() {
        return meta.getText();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /** Cuadro con el icono de pulso del logo. */
    private static final class LogoTile extends JComponent {

        private static final long serialVersionUID = 1L;
        private static final int SIZE = 30;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(SIZE, SIZE);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D surface = (Graphics2D) graphics.create();
            surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, SIZE - 1.0, SIZE - 1.0, 8, 8);
            surface.setColor(HomeTheme.LOGO_TILE);
            surface.fill(box);
            surface.setColor(new Color(0x1D, 0x4D, 0x47));
            surface.draw(box);
            HomeIcon.PULSE.paint(surface, 6, 6, 18, HomeTheme.ACCENT);
            surface.dispose();
        }
    }
}
