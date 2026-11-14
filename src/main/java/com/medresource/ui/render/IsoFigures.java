package com.medresource.ui.render;

import com.medresource.model.Patient;
import com.medresource.model.Position;
import com.medresource.model.Sex;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Las personas de la vista 2.5D: pacientes, médicos, enfermeros y camilleros, cada uno donde lo pone la escena.
 *
 * <p>Las personas se mueven sobre el plano 2D; aquí solo se decide cómo se ven. Un paciente que está quieto en el
 * centro de una cama, de la mesa de operaciones o de una mesa de la morgue va acostado encima, con una cobija del
 * color de su triage (un fallecido, cubierto con una sábana gris); en el centro de una silla de la sala de espera va
 * sentado; quien no puede caminar va acostado en una camilla, que empuja un camillero mientras se mueve; los demás van
 * de pie. El personal que está en su puesto de la sala del personal va sentado.</p>
 *
 * <p>Cada persona tiene piernas, torso, brazos y cabeza; los pacientes tienen pelo de uno de seis tonos, siempre el
 * mismo para el mismo código. El color de la ropa dice quién es cada uno: el del triage para un paciente (gris si
 * falleció), bata blanca con
 * un distintivo del color de su especialidad para un médico, verde agua para un enfermero y gris azulado para un
 * camillero; el personal lleva además un gorro más oscuro. Sobre la cabeza de cada paciente va su barra de salud y,
 * en tratamiento, la de su avance.</p>
 */
public final class IsoFigures {

    /** Tamaño de un menor de edad respecto a un adulto. */
    public static final double MINOR_SCALE = 0.8;
    /** Radio del anillo del paciente seleccionado, en metros. */
    public static final double SELECTION_RADIUS = 0.3;
    /** Cuánto se separa el camillero de la camilla que empuja, en casillas. */
    public static final double PUSHER_OFFSET = 0.5;
    /** Altura, en metros, del punto que se mira de una persona de pie (para el clic y la cámara). */
    public static final double STANDING_FOCUS = 0.35;

    private static final double CENTER_TOLERANCE = 1e-6;
    private static final Color[] HAIR = {new Color(0x2B, 0x1D, 0x14), new Color(0x4A, 0x32, 0x22),
        new Color(0x1C, 0x1C, 0x1C), new Color(0x6B, 0x44, 0x23), new Color(0xA0, 0x52, 0x2D),
        new Color(0xB8, 0xB8, 0xB8)};

    /** Cómo va un paciente en este instante. */
    public enum Pose {
        /** De pie. */
        STANDING,
        /** Sentado en una silla de la sala de espera. */
        SEATED,
        /** Acostado en una cama. */
        IN_BED,
        /** Acostado en la mesa de operaciones. */
        ON_TABLE,
        /** Acostado en una mesa de la morgue. */
        ON_SLAB,
        /** Acostado en una camilla. */
        ON_STRETCHER
    }

    private final IsoProjection projection;
    private final IsoFurniture furniture;

    /**
     * Prepara las personas de un plano.
     *
     * @param projection proyección de la vista
     * @param furniture  muebles del plano, para saber dónde se acuesta o se sienta cada paciente
     * @throws IllegalArgumentException si falta alguno
     */
    public IsoFigures(IsoProjection projection, IsoFurniture furniture) {
        if (projection == null || furniture == null) {
            throw new IllegalArgumentException("La proyección y los muebles son obligatorios.");
        }
        this.projection = projection;
        this.furniture = furniture;
    }

    /**
     * Cómo va un paciente: acostado si está quieto en el centro de una cama o mesa, sentado si está quieto en el
     * centro de una silla, en camilla si no puede caminar y, si no, de pie.
     *
     * @param marker paciente
     * @return su postura
     * @throws IllegalArgumentException si falta el paciente
     */
    public Pose poseOf(PatientMarker marker) {
        if (marker == null) {
            throw new IllegalArgumentException("El paciente es obligatorio.");
        }
        Optional<IsoFurniture.Kind> kind = centeredCell(marker).flatMap(furniture::kindAt);
        if (kind.isPresent()) {
            switch (kind.get()) {
                case BED -> {
                    return Pose.IN_BED;
                }
                case OPERATING_TABLE -> {
                    return Pose.ON_TABLE;
                }
                case SLAB -> {
                    return Pose.ON_SLAB;
                }
                case CHAIR -> {
                    if (!marker.onStretcher()) {
                        return Pose.SEATED;
                    }
                }
            }
        }
        return marker.onStretcher() ? Pose.ON_STRETCHER : Pose.STANDING;
    }

    /** La casilla del paciente si está quieto justo en su centro. */
    private Optional<Position> centeredCell(PatientMarker marker) {
        if (marker.moving()) {
            return Optional.empty();
        }
        double gx = projection.gridX(marker.x());
        double gy = projection.gridY(marker.y());
        int col = (int) Math.floor(gx);
        int row = (int) Math.floor(gy);
        if (Math.abs(gx - col - 0.5) > CENTER_TOLERANCE || Math.abs(gy - row - 0.5) > CENTER_TOLERANCE) {
            return Optional.empty();
        }
        return Optional.of(new Position(col, row));
    }

    /**
     * Punto de la vista que se mira de un paciente: donde se le hace clic y donde se centra la cámara que lo sigue.
     *
     * @param marker paciente
     * @return punto de la vista, a la altura de su cuerpo o de su cabeza si va acostado
     * @throws IllegalArgumentException si falta el paciente
     */
    public Point2D focusOf(PatientMarker marker) {
        Pose pose = poseOf(marker);
        double gx = projection.gridX(marker.x());
        double gy = projection.gridY(marker.y());
        return switch (pose) {
            case IN_BED -> projection.at(Math.floor(gx) + 0.72, Math.floor(gy) + 0.45, IsoFurniture.MATTRESS_TOP);
            case ON_TABLE -> projection.at(Math.floor(gx) + 0.72, Math.floor(gy) + 0.45, IsoFurniture.OPERATING_TOP);
            case ON_SLAB -> projection.at(Math.floor(gx) + 0.5, Math.floor(gy) + 0.5, IsoFurniture.SLAB_TOP);
            case SEATED -> projection.at(gx, gy, IsoFurniture.SEAT_TOP + 0.2);
            case ON_STRETCHER -> projection.at(gx, gy, 0.4);
            case STANDING -> projection.at(gx, gy, STANDING_FOCUS);
        };
    }

    /**
     * Arma un elemento por persona, para dibujarlas en su turno junto con los muros y los muebles.
     *
     * @param patients pacientes
     * @param staff    médicos y enfermeros
     * @param porters  camilleros que buscan o acompañan a un paciente
     * @param selected código del paciente seleccionado, o nulo
     * @return las personas
     * @throws IllegalArgumentException si alguna lista es nula
     */
    public List<IsoItem> items(List<PatientMarker> patients, List<StaffMarker> staff, List<PorterMarker> porters,
                               String selected) {
        if (patients == null || staff == null || porters == null) {
            throw new IllegalArgumentException("Las listas de personas son obligatorias.");
        }
        List<IsoItem> items = new ArrayList<>();
        for (PatientMarker marker : patients) {
            boolean isSelected = marker.code().equals(selected);
            items.add(patientItem(marker, isSelected));
        }
        for (StaffMarker marker : staff) {
            double gx = projection.gridX(marker.x());
            double gy = projection.gridY(marker.y());
            items.add(new IsoItem(projection.depth(gx, gy), IsoItem.PERSON,
                    graphics -> paintStaff(graphics, marker, gx, gy)));
        }
        for (PorterMarker marker : porters) {
            double gx = projection.gridX(marker.x());
            double gy = projection.gridY(marker.y());
            items.add(new IsoItem(projection.depth(gx, gy), IsoItem.PERSON,
                    graphics -> standing(graphics, gx, gy, 0, porter())));
        }
        return items;
    }

    private IsoItem patientItem(PatientMarker marker, boolean selected) {
        Pose pose = poseOf(marker);
        double gx = projection.gridX(marker.x());
        double gy = projection.gridY(marker.y());
        double depth = switch (pose) {
            case IN_BED, ON_TABLE, ON_SLAB, SEATED -> projection.depth(Math.floor(gx) + 0.5, Math.floor(gy) + 0.5);
            default -> projection.depth(gx, gy);
        };
        return new IsoItem(depth, IsoItem.PERSON, graphics -> paintPatient(graphics, marker, pose, gx, gy, selected));
    }

    private void paintPatient(Graphics2D graphics, PatientMarker marker, Pose pose, double gx, double gy,
                              boolean selected) {
        if (selected) {
            IsoBrush.ring(graphics, projection, gx, gy, SELECTION_RADIUS, Palette.SELECTION);
        }
        Color body = bodyOf(marker);
        double scale = marker.minor() ? MINOR_SCALE : 1.0;
        int col = (int) Math.floor(gx);
        int row = (int) Math.floor(gy);
        Point2D top = switch (pose) {
            case IN_BED -> lying(graphics, col + IsoFurniture.BED_LEFT + 0.05, row + 0.42,
                    IsoFurniture.BED_RIGHT - IsoFurniture.BED_LEFT - 0.1, 0.42, IsoFurniture.MATTRESS_TOP,
                    col + 0.72, row + 0.25, marker, body);
            case ON_TABLE -> lying(graphics, col + 0.56, row + 0.4, 0.32, 0.44, IsoFurniture.OPERATING_TOP,
                    col + 0.72, row + 0.24, marker, body);
            case ON_SLAB -> lying(graphics, col + 0.26, row + 0.18, 0.48, 0.64, IsoFurniture.SLAB_TOP,
                    col + 0.5, row + 0.3, marker, body);
            case ON_STRETCHER -> stretcher(graphics, gx, gy, marker, body);
            case SEATED -> IsoBrush.person(graphics, projection, gx, gy - 0.02, IsoFurniture.SEAT_TOP, marker.bob(),
                    new IsoBrush.Person(body, null, marker.sex(), scale, true, hairOf(marker.code())));
            case STANDING -> standing(graphics, gx, gy, marker.bob(),
                    new IsoBrush.Person(body, null, marker.sex(), scale, false, hairOf(marker.code())));
        };
        if (!marker.deceased()) {
            IsoBrush.bar(graphics, top.getX(), top.getY() - 6, IsoBrush.BAR_WIDTH,
                    marker.health() / Patient.MAX_HEALTH, Palette.health(marker.health()));
            if (marker.inTreatment()) {
                IsoBrush.bar(graphics, top.getX(), top.getY() - 11, IsoBrush.BAR_WIDTH + 4, marker.progress(),
                        Palette.TREATMENT_PROGRESS);
            }
        }
    }

    /**
     * Paciente acostado: una cobija del color de su triage y la cabeza sobre la almohada; un fallecido va cubierto
     * entero con una sábana gris.
     *
     * @return punto de la pantalla encima de la cabeza
     */
    private Point2D lying(Graphics2D graphics, double x, double y, double w, double d, double z, double headX,
                          double headY, PatientMarker marker, Color body) {
        if (marker.deceased()) {
            IsoBrush.box(graphics, projection, x, headY - 0.14, z, w, y + d - headY + 0.14, 0.1, IsoPalette.SHEET);
            return projection.at(headX, headY, z + 0.15);
        }
        IsoBrush.box(graphics, projection, x, y, z, w, d, 0.09, body);
        IsoBrush.box(graphics, projection, x, y, z + 0.09, w, 0.07, 0.01, IsoPalette.lighter(body, 0.55));
        double radius = IsoBrush.screenRadius(IsoBrush.HEAD_RADIUS * (marker.minor() ? MINOR_SCALE : 1.0));
        Point2D head = projection.at(headX, headY, z + 0.08);
        IsoBrush.head(graphics, head.getX(), head.getY() - radius * 0.6, radius, null, hairOf(marker.code()));
        return new Point2D.Double(head.getX(), head.getY() - radius * 1.6);
    }

    /** Camilla con ruedas y el paciente acostado; mientras se mueve, el camillero la empuja desde la derecha. */
    private Point2D stretcher(Graphics2D graphics, double gx, double gy, PatientMarker marker, Color body) {
        double x = gx - 0.36;
        double y = gy - 0.17;
        double w = 0.68;
        double d = 0.34;
        IsoBrush.shadow(graphics, projection, gx, gy, 0.36);
        for (double wheel : new double[] {x + 0.08, x + w - 0.08}) {
            Point2D at = projection.at(wheel, y + d, 0.04);
            graphics.setColor(IsoPalette.OUTLINE);
            graphics.fill(new Ellipse2D.Double(at.getX() - 2.6, at.getY() - 1.8, 5.2, 3.6));
        }
        double lift = marker.bob();
        IsoTheme theme = furniture.getTheme();
        IsoBrush.box(graphics, projection, x + 0.08, y + 0.08, 0.05, w - 0.16, d - 0.16, 0.18,
                theme.color(IsoTheme.Part.METAL));
        IsoBrush.box(graphics, projection, x, y, 0.23, w, d, 0.05, theme.color(IsoTheme.Part.BED_FRAME));
        IsoBrush.box(graphics, projection, x + 0.02, y + 0.02, 0.28, w - 0.04, d - 0.04, 0.05,
                theme.color(IsoTheme.Part.MATTRESS));
        Point2D top;
        if (marker.deceased()) {
            IsoBrush.box(graphics, projection, x + 0.04, y + 0.04, 0.33, w - 0.08, d - 0.08, 0.1, IsoPalette.SHEET);
            top = projection.at(gx, gy, 0.5);
        } else {
            IsoBrush.box(graphics, projection, x + 0.25, y + 0.04, 0.33, w - 0.3, d - 0.08, 0.09, body);
            double radius = IsoBrush.screenRadius(IsoBrush.HEAD_RADIUS);
            Point2D head = projection.at(x + 0.13, gy, 0.4);
            IsoBrush.head(graphics, head.getX(), head.getY() - radius * 0.6 + lift, radius, null,
                    hairOf(marker.code()));
            top = new Point2D.Double(head.getX(), head.getY() - radius * 1.6);
        }
        if (marker.isCarried()) {
            standing(graphics, gx + PUSHER_OFFSET, gy, 0, porter());
        }
        return top;
    }

    private Point2D standing(Graphics2D graphics, double gx, double gy, double lift, IsoBrush.Person person) {
        IsoBrush.shadow(graphics, projection, gx, gy, IsoBrush.BODY_RADIUS * 1.45 * person.scale());
        return IsoBrush.person(graphics, projection, gx, gy, 0, lift, person);
    }

    private void paintStaff(Graphics2D graphics, StaffMarker marker, double gx, double gy) {
        Color body = marker.isDoctor() ? Palette.DOCTOR_COAT : Palette.NURSE_UNIFORM;
        Color cap = marker.isDoctor() ? IsoPalette.DOCTOR_CAP : IsoPalette.cap(Palette.NURSE_UNIFORM);
        IsoBrush.Person person = new IsoBrush.Person(body, cap, marker.sex(), 1.0, marker.seated());
        if (marker.seated()) {
            IsoBrush.person(graphics, projection, gx, gy, IsoFurniture.SEAT_TOP, marker.bob(), person);
        } else {
            standing(graphics, gx, gy, marker.bob(), person);
        }
        if (marker.isDoctor()) {
            Point2D chest = projection.at(gx, gy, (marker.seated() ? IsoFurniture.SEAT_TOP : 0)
                    + IsoBrush.chestHeight(marker.seated()));
            graphics.setColor(Palette.specialty(marker.specialty()));
            graphics.fill(new Rectangle2D.Double(chest.getX() - 3.5, chest.getY() + marker.bob() - 1.2, 7, 2.4));
        }
    }

    private static IsoBrush.Person porter() {
        return new IsoBrush.Person(Palette.PORTER_UNIFORM, IsoPalette.cap(Palette.PORTER_UNIFORM), Sex.MALE, 1.0,
                false);
    }

    /**
     * Color del pelo de un paciente: uno de seis tonos, siempre el mismo para el mismo código.
     *
     * @param code código del paciente
     * @return color del pelo
     */
    public static Color hairOf(String code) {
        return HAIR[Math.floorMod(code == null ? 0 : code.hashCode(), HAIR.length)];
    }

    /** Color del cuerpo de un paciente: el de su triage, gris si falleció o si todavía no tiene triage. */
    private static Color bodyOf(PatientMarker marker) {
        if (marker.deceased()) {
            return Palette.DECEASED;
        }
        return marker.triage() == null ? Palette.UNCLASSIFIED : Palette.triage(marker.triage());
    }
}
