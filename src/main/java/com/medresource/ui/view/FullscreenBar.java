package com.medresource.ui.view;

import com.medresource.ui.controller.SimulationClock;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.function.IntConsumer;

/**
 * Barra de arriba del plano a pantalla completa, con el tema oscuro.
 *
 * <p>A la izquierda, el título «Plano del hospital», la etiqueta «Siguiendo a P003» mientras la cámara sigue a
 * alguien y una ayuda corta. A la derecha, los controles de la simulación: reproducir o pausar, «Siguiente evento»,
 * «Seguir» (la cámara sigue al paciente elegido; solo se puede con un paciente elegido), «Repetir 30 s», las
 * velocidades, el reloj y el botón para salir de la pantalla completa.</p>
 *
 * <p>No tiene reglas: quien la usa les pone acción a los botones y le dice qué mostrar.</p>
 */
public final class FullscreenBar extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Ayuda que se ve junto al título. */
    public static final String HINT = "Rueda para acercar · arrastra para mover · clic derecho para girar · "
            + "Esc para salir";
    /** Texto del botón para seguir al paciente elegido. */
    public static final String FOLLOW_TEXT = "Seguir";
    /** Texto del mismo botón mientras la cámara lo sigue. */
    public static final String UNFOLLOW_TEXT = "Dejar de seguir";

    private final IconButton play = new IconButton(HomeIcon.PLAY, "Reproducir", SimulationTopBar.BUTTON_SIZE);
    private final HomeButton skip = new HomeButton(HomeButton.Style.OUTLINE, "Siguiente evento", HomeIcon.SKIP);
    private final HomeButton follow = new HomeButton(HomeButton.Style.OUTLINE, FOLLOW_TEXT, HomeIcon.TARGET);
    private final HomeButton replay = new HomeButton(HomeButton.Style.OUTLINE, SimulationTopBar.REPLAY_TEXT,
            HomeIcon.CLOCK);
    private final SpeedSegment speeds = new SpeedSegment();
    private final IconButton exit = new IconButton(HomeIcon.COLLAPSE, "Salir de pantalla completa",
            SimulationTopBar.BUTTON_SIZE);
    private final Chip followTag = new Chip("Siguiendo", HomeTheme.ACCENT, HomeTheme.SELECTED, null);
    private final JLabel clock = new JLabel("00:00:00");
    private final JLabel day = new JLabel("Día 1 · minuto 0");
    private boolean running;

    /** Crea la barra, con la simulación en pausa y sin seguir a nadie. */
    public FullscreenBar() {
        super(new BorderLayout(12, 0));
        setOpaque(true);
        setBackground(HomeTheme.BACKGROUND);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, HomeTheme.CARD_BORDER),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        play.setFilled(true);
        exit.setOutlined(true);
        exit.setToolTipText("Salir de pantalla completa (Esc)");
        followTag.setVisible(false);
        add(title(), BorderLayout.WEST);
        add(controls(), BorderLayout.EAST);
    }

    private JComponent title() {
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        title.setOpaque(false);
        title.add(new IconLabel(HomeIcon.MAP, 17, HomeTheme.ACCENT));
        JLabel name = new JLabel(MapFrame.TITLE);
        name.setFont(HomeTheme.font(Font.BOLD, 15f));
        name.setForeground(HomeTheme.TEXT);
        title.add(name);
        title.add(followTag);
        JLabel hint = new JLabel(HINT);
        hint.setFont(HomeTheme.font(Font.PLAIN, 12f));
        hint.setForeground(HomeTheme.MUTED);
        title.add(hint);
        return center(title);
    }

    private JComponent controls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        controls.setOpaque(false);
        controls.add(play);
        controls.add(skip);
        controls.add(follow);
        controls.add(replay);
        controls.add(speeds);
        controls.add(clockColumn());
        controls.add(exit);
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
        column.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
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

    /**
     * Muestra u oculta la etiqueta de a quién sigue la cámara.
     *
     * @param patientCode código del paciente que se sigue, o nulo si la cámara no sigue a nadie
     */
    public void showFollowing(String patientCode) {
        follow.setText(patientCode != null ? UNFOLLOW_TEXT : FOLLOW_TEXT);
        followTag.setVisible(patientCode != null);
        if (patientCode != null) {
            followTag.setText("Siguiendo a " + patientCode);
        }
    }

    /**
     * Habilita el botón «Seguir»: solo se puede seguir a un paciente elegido.
     *
     * @param enabled {@code true} si hay un paciente elegido
     */
    public void setFollowEnabled(boolean enabled) {
        follow.setEnabled(enabled);
    }

    /** Botón «Seguir» o «Dejar de seguir». */
    public HomeButton followButton() {
        return follow;
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
        replay.setText(replaying ? SimulationTopBar.LIVE_TEXT : SimulationTopBar.REPLAY_TEXT);
    }

    public IconButton playButton() {
        return play;
    }

    public HomeButton skipButton() {
        return skip;
    }

    public IconButton exitButton() {
        return exit;
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

    /** Texto de la etiqueta de seguimiento, o vacío si no se ve. */
    public String followText() {
        return followTag.isVisible() ? followTag.getText() : "";
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
