package com.medresource.services.management;

import com.medresource.model.CaseConfig;
import com.medresource.model.SimulationCase;
import com.medresource.persistence.CaseDao;
import com.medresource.persistence.DataAccessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO de casos en memoria para las pruebas de los servicios.
 *
 * <p>No es una prueba: guarda los casos en una lista y el identificador es la posición (desde 1). Con
 * {@code failOnSave} simula una base de datos caída al guardar.</p>
 */
final class InMemoryCaseDao implements CaseDao {

    final List<SimulationCase> saved = new ArrayList<>();
    private final List<Integer> ids = new ArrayList<>();
    private int nextId = 1;
    boolean failOnSave;

    @Override
    public Integer save(SimulationCase simulationCase) {
        if (failOnSave) {
            throw new DataAccessException("No se pudo guardar el caso: Oracle apagado");
        }
        saved.add(simulationCase);
        ids.add(nextId);
        return nextId++;
    }

    @Override
    public Optional<Integer> findIdByConfig(CaseConfig config) {
        return saved.stream().filter(stored -> stored.config().equals(config)).findFirst()
                .map(stored -> ids.get(saved.indexOf(stored)));
    }

    @Override
    public Optional<SimulationCase> findById(Integer id) {
        int index = ids.indexOf(id);
        return index < 0 ? Optional.empty() : Optional.of(saved.get(index));
    }

    @Override
    public List<SimulationCase> findAll() {
        return List.copyOf(saved);
    }

    @Override
    public void update(SimulationCase simulationCase) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteById(Integer id) {
        int index = ids.indexOf(id);
        saved.remove(index);
        ids.remove(index);
    }
}