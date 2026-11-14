package com.medresource.ui.render;

import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.StaffSeat;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Vista 2.5D del hospital: el mismo plano visto en diagonal desde arriba, con pisos, muros de vidrio, muebles y
 * personas con volumen.
 *
 * <p>No cambia las coordenadas del mundo: las personas siguen caminando sobre el plano 2D que da el renderizador 2D
 * que recibe ({@link #centerOf} y {@link #seatOf} son los suyos), y esta vista solo las proyecta con una
 * {@link IsoProjection}. Así cambiar entre 2D y 2.5D es instantáneo y no cambia nada de la simulación. Los píxeles
 * del mundo de esta vista ({@link #worldSize}) son los de la proyección, y en ellos trabaja la cámara.</p>
 *
 * <p>Dibuja de atrás hacia adelante ({@link IsoItem#BACK_TO_FRONT}): primero el piso, después los muros, los
 * muebles y las personas en orden de profundidad, y al final las etiquetas de las salas. El edificio y los muebles
 * de cada plano se arman una sola vez y se reutilizan en cada cuadro.</p>
 */
public final class IsometricRenderer implements SceneRenderer {

    /** Distancia máxima, en píxeles de esta vista, entre un clic y el cuerpo de un paciente para seleccionarlo. */
    public static final double PICK_RADIUS = 14.0;

    private final SceneRenderer flat;
    private HospitalLayout cachedLayout;
    private IsoProjection projection;
    private IsoStructure structure;
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

    /** Arma el edificio y los muebles del plano la primera vez que se pide, y los reutiliza después. */
    private IsoProjection prepare(HospitalLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("El plano es obligatorio.");
        }
        if (layout != cachedLayout) {
            projection = IsoProjection.of(layout, flat);
            structure = new IsoStructure(layout, projection);
            IsoFurniture furniture = new IsoFurniture(layout, projection, flat);
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
