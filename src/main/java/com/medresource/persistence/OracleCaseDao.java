package com.medresource.persistence;

import com.medresource.model.BloodType;
import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.PatientProfile;
import com.medresource.model.Scenario;
import com.medresource.model.Sex;
import com.medresource.model.SimulationCase;
import com.medresource.model.Symptom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Guarda y lee en Oracle los casos de simulación, con todos sus pacientes.
 *
 * <p>Un caso ocupa tres tablas: {@code caso} (la configuración), {@code caso_paciente} (los pacientes)
 * y {@code caso_paciente_sintoma} (los síntomas de cada paciente, en orden). Cada operación se hace en una
 * sola transacción. El identificador del caso lo genera Oracle. Los pacientes guardan solo el número de su
 * enfermedad; al leer, la enfermedad se toma del {@link GenericDao} de enfermedades que recibe el DAO.</p>
 *
 * <p>Los casos no se modifican: con la misma configuración el generador produce siempre los mismos
 * pacientes, así que {@link #update(SimulationCase)} no está permitido.</p>
 */
public class OracleCaseDao implements CaseDao {

    static final String INSERT_CASE =
            "INSERT INTO caso (semilla, escenario, cantidad_pacientes, duracion_minutos) VALUES (?, ?, ?, ?)";
    static final String INSERT_PATIENT = "INSERT INTO caso_paciente (id_caso, id_paciente, edad, sexo, tipo_sangre, "
            + "id_enfermedad, minuto_llegada, salud_inicial, irrecuperable) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String INSERT_SYMPTOM =
            "INSERT INTO caso_paciente_sintoma (id_caso, id_paciente, orden_sintoma, sintoma) VALUES (?, ?, ?, ?)";
    static final String DELETE_CASE = "DELETE FROM caso WHERE id = ?";

    static final String SELECT_CASES =
            "SELECT id, semilla, escenario, cantidad_pacientes, duracion_minutos FROM caso ORDER BY id";
    static final String SELECT_CASE =
            "SELECT id, semilla, escenario, cantidad_pacientes, duracion_minutos FROM caso WHERE id = ?";
    static final String SELECT_ID_BY_CONFIG = "SELECT id FROM caso WHERE semilla = ? AND escenario = ? "
            + "AND cantidad_pacientes = ? AND duracion_minutos = ?";
    static final String SELECT_PATIENTS = "SELECT id_caso, id_paciente, edad, sexo, tipo_sangre, id_enfermedad, "
            + "minuto_llegada, salud_inicial, irrecuperable FROM caso_paciente ORDER BY id_caso, id_paciente";
    static final String SELECT_PATIENTS_OF = "SELECT id_caso, id_paciente, edad, sexo, tipo_sangre, id_enfermedad, "
            + "minuto_llegada, salud_inicial, irrecuperable FROM caso_paciente WHERE id_caso = ? ORDER BY id_paciente";
    static final String SELECT_SYMPTOMS = "SELECT id_caso, id_paciente, sintoma FROM caso_paciente_sintoma "
            + "ORDER BY id_caso, id_paciente, orden_sintoma";
    static final String SELECT_SYMPTOMS_OF = "SELECT id_caso, id_paciente, sintoma FROM caso_paciente_sintoma "
            + "WHERE id_caso = ? ORDER BY id_paciente, orden_sintoma";

    /** Nombre de la columna que Oracle genera al insertar un caso. */
    private static final String GENERATED_COLUMN = "ID";

    /** Un paciente de un caso: el par (caso, paciente) que lo identifica. */
    private record PatientKey(int caseId, int patientId) {
    }

    private final TransactionRunner transactions;
    private final GenericDao<Disease, Integer> diseases;

    /**
     * Crea el DAO.
     *
     * @param connections administrador de conexiones
     * @param diseases    acceso a las enfermedades, para armar los pacientes al leer un caso
     * @throws IllegalArgumentException si alguno es nulo
     */
    public OracleCaseDao(ConnectionManager connections, GenericDao<Disease, Integer> diseases) {
        this.transactions = new TransactionRunner(connections);
        if (diseases == null) {
            throw new IllegalArgumentException("El acceso a datos de enfermedades es obligatorio.");
        }
        this.diseases = diseases;
    }

    // ---------- guardar ----------

    /**
     * Guarda un caso nuevo con todos sus pacientes y sus síntomas.
     *
     * @param simulationCase caso que se guarda
     * @return el identificador que Oracle le asignó
     * @throws IllegalArgumentException si el caso es nulo
     * @throws DataAccessException      si ya hay un caso con la misma configuración, si alguna enfermedad
     *                                  de los pacientes no está guardada, o si Oracle rechaza la operación
     */
    @Override
    public Integer save(SimulationCase simulationCase) {
        requireCase(simulationCase);
        return transactions.run("guardar el caso", connection -> {
            int caseId = insertCase(connection, simulationCase.config());
            insertPatients(connection, caseId, simulationCase.profiles());
            return caseId;
        });
    }

    private static int insertCase(Connection connection, CaseConfig config) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CASE, new String[]{GENERATED_COLUMN})) {
            statement.setLong(1, config.seed());
            statement.setString(2, config.scenario().name());
            statement.setInt(3, config.patientCount());
            statement.setInt(4, config.durationMinutes());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Oracle no devolvió el identificador del caso.");
                }
                return keys.getInt(1);
            }
        }
    }

    private static void insertPatients(Connection connection, int caseId, List<PatientProfile> profiles)
            throws SQLException {
        try (PreparedStatement patients = connection.prepareStatement(INSERT_PATIENT);
             PreparedStatement symptoms = connection.prepareStatement(INSERT_SYMPTOM)) {
            for (PatientProfile profile : profiles) {
                patients.setInt(1, caseId);
                patients.setInt(2, profile.id());
                patients.setInt(3, profile.age());
                patients.setString(4, profile.sex().name());
                patients.setString(5, profile.bloodType().name());
                patients.setInt(6, profile.disease().getId());
                patients.setInt(7, profile.arrivalMinute());
                patients.setDouble(8, profile.initialHealth());
                patients.setInt(9, profile.irrecoverable() ? 1 : 0);
                patients.addBatch();
                int order = 0;
                for (Symptom symptom : profile.symptoms()) {
                    symptoms.setInt(1, caseId);
                    symptoms.setInt(2, profile.id());
                    symptoms.setInt(3, order++);
                    symptoms.setString(4, symptom.name());
                    symptoms.addBatch();
                }
            }
            patients.executeBatch();
            symptoms.executeBatch();
        }
    }

    // ---------- leer ----------

    /**
     * Busca el caso guardado con una configuración.
     *
     * @param config configuración buscada
     * @return el identificador del caso, o vacío si no está guardado
     * @throws IllegalArgumentException si la configuración es nula
     * @throws DataAccessException      si Oracle rechaza la operación
     */
    @Override
    public Optional<Integer> findIdByConfig(CaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("La configuración del caso es obligatoria.");
        }
        return transactions.run("buscar el caso de la semilla " + config.seed(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_ID_BY_CONFIG)) {
                statement.setLong(1, config.seed());
                statement.setString(2, config.scenario().name());
                statement.setInt(3, config.patientCount());
                statement.setInt(4, config.durationMinutes());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(rows.getInt("id")) : Optional.<Integer>empty();
                }
            }
        });
    }

    /**
     * Busca un caso por su identificador.
     *
     * @param id identificador buscado
     * @return el caso con todos sus pacientes, o vacío si no existe
     * @throws IllegalArgumentException si el identificador es nulo
     * @throws DataAccessException      si Oracle rechaza la operación o los datos guardados no son válidos
     */
    @Override
    public Optional<SimulationCase> findById(Integer id) {
        requireId(id);
        return transactions.run("buscar el caso " + id,
                connection -> loadCases(connection, id).stream().findFirst());
    }

    /**
     * Lista todos los casos guardados, ordenados por identificador.
     *
     * @return casos con todos sus pacientes; lista vacía si no hay ninguno
     * @throws DataAccessException si Oracle rechaza la operación o los datos guardados no son válidos
     */
    @Override
    public List<SimulationCase> findAll() {
        return transactions.run("listar los casos", connection -> loadCases(connection, null));
    }

    /** Lee un caso (si se da su id) o todos, con sus pacientes y sus síntomas. */
    private List<SimulationCase> loadCases(Connection connection, Integer id) throws SQLException {
        List<SimulationCase> cases = new ArrayList<>();
        List<Map<String, Object>> headers = readCases(connection, id);
        if (headers.isEmpty()) {
            return cases;
        }
        Map<Integer, Disease> catalog = new HashMap<>();
        diseases.findAll().forEach(disease -> catalog.put(disease.getId(), disease));
        Map<PatientKey, List<Symptom>> symptoms = readSymptoms(connection, id);
        Map<Integer, List<PatientProfile>> profiles = readPatients(connection, id, catalog, symptoms);
        for (Map<String, Object> header : headers) {
            int caseId = (Integer) header.get("id");
            cases.add(toCase(header, profiles.getOrDefault(caseId, List.of())));
        }
        return cases;
    }

    private static List<Map<String, Object>> readCases(Connection connection, Integer id) throws SQLException {
        List<Map<String, Object>> headers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(id == null ? SELECT_CASES : SELECT_CASE)) {
            if (id != null) {
                statement.setInt(1, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Map<String, Object> header = new HashMap<>();
                    header.put("id", rows.getInt("id"));
                    header.put("semilla", rows.getLong("semilla"));
                    header.put("escenario", rows.getString("escenario"));
                    header.put("cantidad_pacientes", rows.getInt("cantidad_pacientes"));
                    header.put("duracion_minutos", rows.getInt("duracion_minutos"));
                    headers.add(header);
                }
            }
        }
        return headers;
    }

    private static Map<PatientKey, List<Symptom>> readSymptoms(Connection connection, Integer id)
            throws SQLException {
        Map<PatientKey, List<Symptom>> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(id == null ? SELECT_SYMPTOMS : SELECT_SYMPTOMS_OF)) {
            if (id != null) {
                statement.setInt(1, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    PatientKey key = new PatientKey(rows.getInt("id_caso"), rows.getInt("id_paciente"));
                    result.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(parse(Symptom.class, rows.getString("sintoma"), key));
                }
            }
        }
        return result;
    }

    private static Map<Integer, List<PatientProfile>> readPatients(Connection connection, Integer id,
                                                                   Map<Integer, Disease> catalog,
                                                                   Map<PatientKey, List<Symptom>> symptoms)
            throws SQLException {
        Map<Integer, List<PatientProfile>> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(id == null ? SELECT_PATIENTS : SELECT_PATIENTS_OF)) {
            if (id != null) {
                statement.setInt(1, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    PatientKey key = new PatientKey(rows.getInt("id_caso"), rows.getInt("id_paciente"));
                    result.computeIfAbsent(key.caseId(), k -> new ArrayList<>())
                            .add(toProfile(rows, key, catalog, symptoms.getOrDefault(key, List.of())));
                }
            }
        }
        return result;
    }

    /** Arma el paciente de la fila actual; si lo guardado no es válido, lo informa como error de datos. */
    private static PatientProfile toProfile(ResultSet row, PatientKey key, Map<Integer, Disease> catalog,
                                            List<Symptom> symptoms) throws SQLException {
        Disease disease = catalog.get(row.getInt("id_enfermedad"));
        if (disease == null) {
            throw new DataAccessException("El paciente " + key.patientId() + " del caso " + key.caseId()
                    + " usa la enfermedad " + row.getInt("id_enfermedad") + ", que no está guardada.");
        }
        try {
            return new PatientProfile(key.patientId(), row.getInt("edad"),
                    parse(Sex.class, row.getString("sexo"), key),
                    parse(BloodType.class, row.getString("tipo_sangre"), key),
                    disease, symptoms, row.getInt("minuto_llegada"), row.getDouble("salud_inicial"),
                    row.getInt("irrecuperable") == 1);
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Los datos guardados del paciente " + key.patientId() + " del caso "
                    + key.caseId() + " no son válidos: " + e.getMessage(), e);
        }
    }

    private static SimulationCase toCase(Map<String, Object> header, List<PatientProfile> profiles) {
        int caseId = (Integer) header.get("id");
        try {
            CaseConfig config = new CaseConfig((Long) header.get("semilla"),
                    Scenario.valueOf((String) header.get("escenario")),
                    (Integer) header.get("cantidad_pacientes"), (Integer) header.get("duracion_minutos"));
            return new SimulationCase(config, profiles);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DataAccessException("Los datos guardados del caso " + caseId + " no son válidos: "
                    + e.getMessage(), e);
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, PatientKey key) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DataAccessException("El paciente " + key.patientId() + " del caso " + key.caseId()
                    + " tiene un valor desconocido (" + name + ") para " + type.getSimpleName() + ".", e);
        }
    }

    // ---------- cambiar y borrar ----------

    /**
     * No está permitido: los casos guardados no se modifican.
     *
     * @param simulationCase no se usa
     * @throws UnsupportedOperationException siempre
     */
    @Override
    public void update(SimulationCase simulationCase) {
        throw new UnsupportedOperationException(
                "Los casos guardados no se modifican: guarde un caso nuevo con otra configuración.");
    }

    /**
     * Borra un caso con sus pacientes, sus síntomas y los resultados de sus ejecuciones.
     *
     * @param id identificador del caso
     * @throws IllegalArgumentException si el identificador es nulo
     * @throws DataAccessException      si no existe o si Oracle rechaza la operación
     */
    @Override
    public void deleteById(Integer id) {
        requireId(id);
        transactions.run("borrar el caso " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_CASE)) {
                statement.setInt(1, id);
                if (statement.executeUpdate() == 0) {
                    throw new DataAccessException("No existe el caso " + id + ".");
                }
            }
            return null;
        });
    }

    private static void requireCase(SimulationCase simulationCase) {
        if (simulationCase == null) {
            throw new IllegalArgumentException("El caso es obligatorio.");
        }
    }

    private static void requireId(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("El identificador del caso es obligatorio.");
        }
    }
}