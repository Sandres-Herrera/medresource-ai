package com.medresource.services.strategy;

import com.medresource.model.Disease;
import com.medresource.model.Doctor;
import com.medresource.model.Hospital;
import com.medresource.model.Patient;
import com.medresource.model.TreatmentType;
import com.medresource.model.TriageLevel;

import java.util.List;

/**
 * Convierte la situación de un paciente en espera en los números que recibe la red neuronal.
 *
 * <p>Todos los valores van de 0 a 1 para que ninguno domine a los demás. Se calculan
 * con el <b>diagnóstico</b>, nunca con la enfermedad real, y el sexo no interviene.
 * Los números que describen recursos valen 1 cuando el recurso no hace falta o está
 * disponible, y 0 cuando hace falta y no hay.</p>
 */
public class FeatureExtractor {

    /** Nombres de las características, en el orden del vector que devuelve {@link #extract}. */
    public static final List<String> FEATURE_NAMES = List.of(
            "health",
            "age",
            "urgency",
            "minutesUntilDeath",
            "treatmentMinutes",
            "bestDoctorEffectiveness",
            "waitingMinutes",
            "bedAvailable",
            "operatingRoomAvailable",
            "bloodAvailable",
            "needsIcu",
            "freeDoctors",
            "freeNurses",
            "queueSize");

    /** Cantidad de características del vector. */
    public static final int FEATURE_COUNT = FEATURE_NAMES.size();

    /** Minutos que equivalen a 1 en las características de tiempo; lo que pase se corta en 1 (supuesto). */
    public static final int TIME_SCALE_MINUTES = 480;

    /** Pacientes en espera que equivalen a 1 en el tamaño de la cola; lo que pase se corta en 1 (supuesto). */
    public static final int QUEUE_SCALE = 50;

    private static final double MAX_AGE = 100.0;

    private final SurvivalEstimator estimator;

    /** Crea un extractor con el estimador de supervivencia estándar. */
    public FeatureExtractor() {
        this(new SurvivalEstimator());
    }

    /**
     * Crea un extractor con un estimador dado.
     *
     * @param estimator estimador de supervivencia
     * @throws IllegalArgumentException si el estimador es nulo
     */
    public FeatureExtractor(SurvivalEstimator estimator) {
        if (estimator == null) {
            throw new IllegalArgumentException("El estimador de supervivencia es obligatorio.");
        }
        this.estimator = estimator;
    }

    /**
     * Describe a un paciente en espera con un vector de {@link #FEATURE_COUNT} números de 0 a 1.
     *
     * <p>El orden es el de {@link #FEATURE_NAMES}. La urgencia es 1 para el triage I y 0 para
     * el V; si el paciente aún no tiene triage se usa el típico de su diagnóstico. La duración
     * del tratamiento se calcula con el mejor médico libre, o con efectividad 1 si no hay ninguno.</p>
     *
     * @param candidate paciente con su diagnóstico
     * @param waiting   todos los pacientes en espera, incluido este
     * @param hospital  hospital con sus recursos
     * @param minute    minuto actual de la simulación
     * @return vector de características
     * @throws IllegalArgumentException si algún dato es nulo
     */
    public double[] extract(WaitingPatient candidate, List<WaitingPatient> waiting, Hospital hospital, int minute) {
        if (candidate == null || waiting == null || hospital == null) {
            throw new IllegalArgumentException("El paciente, la lista de espera y el hospital son obligatorios.");
        }
        Patient patient = candidate.patient();
        Disease diagnosis = candidate.diagnosis();
        double bestEffectiveness = bestEffectiveness(hospital, diagnosis, patient.getAge());
        double referenceEffectiveness = bestEffectiveness > 0.0 ? bestEffectiveness : 1.0;
        TriageLevel triage = patient.hasTriage() ? patient.getTriageLevel() : diagnosis.typicalTriage();

        double[] features = new double[FEATURE_COUNT];
        features[index("health")] = patient.getHealth() / Patient.MAX_HEALTH;
        features[index("age")] = Math.min(patient.getAge(), MAX_AGE) / MAX_AGE;
        features[index("urgency")] = (TriageLevel.V.getLevel() - triage.getLevel())
                / (double) (TriageLevel.V.getLevel() - TriageLevel.I.getLevel());
        features[index("minutesUntilDeath")] = timeFeature(estimator.minutesUntilDeath(candidate));
        features[index("treatmentMinutes")] = timeFeature(estimator.treatmentMinutes(candidate, referenceEffectiveness));
        features[index("bestDoctorEffectiveness")] = bestEffectiveness;
        features[index("waitingMinutes")] = timeFeature(patient.waitingTime(minute));
        features[index("bedAvailable")] = bedAvailable(hospital, diagnosis) ? 1.0 : 0.0;
        features[index("operatingRoomAvailable")] =
                !diagnosis.getTreatment().requiresOperatingRoom() || !hospital.freeOperatingRooms().isEmpty() ? 1.0 : 0.0;
        features[index("bloodAvailable")] = !diagnosis.requiresBlood() || hospital.getBloodBank()
                .hasCompatibleUnits(patient.getBloodType(), PriorityBasedStrategy.BLOOD_UNITS_PER_TREATMENT) ? 1.0 : 0.0;
        features[index("needsIcu")] = diagnosis.requiresIcu() ? 1.0 : 0.0;
        features[index("freeDoctors")] = fraction(hospital.freeDoctors().size(), hospital.getDoctors().size());
        features[index("freeNurses")] = fraction(hospital.freeNurses().size(), hospital.getNurses().size());
        features[index("queueSize")] = Math.min(waiting.size(), QUEUE_SCALE) / (double) QUEUE_SCALE;
        return features;
    }

    private static int index(String name) {
        return FEATURE_NAMES.indexOf(name);
    }

    private static double timeFeature(int minutes) {
        return Math.min(minutes, TIME_SCALE_MINUTES) / (double) TIME_SCALE_MINUTES;
    }

    private static double fraction(int part, int total) {
        return total == 0 ? 0.0 : part / (double) total;
    }

    private static double bestEffectiveness(Hospital hospital, Disease diagnosis, int age) {
        double best = 0.0;
        for (Doctor doctor : hospital.freeDoctors()) {
            best = Math.max(best, doctor.effectivenessFor(diagnosis, age));
        }
        return best;
    }

    private static boolean bedAvailable(Hospital hospital, Disease diagnosis) {
        TreatmentType treatment = diagnosis.getTreatment();
        if (diagnosis.requiresIcu()) {
            return !hospital.freeIcuBeds().isEmpty();
        }
        return !treatment.requiresBed() || !hospital.freeBeds().isEmpty();
    }
}