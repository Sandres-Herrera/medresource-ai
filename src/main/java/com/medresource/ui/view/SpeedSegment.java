package com.medresource.ui.view;

import com.medresource.ui.controller.SimulationClock;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Grupo de pastillas para elegir la velocidad de la simulación (×1, ×2, ×5 y ×10), dentro de una caja redondeada
 * con el tema oscuro. Lo usan la barra de la ventana y la barra del plano a pantalla completa.
 *
 * <p>Al pulsar una pastilla, queda marcada y se avisa a quien escucha con {@link #onSpeed}.</p>
 */
public final class SpeedSegment extends JPanel {

    private static final long serialVersionUID = 1L;

    private final Map<Integer, Pill> pills = new LinkedHashMap<>();
    private transient IntConsumer speedAction = speed -> { };

    /** Crea el grupo con una pastilla por velocidad, sin ninguna marcada. */
    public SpeedSegment() {
        super(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        for (int speed : SimulationClock.SPEEDS) {
            Pill pill = new Pill("×" + speed);
            pill.addActionListener(event -> {
                setSpeed(speed);
                speedAction.accept(speed);
            });
            pills.put(speed, pill);
            add(pill);
        }
    }

    /**
     * Cambia lo que se hace al elegir una velocidad.
     *
     * @param action recibe la velocidad elegida
     * @throws IllegalArgumentException si es nula
     */
    public void onSpeed(IntConsumer action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de la velocidad es obligatoria.");
        }
        this.speedAction = action;
    }

    /**
     * Marca la velocidad elegida.
     *
     * @param speed una de {@link SimulationClock#SPEEDS}
     * @throws IllegalArgumentException si no es una de ellas
     */
    public void setSpeed(int speed) {
        if (!pills.containsKey(speed)) {
            throw new IllegalArgumentException("La velocidad debe ser una de " + SimulationClock.SPEEDS + ".");
        }
        pills.forEach((each, pill) -> pill.setSelected(each == speed));
    }

    /**
     * Pastilla de una velocidad.
     *
     * @param speed velocidad
     * @return su pastilla, o nula si no existe
     */
    public Pill pill(int speed) {
        return pills.get(speed);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D surface = (Graphics2D) graphics.create();
        surface.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D box = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, 10, 10);
        surface.setColor(HomeTheme.CARD);
        surface.fill(box);
        surface.setColor(HomeTheme.CARD_BORDER);
        surface.draw(box);
        surface.dispose();
    }
}
