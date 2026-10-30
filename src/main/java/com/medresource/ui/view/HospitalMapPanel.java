package com.medresource.ui.view;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.ui.render.Camera;
import com.medresource.ui.render.Palette;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.PatientPainter;
import com.medresource.ui.render.SceneRenderer;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Panel de Swing que muestra el plano del hospital.
 *
 * <p>Dibuja el plano con el {@link SceneRenderer} que recibe y lo ajusta al tamaño del panel con una
 * {@link Camera}: se ve completo y centrado, y se reajusta solo cuando la ventana cambia de tamaño. Encima
 * del plano dibuja a los pacientes que le den con {@link #showPatients}. No tiene reglas de simulación: solo
 * muestra lo que le dan.</p>
 */
public final class HospitalMapPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Espacio libre alrededor del plano, en píxeles. */
    public static final int MARGIN = 8;

    private static final int MAX_PREFERRED_WIDTH = 1200;
    private static final int MAX_PREFERRED_HEIGHT = 640;

    private transient HospitalLayout layout;
    private final transient SceneRenderer renderer;
    private final transient PatientPainter patientPainter = new PatientPainter();
    private transient List<PatientMarker> markers = List.of();
    private int overflow;

    /**
     * Crea el panel.
     *
     * @param layout   plano que se muestra
     * @param renderer renderizador que lo dibuja
     * @throws IllegalArgumentException si alguno es nulo
     */
    public HospitalMapPanel(HospitalLayout layout, SceneRenderer renderer) {
        if (renderer == null) {
            throw new IllegalArgumentException("El renderizador es obligatorio.");
        }
        this.renderer = renderer;
        this.layout = requireLayout(layout);
        setBackground(Palette.BACKGROUND);
    }

    /**
     * Cambia el plano que se muestra y vuelve a dibujar.
     *
     * @param layout plano nuevo
     * @throws IllegalArgumentException si el plano es nulo
     */
    public void showLayout(HospitalLayout layout) {
        this.layout = requireLayout(layout);
        revalidate();
        repaint();
    }

    /**
     * Cambia los pacientes que se dibujan encima del plano y vuelve a dibujar.
     *
     * @param markers  pacientes con su posición en el mundo (puntos de {@link SceneRenderer#centerOf})
     * @param overflow pacientes esperando que no caben en las sillas; si es mayor que 0 se dibuja la
     *                 insignia «+N» junto a la última silla
     * @throws IllegalArgumentException si la lista es nula o el sobrante es negativo
     */
    public void showPatients(List<PatientMarker> markers, int overflow) {
        if (markers == null) {
            throw new IllegalArgumentException("La lista de pacientes es obligatoria.");
        }
        if (overflow < 0) {
            throw new IllegalArgumentException("El sobrante de la sala de espera no puede ser negativo.");
        }
        this.markers = List.copyOf(markers);
        this.overflow = overflow;
        repaint();
    }

    /**
     * Dibuja el plano en una imagen, igual que se vería en un panel de ese tamaño; sirve para las
     * capturas de los documentos.
     *
     * @param width  ancho de la imagen en píxeles
     * @param height alto de la imagen en píxeles
     * @return imagen con el plano ajustado a ese tamaño
     * @throws IllegalArgumentException si el ancho o el alto no son positivos
     */
    public BufferedImage toImage(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("La imagen debe tener ancho y alto positivos.");
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        paintScene(graphics, width, height);
        graphics.dispose();
        return image;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension world = renderer.worldSize(layout);
        return new Dimension(Math.min(world.width, MAX_PREFERRED_WIDTH) + 2 * MARGIN,
                Math.min(world.height, MAX_PREFERRED_HEIGHT) + 2 * MARGIN);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D surface = (Graphics2D) graphics.create();
        paintScene(surface, getWidth(), getHeight());
        surface.dispose();
    }

    private void paintScene(Graphics2D graphics, int width, int height) {
        graphics.setColor(Palette.BACKGROUND);
        graphics.fillRect(0, 0, width, height);
        if (width <= 2 * MARGIN || height <= 2 * MARGIN) {
            return;
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Dimension world = renderer.worldSize(layout);
        Camera.fit(world.width, world.height, width, height, MARGIN).applyTo(graphics);
        renderer.render(graphics, layout);
        patientPainter.paint(graphics, markers);
        List<Position> seats = layout.getWaitingSpots();
        patientPainter.paintOverflow(graphics, renderer.centerOf(seats.get(seats.size() - 1)), overflow);
    }

    private static HospitalLayout requireLayout(HospitalLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("El plano es obligatorio.");
        }
        return layout;
    }
}