package com.medresource.services.management;

import com.medresource.model.ComparisonSaveResult;
import com.medresource.model.RunComparison;
import com.medresource.model.SavedComparison;
import com.medresource.persistence.ComparisonDao;
import com.medresource.persistence.ConnectionManager;
import com.medresource.persistence.DataAccessException;
import com.medresource.persistence.DatabaseConfig;
import com.medresource.persistence.OracleCaseDao;
import com.medresource.persistence.OracleComparisonDao;
import com.medresource.persistence.OracleDiseaseDao;
import com.medresource.persistence.OracleResultDao;
import com.medresource.services.simulation.CaseGenerator;

import java.util.List;
import java.util.Optional;

/**
 * Compara ejecuciones guardadas y guarda las comparaciones que el usuario decide conservar.
 *
 * <p>Comparar no guarda nada: la comparación solo se guarda cuando se pide con {@link #save(RunComparison)}. Si
 * ese par de ejecuciones ya estaba guardado, no se repite: se devuelve la comparación que ya estaba.</p>
 */
public class ComparisonArchive {

    private final RunComparator comparator;
    private final ComparisonDao comparisons;

    /**
     * Crea el archivo.
     *
     * @param runs        servicio de ejecuciones guardadas
     * @param comparisons acceso a las comparaciones guardadas
     * @throws IllegalArgumentException si alguno es nulo
     */
    public ComparisonArchive(SimulationService runs, ComparisonDao comparisons) {
        if (runs == null || comparisons == null) {
            throw new IllegalArgumentException("El servicio de ejecuciones y el acceso a las comparaciones son "
                    + "obligatorios.");
        }
        this.comparator = new RunComparator(runs);
        this.comparisons = comparisons;
    }

    /**
     * Crea el archivo que usa la aplicación, con Oracle, si el equipo tiene configurada la base de datos.
     *
     * @return el archivo, o vacío si no existe {@code db.properties}
     * @throws DataAccessException si {@code db.properties} existe pero no se puede leer
     */
    public static Optional<ComparisonArchive> fromConfiguration() {
        if (DatabaseConfig.class.getResource(DatabaseConfig.RESOURCE_NAME) == null) {
            return Optional.empty();
        }
        ConnectionManager connections = new ConnectionManager(DatabaseConfig.fromClasspath());
        OracleDiseaseDao diseases = new OracleDiseaseDao(connections);
        CaseService cases = new CaseService(new OracleCaseDao(connections, diseases), new CaseGenerator());
        return Optional.of(new ComparisonArchive(
                new SimulationService(cases, new OracleResultDao(connections, diseases)),
                new OracleComparisonDao(connections)));
    }

    /**
     * Compara dos ejecuciones guardadas sin guardar nada.
     *
     * @param runA ejecución de referencia
     * @param runB ejecución que se compara con ella
     * @return la comparación
     * @throws IllegalArgumentException si no se pueden comparar; el mensaje dice por qué
     * @throws DataAccessException      si la base de datos rechaza la operación
     */
    public RunComparison compare(int runA, int runB) {
        return comparator.compare(runA, runB);
    }

    /**
     * Guarda una comparación, o devuelve la que ya estaba guardada para el mismo par de ejecuciones.
     *
     * @param comparison comparación que el usuario decidió guardar
     * @return la comparación guardada y si ya lo estaba
     * @throws IllegalArgumentException si la comparación es nula
     * @throws DataAccessException      si la base de datos no acepta la comparación o rechaza la operación
     */
    public ComparisonSaveResult save(RunComparison comparison) {
        if (comparison == null) {
            throw new IllegalArgumentException("La comparación es obligatoria.");
        }
        Optional<SavedComparison> existing = comparisons.findByRuns(comparison.runA(), comparison.runB());
        if (existing.isPresent()) {
            return new ComparisonSaveResult(existing.get(), true);
        }
        return new ComparisonSaveResult(comparisons.save(comparison), false);
    }

    /**
     * Lista las comparaciones guardadas, de la más reciente a la más antigua.
     *
     * @return comparaciones guardadas; lista vacía si no hay ninguna
     * @throws DataAccessException si la base de datos rechaza la operación
     */
    public List<SavedComparison> saved() {
        return comparisons.findAll();
    }

    /**
     * Abre una comparación guardada, tal como se guardó.
     *
     * @param id identificador
     * @return la comparación, o vacío si no existe
     * @throws DataAccessException si la base de datos rechaza la operación
     */
    public Optional<SavedComparison> find(int id) {
        return comparisons.findById(id);
    }

    /**
     * Borra una comparación guardada; las ejecuciones y sus reportes no cambian.
     *
     * @param id identificador
     * @throws DataAccessException si no existe o la base de datos rechaza la operación
     */
    public void delete(int id) {
        comparisons.deleteById(id);
    }
}
