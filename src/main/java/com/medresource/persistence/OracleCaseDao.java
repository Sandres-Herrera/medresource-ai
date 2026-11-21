package com.medresource.persistence;

import com.medresource.model.BloodType;
import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.PatientProfile;
import com.medresource.model.Scenario;
import com.medresource.model.Sex;
import com.medresource.model.SimulationCase;
import com.medresource.model.Symptom;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    /** {@code pr_crear_caso(semilla, escenario, cantidad, duracion, id)}: el último parámetro sale. */
    static final String INSERT_CASE = "{call pr_crear_caso(?, ?, ?, ?, ?)}";
    /** {@code pr_agregar_paciente(...)}: guarda un paciente y sus síntomas, separados por coma. */
    static final String INSERT_PATIENT = "{call pr_agregar_paciente(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    /** {@code pr_borrar_caso(id, borrados)}: el segundo parámetro dice cuántas filas se borraron. */
    static final String DELETE_CASE = "{call pr_borrar_caso(?, ?)}";
    /** {@code pr_buscar_id_caso(semilla, escenario, cantidad, duracion, id)}: el id sale nulo si no existe. */
    static final String SELECT_ID_BY_CONFIG = "{call pr_buscar_id_caso(?, ?, ?, ?, ?)}";
    /** {@code pr_leer_casos(id, cursor)}: si el id es nulo, devuelve todos. */
    static final String SELECT_CASES = "{call pr_leer_casos(?, ?)}";
    /** {@code pr_leer_pacientes(id_caso, cursor)}: si el id es nulo, devuelve los de todos los casos. */
    static final String SELECT_PATIENTS = "{call pr_leer_pacientes(?, ?)}";
    /** {@code pr_leer_sintomas(id_caso, cursor)}: si el id es nulo, devuelve los de todos los casos. */
    static final String SELECT_SYMPTOMS = "{call pr_leer_sintomas(?, ?)}";

    /** Posiciones de los parámetros de salida y de los cursores. */
    private static final int SAVED_ID_POSITION = 5;
    private static final int FOUND_ID_POSITION = 5;
    private static final int DELETED_COUNT_POSITION = 2;
    private static final int CURSOR_POSITION = 2;

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
     * Guarda un caso nuevo con todos sus pacientes y sus síntomas, en una sola transacción.
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
        try (CallableStatement call = connection.prepareCall(INSERT_CASE)) {
            call.setLong(1, config.seed());
            call.setString(2, config.scenario().name());
            call.setInt(3, config.patientCount());
            call.setInt(4, config.durationMinutes());
            call.registerOutParameter(SAVED_ID_POSITION, Types.INTEGER);
            call.execute();
            return call.getInt(SAVED_ID_POSITION);
        }
    }

    private static void insertPatients(Connection connection, int caseId, List<PatientProfile> profiles)
            throws SQLException {
        try (CallableStatement call = connection.prepareCall(INSERT_PATIENT)) {
            for (PatientProfile profile : profiles) {
                call.setInt(1, caseId);
                call.setInt(2, profile.id());
                call.setInt(3, profile.age());
                call.setString(4, profile.sex().name());
                call.setString(5, profile.bloodType().name());
                call.setInt(6, profile.disease().getId());
                call.setInt(7, profile.arrivalMinute());
                call.setDouble(8, profile.initialHealth());
                call.setInt(9, profile.irrecoverable() ? 1 : 0);
                call.setString(10, profile.symptoms().stream().map(Symptom::name).collect(Collectors.joining(",")));
                call.execute();
            }
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
            try (CallableStatement call = connection.prepareCall(SELECT_ID_BY_CONFIG)) {
                call.setLong(1, config.seed());
                call.setString(2, config.scenario().name());
                call.setInt(3, config.patientCount());
                call.setInt(4, config.durationMinutes());
                call.registerOutParameter(FOUND_ID_POSITION, Types.INTEGER);
                call.execute();
                Object id = call.getObject(FOUND_ID_POSITION);
                return id == null ? Optional.<Integer>empty() : Optional.of(((Number) id).intValue());
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
        try (CallableStatement call = connection.prepareCall(SELECT_CASES)) {
            setOptionalId(call, id);
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
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
        try (CallableStatement call = connection.prepareCall(SELECT_SYMPTOMS)) {
            setOptionalId(call, id);
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
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
        try (CallableStatement call = connection.prepareCall(SELECT_PATIENTS)) {
            setOptionalId(call, id);
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
                while (rows.next()) {
                    PatientKey key = new PatientKey(rows.getInt("id_caso"), rows.getInt("id_paciente"));
                    result.computeIfAbsent(key.caseId(), k -> new ArrayList<>())
                            .add(toProfile(rows, key, catalog, symptoms.getOrDefault(key, List.of())));
                }
            }
        }
        return result;
    }

    /** Pone el identificador del filtro, o NULL si se quieren todos. */
    private static void setOptionalId(CallableStatement call, Integer id) throws SQLException {
        if (id == null) {
            call.setNull(1, Types.INTEGER);
        } else {
            call.setInt(1, id);
        }
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
            try (CallableStatement call = connection.prepareCall(DELETE_CASE)) {
                call.setInt(1, id);
                call.registerOutParameter(DELETED_COUNT_POSITION, Types.INTEGER);
                call.execute();
                if (call.getInt(DELETED_COUNT_POSITION) == 0) {
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
