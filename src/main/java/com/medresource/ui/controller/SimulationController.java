package com.medresource.ui.controller;

import com.medresource.model.Hospital;
import com.medresource.model.HospitalLayout;
import com.medresource.model.Patient;
import com.medresource.model.PatientStatus;
import com.medresource.model.Position;
import com.medresource.model.RunMetrics;
import com.medresource.model.SimulationEvent;
import com.medresource.services.management.ReportRequest;
import com.medresource.services.simulation.CaseGenerator;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.services.simulation.MetricsCalculator;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.AllocationStrategy;
import com.medresource.ui.render.PatientMarker;
import com.medresource.ui.render.PlanAlert;
import com.medresource.ui.render.PorterMarker;
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
    private AlertTracker alerts;
    private HospitalLayout layout;
    private String strategyName;
    private final SimulationClock clock = new SimulationClock();
    private boolean running;
    private int turboSegmentsShown;
    private double turboStartTime;
    private double turboFinishTime = -1;

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
        alerts = new AlertTracker(layout);
        strategyName = strategy.getName();
        running = false;
        clock.reset();
        turboSegmentsShown = 0;
        turboFinishTime = -1;
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
        if (speed != clock.getSpeed()) {
            turboSegmentsShown = 0;
            turboFinishTime = -1;
        }
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
        if (clock.getSpeed() == SimulationClock.TURBO_SPEED) {
            return skipTurboSegment();
        }
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
     * Recorre aproximadamente un doceavo de la duración restante en cada clic. Se calcula la duración exacta
     * con una simulación independiente y determinista, sin alterar el motor que está mostrando la ventana.
     * {@link ScenePlayer#advanceTo(double)} sigue procesando todos los eventos intermedios en orden; no se
     * modifican tratamientos, desenlaces ni métricas. Los avances manuales TURBO se hacen en pausa.
     */
    private int skipTurboSegment() {
        running = false;
        if (player.isFinished()) {
            return 0;
        }
        if (turboFinishTime < 0) {
            SimulationEngine forecast = new SimulationEngine(new CaseGenerator().generate(setup.caseConfig()),
                    new HospitalFactory().create(setup.hospital()),
                    setup.strategy().create(setup.caseConfig().seed()));
            forecast.run();
            turboStartTime = clock.getTime();
            // Un minuto adicional asegura que se hayan liberado los eventos del último minuto.
            turboFinishTime = Math.max(turboStartTime, (forecast.getMinute() + 1.0)
                    * SimulationClock.SECONDS_PER_MINUTE);
            turboSegmentsShown = 0;
        }
        int before = player.getMinute();
        turboSegmentsShown++;
        double fraction = Math.min(1.0, turboSegmentsShown / (double) SimulationClock.TURBO_SEGMENTS);
        double target = turboStartTime + (turboFinishTime - turboStartTime) * fraction;
        clock.jumpTo(Math.max(clock.getTime(), target));
        showClockTime();
        return player.getMinute() - before;
    }

    /**
     * Eventos que se vieron desde la última llamada, para el registro de la ventana.
     *
     * @return eventos nuevos en orden cronológico
     */
    public List<SimulationEvent> drainNewEvents() {
        List<SimulationEvent> events = player.drainReleasedEvents();
        alerts.onEvents(events, player.getTime(), player.getState()::positionOf);
        return events;
    }

    /**
     * Avisos que se ven ahora sobre el plano: los fallecidos y las llegadas masivas recientes (de los eventos ya
     * entregados por {@link #drainNewEvents}), la UCI llena y la sangre agotada.
     *
     * @return los avisos
     */
    public List<PlanAlert> alerts() {
        Hospital hospital = engine.getHospital();
        alerts.onResources(hospital.freeIcuBeds().size(), hospital.getIcuBeds().size(),
                hospital.getBloodBank().getUnits());
        return alerts.active(player.getTime());
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

    /**
     * Datos para generar el reporte de fin de simulación: el resultado, la traza y el hospital de lo que se vio, y
     * cómo volver a simular el mismo caso con la misma estrategia.
     *
     * @return los datos si la simulación terminó; vacío si todavía no
     */
    public Optional<ReportRequest> reportRequest() {
        if (!player.isFinished()) {
            return Optional.empty();
        }
        long seed = setup.caseConfig().seed();
        StrategyOption option = setup.strategy();
        return Optional.of(new ReportRequest(engine.getResult(), engine.getTrace(), engine.getHospital(),
                setup.hospital(), () -> option.create(seed), setup.baseRunId()));
    }

    /**
     * Ficha de un paciente tal como se ve ahora.
     *
     * @param patientCode código del paciente
     * @return su ficha, o vacío si no está en la escena (todavía no llegó o ya salió)
     */
    public Optional<PatientCard> patientCard(String patientCode) {
        return player.card(patientCode);
    }

    /**
     * Ficha de un médico o de un enfermero tal como se ve ahora.
     *
     * @param staffCode código del médico o del enfermero
     * @return su ficha, o vacío si no es del hospital
     */
    public Optional<StaffCard> staffCard(String staffCode) {
        return player.staffCard(staffCode);
    }

    /**
     * Lo que se ve ahora en una casilla del plano: quién está ahí, desde hace cuánto y cuánto le falta.
     *
     * @param cell casilla del plano
     * @return la información, o vacío si ahí no hay un recurso ni un paciente
     */
    public Optional<PlaceInfo> placeInfo(Position cell) {
        return player.placeInfo(cell);
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

    /** Camilleros que buscan o acompañan a un paciente en lo que se ve ahora. */
    public List<PorterMarker> porterMarkers() {
        return player.porterMarkers();
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
