package com.medresource.ui.view;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.ui.controller.PlaceInfo;
import com.medresource.ui.render.Camera;
import com.medresource.ui.render.IsoProjection;
import com.medresource.ui.render.IsoTheme;
import com.medresource.ui.render.IsometricRenderer;
import com.medresource.ui.render.Palette;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.PatientPainter;
import com.medresource.ui.render.PatientPicker;
import com.medresource.ui.render.PlanAlert;
import com.medresource.ui.render.PorterMarker;
import com.medresource.ui.render.SceneRenderer;
import com.medresource.ui.render.StaffMarker;
import com.medresource.ui.render.StaffPainter;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
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
import java.util.function.Function;

/**
 * Panel de Swing que muestra el plano del hospital.
 *
 * <p>Dibuja el plano con el {@link SceneRenderer} que recibe y lo ajusta al tamaño del panel con una
 * {@link Camera}: se ve completo y centrado, y se reajusta solo cuando la ventana cambia de tamaño. Encima
 * del plano dibuja al personal que le den con {@link #showStaff} y a los pacientes que le den con
 * {@link #showPatients}. No tiene reglas de simulación: solo muestra lo que le dan.</p>
 *
 * <p>Al hacer clic sobre un paciente, el panel lo selecciona (lo marca con un anillo) y avisa a quien escucha con
 * {@link #setSelectionListener}; al hacer clic sobre un médico o un enfermero avisa a quien escucha con
 * {@link #setStaffListener}; al hacer clic donde no hay nadie, quita la selección. Con
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
 * siguen en los mismos puntos del plano y solo cambia cómo se dibujan. La cámara, la rueda, el arrastre, el clic y el
 * seguimiento funcionan igual en las dos vistas. Encima del plano también dibuja a los camilleros que le den con
 * {@link #showPorters}. Los monitores de la UCI se mueven con el reloj real.</p>
 *
 * <p>En la vista 2.5D la cámara es libre: arrastrando con el botón derecho el hospital gira (de lado a lado) y la
 * mirada sube hasta verlo desde arriba (de arriba a abajo) ({@link #rotateBy}); la brújula de arriba a la derecha
 * muestra hacia dónde queda la parte de arriba del plano y un clic sobre ella, como {@link #resetView}, devuelve la
 * vista inicial.</p>
 *
 * <p>Con {@link #setAnimated(boolean)} los cambios de cámara se ven poco a poco: al seguir a un paciente la cámara lo
 * alcanza y lo acompaña con un pequeño retraso en vez de saltar, al restablecer la vista el hospital gira de vuelta y
 * al cambiar entre 2D y 2.5D el plano se inclina y se levanta (o se aplana) durante {@value #TRANSITION_SECONDS} s.
 * Sin animación (lo normal en las pruebas), todo cambia al instante.</p>
 *
 * <p>Los avisos que le den con {@link #showAlerts} se dibujan como íconos sobre su lugar; un clic sobre uno lleva la
 * cámara hasta allá ({@link #flyTo}). Con {@link #setHoverEnabled(boolean)} (lo pide la pantalla completa), al pasar
 * el ratón sobre una cama, un quirófano o un consultorio aparece un panel flotante con quién está ahí, desde hace
 * cuánto y cuánto le falta, según lo que diga {@link #setPlaceInfo}. Con {@link #setBanner} se ve un cartel arriba,
 * por ejemplo durante una repetición.</p>
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

    /** Grados que gira el hospital por cada píxel que se arrastra de lado con el botón derecho. */
    public static final double YAW_PER_PIXEL = 0.4;
    /** Grados que sube la mirada por cada píxel que se arrastra hacia arriba con el botón derecho. */
    public static final double ELEVATION_PER_PIXEL = 0.3;

    /** Segundos que tarda la cámara en recorrer la mayor parte del camino hacia donde tiene que estar. */
    public static final double CAMERA_LAG = 0.25;
    /** Segundos que dura el cambio animado entre la vista 2D y la 2.5D. */
    public static final double TRANSITION_SECONDS = 0.8;
    /** Segundos que tarda el hospital en volver a la vista inicial al restablecerla. */
    public static final double RESET_SECONDS = 0.6;
    /** Aumento, respecto al normal, con que la cámara llega al lugar de un aviso. */
    public static final double ALERT_ZOOM = 3.0;
    /** Altura, en metros, a la que va el ícono de un aviso sobre su lugar en la vista 2.5D. */
    public static final double ALERT_HEIGHT = 1.7;

    /** Segundos que sigue suave la cámara después de un salto (empezar o dejar de seguir, ir a un aviso). */
    private static final double SMOOTH_AFTER_JUMP = 1.5;
    private static final int ANIMATION_MILLIS = 16;

    private transient HospitalLayout layout;
    private final transient SceneRenderer renderer;
    private final transient IsometricRenderer isometricRenderer;
    private final transient PatientPainter patientPainter = new PatientPainter();
    private final transient StaffPainter staffPainter = new StaffPainter();
    private transient List<StaffMarker> staffMarkers = List.of();
    private transient List<PatientMarker> markers = List.of();
    private transient List<PorterMarker> porters = List.of();
    private transient List<PlanAlert> alerts = List.of();
    private boolean isometric;
    private int overflow;
    private String selected;
    private boolean following;
    private boolean covering;
    private double centerX = Double.NaN;
    private double centerY = Double.NaN;
    private double zoomLevel = 1;
    private double followLevel = 1;
    private double yaw;
    private double elevation = IsoProjection.DEFAULT_ELEVATION;
    private transient Point dragFrom;
    private boolean dragged;
    private boolean rotating;
    private boolean animated;
    private transient Camera shown;
    private int shownWidth;
    private int shownHeight;
    private long lastPaintNanos;
    private long smoothUntilNanos;
    private long transitionStartNanos;
    private boolean transitionToIsometric;
    private long resetStartNanos;
    private double resetFromYaw;
    private double resetFromElevation;
    private boolean hoverEnabled;
    private transient Position hoverCell;
    private transient Point hoverPoint;
    private transient PlaceInfo hoverInfo;
    private String banner;
    private final transient Timer animation;
    private transient Consumer<String> selectionListener = code -> { };
    private transient Consumer<String> staffListener = code -> { };
    private transient Consumer<Boolean> followListener = on -> { };
    private transient Function<Position, Optional<PlaceInfo>> placeInfo = cell -> Optional.empty();

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
        this.animation = new Timer(ANIMATION_MILLIS, event -> repaint());
        setBackground(Palette.BACKGROUND);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (dragged || !SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                clickAt(event.getX(), event.getY());
            }

            @Override
            public void mousePressed(MouseEvent event) {
                dragFrom = event.getPoint();
                dragged = false;
                rotating = SwingUtilities.isRightMouseButton(event) && isometric;
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragFrom == null) {
                    return;
                }
                int dx = event.getX() - dragFrom.x;
                int dy = event.getY() - dragFrom.y;
                if (!dragged && Math.abs(dx) + Math.abs(dy) < DRAG_THRESHOLD) {
                    return;
                }
                if (rotating) {
                    dragged = true;
                    rotateBy(dx * YAW_PER_PIXEL, -dy * ELEVATION_PER_PIXEL);
                } else if (canPan()) {
                    dragged = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    panBy(dx, dy);
                } else {
                    return;
                }
                dragFrom = event.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragFrom = null;
                rotating = false;
                if (dragged) {
                    setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                boolean onSomething = patientAt(event.getX(), event.getY()).isPresent()
                        || staffAt(event.getX(), event.getY()).isPresent()
                        || alertAt(event.getX(), event.getY()).isPresent()
                        || onCompass(event.getX(), event.getY());
                setCursor(Cursor.getPredefinedCursor(onSomething ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                hoverAt(event.getX(), event.getY());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                clearHover();
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
     * Hace lo que pide un clic en un punto del panel: en la brújula, restablece la vista; en un aviso, lleva la
     * cámara hasta allá; en un paciente, lo selecciona; en un médico o un enfermero, avisa quién es; donde no hay
     * nadie, quita la selección.
     *
     * @param screenX coordenada x en el panel
     * @param screenY coordenada y en el panel
     */
    public void clickAt(int screenX, int screenY) {
        if (onCompass(screenX, screenY)) {
            resetView();
            return;
        }
        Optional<PlanAlert> alert = alertAt(screenX, screenY);
        if (alert.isPresent()) {
            flyTo(alert.get().position());
            return;
        }
        Optional<String> patient = patientAt(screenX, screenY);
        if (patient.isEmpty()) {
            Optional<String> member = staffAt(screenX, screenY);
            if (member.isPresent()) {
                staffListener.accept(member.get());
                return;
            }
        }
        String code = patient.orElse(null);
        select(code);
        selectionListener.accept(code);
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
        shown = null;
        clearHover();
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
        if (hoverCell != null) {
            hoverInfo = placeInfo.apply(hoverCell).orElse(null);
        }
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
     * Cambia los avisos que se dibujan sobre el plano y vuelve a dibujar.
     *
     * @param alerts avisos, cada uno sobre su lugar
     * @throws IllegalArgumentException si la lista es nula
     */
    public void showAlerts(List<PlanAlert> alerts) {
        if (alerts == null) {
            throw new IllegalArgumentException("La lista de avisos es obligatoria.");
        }
        this.alerts = List.copyOf(alerts);
        repaint();
    }

    /**
     * Cambia entre la vista 2D y la 2.5D. Al cambiar, vuelve al aumento normal y al centro del plano; la selección,
     * el seguimiento y el ángulo de la cámara 2.5D se conservan. Con animación, el plano se inclina y se levanta (o
     * se aplana) poco a poco.
     *
     * @param isometric {@code true} para la vista 2.5D; {@code false} para la 2D
     */
    public void setIsometric(boolean isometric) {
        if (this.isometric != isometric) {
            this.isometric = isometric;
            clearHover();
            if (animated) {
                transitionStartNanos = System.nanoTime();
                transitionToIsometric = isometric;
                animation.start();
            }
            resetZoom();
            shown = null;
        }
    }

    public boolean isIsometric() {
        return isometric;
    }

    /** Indica si el plano está cambiando, con animación, entre la vista 2D y la 2.5D. */
    public boolean isInTransition() {
        return transitionStartNanos != 0 && transitionShare() < 1.0;
    }

    /**
     * Cambia el tema de la vista 2.5D, claro u oscuro. No cambia la cámara ni la selección.
     *
     * @param theme tema
     * @throws IllegalArgumentException si es nulo
     */
    public void setIsoTheme(IsoTheme theme) {
        isometricRenderer.setTheme(theme);
        repaint();
    }

    /** Tema de la vista 2.5D: el claro, si nadie lo cambia. */
    public IsoTheme getIsoTheme() {
        return isometricRenderer.getTheme();
    }

    /**
     * Gira el hospital y sube o baja la mirada de la vista 2.5D, como al arrastrar con el botón derecho. La
     * elevación queda entre {@value IsoProjection#DEFAULT_ELEVATION} y {@value IsoProjection#MAX_ELEVATION} grados.
     *
     * @param degreesYaw       grados que gira el hospital
     * @param degreesElevation grados que sube la mirada (negativo: baja)
     */
    public void rotateBy(double degreesYaw, double degreesElevation) {
        setView(yaw + degreesYaw, clamp(elevation + degreesElevation, IsoProjection.DEFAULT_ELEVATION,
                IsoProjection.MAX_ELEVATION));
    }

    /**
     * Cambia el ángulo de la cámara 2.5D al instante. Si la vista estaba acercada, el punto del piso que se veía en el
     * centro sigue en el centro.
     *
     * @param newYaw       giro del hospital, en grados
     * @param newElevation elevación de la mirada, en grados
     * @throws IllegalArgumentException si el giro no es un número o la elevación está fuera de su rango
     */
    public void setView(double newYaw, double newElevation) {
        IsoProjection before = isometricRenderer.projectionOf(layout);
        IsoProjection after = before.withView(newYaw, newElevation);
        Point2D plan = Double.isNaN(centerX) ? null : before.toPlan(centerX, centerY);
        resetStartNanos = 0;
        yaw = after.yaw();
        elevation = after.elevation();
        isometricRenderer.setView(yaw, elevation);
        if (plan != null && isometric) {
            Point2D moved = after.fromPlan(plan.getX(), plan.getY(), 0);
            keepCenter(moved.getX(), moved.getY());
        }
        repaint();
    }

    /** Giro de la cámara 2.5D, en grados: 0 en la vista inicial. */
    public double getYaw() {
        return yaw;
    }

    /** Elevación de la cámara 2.5D, en grados: {@value IsoProjection#DEFAULT_ELEVATION} en la vista inicial. */
    public double getElevation() {
        return elevation;
    }

    /**
     * Devuelve la vista inicial: sin giro, con la elevación de siempre, con el aumento normal y centrada. Con
     * animación, el hospital gira de vuelta poco a poco.
     */
    public void resetView() {
        double fromYaw = yaw;
        double fromElevation = elevation;
        setView(0, IsoProjection.DEFAULT_ELEVATION);
        resetZoom();
        if (animated && isometric && (fromYaw != 0 || fromElevation != IsoProjection.DEFAULT_ELEVATION)) {
            resetFromYaw = fromYaw;
            resetFromElevation = fromElevation;
            resetStartNanos = System.nanoTime();
            jumped();
        }
    }

    /**
     * Hace que los cambios de cámara se vean poco a poco o al instante.
     *
     * @param animated {@code true} para animarlos; {@code false} para que cambien al instante
     */
    public void setAnimated(boolean animated) {
        this.animated = animated;
        if (!animated) {
            animation.stop();
            transitionStartNanos = 0;
            resetStartNanos = 0;
            shown = null;
        }
    }

    public boolean isAnimated() {
        return animated;
    }

    /**
     * Selecciona un paciente: se dibuja con un anillo y, si la cámara sigue la selección, la cámara va con él.
     *
     * @param patientCode código del paciente, o nulo para no seleccionar a nadie
     */
    public void select(String patientCode) {
        if (following && patientCode != null && !patientCode.equals(selected)) {
            jumped();
        }
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
        if (following != this.following) {
            jumped();
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
        shown = null;
        repaint();
    }

    public boolean isCovering() {
        return covering;
    }

    /**
     * Lleva la cámara hasta una casilla del plano: deja de seguir al paciente (y avisa con
     * {@link #setFollowListener}), se acerca {@value #ALERT_ZOOM} veces y la pone en el centro.
     *
     * @param position casilla
     * @throws IllegalArgumentException si es nula
     */
    public void flyTo(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("La casilla es obligatoria.");
        }
        if (following) {
            following = false;
            followListener.accept(false);
        }
        jumped();
        zoomLevel = Math.max(zoomLevel, ALERT_ZOOM);
        Point2D target = worldPointOf(position, 0);
        keepCenter(target.getX(), target.getY());
        repaint();
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
        if (getWidth() <= 2 * MARGIN || getHeight() <= 2 * MARGIN) {
            centerX = x;
            centerY = y;
            return;
        }
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
     * Cambia a quién se avisa cuando el usuario hace clic sobre un médico o un enfermero.
     *
     * @param listener recibe el código del médico o del enfermero
     * @throws IllegalArgumentException si es nulo
     */
    public void setStaffListener(Consumer<String> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("El oyente del personal es obligatorio.");
        }
        this.staffListener = listener;
    }

    /**
     * Cambia a quién se avisa cuando el panel deja de seguir al paciente por su cuenta (al ir a un aviso).
     *
     * @param listener recibe {@code false} cuando deja de seguirlo
     * @throws IllegalArgumentException si es nulo
     */
    public void setFollowListener(Consumer<Boolean> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("El oyente del seguimiento es obligatorio.");
        }
        this.followListener = listener;
    }

    /**
     * Cambia de dónde sale lo que muestra el panel flotante de un lugar.
     *
     * @param provider da lo que hay en una casilla, o vacío si ahí no hay nada que mostrar
     * @throws IllegalArgumentException si es nulo
     */
    public void setPlaceInfo(Function<Position, Optional<PlaceInfo>> provider) {
        if (provider == null) {
            throw new IllegalArgumentException("La fuente de la información de los lugares es obligatoria.");
        }
        this.placeInfo = provider;
    }

    /**
     * Activa o desactiva el panel flotante que aparece al pasar el ratón sobre un lugar.
     *
     * @param enabled {@code true} para mostrarlo (en la pantalla completa); {@code false} para no mostrarlo
     */
    public void setHoverEnabled(boolean enabled) {
        this.hoverEnabled = enabled;
        if (!enabled) {
            clearHover();
        }
    }

    public boolean isHoverEnabled() {
        return hoverEnabled;
    }

    /**
     * Pasa el ratón por un punto del panel: si el panel flotante está activo y ahí hay un lugar con algo que mostrar,
     * lo muestra junto al puntero; si no, lo quita.
     *
     * @param screenX coordenada x del puntero
     * @param screenY coordenada y del puntero
     */
    public void hoverAt(int screenX, int screenY) {
        if (!hoverEnabled || isInTransition()) {
            clearHover();
            return;
        }
        Optional<Position> cell = cellAt(screenX, screenY);
        hoverCell = cell.orElse(null);
        hoverInfo = cell.flatMap(placeInfo).orElse(null);
        hoverPoint = hoverInfo == null ? null : new Point(screenX, screenY);
        repaint();
    }

    /** Lo que muestra el panel flotante ahora, o vacío si no se ve. */
    public Optional<PlaceInfo> getHoverInfo() {
        return Optional.ofNullable(hoverPoint == null ? null : hoverInfo);
    }

    private void clearHover() {
        boolean had = hoverPoint != null;
        hoverCell = null;
        hoverInfo = null;
        hoverPoint = null;
        if (had) {
            repaint();
        }
    }

    /**
     * Pone o quita el cartel de arriba del plano.
     *
     * @param text texto del cartel, o nulo para quitarlo
     */
    public void setBanner(String text) {
        this.banner = text == null || text.isBlank() ? null : text;
        repaint();
    }

    /** Texto del cartel de arriba del plano, o vacío si no hay. */
    public Optional<String> getBanner() {
        return Optional.ofNullable(banner);
    }

    /**
     * Busca el paciente que se ve en un punto del panel.
     *
     * @param screenX coordenada x en el panel, en píxeles
     * @param screenY coordenada y en el panel, en píxeles
     * @return código del paciente, o vacío si ahí no hay nadie
     */
    public Optional<String> patientAt(int screenX, int screenY) {
        Optional<Point2D> world = worldAt(screenX, screenY);
        if (world.isEmpty()) {
            return Optional.empty();
        }
        if (isometric) {
            return isometricRenderer.pick(layout, markers, world.get().getX(), world.get().getY());
        }
        return PatientPicker.pick(markers, world.get().getX(), world.get().getY());
    }

    /**
     * Busca al médico o enfermero que se ve en un punto del panel.
     *
     * @param screenX coordenada x en el panel, en píxeles
     * @param screenY coordenada y en el panel, en píxeles
     * @return su código, o vacío si ahí no hay nadie del personal
     */
    public Optional<String> staffAt(int screenX, int screenY) {
        Optional<Point2D> world = worldAt(screenX, screenY);
        if (world.isEmpty()) {
            return Optional.empty();
        }
        if (isometric) {
            return isometricRenderer.pickStaff(layout, staffMarkers, world.get().getX(), world.get().getY());
        }
        return StaffPainter.pick(staffMarkers, world.get().getX(), world.get().getY());
    }

    /**
     * Busca el aviso cuyo ícono se ve en un punto del panel.
     *
     * @param screenX coordenada x en el panel, en píxeles
     * @param screenY coordenada y en el panel, en píxeles
     * @return el aviso, o vacío si ahí no hay ninguno
     */
    public Optional<PlanAlert> alertAt(int screenX, int screenY) {
        Optional<Camera> camera = displayedCamera(getWidth(), getHeight());
        if (camera.isEmpty()) {
            return Optional.empty();
        }
        for (int index = alerts.size() - 1; index >= 0; index--) {
            PlanAlert alert = alerts.get(index);
            if (MapOverlay.hitsAlert(alertPoint(camera.get(), alert), screenX, screenY)) {
                return Optional.of(alert);
            }
        }
        return Optional.empty();
    }

    /**
     * Casilla del piso que se ve en un punto del panel.
     *
     * @param screenX coordenada x en el panel, en píxeles
     * @param screenY coordenada y en el panel, en píxeles
     * @return la casilla, o vacío si el punto queda fuera del plano
     */
    public Optional<Position> cellAt(int screenX, int screenY) {
        Optional<Point2D> world = worldAt(screenX, screenY);
        if (world.isEmpty()) {
            return Optional.empty();
        }
        IsoProjection projection = isometricRenderer.projectionOf(layout);
        Point2D plan = isometric ? projection.toPlan(world.get().getX(), world.get().getY()) : world.get();
        int col = (int) Math.floor(projection.gridX(plan.getX()));
        int row = (int) Math.floor(projection.gridY(plan.getY()));
        if (col < 0 || row < 0 || col >= layout.getCols() || row >= layout.getRows()) {
            return Optional.empty();
        }
        return Optional.of(new Position(col, row));
    }

    /**
     * Cámara con la que se dibuja el plano en un panel de ese tamaño: sigue al paciente seleccionado si así se
     * pidió y está en la escena; si no, muestra el plano completo o, si el plano llena el panel, la parte a la que
     * se arrastró. Es adonde va la cámara: con animación, lo que se ve la alcanza poco a poco.
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
        paintScene(graphics, width, height, cameraFor(width, height));
        paintOverlay(graphics, width, height, cameraFor(width, height));
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
        int width = getWidth();
        int height = getHeight();
        if (isInTransition()) {
            paintTransition(surface, width, height);
        } else {
            transitionStartNanos = 0;
            Optional<Camera> camera = advanceCamera(width, height);
            paintScene(surface, width, height, camera);
            paintOverlay(surface, width, height, camera);
        }
        surface.dispose();
        if (!isAnimating()) {
            animation.stop();
        }
    }

    // ---------- cámara animada ----------

    /** Anota que la cámara dio un salto: durante un rato lo que se ve la alcanza poco a poco. */
    private void jumped() {
        if (animated) {
            smoothUntilNanos = System.nanoTime() + (long) (SMOOTH_AFTER_JUMP * 1e9);
            animation.start();
        }
    }

    /** Indica si algo de la cámara sigue moviéndose y hay que volver a dibujar aunque la escena no cambie. */
    private boolean isAnimating() {
        if (!animated) {
            return false;
        }
        long now = System.nanoTime();
        return isInTransition() || resetStartNanos != 0 || now < smoothUntilNanos;
    }

    /** Indica si la cámara que se ve debe ir detrás de la que pide la escena, en vez de saltar a ella. */
    private boolean smoothing() {
        return animated && (following || System.nanoTime() < smoothUntilNanos);
    }

    /**
     * Cámara de este cuadro: con animación, se acerca a la que pide la escena una parte del camino según el tiempo
     * que pasó desde el cuadro anterior; sin animación, es la que pide la escena.
     */
    private Optional<Camera> advanceCamera(int width, int height) {
        applyViewAnimation();
        Optional<Camera> target = cameraFor(width, height);
        long now = System.nanoTime();
        double elapsed = lastPaintNanos == 0 ? 0 : Math.min(0.1, (now - lastPaintNanos) / 1e9);
        lastPaintNanos = now;
        if (target.isEmpty() || !smoothing() || shown == null || shownWidth != width || shownHeight != height) {
            remember(target.orElse(null), width, height);
            return target;
        }
        double share = 1 - Math.exp(-elapsed / CAMERA_LAG);
        Point2D from = shown.toWorld(width / 2.0, height / 2.0);
        Point2D to = target.get().toWorld(width / 2.0, height / 2.0);
        double zoom = Math.exp(Math.log(shown.zoom()) + (Math.log(target.get().zoom()) - Math.log(shown.zoom()))
                * share);
        double x = from.getX() + (to.getX() - from.getX()) * share;
        double y = from.getY() + (to.getY() - from.getY()) * share;
        Camera next = new Camera(zoom, width / 2.0 - x * zoom, height / 2.0 - y * zoom);
        boolean close = Math.hypot(to.getX() - x, to.getY() - y) * zoom < 0.5
                && Math.abs(Math.log(target.get().zoom() / zoom)) < 0.002;
        remember(close ? target.get() : next, width, height);
        if (!close) {
            animation.start();
        }
        return Optional.of(shown);
    }

    private void remember(Camera camera, int width, int height) {
        shown = camera;
        shownWidth = width;
        shownHeight = height;
    }

    /** Cámara con la que se ve ahora el plano: la del último cuadro dibujado o, si no hay, la que pide la escena. */
    private Optional<Camera> displayedCamera(int width, int height) {
        if (animated && shown != null && shownWidth == width && shownHeight == height) {
            return Optional.of(shown);
        }
        return cameraFor(width, height);
    }

    /** Punto del mundo de la vista que se ve en un punto del panel. */
    private Optional<Point2D> worldAt(int screenX, int screenY) {
        return displayedCamera(getWidth(), getHeight()).map(camera -> camera.toWorld(screenX, screenY));
    }

    /** Mientras el hospital vuelve a la vista inicial, pone en el renderizador el ángulo de este cuadro. */
    private void applyViewAnimation() {
        if (resetStartNanos == 0) {
            isometricRenderer.setView(yaw, elevation);
            return;
        }
        double share = Math.min(1.0, (System.nanoTime() - resetStartNanos) / (RESET_SECONDS * 1e9));
        double eased = ease(share);
        isometricRenderer.setView(turn(resetFromYaw, yaw, eased),
                resetFromElevation + (elevation - resetFromElevation) * eased);
        if (share >= 1.0) {
            resetStartNanos = 0;
        }
    }

    /** Parte del cambio entre la vista 2D y la 2.5D que ya pasó, de 0 a 1. */
    private double transitionShare() {
        if (transitionStartNanos == 0) {
            return 1.0;
        }
        return Math.min(1.0, (System.nanoTime() - transitionStartNanos) / (TRANSITION_SECONDS * 1e9));
    }

    /**
     * Un cuadro del cambio entre 2D y 2.5D: el hospital en 2.5D, que pasa de verse plano desde arriba y alineado
     * con el plano 2D a la vista de la cámara, y encima el plano 2D, que se desvanece al principio.
     */
    private void paintTransition(Graphics2D graphics, int width, int height) {
        double share = transitionShare();
        double raised = ease(transitionToIsometric ? share : 1 - share);
        isometricRenderer.setView(turn(-45, yaw, raised),
                IsoProjection.MAX_ELEVATION + (elevation - IsoProjection.MAX_ELEVATION) * raised);
        graphics.setColor(isometricRenderer.background());
        graphics.fillRect(0, 0, width, height);
        Dimension world = isometricRenderer.worldSize(layout);
        if (width <= 2 * MARGIN || height <= 2 * MARGIN) {
            return;
        }
        Graphics2D iso = (Graphics2D) graphics.create();
        smooth(iso);
        Camera.fit(world.width, world.height, width, height, MARGIN).applyTo(iso);
        isometricRenderer.paint(iso, layout, markers, staffMarkers, porters, selected, overflow);
        iso.dispose();
        double flat = Math.max(0.0, Math.min(1.0, 1 - raised / 0.35));
        if (flat > 0) {
            Graphics2D plan = (Graphics2D) graphics.create();
            plan.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) flat));
            plan.setColor(Palette.BACKGROUND);
            plan.fillRect(0, 0, width, height);
            Dimension planWorld = renderer.worldSize(layout);
            smooth(plan);
            Camera.fit(planWorld.width, planWorld.height, width, height, MARGIN).applyTo(plan);
            paintFlat(plan);
            plan.dispose();
        }
        lastPaintNanos = System.nanoTime();
        shown = null;
    }

    /** Suavizado de 0 a 1: empieza y termina despacio. */
    private static double ease(double share) {
        double t = Math.max(0.0, Math.min(1.0, share));
        return t * t * (3 - 2 * t);
    }

    /** Ángulo entre dos giros por el camino más corto. */
    private static double turn(double from, double to, double share) {
        double difference = ((to - from) % 360 + 540) % 360 - 180;
        return from + difference * share;
    }

    // ---------- dibujo ----------

    private void paintScene(Graphics2D graphics, int width, int height, Optional<Camera> camera) {
        graphics.setColor(isometric ? isometricRenderer.background() : Palette.BACKGROUND);
        graphics.fillRect(0, 0, width, height);
        if (camera.isEmpty()) {
            return;
        }
        Graphics2D scene = (Graphics2D) graphics.create();
        smooth(scene);
        camera.get().applyTo(scene);
        if (isometric) {
            isometricRenderer.setClock(System.nanoTime() / 1e9);
            isometricRenderer.paint(scene, layout, markers, staffMarkers, porters, selected, overflow);
        } else {
            paintFlat(scene);
        }
        scene.dispose();
    }

    /** Plano 2D con el personal, los camilleros, los pacientes, la selección y el sobrante de la sala de espera. */
    private void paintFlat(Graphics2D graphics) {
        renderer.render(graphics, layout);
        staffPainter.paint(graphics, staffMarkers);
        staffPainter.paintPorters(graphics, porters);
        patientPainter.paint(graphics, markers);
        selectedMarker().ifPresent(marker -> patientPainter.paintSelection(graphics, marker));
        List<Position> seats = layout.getWaitingSpots();
        patientPainter.paintOverflow(graphics, renderer.centerOf(seats.get(seats.size() - 1)), overflow);
    }

    /** Lo que va encima del plano, en píxeles de la pantalla: avisos, brújula, cartel y panel flotante. */
    private void paintOverlay(Graphics2D graphics, int width, int height, Optional<Camera> camera) {
        if (camera.isEmpty()) {
            return;
        }
        double pulse = System.nanoTime() / 1e9;
        for (PlanAlert alert : alerts) {
            MapOverlay.paintAlert(graphics, alert, alertPoint(camera.get(), alert), pulse);
        }
        if (isometric) {
            IsoProjection projection = isometricRenderer.projectionOf(layout);
            MapOverlay.paintCompass(graphics, compassCenter(width), projection.step(0, -1));
        }
        if (banner != null) {
            MapOverlay.paintBanner(graphics, width, banner);
        }
        if (hoverPoint != null && hoverInfo != null) {
            MapOverlay.paintPlaceCard(graphics, hoverPoint.x, hoverPoint.y, hoverInfo, width, height);
        }
    }

    /** Centro del ícono de un aviso en la pantalla: sobre su casilla (en 2.5D, a {@value #ALERT_HEIGHT} m). */
    private Point2D alertPoint(Camera camera, PlanAlert alert) {
        Point2D world = worldPointOf(alert.position(), ALERT_HEIGHT);
        Point2D screen = camera.toScreen(world.getX(), world.getY());
        return isometric ? screen : new Point2D.Double(screen.getX(), screen.getY() - 14);
    }

    /** Punto del mundo de la vista que se está usando sobre el centro de una casilla, a cierta altura en 2.5D. */
    private Point2D worldPointOf(Position position, double height) {
        if (isometric) {
            return isometricRenderer.pointOver(layout, position, height);
        }
        return renderer.centerOf(position);
    }

    private static Point2D compassCenter(int width) {
        return new Point2D.Double(width - MapOverlay.COMPASS_INSET, MapOverlay.COMPASS_INSET);
    }

    /** Indica si un punto del panel cae sobre la brújula (solo en 2.5D). */
    private boolean onCompass(int screenX, int screenY) {
        return isometric && compassCenter(getWidth()).distance(screenX, screenY) <= MapOverlay.COMPASS_RADIUS;
    }

    private static void smooth(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
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
