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
import com.medresource.services.strategy.ModelStore;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.SceneRenderer;

import java.util.List;
import java.util.Optional;

/**
 * Controla una simulación que se ve en pantalla: reloj, pausa, paso a paso, reinicio y velocidad.
 *
 * <p>No usa Swing: la ventana llama a {@link #advance(double)} en cada cuadro con los milisegundos reales
 * que pasaron, y el controlador decide cuántos minutos simular según la velocidad (minutos simulados por
 * segundo real) y cuánto mover a los pacientes. La velocidad solo cambia lo que se ve: el resultado de la
 * simulación es el mismo con cualquier velocidad.</p>
 */
public final class SimulationController {

    /** Velocidades que se pueden elegir, en minutos simulados por segundo. */
    public static final List<Integer> SPEEDS = List.of(1, 5, 20, 60);

    /** Velocidad inicial, en minutos simulados por segundo. */
    public static final int DEFAULT_SPEED = 5;

    /** Máximo de minutos que se simulan en un solo cuadro, para que la ventana no se congele. */
    public static final int MAX_MINUTES_PER_FRAME = 10;

    private final SimulationSetup setup;
    private final SceneRenderer renderer;
    private final ModelStore store;
    private SimulationEngine engine;
    private ScenePlayer player;
    private HospitalLayout layout;
    private String strategyName;
    private int speed = DEFAULT_SPEED;
    private boolean running;
    private double minuteProgress;
    private int deliveredEvents;

    /**
     * Prepara la simulación sin empezarla; las redes de la IA se cargan de la carpeta {@code models}.
     *
     * @param setup    hospital, caso y estrategia
     * @param renderer renderizador que da el centro de cada casilla
     * @throws IllegalArgumentException si algún argumento es nulo
     * @throws IllegalStateException    si la estrategia necesita un modelo que no existe
     */
    public SimulationController(SimulationSetup setup, SceneRenderer renderer) {
        this(setup, renderer, new ModelStore(ModelStore.DEFAULT_DIRECTORY));
    }

    /**
     * Prepara la simulación sin empezarla.
     *
     * @param setup    hospital, caso y estrategia
     * @param renderer renderizador que da el centro de cada casilla
     * @param store    almacén de las redes entrenadas
     * @throws IllegalArgumentException si algún argumento es nulo
     * @throws IllegalStateException    si la estrategia necesita un modelo que no existe
     */
    public SimulationController(SimulationSetup setup, SceneRenderer renderer, ModelStore store) {
        if (setup == null || renderer == null || store == null) {
            throw new IllegalArgumentException("La configuración, el renderizador y los modelos son obligatorios.");
        }
        this.setup = setup;
        this.renderer = renderer;
        this.store = store;
        reset();
    }

    /**
     * Vuelve al minuto 0 con el mismo caso, hospital y estrategia; queda en pausa.
     *
     * @throws IllegalStateException si la estrategia necesita un modelo que no existe
     */
    public void reset() {
        Hospital hospital = new HospitalFactory().create(setup.hospital());
        AllocationStrategy strategy = setup.strategy().create(setup.caseConfig().seed(), store);
        engine = new SimulationEngine(new CaseGenerator().generate(setup.caseConfig()), hospital, strategy);
        layout = new LayoutFactory().create(hospital);
        player = new ScenePlayer(engine, layout, renderer);
        strategyName = strategy.getName();
        running = false;
        minuteProgress = 0;
        deliveredEvents = 0;
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
     * @param minutesPerSecond minutos simulados por segundo real; debe ser una de {@link #SPEEDS}
     * @throws IllegalArgumentException si la velocidad no está en la lista
     */
    public void setSpeed(int minutesPerSecond) {
        if (!SPEEDS.contains(minutesPerSecond)) {
            throw new IllegalArgumentException("La velocidad debe ser una de " + SPEEDS + ".");
        }
        this.speed = minutesPerSecond;
    }

    public int getSpeed() {
        return speed;
    }

    /**
     * Avanza el reloj: simula los minutos que tocan según el tiempo real que pasó y mueve a los pacientes.
     * En pausa no hace nada. Cuando la simulación termina, queda en pausa.
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
        minuteProgress += speed * elapsedMillis / 1000.0;
        int simulated = 0;
        while (minuteProgress >= 1.0 && simulated < MAX_MINUTES_PER_FRAME) {
            player.animate(1.0);
            if (!player.step()) {
                finishClock();
                return;
            }
            minuteProgress -= 1.0;
            simulated++;
        }
        minuteProgress = Math.min(minuteProgress, 1.0);
        player.animate(minuteProgress);
        if (player.isFinished()) {
            finishClock();
        }
    }

    /**
     * Simula exactamente un minuto y deja a los pacientes en su lugar; pone la simulación en pausa.
     *
     * @return {@code true} si se simuló un minuto; {@code false} si ya había terminado
     */
    public boolean stepOnce() {
        running = false;
        player.animate(1.0);
        boolean stepped = player.step();
        player.animate(1.0);
        minuteProgress = 0;
        return stepped;
    }

    /**
     * Eventos que ocurrieron desde la última llamada, para el registro de la ventana.
     *
     * @return eventos nuevos en orden cronológico
     */
    public List<SimulationEvent> drainNewEvents() {
        List<SimulationEvent> events = engine.getEvents();
        List<SimulationEvent> fresh = List.copyOf(events.subList(deliveredEvents, events.size()));
        deliveredEvents = events.size();
        return fresh;
    }

    /**
     * Contadores del estado actual de los pacientes y del hospital.
     *
     * @return contadores en vivo
     */
    public LiveCounters counters() {
        int minute = engine.getMinute();
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
            if (patient.getArrivalMinute() >= minute) {
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
        if (!engine.isFinished()) {
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

    public boolean isFinished() {
        return player.isFinished();
    }

    private void finishClock() {
        player.animate(1.0);
        running = false;
        minuteProgress = 0;
    }
}
