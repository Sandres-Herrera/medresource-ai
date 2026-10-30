package com.medresource.services.management;

import com.medresource.model.CaseConfig;
import com.medresource.model.SimulationCase;
import com.medresource.persistence.CaseDao;
import com.medresource.services.simulation.CaseGenerator;

import java.util.Optional;

/**
 * Gestiona los casos de simulación guardados.
 *
 * <p>Un caso se identifica por su configuración (semilla, escenario, cantidad de pacientes y duración).
 * Pedir dos veces la misma configuración devuelve el mismo caso guardado, así una ejecución se puede
 * repetir con los mismos pacientes. Las enfermedades de los pacientes tienen que estar guardadas
 * (véase {@link DiseaseService#loadDefaultCatalog()}); si no, la base de datos rechaza el caso con un
 * {@link com.medresource.persistence.DataAccessException}.</p>
 */
public class CaseService {

    private final CaseDao dao;
    private final CaseGenerator generator;

    /**
     * Crea el servicio.
     *
     * @param dao       acceso a los casos guardados
     * @param generator generador de los casos nuevos
     * @throws IllegalArgumentException si alguno es nulo
     */
    public CaseService(CaseDao dao, CaseGenerator generator) {
        if (dao == null) {
            throw new IllegalArgumentException("El acceso a datos de casos es obligatorio.");
        }
        if (generator == null) {
            throw new IllegalArgumentException("El generador de casos es obligatorio.");
        }
        this.dao = dao;
        this.generator = generator;
    }

    /**
     * Devuelve el identificador del caso de una configuración: el que ya está guardado o, si todavía no
     * existe, el de un caso nuevo que se genera y se guarda.
     *
     * @param config configuración del caso
     * @return identificador del caso guardado
     * @throws IllegalArgumentException si la configuración es nula
     */
    public int idFor(CaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración del caso es obligatoria.");
        }
        return dao.findIdByConfig(config).orElseGet(() -> dao.save(generator.generate(config)));
    }

    /**
     * Busca un caso guardado.
     *
     * @param id identificador del caso
     * @return el caso con todos sus pacientes, o vacío si no existe
     */
    public Optional<SimulationCase> find(int id) {
        return dao.findById(id);
    }

    /**
     * Borra un caso con sus pacientes y los resultados de sus ejecuciones.
     *
     * @param id identificador del caso
     * @throws IllegalArgumentException si el caso no existe
     */
    public void delete(int id) {
        if (dao.findById(id).isEmpty()) {
            throw new IllegalArgumentException("No existe el caso con el identificador " + id + ".");
        }
        dao.deleteById(id);
    }
}