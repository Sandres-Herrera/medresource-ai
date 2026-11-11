package com.medresource.ui.controller;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.Scenario;
import com.medresource.services.simulation.CaseGenerator;

import java.util.Locale;
import java.util.Optional;
import java.util.Random;

/**
 * Borrador de una simulación nueva mientras el usuario la arma en el formulario: el hospital, el escenario, los
 * pacientes, el tiempo de llegadas, la semilla, la estrategia y la velocidad inicial.
 *
 * <p>Nunca guarda un valor inválido: cada cantidad se ajusta a su rango al cambiarla, así que
 * {@link #toRequest()} siempre funciona. Además describe en palabras lo que se eligió, para que el formulario
 * lo muestre mientras se edita: cómo llegan los pacientes en cada escenario con el tiempo elegido, y un resumen
 * con el ritmo de llegada, el tamaño del hospital y cuánto dura la simulación en pantalla.</p>
 *
 * <p>No usa Swing: el formulario solo le pasa lo que cambia el usuario y muestra lo que el borrador describe.</p>
 */
public final class SetupDraft {

    /** Mayor semilla que se sortea con el dado. */
    public static final int MAX_RANDOM_SEED = 99_999;

    private HospitalConfig hospital = HospitalConfig.standard();
    private Scenario scenario = Scenario.NORMAL;
    private int patients = CaseConfig.DEFAULT_PATIENTS;
    private int durationMinutes = CaseConfig.DEFAULT_DURATION_MINUTES;
    private long seed = 1L;
    private StrategyOption strategy = StrategyOption.CLINICAL_PRIORITY;
    private int speed = SimulationClock.DEFAULT_SPEED;

    public HospitalConfig getHospital() {
        return hospital;
    }

    /**
     * Cambia todo el hospital.
     *
     * @param hospital configuración nueva
     * @throws IllegalArgumentException si es nula
     */
    public void setHospital(HospitalConfig hospital) {
        if (hospital == null) {
            throw new IllegalArgumentException("La configuración del hospital es obligatoria.");
        }
        this.hospital = hospital;
    }

    /**
     * Cambia un recurso del hospital; la cantidad se ajusta a su rango.
     *
     * @param field recurso
     * @param value cantidad pedida
     * @throws IllegalArgumentException si el recurso es nulo
     */
    public void setResource(HospitalField field, int value) {
        if (field == null) {
            throw new IllegalArgumentException("El recurso es obligatorio.");
        }
        hospital = field.with(hospital, field.clamp(value));
    }

    /** Tamaño ya armado al que corresponde el hospital, o vacío si el usuario lo personalizó. */
    public Optional<HospitalPreset> getPreset() {
        return HospitalPreset.matching(hospital);
    }

    public Scenario getScenario() {
        return scenario;
    }

    /**
     * Cambia el escenario.
     *
     * @param scenario escenario
     * @throws IllegalArgumentException si es nulo
     */
    public void setScenario(Scenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("El escenario es obligatorio.");
        }
        this.scenario = scenario;
    }

    public int getPatients() {
        return patients;
    }

    /**
     * Cambia la cantidad de pacientes; se ajusta al rango de {@link CaseConfig}.
     *
     * @param patients cantidad pedida
     */
    public void setPatients(int patients) {
        this.patients = Math.max(CaseConfig.MIN_PATIENTS, Math.min(CaseConfig.MAX_PATIENTS, patients));
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    /**
     * Cambia el tiempo en que llegan los pacientes; se ajusta al rango de {@link CaseConfig}.
     *
     * @param minutes minutos pedidos
     */
    public void setDurationMinutes(int minutes) {
        this.durationMinutes = Math.max(CaseConfig.MIN_DURATION_MINUTES,
                Math.min(CaseConfig.MAX_DURATION_MINUTES, minutes));
    }

    public long getSeed() {
        return seed;
    }

    /**
     * Cambia la semilla; una negativa se toma como 0.
     *
     * @param seed semilla pedida
     */
    public void setSeed(long seed) {
        this.seed = Math.max(0L, seed);
    }

    /**
     * Sortea una semilla nueva, de 1 a {@value #MAX_RANDOM_SEED}, para ver otro caso del mismo escenario.
     *
     * @param random generador de números al azar
     * @return la semilla sorteada
     * @throws IllegalArgumentException si el generador es nulo
     */
    public long rollSeed(Random random) {
        if (random == null) {
            throw new IllegalArgumentException("El generador de números al azar es obligatorio.");
        }
        seed = 1L + random.nextInt(MAX_RANDOM_SEED);
        return seed;
    }

    public StrategyOption getStrategy() {
        return strategy;
    }

    /**
     * Cambia la estrategia.
     *
     * @param strategy estrategia
     * @throws IllegalArgumentException si es nula
     */
    public void setStrategy(StrategyOption strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("La estrategia es obligatoria.");
        }
        this.strategy = strategy;
    }

    public int getSpeed() {
        return speed;
    }

    /**
     * Cambia la velocidad con la que empieza la simulación.
     *
     * @param speed una de {@link SimulationClock#SPEEDS}
     * @throws IllegalArgumentException si no es una de ellas
     */
    public void setSpeed(int speed) {
        if (!SimulationClock.SPEEDS.contains(speed)) {
            throw new IllegalArgumentException("La velocidad debe ser una de " + SimulationClock.SPEEDS + ".");
        }
        this.speed = speed;
    }

    /**
     * Cómo llegan los pacientes en un escenario con el tiempo de llegadas elegido, en una línea corta: por
     * ejemplo, en una catástrofe de 480 minutos, «70 % en 48 min».
     *
     * @param scenario escenario
     * @return descripción de las llegadas
     * @throws IllegalArgumentException si el escenario es nulo
     */
    public String arrivalsOf(Scenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("El escenario es obligatorio.");
        }
        return switch (scenario) {
            case NORMAL -> "Llegadas parejas";
            case HIGH_DEMAND -> "Todos en las primeras "
                    + formatTime(percentOfDuration(CaseGenerator.HIGH_DEMAND_TIME_PERCENT));
            case CATASTROPHE -> CaseGenerator.CATASTROPHE_BURST_PERCENT + " % en "
                    + formatTime(percentOfDuration(CaseGenerator.CATASTROPHE_BURST_TIME_PERCENT));
            case UNEXPECTED_EMERGENCIES -> CaseGenerator.EMERGENCY_PERCENT + " % urgentes sin aviso";
        };
    }

    /**
     * Resumen de lo elegido: ritmo promedio de llegada, tamaño del hospital y cuánto dura la llegada de pacientes
     * en pantalla con la velocidad elegida. Por ejemplo: «1 paciente cada 4,8 min · hospital estándar · a ×10 se
     * ve en 48 min».
     */
    public String summary() {
        double minutesPerPatient = durationMinutes / (double) patients;
        String rhythm = minutesPerPatient >= 1.0
                ? "1 paciente cada " + decimal(minutesPerPatient) + " min"
                : decimal(1.0 / minutesPerPatient) + " pacientes por minuto";
        String size = getPreset().map(preset -> "hospital " + preset.getDisplayName().toLowerCase(Locale.ROOT))
                .orElse("hospital personalizado");
        return rhythm + " · " + size + " · a ×" + speed + " se ve en " + formatTime(durationMinutes / (double) speed);
    }

    /**
     * Arma el pedido con lo elegido.
     *
     * @return configuración y velocidad, listas para abrir la simulación
     */
    public SimulationRequest toRequest() {
        CaseConfig caseConfig = new CaseConfig(seed, scenario, patients, durationMinutes);
        return new SimulationRequest(new SimulationSetup(hospital, caseConfig, strategy), speed);
    }

    private int percentOfDuration(int percent) {
        return durationMinutes * percent / 100;
    }

    /**
     * Escribe un tiempo en minutos de forma corta: hasta 2 horas en minutos («48 min»), desde ahí en horas con un
     * decimal si hace falta («4 h», «4,5 h»).
     *
     * @param minutes minutos, no negativos
     * @return el tiempo escrito
     */
    static String formatTime(double minutes) {
        if (minutes < 120) {
            return Math.round(minutes) + " min";
        }
        return decimal(minutes / 60.0) + " h";
    }

    /** Número con un decimal y coma, sin «,0» al final: 4,8 · 4 · 16,7. */
    private static String decimal(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", rounded).replace('.', ',');
    }
}
