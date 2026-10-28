package com.medresource.services.simulation;

import com.medresource.model.BloodType;
import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.DiseaseCatalog;
import com.medresource.model.PatientProfile;
import com.medresource.model.Person;
import com.medresource.model.Scenario;
import com.medresource.model.Sex;
import com.medresource.model.SimulationCase;
import com.medresource.model.TriageLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/**
 * Genera los pacientes de un caso a partir de una semilla.
 *
 * <p>Con la misma configuración y el mismo catálogo siempre produce el mismo
 * caso. Las enfermedades salen según su frecuencia y la forma de las llegadas
 * depende del escenario:</p>
 * <ul>
 *   <li>Hospital normal: llegadas repartidas en toda la duración.</li>
 *   <li>Alta demanda: todas llegan en la primera mitad.</li>
 *   <li>Catástrofe: el 70 % llega en el primer 10 % del tiempo y las
 *       enfermedades urgentes (triage I o II) son tres veces más probables.</li>
 *   <li>Emergencias inesperadas: el 20 % son casos urgentes que llegan sin
 *       aviso a partir del primer cuarto del tiempo.</li>
 * </ul>
 */
public class CaseGenerator {

    private static final double URGENT_BOOST_IN_CATASTROPHE = 3.0;

    /** Distribución aproximada de tipos de sangre en la población (supuesto). */
    private static final Map<BloodType, Double> BLOOD_WEIGHTS = new EnumMap<>(BloodType.class);

    static {
        BLOOD_WEIGHTS.put(BloodType.O_POSITIVE, 38.0);
        BLOOD_WEIGHTS.put(BloodType.O_NEGATIVE, 7.0);
        BLOOD_WEIGHTS.put(BloodType.A_POSITIVE, 34.0);
        BLOOD_WEIGHTS.put(BloodType.A_NEGATIVE, 6.0);
        BLOOD_WEIGHTS.put(BloodType.B_POSITIVE, 9.0);
        BLOOD_WEIGHTS.put(BloodType.B_NEGATIVE, 2.0);
        BLOOD_WEIGHTS.put(BloodType.AB_POSITIVE, 3.0);
        BLOOD_WEIGHTS.put(BloodType.AB_NEGATIVE, 1.0);
    }

    private record Arrival(int minute, boolean emergency) {
    }

    private final List<Disease> diseases;

    /**
     * Crea un generador con las enfermedades del catálogo por defecto.
     */
    public CaseGenerator() {
        this(DiseaseCatalog.defaultDiseases());
    }

    /**
     * Crea un generador con las enfermedades dadas.
     *
     * @param diseases enfermedades que pueden aparecer; al menos una
     * @throws IllegalArgumentException si la lista es nula o está vacía
     */
    public CaseGenerator(List<Disease> diseases) {
        if (diseases == null || diseases.isEmpty()) {
            throw new IllegalArgumentException("El generador necesita al menos una enfermedad.");
        }
        this.diseases = List.copyOf(diseases);
    }

    /**
     * Genera el caso de una configuración.
     *
     * @param config configuración del caso
     * @return caso con sus pacientes numerados por orden de llegada
     * @throws IllegalArgumentException si la configuración es nula, o si el
     *                                  escenario necesita casos urgentes y el
     *                                  catálogo no tiene ninguno
     */
    public SimulationCase generate(CaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración del caso es obligatoria.");
        }
        List<Disease> urgent = diseases.stream().filter(CaseGenerator::isUrgent).toList();
        boolean needsUrgent = config.scenario() == Scenario.CATASTROPHE
                || config.scenario() == Scenario.UNEXPECTED_EMERGENCIES;
        if (needsUrgent && urgent.isEmpty()) {
            throw new IllegalArgumentException(
                    "El escenario '" + config.scenario().getDisplayName()
                            + "' necesita enfermedades de triage I o II en el catálogo.");
        }

        Random random = new Random(config.seed());
        List<Arrival> arrivals = generateArrivals(config, random);
        List<PatientProfile> profiles = new ArrayList<>();
        for (int i = 0; i < arrivals.size(); i++) {
            profiles.add(generateProfile(i + 1, arrivals.get(i), config.scenario(), urgent, random));
        }
        return new SimulationCase(config, profiles);
    }

    private List<Arrival> generateArrivals(CaseConfig config, Random random) {
        int count = config.patientCount();
        int duration = config.durationMinutes();
        List<Arrival> arrivals = new ArrayList<>();
        switch (config.scenario()) {
            case NORMAL -> addUniform(arrivals, random, count, 0, duration, false);
            case HIGH_DEMAND -> addUniform(arrivals, random, count, 0, duration / 2, false);
            case CATASTROPHE -> {
                int burst = count * 7 / 10;
                addUniform(arrivals, random, burst, 0, duration / 10, false);
                addUniform(arrivals, random, count - burst, 0, duration, false);
            }
            case UNEXPECTED_EMERGENCIES -> {
                int emergencies = count / 5;
                addUniform(arrivals, random, count - emergencies, 0, duration, false);
                addUniform(arrivals, random, emergencies, duration / 4, duration, true);
            }
        }
        arrivals.sort(Comparator.comparingInt(Arrival::minute));
        return arrivals;
    }

    private void addUniform(List<Arrival> arrivals, Random random, int count, int from, int to, boolean emergency) {
        for (int i = 0; i < count; i++) {
            arrivals.add(new Arrival(from + random.nextInt(to - from), emergency));
        }
    }

    private PatientProfile generateProfile(int id, Arrival arrival, Scenario scenario,
                                           List<Disease> urgent, Random random) {
        Disease disease;
        if (arrival.emergency()) {
            disease = pickWeighted(random, urgent, Disease::getFrequency);
        } else if (scenario == Scenario.CATASTROPHE) {
            disease = pickWeighted(random, diseases, d -> d.getFrequency()
                    * (isUrgent(d) ? URGENT_BOOST_IN_CATASTROPHE : 1.0));
        } else {
            disease = pickWeighted(random, diseases, Disease::getFrequency);
        }
        int maxAge = Math.min(disease.getMaxAge(), Person.MAX_AGE);
        int age = disease.getMinAge() + random.nextInt(maxAge - disease.getMinAge() + 1);
        Sex sex = Sex.values()[random.nextInt(Sex.values().length)];
        BloodType bloodType = pickWeighted(random, List.copyOf(BLOOD_WEIGHTS.keySet()), BLOOD_WEIGHTS::get);
        double health = initialHealth(disease.typicalTriage(), random);
        boolean irrecoverable = random.nextDouble() < disease.getIrreducibleMortality();
        return new PatientProfile(id, age, sex, bloodType, disease, disease.getSymptoms(),
                arrival.minute(), health, irrecoverable);
    }

    /** Los casos más urgentes llegan con menos salud. */
    private double initialHealth(TriageLevel triage, Random random) {
        double min;
        double max;
        switch (triage) {
            case I -> { min = 50; max = 70; }
            case II -> { min = 60; max = 80; }
            case III -> { min = 75; max = 95; }
            case IV -> { min = 85; max = 100; }
            default -> { min = 90; max = 100; }
        }
        double health = min + random.nextDouble() * (max - min);
        return Math.round(health * 10) / 10.0;
    }

    private static boolean isUrgent(Disease disease) {
        return disease.typicalTriage().isMoreUrgentThan(TriageLevel.III);
    }

    private static <T> T pickWeighted(Random random, List<T> items, ToDoubleFunction<T> weight) {
        double total = 0;
        for (T item : items) {
            total += weight.applyAsDouble(item);
        }
        double point = random.nextDouble() * total;
        for (T item : items) {
            point -= weight.applyAsDouble(item);
            if (point < 0) {
                return item;
            }
        }
        return items.get(items.size() - 1);
    }
}