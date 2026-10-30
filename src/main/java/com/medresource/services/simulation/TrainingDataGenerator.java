package com.medresource.services.simulation;

import com.medresource.model.HospitalConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Genera los ejemplos con los que se entrena la red neuronal, a partir de simulaciones.
 *
 * <p>Por cada semilla y cada escenario simula el mismo caso con tres estrategias
 * (FIFO, prioridad clínica y aleatoria) para que la red vea decisiones variadas.
 * Cada paciente atendido aporta un ejemplo con cómo se veía cuando lo eligieron y
 * cómo le fue; cada paciente que nunca fue atendido aporta uno con cómo se veía
 * la última vez que esperó, con resultado 0.</p>
 *
 * <p>Las semillas de entrenamiento empiezan en {@value #TRAINING_FIRST_SEED} y las
 * de evaluación en {@value #EVALUATION_FIRST_SEED}: nunca se mezclan, así que la red
 * se mide con casos que no vio al aprender.</p>
 */
public class TrainingDataGenerator {

    /** Primera semilla de los casos de entrenamiento. */
    public static final long TRAINING_FIRST_SEED = 1;

    /** Primera semilla de los casos de evaluación. */
    public static final long EVALUATION_FIRST_SEED = 1_000_000;

    private final HospitalConfig hospitalConfig;

    /**
     * Crea un generador para un tamaño de hospital.
     *
     * @param hospitalConfig hospital en el que se simula
     * @throws IllegalArgumentException si la configuración es nula
     */
    public TrainingDataGenerator(HospitalConfig hospitalConfig) {
        if (hospitalConfig == null) {
            throw new IllegalArgumentException("La configuración del hospital es obligatoria.");
        }
        this.hospitalConfig = hospitalConfig;
    }

    /**
     * Arma una lista de semillas consecutivas.
     *
     * @param first primera semilla
     * @param count cuántas semillas, al menos 1
     * @return semillas {@code first}, {@code first + 1}, …
     * @throws IllegalArgumentException si {@code count} es menor que 1
     */
    public static List<Long> seeds(long first, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Debe haber al menos una semilla.");
        }
        List<Long> seeds = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            seeds.add(first + index);
        }
        return seeds;
    }
}