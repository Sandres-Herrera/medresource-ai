package com.medresource.ui.controller;

import com.medresource.model.Hospital;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Patient;
import com.medresource.model.PatientStatus;
import com.medresource.model.RunMetrics;
import com.medresource.model.SimulationEvent;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.services.simulation.MetricsCalculator;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.SceneRenderer;
import com.medresource.ui.render.StaffMarker;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Controla una simulación que se ve en pantalla: reloj, pausa, paso a paso, salto al siguiente evento,
 * reinicio y velocidad.
 *
 * <p>No usa Swing: la ventana llama a {@link #advance(double)} en cada cuadro con los milisegundos reales
 * que pasaron. El {@link SimulationClock} los convierte en segundos simulados según la velocidad (×1 es
 * tiempo real) y el {@link ScenePlayer} lleva la escena hasta ese momento, de forma continua: cada evento se
 * ve en su propio segundo y los pacientes caminan mientras otros están en tratamiento. La velocidad solo
 * cambia lo que se ve: el resultado de la simulación es el mismo con cualquier velocidad.</p>
 */
public final class SimulationController {

    private final SimulationSetup setup;
    private final SceneRenderer renderer;
    private SimulationEngine engine;
    private ScenePlayer player;
    private HospitalLayout layout;
    private String strategyName;
    private final SimulationClock clock = new SimulationClock();
    private boolean running;

    /**
     * Prepara la simulación sin empezarla.
     *
     * @param setup    hospital, caso y estrategia
     * @param renderer renderizador que da el centro de cada casilla
     * @throws IllegalArgumentException si algún argumento es nulo
     */
    public SimulationController(SimulationSetup setup, SceneRenderer renderer) {
        if (setup == null || renderer == null) {
            throw new IllegalArgumentException("La configuración y el renderizador son obligatorios.");
        }
        this.setup = setup;
        this.renderer = renderer;
        reset();
    }

    /** Vuelve al minuto 0 con el mismo caso, hospital, estrategia y velocidad; queda en pausa. */
    public void reset() {
        Hospital hospital = new HospitalFactory().create(setup.hospital());
        AllocationStrategy strategy = setup.strategy().create(setup.caseConfig().seed());
        engine = new SimulationEngine(new CaseGenerator().generate(setup.caseConfig()), hospital, strategy);
        layout = new LayoutFactory().create(hospital);
        player = new ScenePlayer(engine, layout, renderer);
        strategyName = strategy.getName();
        running = false;
        clock.reset();
    }

    /** Empieza o continúa la simulación; no hace nada si ya terminó. */
    public void play() {
        running = !player.isFinished();
    }

    public void pause() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Cambia la velocidad.
     *
     * @param speed segundos simulados por segundo real; debe ser una de {@link SimulationClock#SPEEDS}
     * @throws IllegalArgumentException si la velocidad no está en la lista
     */
    public void setSpeed(int speed) {
        clock.setSpeed(speed);
    }

    public int getSpeed() {
        return clock.getSpeed();
    }

    /**
     * Avanza el reloj con el tiempo real que pasó y lleva la escena hasta ese momento. En pausa no hace nada.
     * Cuando ya se vio toda la simulación, queda en pausa.
     *
     * @param elapsedMillis milisegundos reales desde el cuadro anterior
     * @throws IllegalArgumentException si el tiempo es negativo
     */
    public void advance(double elapsedMillis) {
        if (elapsedMillis < 0) {
            throw new IllegalArgumentException("El tiempo transcurrido no puede ser negativo.");
        }
        if (!running) {
            return;
        }
        clock.advance(elapsedMillis);
        showClockTime();
    }

    /**
     * Adelanta hasta el comienzo del minuto siguiente y pone la simulación en pausa.
     *
     * @return {@code true} si avanzó; {@code false} si ya se había visto toda la simulación
     */
    public boolean stepOnce() {
        running = false;
        if (player.isFinished()) {
            return false;
        }
        clock.jumpTo((player.getMinute() + 1) * (double) SimulationClock.SECONDS_PER_MINUTE);
        showClockTime();
        return true;
    }

    /**
     * Adelanta el reloj hasta el siguiente evento (llega un paciente, empieza o termina un tratamiento,
     * alguien muere…), para no esperar en tiempo real los ratos en que nadie se mueve. El evento se ve en ese
     * momento y la simulación sigue corriendo o en pausa, como estaba.
     *
     * @return minutos que se adelantaron; 0 si ya no quedan eventos
     */
    public int skipToNextEvent() {
        int minuteBefore = player.getMinute();
        OptionalDouble next = player.nextEventTime();
        if (next.isEmpty()) {
            running = false;
            return 0;
        }
        clock.jumpTo(Math.max(clock.getTime(), next.getAsDouble()));
        showClockTime();
        return player.getMinute() - minuteBefore;
    }

    /**
     * Eventos que se vieron desde la última llamada, para el registro de la ventana.
     *
     * @return eventos nuevos en orden cronológico
     */
    public List<SimulationEvent> drainNewEvents() {
        return player.drainReleasedEvents();
    }

    /**
     * Contadores del estado actual de los pacientes y del hospital.
     *
     * @return contadores en vivo
     */
    public LiveCounters counters() {
        int minute = player.getMinute();
        List<Patient> patients = engine.getPatients();
        int arrived = 0;
        int waiting = 0;
        int treating = 0;
        int icu = 0;
        int discharged = 0;
        int referred = 0;
        int deceased = 0;
        int unattended = 0;
        for (Patient patient : patients) {
            if (!player.hasArrived(patient.getCode())) {
                continue;
            }
            arrived++;
            PatientStatus status = patient.getStatus();
            switch (status) {
                case WAITING -> waiting++;
                case IN_TREATMENT -> treating++;
                case IN_ICU -> icu++;
                case DISCHARGED, STABILIZED_WITHOUT_CURE -> discharged++;
                case REFERRED -> referred++;
                case DECEASED -> deceased++;
                case UNATTENDED -> unattended++;
            }
        }
        Hospital hospital = engine.getHospital();
        return new LiveCounters(minute, patients.size(), arrived, waiting, treating, icu, discharged, referred,
                deceased, unattended,
                hospital.freeBeds().size(), hospital.getBeds().size(),
                hospital.freeIcuBeds().size(), hospital.getIcuBeds().size(),
                hospital.freeOperatingRooms().size(), hospital.getOperatingRooms().size(),
                hospital.freeDoctors().size(), hospital.getDoctors().size(),
                hospital.freeNurses().size(), hospital.getNurses().size(),
                hospital.getBloodBank().totalUnits(),
                hospital.getMorgue().occupancy(), hospital.getMorgue().getCapacity());
    }

    /**
     * Métricas finales de la ejecución.
     *
     * @return métricas si la simulación terminó; vacío si todavía no
     */
    public Optional<RunMetrics> metrics() {
        if (!player.isFinished()) {
            return Optional.empty();
        }
        return Optional.of(new MetricsCalculator().calculate(engine.getResult()));
    }

    public List<PatientMarker> markers() {
        return player.markers();
    }

    public int overflow() {
        return player.overflow();
    }

    public List<StaffMarker> staffMarkers() {
        return player.staffMarkers();
    }

    public HospitalLayout getLayout() {
        return layout;
    }

    public SimulationSetup getSetup() {
        return setup;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public int getMinute() {
        return player.getMinute();
    }

    /** Segundo dentro del minuto simulado actual, de 0 a 59. */
    public int getSecond() {
        return clock.getSecond();
    }

    public boolean isFinished() {
        return player.isFinished();
    }

    /** Lleva la escena hasta la hora del reloj; si ya se vio todo, pone la simulación en pausa. */
    private void showClockTime() {
        player.advanceTo(clock.getTime());
        if (player.isFinished()) {
            running = false;
        }
    }
}
