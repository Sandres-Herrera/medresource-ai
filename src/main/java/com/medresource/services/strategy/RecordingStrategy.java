package com.medresource.services.strategy;

import com.medresource.model.Hospital;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decorador que deja la decisión de otra estrategia intacta y, de paso, anota cómo se
 * veía cada paciente en espera para poder entrenar la red.
 *
 * <p>De cada paciente guarda sus características de la última vez que estuvo en la lista
 * de espera. Para quien fue atendido es el momento en que lo eligieron (después ya no
 * está en la lista); para quien nunca fue atendido, el último instante antes de salir.
 * El motor no sabe que existe: lo único que ve es una estrategia más.</p>
 */
public class RecordingStrategy implements AllocationStrategy {

    private final AllocationStrategy delegate;
    private final FeatureExtractor extractor;
    private final Map<String, double[]> lastSeen = new HashMap<>();

    /**
     * Envuelve una estrategia.
     *
     * @param delegate  estrategia que toma las decisiones
     * @param extractor extractor que describe a cada paciente
     * @throws IllegalArgumentException si algún argumento es nulo
     */
    public RecordingStrategy(AllocationStrategy delegate, FeatureExtractor extractor) {
        if (delegate == null || extractor == null) {
            throw new IllegalArgumentException("La estrategia y el extractor son obligatorios.");
        }
        this.delegate = delegate;
        this.extractor = extractor;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Optional<Assignment> nextAssignment(int minute, List<WaitingPatient> waiting, Hospital hospital) {
        Optional<Assignment> decision = delegate.nextAssignment(minute, waiting, hospital);
        for (WaitingPatient candidate : waiting) {
            lastSeen.put(candidate.patient().getCode(), extractor.extract(candidate, waiting, hospital, minute));
        }
        return decision;
    }

    /**
     * Devuelve cómo se veía un paciente la última vez que estuvo en espera.
     *
     * @param patientCode código del paciente, por ejemplo {@code P001}
     * @return copia de las características, o vacío si el paciente nunca estuvo en espera
     */
    public Optional<double[]> featuresFor(String patientCode) {
        return Optional.ofNullable(lastSeen.get(patientCode)).map(double[]::clone);
    }
}