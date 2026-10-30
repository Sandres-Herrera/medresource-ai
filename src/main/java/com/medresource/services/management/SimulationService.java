package com.medresource.services.management;

import com.medresource.model.CaseConfig;
import com.medresource.model.HospitalConfig;
import com.medresource.model.RunRecord;
import com.medresource.model.SimulationCase;
import com.medresource.model.SimulationResult;
import com.medresource.persistence.ResultDao;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.SimulationEngine;
import com.medresource.services.strategy.AllocationStrategy;

import java.util.List;
import java.util.Optional;

/**
 * Ejecuta simulaciones y gestiona las ejecuciones guardadas.
 *
 * <p>Ejecutar es: obtener el caso de la configuración (el guardado o uno nuevo, véase
 * {@link CaseService#idFor(CaseConfig)}), correr el motor con la estrategia y el hospital pedidos, y
 * guardar el resultado con los recursos del hospital. Así la misma configuración se puede ejecutar con
 * varias estrategias sobre exactamente los mismos pacientes.</p>
 */
public class SimulationService {

    private final CaseService cases;
    private final ResultDao results;
    private final HospitalFactory hospitalFactory = new HospitalFactory();

    /**
     * Crea el servicio.
     *
     * @param cases   servicio de casos
     * @param results acceso a las ejecuciones guardadas
     * @throws IllegalArgumentException si alguno es nulo
     */
    public SimulationService(CaseService cases, ResultDao results) {
        if (cases == null) {
            throw new IllegalArgumentException("El servicio de casos es obligatorio.");
        }
        if (results == null) {
            throw new IllegalArgumentException("El acceso a datos de ejecuciones es obligatorio.");
        }
        this.cases = cases;
        this.results = results;
    }

    /**
     * Ejecuta una simulación completa y guarda su resultado.
     *
     * @param config   configuración del caso
     * @param hospital recursos del hospital
     * @param strategy estrategia de asignación
     * @return identificador de la ejecución guardada
     * @throws IllegalArgumentException si algún dato es nulo
     */
    public int run(CaseConfig config, HospitalConfig hospital, AllocationStrategy strategy) {
        if (config == null || hospital == null || strategy == null) {
            throw new IllegalArgumentException("La configuración, el hospital y la estrategia son obligatorios.");
        }
        int caseId = cases.idFor(config);
        SimulationCase simulationCase = cases.find(caseId).orElseThrow(
                () -> new IllegalStateException("El caso " + caseId + " desapareció después de guardarse."));
        SimulationResult result =
                new SimulationEngine(simulationCase, hospitalFactory.create(hospital), strategy).run();
        return results.save(new RunRecord(caseId, hospital, result));
    }

    /**
     * Guarda el resultado de una simulación que se ejecutó por fuera del servicio.
     *
     * @param caseId   identificador del caso guardado sobre el que se ejecutó
     * @param hospital recursos del hospital con los que se ejecutó
     * @param result   resultado de la ejecución
     * @return identificador de la ejecución guardada
     * @throws IllegalArgumentException si el caso no existe o algún dato es inválido
     */
    public int save(int caseId, HospitalConfig hospital, SimulationResult result) {
        RunRecord run = new RunRecord(caseId, hospital, result);
        if (cases.find(caseId).isEmpty()) {
            throw new IllegalArgumentException("No existe el caso con el identificador " + caseId + ".");
        }
        return results.save(run);
    }

    /**
     * Busca una ejecución guardada.
     *
     * @param id identificador de la ejecución
     * @return la ejecución, o vacío si no existe
     */
    public Optional<RunRecord> find(int id) {
        return results.findById(id);
    }

    /**
     * Busca las ejecuciones de un caso.
     *
     * @param caseId identificador del caso
     * @return ejecuciones del caso, ordenadas por identificador; lista vacía si no tiene ninguna
     */
    public List<RunRecord> resultsOfCase(int caseId) {
        return results.findIdsByCase(caseId).stream()
                .map(id -> results.findById(id).orElseThrow(
                        () -> new IllegalStateException("La ejecución " + id + " desapareció mientras se leía.")))
                .toList();
    }

    /**
     * Borra una ejecución con los resultados de sus pacientes y sus eventos.
     *
     * @param id identificador de la ejecución
     * @throws IllegalArgumentException si la ejecución no existe
     */
    public void delete(int id) {
        if (results.findById(id).isEmpty()) {
            throw new IllegalArgumentException("No existe la ejecución con el identificador " + id + ".");
        }
        results.deleteById(id);
    }
}