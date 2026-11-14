package com.medresource.ui.controller;

import com.medresource.model.Area;
import com.medresource.model.BloodType;
import com.medresource.model.EventType;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.SimulationEvent;
import com.medresource.model.ZoneType;
import com.medresource.ui.render.PlanAlert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Lleva los avisos que se dibujan sobre el plano.
 *
 * <p>Hay dos clases de avisos. El de un fallecido aparece cuando se ve su muerte y dura {@value #EVENT_SECONDS}
 * segundos simulados. Los de un estado se ven mientras ese estado dure: la UCI sin camas libres, un tipo de sangre
 * agotado y la llegada masiva, cuando entran {@value #MASS_ARRIVALS} pacientes o más en {@value #MASS_WINDOW}
 * segundos simulados. No cambia nada de la simulación: solo mira lo que pasa.</p>
 */
public final class AlertTracker {

    /** Segundos simulados que dura en el plano el aviso de un suceso. */
    public static final double EVENT_SECONDS = 120.0;
    /** Pacientes que tienen que llegar juntos para que cuente como llegada masiva. */
    public static final int MASS_ARRIVALS = 6;
    /** Segundos simulados en los que se cuentan las llegadas para saber si es una llegada masiva. */
    public static final double MASS_WINDOW = 120.0;

    private final HospitalLayout layout;
    private final List<Timed> events = new ArrayList<>();
    private final List<Double> arrivals = new ArrayList<>();
    private PlanAlert icuFull;
    private PlanAlert bloodShortage;

    /**
     * Crea el registro de avisos de un plano.
     *
     * @param layout plano del hospital
     * @throws IllegalArgumentException si es nulo
     */
    public AlertTracker(HospitalLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("El plano es obligatorio.");
        }
        this.layout = layout;
    }

    /**
     * Anota los avisos de los eventos que se acaban de ver: un fallecido sobre el lugar donde murió, y cada llegada
     * para saber si hay una llegada masiva.
     *
     * @param newEvents  eventos que se acaban de ver
     * @param time       momento que se ve, en segundos simulados
     * @param positionOf dónde está cada paciente en la escena
     * @throws IllegalArgumentException si falta la lista o la búsqueda de posiciones
     */
    public void onEvents(List<SimulationEvent> newEvents, double time,
                         Function<String, Optional<Position>> positionOf) {
        if (newEvents == null || positionOf == null) {
            throw new IllegalArgumentException("Los eventos y las posiciones son obligatorios.");
        }
        for (SimulationEvent event : newEvents) {
            if (event.type() == EventType.DEATH) {
                Position where = positionOf.apply(event.patientCode())
                        .or(() -> Optional.ofNullable(event.resourceCode()).flatMap(layout::positionOf))
                        .orElse(layout.getEntrance());
                events.add(new Timed(new PlanAlert(PlanAlert.Kind.DEATH, where, "Falleció " + event.patientCode()),
                        time + EVENT_SECONDS));
            } else if (event.type() == EventType.ARRIVAL) {
                arrivals.add(time);
            }
        }
    }

    /**
     * Revisa los recursos: si la UCI se quedó sin camas libres o se agotó la sangre de algún tipo, deja su aviso
     * mientras dure; si ya no pasa, lo quita.
     *
     * @param freeIcuBeds  camas de UCI libres
     * @param totalIcuBeds camas de UCI
     * @param blood        unidades de sangre de cada tipo
     * @throws IllegalArgumentException si falta la sangre
     */
    public void onResources(int freeIcuBeds, int totalIcuBeds, Map<BloodType, Integer> blood) {
        if (blood == null) {
            throw new IllegalArgumentException("Las unidades de sangre son obligatorias.");
        }
        icuFull = totalIcuBeds > 0 && freeIcuBeds == 0
                ? centerOf(ZoneType.ICU).map(at -> new PlanAlert(PlanAlert.Kind.ICU_FULL, at, "UCI llena"))
                        .orElse(null)
                : null;
        List<String> missing = new ArrayList<>();
        for (BloodType type : BloodType.values()) {
            if (blood.getOrDefault(type, 0) == 0) {
                missing.add(type.getLabel());
            }
        }
        bloodShortage = missing.isEmpty() ? null : centerOf(ZoneType.BLOOD_BANK)
                .map(at -> new PlanAlert(PlanAlert.Kind.BLOOD_SHORTAGE, at, "Sin sangre " + String.join(", ", missing)))
                .orElse(null);
    }

    /**
     * Avisos que se ven en un momento: los de los estados que siguen y los de los fallecidos que no han vencido.
     *
     * @param time momento que se ve, en segundos simulados
     * @return los avisos, primero los de estado y después los de los fallecidos, del más viejo al más nuevo
     */
    public List<PlanAlert> active(double time) {
        events.removeIf(timed -> timed.until() <= time);
        arrivals.removeIf(at -> at < time - MASS_WINDOW);
        List<PlanAlert> active = new ArrayList<>();
        if (arrivals.size() >= MASS_ARRIVALS) {
            active.add(new PlanAlert(PlanAlert.Kind.MASS_ARRIVAL, layout.getEntrance(),
                    "Llegada masiva: " + arrivals.size() + " pacientes"));
        }
        if (icuFull != null) {
            active.add(icuFull);
        }
        if (bloodShortage != null) {
            active.add(bloodShortage);
        }
        events.forEach(timed -> active.add(timed.alert()));
        return active;
    }

    /** Borra todos los avisos, como al volver a empezar la simulación. */
    public void clear() {
        events.clear();
        arrivals.clear();
        icuFull = null;
        bloodShortage = null;
    }

    /** Casilla del centro de la primera área de una zona. */
    private Optional<Position> centerOf(ZoneType zone) {
        List<Area> areas = layout.areasOf(zone);
        if (areas.isEmpty()) {
            return Optional.empty();
        }
        Area area = areas.get(0);
        return Optional.of(new Position(area.col() + area.cols() / 2, area.row() + area.rows() / 2));
    }

    /** Aviso de un suceso con el momento en que vence. */
    private record Timed(PlanAlert alert, double until) {
    }
}
