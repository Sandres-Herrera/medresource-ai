package com.medresource.ui.render;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.StaffSeat;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Vista 2.5D del hospital: el mismo plano visto en diagonal desde arriba, con césped y árboles alrededor, pisos de
 * baldosa, muros cortados a media altura con ventanales y letreros, muebles y personas con volumen, en un tema claro
 * o uno oscuro ({@link #setTheme}).
 *
 * <p>No cambia las coordenadas del mundo: las personas siguen caminando sobre el plano 2D que da el renderizador 2D
 * que recibe ({@link #centerOf} y {@link #seatOf} son los suyos), y esta vista solo las proyecta con una
 * {@link IsoProjection}. Así cambiar entre 2D y 2.5D es instantáneo y no cambia nada de la simulación. Los píxeles
 * del mundo de esta vista ({@link #worldSize}) son los de la proyección, y en ellos trabaja la cámara.</p>
 *
 * <p>Dibuja de atrás hacia adelante ({@link IsoItem#BACK_TO_FRONT}): primero el piso, después los muros, los
 * muebles, los letreros y las personas en orden de profundidad. El edificio y los muebles de cada plano se arman una
 * sola vez por tema y por ángulo de la cámara, y se reutilizan en cada cuadro.</p>
 *
 * <p>La cámara puede girar el hospital y levantar la mirada ({@link #setView}): el giro va de 0 a 360 grados y la
 * elevación, de la diagonal de siempre hasta verlo desde arriba. Cambiar el ángulo vuelve a armar el edificio en el
 * cuadro siguiente; las personas no cambian de lugar.</p>
 *
 * <p>En cada cuadro las puertas se abren para quien se acerca a ellas, sea paciente, médico, enfermero o camillero, y
 * los monitores de la UCI y del quirófano muestran los latidos del paciente acostado en su cama o en su mesa. El
 * trazo corre con el reloj de la animación ({@link #setClock}), que no es el de la simulación.</p>
 */
public final class IsometricRenderer implements SceneRenderer {

    /** Distancia máxima, en píxeles de esta vista, entre un clic y el cuerpo de un paciente para seleccionarlo. */
    public static final double PICK_RADIUS = 14.0;

    private final SceneRenderer flat;
    private IsoTheme theme = IsoTheme.LIGHT;
    private double yaw;
    private double elevation = IsoProjection.DEFAULT_ELEVATION;
    private HospitalLayout cachedLayout;
    private IsoProjection projection;
    private IsoStructure structure;
    private IsoFurniture furniture;
    private double clock;
    private IsoFigures figures;
    private List<IsoItem> fixedItems = List.of();

    /**
     * Crea la vista 2.5D sobre un renderizador 2D.
     *
     * @param flat renderizador 2D que ubica a las personas en el plano
     * @throws IllegalArgumentException si es nulo
     */
    public IsometricRenderer(SceneRenderer flat) {
        if (flat == null) {
            throw new IllegalArgumentException("El renderizador 2D es obligatorio.");
        }
        this.flat = flat;
    }

    /**
     * Cambia el tema de colores. El edificio y los muebles se vuelven a armar en el próximo cuadro; las personas no
     * cambian.
     *
     * @param theme tema claro u oscuro
     * @throws IllegalArgumentException si es nulo
     */
    public void setTheme(IsoTheme theme) {
        if (theme == null) {
            throw new IllegalArgumentException("El tema es obligatorio.");
        }
        if (this.theme != theme) {
            this.theme = theme;
            cachedLayout = null;
        }
    }

    /**
     * Cambia el ángulo de la cámara. El edificio se vuelve a armar en el próximo cuadro si el ángulo cambió.
     *
     * @param yaw       giro del hospital alrededor de su centro, en grados (se lleva a una vuelta)
     * @param elevation ángulo de la mirada sobre el piso, en grados, de {@value IsoProjection#DEFAULT_ELEVATION} a
     *                  {@value IsoProjection#MAX_ELEVATION}
     * @throws IllegalArgumentException si el giro no es un número o la elevación está fuera de su rango
     */
    public void setView(double yaw, double elevation) {
        IsoProjection check = new IsoProjection(1, 1, 1, 1, yaw, elevation);
        if (check.yaw() != this.yaw || elevation != this.elevation) {
            this.yaw = check.yaw();
            this.elevation = elevation;
            cachedLayout = null;
        }
    }

    /** Giro de la cámara, en grados: 0 en la vista de siempre. */
    public double getYaw() {
        return yaw;
    }

    /** Elevación de la cámara, en grados: {@value IsoProjection#DEFAULT_ELEVATION} en la vista de siempre. */
    public double getElevation() {
        return elevation;
    }

    /** Tema de colores con que se dibuja: el claro, si nadie lo cambia. */
    public IsoTheme getTheme() {
        return theme;
    }

    /** Color del fondo del panel alrededor del terreno, según el tema. */
    public Color background() {
        return theme.color(IsoTheme.Part.BACKGROUND);
    }

    @Override
    public Dimension worldSize(HospitalLayout layout) {
        return prepare(layout).size();
    }

    /** El mismo punto que da el renderizador 2D: las personas caminan sobre el plano. */
    @Override
    public Point2D centerOf(Position position) {
        return flat.centerOf(position);
    }

    /** El mismo punto que da el renderizador 2D. */
    @Override
    public Point2D seatOf(StaffSeat seat) {
        return flat.seatOf(seat);
    }

    /** Dibuja el hospital sin personas. */
    @Override
    public void render(Graphics2D graphics, HospitalLayout layout) {
        paint(graphics, layout, List.of(), List.of(), List.of(), null, 0);
    }

    /**
     * Dibuja el hospital con las personas, de atrás hacia adelante.
     *
     * @param graphics superficie de dibujo, ya preparada con la cámara
     * @param layout   plano del hospital
     * @param patients pacientes, con su punto en el plano 2D
     * @param staff    médicos y enfermeros, con su punto en el plano 2D
     * @param porters  camilleros, con su punto en el plano 2D
     * @param selected código del paciente seleccionado, o nulo
     * @param overflow pacientes que esperan y no caben en las sillas; si es mayor que 0 se dibuja «+N» junto a la
     *                 última silla
     * @throws IllegalArgumentException si falta la superficie, el plano o alguna lista, o el sobrante es negativo
     */
    public void paint(Graphics2D graphics, HospitalLayout layout, List<PatientMarker> patients,
                      List<StaffMarker> staff, List<PorterMarker> porters, String selected, int overflow) {
        if (graphics == null) {
            throw new IllegalArgumentException("La superficie de dibujo es obligatoria.");
        }
        if (overflow < 0) {
            throw new IllegalArgumentException("El sobrante de la sala de espera no puede ser negativo.");
        }
        prepare(layout);
        openDoors(patients, staff, porters);
        showVitals(patients);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        structure.paintGround(graphics);
        List<IsoItem> items = new ArrayList<>(fixedItems);
        items.addAll(figures.items(patients, staff, porters, selected));
        items.sort(IsoItem.BACK_TO_FRONT);
        for (IsoItem item : items) {
            item.paint(graphics);
        }
        paintOverflow(graphics, layout, overflow);
    }

    /**
     * Cambia el momento de la animación: mueve el trazo de los monitores y el parpadeo de las alarmas.
     *
     * @param seconds segundos desde cualquier origen; el panel le pasa el reloj real
     * @throws IllegalArgumentException si no es un número
     */
    public void setClock(double seconds) {
        if (!Double.isFinite(seconds)) {
            throw new IllegalArgumentException("El momento de la animación debe ser un número.");
        }
        this.clock = seconds;
    }

    /**
     * Indica si la luz de alarma del monitor de una cama o mesa se ve encendida en el último cuadro dibujado.
     *
     * @param layout plano del hospital
     * @param spot   casilla de la cama o de la mesa
     * @return {@code true} si se ve encendida
     */
    public boolean alarmOn(HospitalLayout layout, Position spot) {
        prepare(layout);
        return furniture.alarmOn(spot);
    }

    /** Pasa a los monitores la salud de cada paciente acostado en una cama o en una mesa de operaciones. */
    private void showVitals(List<PatientMarker> patients) {
        Map<Position, Double> health = new HashMap<>();
        for (PatientMarker marker : patients) {
            IsoFigures.Pose pose = figures.poseOf(marker);
            if (pose == IsoFigures.Pose.IN_BED || pose == IsoFigures.Pose.ON_TABLE) {
                health.put(new Position((int) Math.floor(projection.gridX(marker.x())),
                        (int) Math.floor(projection.gridY(marker.y()))), marker.deceased() ? 0.0 : marker.health());
            }
        }
        furniture.showVitals(clock, health);
    }

    /** Abre las puertas a las que se acerca alguien: pacientes, personal o camilleros. */
    private void openDoors(List<PatientMarker> patients, List<StaffMarker> staff, List<PorterMarker> porters) {
        if (patients == null || staff == null || porters == null) {
            throw new IllegalArgumentException("Las listas de personas son obligatorias.");
        }
        List<Point2D> people = new ArrayList<>();
        patients.forEach(marker -> people.add(gridOf(marker.x(), marker.y())));
        staff.forEach(marker -> people.add(gridOf(marker.x(), marker.y())));
        porters.forEach(marker -> people.add(gridOf(marker.x(), marker.y())));
        structure.openDoorsNear(people);
    }

    private Point2D gridOf(double planX, double planY) {
        return new Point2D.Double(projection.gridX(planX), projection.gridY(planY));
    }

    /**
     * Punto de esta vista que se mira de un paciente: donde la cámara lo sigue.
     *
     * @param layout plano del hospital
     * @param marker paciente
     * @return punto de esta vista
     * @throws IllegalArgumentException si falta alguno
     */
    public Point2D focusOf(HospitalLayout layout, PatientMarker marker) {
        prepare(layout);
        return figures.focusOf(marker);
    }

    /**
     * Busca el paciente que se ve en un punto de esta vista.
     *
     * @param layout  plano del hospital
     * @param markers pacientes, en el orden en que se dibujan
     * @param viewX   coordenada x en esta vista
     * @param viewY   coordenada y en esta vista
     * @return código del paciente más cercano a menos de {@value #PICK_RADIUS} píxeles, o vacío si no hay ninguno
     * @throws IllegalArgumentException si falta el plano o la lista
     */
    public Optional<String> pick(HospitalLayout layout, List<PatientMarker> markers, double viewX, double viewY) {
        if (markers == null) {
            throw new IllegalArgumentException("La lista de pacientes es obligatoria.");
        }
        prepare(layout);
        String best = null;
        double bestDistance = PICK_RADIUS;
        for (PatientMarker marker : markers) {
            double distance = figures.focusOf(marker).distance(viewX, viewY);
            if (distance <= bestDistance) {
                best = marker.code();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Proyección que usa esta vista para un plano.
     *
     * @param layout plano del hospital
     * @return la proyección
     * @throws IllegalArgumentException si el plano es nulo
     */
    public IsoProjection projectionOf(HospitalLayout layout) {
        return prepare(layout);
    }

    /**
     * Arma el edificio y los muebles del plano la primera vez que se pide (o después de cambiar de tema), y los
     * reutiliza en los demás cuadros.
     */
    private IsoProjection prepare(HospitalLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("El plano es obligatorio.");
        }
        if (layout != cachedLayout) {
            projection = IsoProjection.of(layout, flat).withView(yaw, elevation);
            structure = new IsoStructure(layout, projection, theme);
            furniture = new IsoFurniture(layout, projection, flat, theme);
            figures = new IsoFigures(projection, furniture);
            List<IsoItem> items = new ArrayList<>(structure.items());
            items.addAll(furniture.items());
            fixedItems = List.copyOf(items);
            cachedLayout = layout;
        }
        return projection;
    }

    /** Insignia «+N» junto a la última silla de la sala de espera. */
    private void paintOverflow(Graphics2D graphics, HospitalLayout layout, int overflow) {
        List<Position> seats = layout.getWaitingSpots();
        if (overflow <= 0 || seats.isEmpty()) {
            return;
        }
        Position last = seats.get(seats.size() - 1);
        Point2D at = projection.at(last.col() + 0.9, last.row() + 0.5, 0.7);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        FontMetrics metrics = graphics.getFontMetrics();
        String text = "+" + overflow;
        double width = metrics.stringWidth(text) + 10.0;
        double height = 17.0;
        graphics.setColor(Palette.BADGE);
        graphics.fill(new RoundRectangle2D.Double(at.getX(), at.getY() - height, width, height, height, height));
        graphics.setColor(Palette.CARD);
        graphics.drawString(text, (float) (at.getX() + 5), (float) (at.getY() - height / 2 + metrics.getAscent() / 2.0
                - 1));
    }
}
