package com.medresource.ui.render;

import java.util.List;
import java.util.Optional;

/**
 * Encuentra qué paciente hay en un punto del mundo, para saber a quién se le hizo clic.
 *
 * <p>Un paciente se dibuja {@value PatientPainter#OFFSET_X} píxeles a la derecha de su punto
 * ({@link PatientPainter}); se le atina si el punto queda a menos de {@value #PICK_RADIUS} píxeles del centro de su
 * figura. Si varios quedan a esa distancia, gana el más cercano y, a igual distancia, el que se dibuja encima (el
 * último de la lista).</p>
 */
public final class PatientPicker {

    /** Distancia máxima, en píxeles del mundo, entre el punto y el centro de la figura. */
    public static final double PICK_RADIUS = 16.0;

    private PatientPicker() {
    }

    /**
     * Busca el paciente que hay en un punto del mundo.
     *
     * @param markers pacientes como se dibujan, en el orden en que se dibujan
     * @param worldX  coordenada x del punto en el mundo
     * @param worldY  coordenada y del punto en el mundo
     * @return código del paciente, o vacío si no hay ninguno tan cerca
     * @throws IllegalArgumentException si la lista es nula
     */
    public static Optional<String> pick(List<PatientMarker> markers, double worldX, double worldY) {
        if (markers == null) {
            throw new IllegalArgumentException("La lista de pacientes es obligatoria.");
        }
        String best = null;
        double bestDistance = PICK_RADIUS;
        for (PatientMarker marker : markers) {
            double distance = Math.hypot(worldX - (marker.x() + PatientPainter.OFFSET_X),
                    worldY - (marker.y() + marker.bob()));
            if (distance <= bestDistance) {
                best = marker.code();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
