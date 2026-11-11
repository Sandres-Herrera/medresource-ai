package com.medresource.ui.view;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.ui.render.Camera;
import com.medresource.ui.render.Palette;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.PatientPainter;
import com.medresource.ui.render.PatientPicker;
import com.medresource.ui.render.SceneRenderer;
import com.medresource.ui.render.StaffMarker;
import com.medresource.ui.render.StaffPainter;

import javax.swing.JPanel;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Panel de Swing que muestra el plano del hospital.
 *
 * <p>Dibuja el plano con el {@link SceneRenderer} que recibe y lo ajusta al tamaño del panel con una
 * {@link Camera}: se ve completo y centrado, y se reajusta solo cuando la ventana cambia de tamaño. Encima
 * del plano dibuja al personal que le den con {@link #showStaff} y a los pacientes que le den con
 * {@link #showPatients}. No tiene reglas de simulación: solo muestra lo que le dan.</p>
 *
 * <p>Al hacer clic sobre un paciente, el panel lo selecciona (lo marca con un anillo) y avisa a quien escucha con
 * {@link #setSelectionListener}; al hacer clic donde no hay nadie, quita la selección. Con
 * {@link #setFollowing(boolean)} la cámara sigue al paciente seleccionado más de cerca
 * ({@value #FOLLOW_ZOOM}×, o el aumento que deja ver todo el plano si es mayor), sin mostrar lo que hay fuera
 * del plano.</p>
 */
public final class HospitalMapPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Espacio libre alrededor del plano, en píxeles. */
    public static final int MARGIN = 8;

    /** Aumento con el que la cámara sigue al paciente seleccionado. */
    public static final double FOLLOW_ZOOM = 1.6;

    private static final int MAX_PREFERRED_WIDTH = 1200;
    private static final int MAX_PREFERRED_HEIGHT = 640;

    private transient HospitalLayout layout;
    private final transient SceneRenderer renderer;
    private final transient PatientPainter patientPainter = new PatientPainter();
    private final transient StaffPainter staffPainter = new StaffPainter();
    private transient List<StaffMarker> staffMarkers = List.of();
    private transient List<PatientMarker> markers = List.of();
    private int overflow;
    private String selected;
    private boolean following;
    private transient Consumer<String> selectionListener = code -> { };

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
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                String code = patientAt(event.getX(), event.getY()).orElse(null);
                select(code);
                selectionListener.accept(code);
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                setCursor(Cursor.getPredefinedCursor(patientAt(event.getX(), event.getY()).isPresent()
                        ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
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
     * Cambia los médicos y enfermeros que se dibujan encima del plano y vuelve a dibujar.
     *
     * @param markers médicos y enfermeros con su posición en el mundo
     * @throws IllegalArgumentException si la lista es nula
     */
    public void showStaff(List<StaffMarker> markers) {
        if (markers == null) {
            throw new IllegalArgumentException("La lista del personal es obligatoria.");
        }
        this.staffMarkers = List.copyOf(markers);
        repaint();
    }

    /**
     * Selecciona un paciente: se dibuja con un anillo y, si la cámara sigue la selección, la cámara va con él.
     *
     * @param patientCode código del paciente, o nulo para no seleccionar a nadie
     */
    public void select(String patientCode) {
        this.selected = patientCode;
        repaint();
    }

    public Optional<String> getSelected() {
        return Optional.ofNullable(selected);
    }

    /**
     * Hace que la cámara siga o deje de seguir al paciente seleccionado.
     *
     * @param following {@code true} para seguirlo de cerca; {@code false} para ver todo el plano
     */
    public void setFollowing(boolean following) {
        this.following = following;
        repaint();
    }

    public boolean isFollowing() {
        return following;
    }

    /**
     * Cambia a quién se avisa cuando el usuario hace clic en el plano.
     *
     * @param listener recibe el código del paciente al que se le hizo clic, o nulo si fue donde no hay nadie
     * @throws IllegalArgumentException si es nulo
     */
    public void setSelectionListener(Consumer<String> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("El oyente de la selección es obligatorio.");
        }
        this.selectionListener = listener;
    }

    /**
     * Busca el paciente que se ve en un punto del panel.
     *
     * @param screenX coordenada x en el panel, en píxeles
     * @param screenY coordenada y en el panel, en píxeles
     * @return código del paciente, o vacío si ahí no hay nadie
     */
    public Optional<String> patientAt(int screenX, int screenY) {
        Optional<Camera> camera = cameraFor(getWidth(), getHeight());
        if (camera.isEmpty()) {
            return Optional.empty();
        }
        Point2D world = camera.get().toWorld(screenX, screenY);
        return PatientPicker.pick(markers, world.getX(), world.getY());
    }

    /**
     * Cámara con la que se dibuja el plano en un panel de ese tamaño: sigue al paciente seleccionado si así se
     * pidió y está en la escena; si no, muestra el plano completo.
     *
     * @param width  ancho del panel en píxeles
     * @param height alto del panel en píxeles
     * @return la cámara, o vacío si el panel no es más grande que los márgenes
     */
    public Optional<Camera> cameraFor(int width, int height) {
        if (width <= 2 * MARGIN || height <= 2 * MARGIN) {
            return Optional.empty();
        }
        Dimension world = renderer.worldSize(layout);
        Camera fit = Camera.fit(world.width, world.height, width, height, MARGIN);
        Optional<PatientMarker> target = selectedMarker();
        if (!following || target.isEmpty()) {
            return Optional.of(fit);
        }
        double zoom = Math.min(Camera.MAX_ZOOM, Math.max(fit.zoom(), FOLLOW_ZOOM));
        PatientMarker marker = target.get();
        return Optional.of(Camera.follow(marker.x() + PatientPainter.OFFSET_X, marker.y(), world.width,
                world.height, width, height, zoom));
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
        Optional<Camera> camera = cameraFor(width, height);
        if (camera.isEmpty()) {
            return;
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        camera.get().applyTo(graphics);
        renderer.render(graphics, layout);
        staffPainter.paint(graphics, staffMarkers);
        patientPainter.paint(graphics, markers);
        selectedMarker().ifPresent(marker -> patientPainter.paintSelection(graphics, marker));
        List<Position> seats = layout.getWaitingSpots();
        patientPainter.paintOverflow(graphics, renderer.centerOf(seats.get(seats.size() - 1)), overflow);
    }

    /** El paciente seleccionado tal como se dibuja ahora, si está en la escena. */
    private Optional<PatientMarker> selectedMarker() {
        if (selected == null) {
            return Optional.empty();
        }
        return markers.stream().filter(marker -> marker.code().equals(selected)).findFirst();
    }

    private static HospitalLayout requireLayout(HospitalLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("El plano es obligatorio.");
        }
        return layout;
    }
}