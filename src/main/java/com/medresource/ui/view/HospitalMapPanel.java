package com.medresource.ui.view;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.ui.render.Camera;
import com.medresource.ui.render.IsoPalette;
import com.medresource.ui.render.IsometricRenderer;
import com.medresource.ui.render.Palette;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.PatientPainter;
import com.medresource.ui.render.PatientPicker;
import com.medresource.ui.render.PorterMarker;
import com.medresource.ui.render.SceneRenderer;
import com.medresource.ui.render.StaffMarker;
import com.medresource.ui.render.StaffPainter;

import javax.swing.JPanel;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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
 * ({@value #FOLLOW_ZOOM} veces el aumento con que se ve el plano cuando no sigue a nadie), sin mostrar lo
 * que hay fuera del plano.</p>
 *
 * <p>Con {@link #setCovering(boolean)} el plano llena todo el panel, sin dejar fondo a los lados: se ve más grande
 * y lo que no cabe se ve arrastrando el plano con el ratón ({@link #panBy}).</p>
 *
 * <p>La rueda del ratón acerca o aleja el plano alrededor del puntero ({@link #zoomAt}), hasta
 * {@value #MAX_ZOOM_LEVEL} veces el aumento normal; acercado, el plano también se arrastra. Mientras la cámara sigue
 * a un paciente, la rueda cambia cuán de cerca lo sigue.</p>
 *
 * <p>Con {@link #setIsometric(boolean)} el mismo plano se ve en 2.5D ({@link IsometricRenderer}): las personas
 * siguen en los mismos puntos del plano y solo cambia cómo se dibujan, así que el cambio es instantáneo. La cámara,
 * la rueda, el arrastre, el clic y el seguimiento funcionan igual en las dos vistas. Encima del plano también dibuja
 * a los camilleros que le den con {@link #showPorters}.</p>
 */
public final class HospitalMapPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Espacio libre alrededor del plano, en píxeles. */
    public static final int MARGIN = 8;

    /**
     * Cuántas veces más cerca que el plano sin seguir a nadie se ve al paciente seleccionado mientras la cámara lo
     * sigue; así el acercamiento es el mismo con la ventana grande o pequeña.
     */
    public static final double FOLLOW_ZOOM = 3.0;

    /** Cuántos píxeles hay que mover el ratón presionado para que cuente como arrastre y no como clic. */
    public static final int DRAG_THRESHOLD = 4;

    /** Cuántas veces más cerca que lo normal se puede ver el plano con la rueda del ratón. */
    public static final double MAX_ZOOM_LEVEL = 6.0;

    /** Cuánto acerca cada paso de la rueda del ratón. */
    public static final double WHEEL_STEP = 1.15;

    /** Ancho máximo que pide el panel, para que la ventana de la simulación quepa en una pantalla de 1366 × 768. */
    public static final int MAX_PREFERRED_WIDTH = 1060;
    /** Alto máximo que pide el panel, por la misma razón. */
    public static final int MAX_PREFERRED_HEIGHT = 450;

    private transient HospitalLayout layout;
    private final transient SceneRenderer renderer;
    private final transient IsometricRenderer isometricRenderer;
    private final transient PatientPainter patientPainter = new PatientPainter();
    private final transient StaffPainter staffPainter = new StaffPainter();
    private transient List<StaffMarker> staffMarkers = List.of();
    private transient List<PatientMarker> markers = List.of();
    private transient List<PorterMarker> porters = List.of();
    private boolean isometric;
    private int overflow;
    private String selected;
    private boolean following;
    private boolean covering;
    private double centerX = Double.NaN;
    private double centerY = Double.NaN;
    private double zoomLevel = 1;
    private double followLevel = 1;
    private transient Point dragFrom;
    private boolean dragged;
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
        this.isometricRenderer = new IsometricRenderer(renderer);
        this.layout = requireLayout(layout);
        setBackground(Palette.BACKGROUND);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (dragged) {
                    return;
                }
                String code = patientAt(event.getX(), event.getY()).orElse(null);
                select(code);
                selectionListener.accept(code);
            }

            @Override
            public void mousePressed(MouseEvent event) {
                dragFrom = event.getPoint();
                dragged = false;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragFrom == null || !canPan()) {
                    return;
                }
                int dx = event.getX() - dragFrom.x;
                int dy = event.getY() - dragFrom.y;
                if (!dragged && Math.abs(dx) + Math.abs(dy) < DRAG_THRESHOLD) {
                    return;
                }
                dragged = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                panBy(dx, dy);
                dragFrom = event.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragFrom = null;
                if (dragged) {
                    setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                setCursor(Cursor.getPredefinedCursor(patientAt(event.getX(), event.getY()).isPresent()
                        ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(this::onWheel);
    }

    private void onWheel(MouseWheelEvent event) {
        double rotation = event.getPreciseWheelRotation();
        if (rotation != 0) {
            zoomAt(event.getX(), event.getY(), Math.pow(WHEEL_STEP, -rotation));
        }
    }

    /**
     * Cambia el plano que se muestra y vuelve a dibujar.
     *
     * @param layout plano nuevo
     * @throws IllegalArgumentException si el plano es nulo
     */
    public void showLayout(HospitalLayout layout) {
        this.layout = requireLayout(layout);
        centerX = Double.NaN;
        centerY = Double.NaN;
        zoomLevel = 1;
        followLevel = 1;
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
     * Cambia los camilleros que se dibujan encima del plano y vuelve a dibujar.
     *
     * @param porters camilleros con su posición en el mundo
     * @throws IllegalArgumentException si la lista es nula
     */
    public void showPorters(List<PorterMarker> porters) {
        if (porters == null) {
            throw new IllegalArgumentException("La lista de camilleros es obligatoria.");
        }
        this.porters = List.copyOf(porters);
        repaint();
    }

    /**
     * Cambia entre la vista 2D y la 2.5D. Al cambiar, vuelve al aumento normal y al centro del plano; la selección y
     * el seguimiento se conservan.
     *
     * @param isometric {@code true} para la vista 2.5D; {@code false} para la 2D
     */
    public void setIsometric(boolean isometric) {
        if (this.isometric != isometric) {
            this.isometric = isometric;
            resetZoom();
        }
    }

    public boolean isIsometric() {
        return isometric;
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
        if (following && !this.following) {
            followLevel = 1;
        }
        this.following = following;
        repaint();
    }

    public boolean isFollowing() {
        return following;
    }

    /**
     * Hace que el plano llene todo el panel o que se vea completo.
     *
     * @param covering {@code true} para que llene el panel sin dejar fondo a los lados (lo que no cabe se ve
     *                 arrastrando); {@code false} para ver todo el plano con un margen alrededor
     */
    public void setCovering(boolean covering) {
        this.covering = covering;
        repaint();
    }

    public boolean isCovering() {
        return covering;
    }

    /**
     * Mueve el plano como si se arrastrara con el ratón, sin pasarse de sus bordes. Solo hace algo cuando el plano
     * llena el panel y la cámara no sigue a nadie.
     *
     * @param dx píxeles que se mueve el plano hacia la derecha (negativo: hacia la izquierda)
     * @param dy píxeles que se mueve el plano hacia abajo (negativo: hacia arriba)
     */
    public void panBy(int dx, int dy) {
        if (!canPan()) {
            return;
        }
        Optional<Camera> camera = cameraFor(getWidth(), getHeight());
        if (camera.isEmpty()) {
            return;
        }
        Point2D wanted = camera.get().toWorld(getWidth() / 2.0 - dx, getHeight() / 2.0 - dy);
        keepCenter(wanted.getX(), wanted.getY());
        repaint();
    }

    /**
     * Acerca o aleja el plano dejando quieto el punto que está bajo el puntero, como hace la rueda del ratón. El
     * aumento queda entre el normal y {@value #MAX_ZOOM_LEVEL} veces el normal. Si la cámara sigue a un paciente,
     * cambia cuán de cerca lo sigue.
     *
     * @param screenX coordenada x del puntero en el panel
     * @param screenY coordenada y del puntero en el panel
     * @param factor  cuánto se acerca (mayor que 1) o se aleja (menor que 1)
     * @throws IllegalArgumentException si el factor no es un número positivo
     */
    public void zoomAt(int screenX, int screenY, double factor) {
        if (Double.isNaN(factor) || factor <= 0) {
            throw new IllegalArgumentException("El factor de aumento debe ser positivo.");
        }
        Optional<Camera> camera = cameraFor(getWidth(), getHeight());
        if (camera.isEmpty()) {
            return;
        }
        if (following && selectedMarker().isPresent()) {
            followLevel = clamp(followLevel * factor, 1 / FOLLOW_ZOOM, MAX_ZOOM_LEVEL / FOLLOW_ZOOM);
            repaint();
            return;
        }
        Point2D anchor = camera.get().toWorld(screenX, screenY);
        zoomLevel = clamp(zoomLevel * factor, 1, MAX_ZOOM_LEVEL);
        Dimension world = worldSize();
        double zoom = clamp(baseZoom(world, getWidth(), getHeight()) * zoomLevel, Camera.MIN_ZOOM, Camera.MAX_ZOOM);
        keepCenter(anchor.getX() - (screenX - getWidth() / 2.0) / zoom,
                anchor.getY() - (screenY - getHeight() / 2.0) / zoom);
        repaint();
    }

    /** Vuelve al aumento normal y al centro del plano, como si nunca se hubiera usado la rueda ni arrastrado. */
    public void resetZoom() {
        zoomLevel = 1;
        followLevel = 1;
        centerX = Double.NaN;
        centerY = Double.NaN;
        repaint();
    }

    /** Cuántas veces más cerca que lo normal se ve el plano por la rueda del ratón; 1 es lo normal. */
    public double getZoomLevel() {
        return zoomLevel;
    }

    /** Guarda el centro de la vista, corregido para no pasarse de los bordes del plano. */
    private void keepCenter(double x, double y) {
        Camera moved = viewCamera(getWidth(), getHeight(), x, y);
        Point2D center = moved.toWorld(getWidth() / 2.0, getHeight() / 2.0);
        centerX = center.getX();
        centerY = center.getY();
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
        if (isometric) {
            return isometricRenderer.pick(layout, markers, world.getX(), world.getY());
        }
        return PatientPicker.pick(markers, world.getX(), world.getY());
    }

    /**
     * Cámara con la que se dibuja el plano en un panel de ese tamaño: sigue al paciente seleccionado si así se
     * pidió y está en la escena; si no, muestra el plano completo o, si el plano llena el panel, la parte a la que
     * se arrastró.
     *
     * @param width  ancho del panel en píxeles
     * @param height alto del panel en píxeles
     * @return la cámara, o vacío si el panel no es más grande que los márgenes
     */
    public Optional<Camera> cameraFor(int width, int height) {
        if (width <= 2 * MARGIN || height <= 2 * MARGIN) {
            return Optional.empty();
        }
        Dimension world = worldSize();
        Optional<PatientMarker> target = selectedMarker();
        if (!following || target.isEmpty()) {
            return Optional.of(viewCamera(width, height, centerX, centerY));
        }
        double zoom = clamp(baseZoom(world, width, height) * FOLLOW_ZOOM * followLevel, Camera.MIN_ZOOM,
                Camera.MAX_ZOOM);
        Point2D focus = focusOf(target.get());
        return Optional.of(Camera.follow(focus.getX(), focus.getY(), world.width, world.height, width, height, zoom));
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
        graphics.setColor(isometric ? IsoPalette.BACKGROUND : Palette.BACKGROUND);
        graphics.fillRect(0, 0, width, height);
        Optional<Camera> camera = cameraFor(width, height);
        if (camera.isEmpty()) {
            return;
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        camera.get().applyTo(graphics);
        if (isometric) {
            isometricRenderer.paint(graphics, layout, markers, staffMarkers, porters, selected, overflow);
            return;
        }
        renderer.render(graphics, layout);
        staffPainter.paint(graphics, staffMarkers);
        staffPainter.paintPorters(graphics, porters);
        patientPainter.paint(graphics, markers);
        selectedMarker().ifPresent(marker -> patientPainter.paintSelection(graphics, marker));
        List<Position> seats = layout.getWaitingSpots();
        patientPainter.paintOverflow(graphics, renderer.centerOf(seats.get(seats.size() - 1)), overflow);
    }

    /** Indica si el plano se puede arrastrar: llena el panel y la cámara no sigue a nadie. */
    private boolean canPan() {
        return (covering || zoomLevel > 1) && !(following && selectedMarker().isPresent());
    }

    /**
     * Cámara sin seguir a nadie: el plano completo, o el que llena el panel, con el aumento de la rueda y centrada
     * en el punto pedido (el centro del plano si no hay ninguno).
     */
    private Camera viewCamera(int width, int height, double x, double y) {
        Dimension world = worldSize();
        if (!covering && zoomLevel <= 1) {
            return Camera.fit(world.width, world.height, width, height, MARGIN);
        }
        double zoom = clamp(baseZoom(world, width, height) * zoomLevel, Camera.MIN_ZOOM, Camera.MAX_ZOOM);
        return Camera.follow(Double.isNaN(x) ? world.width / 2.0 : x, Double.isNaN(y) ? world.height / 2.0 : y,
                world.width, world.height, width, height, zoom);
    }

    /** Tamaño del mundo de la vista que se está usando, en píxeles. */
    private Dimension worldSize() {
        return isometric ? isometricRenderer.worldSize(layout) : renderer.worldSize(layout);
    }

    /** Punto del mundo de la vista que se está usando donde la cámara sigue a un paciente. */
    private Point2D focusOf(PatientMarker marker) {
        if (isometric) {
            return isometricRenderer.focusOf(layout, marker);
        }
        return new Point2D.Double(marker.x() + PatientPainter.OFFSET_X, marker.y());
    }

    /** Aumento normal: el que deja ver todo el plano o, si llena el panel, el que lo llena. */
    private double baseZoom(Dimension world, int width, int height) {
        if (covering) {
            return Math.max(width / (double) world.width, height / (double) world.height);
        }
        return Camera.fit(world.width, world.height, width, height, MARGIN).zoom();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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