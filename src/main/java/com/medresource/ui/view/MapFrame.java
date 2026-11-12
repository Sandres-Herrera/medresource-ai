package com.medresource.ui.view;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * Marco del plano en la ventana de la simulación: una tarjeta con borde de acento y, arriba, el título «Plano del
 * hospital», el tamaño del plano y, mientras la cámara sigue a alguien, la etiqueta «Siguiendo a P003».
 *
 * <p>Arriba a la derecha lleva el botón de pantalla completa; quien usa el marco decide qué hacer al pulsarlo
 * ({@link #onFullscreen}). Mientras el plano se ve en otra ventana, {@link #restoreMap()} lo devuelve al marco.</p>
 */
public final class MapFrame extends ThemedCard {

    private static final long serialVersionUID = 1L;

    /** Título del marco. */
    public static final String TITLE = "Plano del hospital";

    private final Chip followTag = new Chip("Siguiendo", HomeTheme.ACCENT, HomeTheme.SELECTED, null);
    /** Lado del botón de pantalla completa, en píxeles. */
    public static final int BUTTON_SIZE = 26;

    private final JLabel size = new JLabel();
    private final IconButton expand = new IconButton(HomeIcon.EXPAND, "Pantalla completa", BUTTON_SIZE);
    private final HospitalMapPanel map;
    private transient Runnable fullscreenAction = () -> { };

    /**
     * Crea el marco alrededor del plano.
     *
     * @param map      panel del plano
     * @param sizeText tamaño del plano y ayuda, por ejemplo «estándar · 20 × 16 m · clic en un paciente»
     * @throws IllegalArgumentException si falta alguno
     */
    public MapFrame(HospitalMapPanel map, String sizeText) {
        super(new BorderLayout(0, 6), 8);
        if (map == null || sizeText == null || sizeText.isBlank()) {
            throw new IllegalArgumentException("El plano y su tamaño son obligatorios.");
        }
        this.map = map;
        setHighlighted(true);
        expand.setOutlined(true);
        expand.setToolTipText("Ver el plano a pantalla completa");
        expand.addActionListener(event -> fullscreenAction.run());
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(new IconLabel(HomeIcon.MAP, 15, HomeTheme.ACCENT));
        JLabel title = new JLabel(TITLE);
        title.setFont(HomeTheme.font(Font.BOLD, 12.5f));
        title.setForeground(HomeTheme.ACCENT);
        left.add(title);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        followTag.setVisible(false);
        size.setText(sizeText);
        size.setFont(HomeTheme.font(Font.PLAIN, 12f));
        size.setForeground(HomeTheme.MUTED);
        right.add(followTag);
        right.add(size);
        right.add(expand);
        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        add(map, BorderLayout.CENTER);
    }

    /**
     * Muestra u oculta la etiqueta de a quién sigue la cámara.
     *
     * @param patientCode código del paciente que se sigue, o nulo si la cámara no sigue a nadie
     */
    public void showFollowing(String patientCode) {
        followTag.setVisible(patientCode != null);
        if (patientCode != null) {
            followTag.setText("Siguiendo a " + patientCode);
        }
    }

    /** Texto de la etiqueta de seguimiento, o vacío si no se ve. */
    public String followText() {
        return followTag.isVisible() ? followTag.getText() : "";
    }

    /**
     * Cambia lo que se hace al pulsar el botón de pantalla completa.
     *
     * @param action acción
     * @throws IllegalArgumentException si es nula
     */
    public void onFullscreen(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("La acción de pantalla completa es obligatoria.");
        }
        this.fullscreenAction = action;
    }

    /** Devuelve el plano al marco si se estaba viendo en otra ventana. */
    public void restoreMap() {
        if (map.getParent() != this) {
            add(map, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }

    /** Indica si el plano está dentro del marco. */
    public boolean hasMap() {
        return map.getParent() == this;
    }

    public IconButton fullscreenButton() {
        return expand;
    }

    /** Tamaño del plano que se ve arriba a la derecha. */
    public String sizeText() {
        return size.getText();
    }
}
