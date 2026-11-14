package com.medresource.ui.controller;

import com.medresource.model.BloodType;
import com.medresource.model.EventType;
import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Position;
import com.medresource.model.SimulationEvent;
import com.medresource.model.ZoneType;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.ui.render.PlanAlert;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de los avisos que se dibujan sobre el plano del hospital estándar.
 */
class AlertTrackerTest {

    private final HospitalLayout layout =
            new LayoutFactory().create(new HospitalFactory().create(HospitalConfig.standard()));
    private final AlertTracker tracker = new AlertTracker(layout);
    private final Position bed = layout.positionOf("H1-C2").orElseThrow();

    private static Map<BloodType, Integer> fullBank() {
        Map<BloodType, Integer> units = new EnumMap<>(BloodType.class);
        for (BloodType type : BloodType.values()) {
            units.put(type, 10);
        }
        return units;
    }

    @Test
    void aDeathAppearsOverThePlaceWhereItHappenedAndFadesAway() {
        tracker.onEvents(List.of(new SimulationEvent(3, EventType.DEATH, "P007", null, "Falleció")), 180,
                code -> Optional.of(bed));

        List<PlanAlert> alerts = tracker.active(200);

        assertEquals(1, alerts.size());
        assertEquals(PlanAlert.Kind.DEATH, alerts.get(0).kind());
        assertEquals(bed, alerts.get(0).position());
        assertEquals("Falleció P007", alerts.get(0).text());
        assertTrue(tracker.active(180 + AlertTracker.EVENT_SECONDS).isEmpty());
    }

    @Test
    void manyArrivalsTogetherAreAMassArrivalOverTheEntrance() {
        List<SimulationEvent> arrivals = new ArrayList<>();
        for (int index = 1; index <= AlertTracker.MASS_ARRIVALS; index++) {
            arrivals.add(new SimulationEvent(1, EventType.ARRIVAL, "P00" + index, null, "Llegó"));
        }

        tracker.onEvents(arrivals.subList(0, AlertTracker.MASS_ARRIVALS - 1), 60, code -> Optional.empty());
        assertTrue(tracker.active(61).isEmpty(), "pocos pacientes no son una llegada masiva");
        tracker.onEvents(arrivals.subList(AlertTracker.MASS_ARRIVALS - 1, arrivals.size()), 70,
                code -> Optional.empty());
        PlanAlert alert = tracker.active(71).get(0);

        assertEquals(PlanAlert.Kind.MASS_ARRIVAL, alert.kind());
        assertEquals(layout.getEntrance(), alert.position());
        assertTrue(alert.text().contains(String.valueOf(AlertTracker.MASS_ARRIVALS)));
        assertTrue(tracker.active(70 + AlertTracker.MASS_WINDOW).isEmpty());
    }

    @Test
    void aFullIcuAndMissingBloodLastWhileTheyHappen() {
        Map<BloodType, Integer> units = fullBank();
        units.put(BloodType.O_NEGATIVE, 0);

        tracker.onResources(0, 6, units);
        List<PlanAlert> alerts = tracker.active(10);

        assertEquals(2, alerts.size());
        assertEquals(PlanAlert.Kind.ICU_FULL, alerts.get(0).kind());
        assertEquals(ZoneType.ICU, layout.areaAt(alerts.get(0).position()).orElseThrow().zone());
        assertEquals(PlanAlert.Kind.BLOOD_SHORTAGE, alerts.get(1).kind());
        assertEquals(ZoneType.BLOOD_BANK, layout.areaAt(alerts.get(1).position()).orElseThrow().zone());
        assertTrue(alerts.get(1).text().contains(BloodType.O_NEGATIVE.getLabel()));

        tracker.onResources(1, 6, fullBank());
        assertTrue(tracker.active(20).isEmpty());
    }

    @Test
    void clearingRemovesEveryAlert() {
        tracker.onResources(0, 6, fullBank());
        tracker.onEvents(List.of(new SimulationEvent(3, EventType.DEATH, "P007", null, "Falleció")), 180,
                code -> Optional.empty());

        tracker.clear();

        assertTrue(tracker.active(181).isEmpty());
    }

    @Test
    void rejectsMissingData() {
        assertThrows(IllegalArgumentException.class, () -> new AlertTracker(null));
        assertThrows(IllegalArgumentException.class, () -> tracker.onEvents(null, 0, code -> Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> tracker.onEvents(List.of(), 0, null));
        assertThrows(IllegalArgumentException.class, () -> tracker.onResources(0, 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PlanAlert(null, bed, "Falleció P001"));
        assertThrows(IllegalArgumentException.class,
                () -> new PlanAlert(PlanAlert.Kind.DEATH, bed, " "));
    }
}
