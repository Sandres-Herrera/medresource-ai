package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.CauseOfDeath;
import com.medresource.model.CompatibilityLevel;
import com.medresource.model.Disease;
import com.medresource.model.EventType;
import com.medresource.model.HospitalConfig;
import com.medresource.model.PatientOutcome;
import com.medresource.model.PatientStatus;
import com.medresource.model.RunRecord;
import com.medresource.model.Scenario;
import com.medresource.model.Sex;
import com.medresource.model.SimulationEvent;
import com.medresource.model.SimulationResult;
import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Guarda y lee en Oracle las ejecuciones de la simulación, con el resultado de cada paciente y el
 * registro de eventos.
 *
 * <p>Una ejecución ocupa cuatro tablas: {@code ejecucion} (la estrategia, la duración y los siete recursos
 * del hospital), {@code ejecucion_medico_extra} (los médicos extra del hospital, en orden),
 * {@code resultado_paciente} (cómo terminó cada paciente) y {@code evento} (los eventos, en orden). Si la
 * ejecución se basa en otra, debe ser del mismo caso. La configuración del caso no se repite: se lee de
 * {@code caso}. Cada operación se hace en una sola transacción y el identificador de la ejecución lo genera
 * Oracle. Los pacientes guardan solo el número de su enfermedad; al leer, la enfermedad se toma del
 * {@link GenericDao} de enfermedades que recibe el DAO.</p>
 *
 * <p>Las ejecuciones no se modifican: {@link #update(RunRecord)} no está permitido.</p>
 */
public class OracleResultDao implements ResultDao {

    static final String INSERT_RESULT = "INSERT INTO ejecucion (id_caso, estrategia, minutos_totales, camas, "
            + "camas_uci, quirofanos, medicos, enfermeros, unidades_sangre_por_tipo, capacidad_morgue, "
            + "id_ejecucion_base) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String INSERT_PATIENT = "INSERT INTO resultado_paciente (id_ejecucion, id_paciente, "
            + "codigo_paciente, edad, sexo, nivel_triage, id_enfermedad, minuto_llegada, minuto_inicio_atencion, "
            + "minuto_desenlace, estado, causa_muerte, nivel_atencion, irrecuperable, salud_final) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String INSERT_EVENT = "INSERT INTO evento (id_ejecucion, orden_evento, minuto_evento, tipo_evento, "
            + "codigo_paciente, codigo_recurso, descripcion) VALUES (?, ?, ?, ?, ?, ?, ?)";
    static final String INSERT_EXTRA_DOCTOR =
            "INSERT INTO ejecucion_medico_extra (id_ejecucion, orden_medico, especialidad) VALUES (?, ?, ?)";
    static final String DELETE_RESULT = "DELETE FROM ejecucion WHERE id = ?";

    static final String SELECT_BASE_CASE = "SELECT id_caso FROM ejecucion WHERE id = ?";
    static final String SELECT_CASE_CONFIG =
            "SELECT semilla, escenario, cantidad_pacientes, duracion_minutos FROM caso WHERE id = ?";
    static final String SELECT_RESULTS = "SELECT r.id, r.id_caso, r.estrategia, r.minutos_totales, r.camas, "
            + "r.camas_uci, r.quirofanos, r.medicos, r.enfermeros, r.unidades_sangre_por_tipo, r.capacidad_morgue, "
            + "r.id_ejecucion_base, c.semilla, c.escenario, c.cantidad_pacientes, c.duracion_minutos "
            + "FROM ejecucion r JOIN caso c ON c.id = r.id_caso ORDER BY r.id";
    static final String SELECT_RESULT = "SELECT r.id, r.id_caso, r.estrategia, r.minutos_totales, r.camas, "
            + "r.camas_uci, r.quirofanos, r.medicos, r.enfermeros, r.unidades_sangre_por_tipo, r.capacidad_morgue, "
            + "r.id_ejecucion_base, c.semilla, c.escenario, c.cantidad_pacientes, c.duracion_minutos "
            + "FROM ejecucion r JOIN caso c ON c.id = r.id_caso WHERE r.id = ?";
    static final String SELECT_IDS_BY_CASE = "SELECT id FROM ejecucion WHERE id_caso = ? ORDER BY id";
    static final String SELECT_PATIENTS = "SELECT id_ejecucion, id_paciente, codigo_paciente, edad, sexo, "
            + "nivel_triage, id_enfermedad, minuto_llegada, minuto_inicio_atencion, minuto_desenlace, estado, "
            + "causa_muerte, nivel_atencion, irrecuperable, salud_final FROM resultado_paciente "
            + "ORDER BY id_ejecucion, id_paciente";
    static final String SELECT_PATIENTS_OF = "SELECT id_ejecucion, id_paciente, codigo_paciente, edad, sexo, "
            + "nivel_triage, id_enfermedad, minuto_llegada, minuto_inicio_atencion, minuto_desenlace, estado, "
            + "causa_muerte, nivel_atencion, irrecuperable, salud_final FROM resultado_paciente "
            + "WHERE id_ejecucion = ? ORDER BY id_paciente";
    static final String SELECT_EVENTS = "SELECT id_ejecucion, minuto_evento, tipo_evento, codigo_paciente, "
            + "codigo_recurso, descripcion FROM evento ORDER BY id_ejecucion, orden_evento";
    static final String SELECT_EXTRA_DOCTORS = "SELECT id_ejecucion, especialidad FROM ejecucion_medico_extra "
            + "ORDER BY id_ejecucion, orden_medico";
    static final String SELECT_EXTRA_DOCTORS_OF = "SELECT id_ejecucion, especialidad FROM ejecucion_medico_extra "
            + "WHERE id_ejecucion = ? ORDER BY orden_medico";
    static final String SELECT_EVENTS_OF = "SELECT id_ejecucion, minuto_evento, tipo_evento, codigo_paciente, "
            + "codigo_recurso, descripcion FROM evento WHERE id_ejecucion = ? ORDER BY orden_evento";

    /** Nombre de la columna que Oracle genera al insertar una ejecución. */
    private static final String GENERATED_COLUMN = "ID";

    private final TransactionRunner transactions;
    private final GenericDao<Disease, Integer> diseases;

    /**
     * Crea el DAO.
     *
     * @param connections administrador de conexiones
     * @param diseases    acceso a las enfermedades, para armar los pacientes al leer una ejecución
     * @throws IllegalArgumentException si alguno es nulo
     */
    public OracleResultDao(ConnectionManager connections, GenericDao<Disease, Integer> diseases) {
        this.transactions = new TransactionRunner(connections);
        if (diseases == null) {
            throw new IllegalArgumentException("El acceso a datos de enfermedades es obligatorio.");
        }
        this.diseases = diseases;
    }

    // ---------- guardar ----------

    /**
     * Guarda una ejecución nueva con el resultado de cada paciente y todos sus eventos.
     *
     * @param run ejecución que se guarda
     * @return el identificador que Oracle le asignó
     * @throws IllegalArgumentException si la ejecución es nula
     * @throws DataAccessException      si el caso no existe, si el resultado no es del caso indicado (otra
     *                                  semilla, escenario, cantidad de pacientes o duración), si alguna
     *                                  enfermedad de los pacientes no está guardada, o si Oracle rechaza la
     *                                  operación
     */
    @Override
    public Integer save(RunRecord run) {
        requireRun(run);
        return transactions.run("guardar la ejecución de " + run.result().strategyName(), connection -> {
            requireSameCase(connection, run);
            requireBaseOfSameCase(connection, run);
            int resultId = insertResult(connection, run);
            insertExtraDoctors(connection, resultId, run.hospital().extraDoctors());
            insertPatients(connection, resultId, run.result().outcomes());
            insertEvents(connection, resultId, run.result().events());
            return resultId;
        });
    }

    /** Comprueba que el caso existe y que el resultado se obtuvo con su configuración. */
    private static void requireSameCase(Connection connection, RunRecord run) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_CASE_CONFIG)) {
            statement.setInt(1, run.caseId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new DataAccessException("No existe el caso " + run.caseId() + ".");
                }
                CaseConfig stored = readConfig(rows, "caso " + run.caseId());
                if (!stored.equals(run.result().config())) {
                    throw new DataAccessException("El resultado no es del caso " + run.caseId()
                            + ": su configuración es " + run.result().config() + " y la del caso es " + stored + ".");
                }
            }
        }
    }

    /** Si la ejecución se basa en otra, comprueba que esa otra existe y es del mismo caso. */
    private static void requireBaseOfSameCase(Connection connection, RunRecord run) throws SQLException {
        if (run.baseRunId() == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BASE_CASE)) {
            statement.setInt(1, run.baseRunId());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new DataAccessException("No existe la ejecución base " + run.baseRunId() + ".");
                }
                int baseCase = rows.getInt("id_caso");
                if (baseCase != run.caseId()) {
                    throw new DataAccessException("La ejecución base " + run.baseRunId() + " es del caso " + baseCase
                            + " y la nueva es del caso " + run.caseId()
                            + ": solo se puede basar en una ejecución del mismo caso.");
                }
            }
        }
    }

    private static int insertResult(Connection connection, RunRecord run) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_RESULT, new String[]{GENERATED_COLUMN})) {
            HospitalConfig hospital = run.hospital();
            statement.setInt(1, run.caseId());
            statement.setString(2, run.result().strategyName());
            statement.setInt(3, run.result().totalMinutes());
            statement.setInt(4, hospital.hospitalizationBeds());
            statement.setInt(5, hospital.icuBeds());
            statement.setInt(6, hospital.operatingRooms());
            statement.setInt(7, hospital.doctors());
            statement.setInt(8, hospital.nurses());
            statement.setInt(9, hospital.bloodUnitsPerType());
            statement.setInt(10, hospital.morgueCapacity());
            if (run.baseRunId() == null) {
                statement.setNull(11, Types.NUMERIC);
            } else {
                statement.setInt(11, run.baseRunId());
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Oracle no devolvió el identificador de la ejecución.");
                }
                return keys.getInt(1);
            }
        }
    }

    /** Guarda los médicos extra del hospital, en orden; si no hay, no hace nada. */
    private static void insertExtraDoctors(Connection connection, int resultId, List<Specialty> doctors)
            throws SQLException {
        if (doctors.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_EXTRA_DOCTOR)) {
            int order = 0;
            for (Specialty specialty : doctors) {
                statement.setInt(1, resultId);
                statement.setInt(2, order++);
                statement.setString(3, specialty.name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertPatients(Connection connection, int resultId, List<PatientOutcome> outcomes)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_PATIENT)) {
            for (PatientOutcome outcome : outcomes) {
                statement.setInt(1, resultId);
                statement.setInt(2, outcome.patientId());
                statement.setString(3, outcome.patientCode());
                statement.setInt(4, outcome.age());
                statement.setString(5, outcome.sex().name());
                statement.setInt(6, outcome.triage().getLevel());
                statement.setInt(7, outcome.disease().getId());
                statement.setInt(8, outcome.arrivalMinute());
                statement.setInt(9, outcome.treatmentStartMinute());
                statement.setInt(10, outcome.outcomeMinute());
                statement.setString(11, outcome.status().name());
                setNullableName(statement, 12, outcome.causeOfDeath());
                setNullableName(statement, 13, outcome.careLevel());
                statement.setInt(14, outcome.irrecoverable() ? 1 : 0);
                statement.setDouble(15, outcome.finalHealth());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertEvents(Connection connection, int resultId, List<SimulationEvent> events)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_EVENT)) {
            int order = 0;
            for (SimulationEvent event : events) {
                statement.setInt(1, resultId);
                statement.setInt(2, order++);
                statement.setInt(3, event.minute());
                statement.setString(4, event.type().name());
                statement.setString(5, event.patientCode());
                if (event.resourceCode() == null) {
                    statement.setNull(6, Types.VARCHAR);
                } else {
                    statement.setString(6, event.resourceCode());
                }
                statement.setString(7, event.description());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** Guarda el nombre de una enumeración, o NULL si no hay valor. */
    private static void setNullableName(PreparedStatement statement, int index, Enum<?> value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.name());
        }
    }

    private static CaseConfig readConfig(ResultSet row, String owner) throws SQLException {
        try {
            return new CaseConfig(row.getLong("semilla"), parse(Scenario.class, row.getString("escenario"), owner),
                    row.getInt("cantidad_pacientes"), row.getInt("duracion_minutos"));
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Los datos guardados del " + owner + " no son válidos: " + e.getMessage(), e);
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, String owner) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DataAccessException(owner + " tiene un valor desconocido (" + name + ") para "
                    + type.getSimpleName() + ".", e);
        }
    }

    // ---------- leer ----------

    /**
     * Busca las ejecuciones guardadas de un caso.
     *
     * @param caseId identificador del caso
     * @return identificadores de las ejecuciones, ordenados; lista vacía si el caso no tiene ninguna
     * @throws DataAccessException si Oracle rechaza la operación
     */
    @Override
    public List<Integer> findIdsByCase(int caseId) {
        return transactions.run("buscar las ejecuciones del caso " + caseId, connection -> {
            List<Integer> ids = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_IDS_BY_CASE)) {
                statement.setInt(1, caseId);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        ids.add(rows.getInt("id"));
                    }
                }
            }
            return ids;
        });
    }

    /**
     * Busca una ejecución por su identificador.
     *
     * @param id identificador buscado
     * @return la ejecución con todos sus pacientes y eventos, o vacío si no existe
     * @throws IllegalArgumentException si el identificador es nulo
     * @throws DataAccessException      si Oracle rechaza la operación o los datos guardados no son válidos
     */
    @Override
    public Optional<RunRecord> findById(Integer id) {
        requireId(id);
        return transactions.run("buscar la ejecución " + id,
                connection -> loadRuns(connection, id).stream().findFirst());
    }

    /**
     * Lista todas las ejecuciones guardadas, ordenadas por identificador. Cada una trae todos sus
     * pacientes y eventos, así que con muchas ejecuciones conviene pedir una por una con
     * {@link #findIdsByCase(int)} y {@link #findById(Integer)}.
     *
     * @return ejecuciones guardadas; lista vacía si no hay ninguna
     * @throws DataAccessException si Oracle rechaza la operación o los datos guardados no son válidos
     */
    @Override
    public List<RunRecord> findAll() {
        return transactions.run("listar las ejecuciones", connection -> loadRuns(connection, null));
    }

    /** Lee una ejecución (si se da su id) o todas, con sus pacientes y sus eventos. */
    private List<RunRecord> loadRuns(Connection connection, Integer id) throws SQLException {
        List<RunRecord> runs = new ArrayList<>();
        List<Map<String, Object>> headers = readHeaders(connection, id);
        if (headers.isEmpty()) {
            return runs;
        }
        Map<Integer, Disease> catalog = new HashMap<>();
        diseases.findAll().forEach(disease -> catalog.put(disease.getId(), disease));
        Map<Integer, List<PatientOutcome>> outcomes = readPatients(connection, id, catalog);
        Map<Integer, List<SimulationEvent>> events = readEvents(connection, id);
        Map<Integer, List<Specialty>> extraDoctors = readExtraDoctors(connection, id);
        for (Map<String, Object> header : headers) {
            int resultId = (Integer) header.get("id");
            runs.add(toRun(header, extraDoctors.getOrDefault(resultId, List.of()),
                    outcomes.getOrDefault(resultId, List.of()), events.getOrDefault(resultId, List.of())));
        }
        return runs;
    }

    private static List<Map<String, Object>> readHeaders(Connection connection, Integer id) throws SQLException {
        List<Map<String, Object>> headers = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(id == null ? SELECT_RESULTS : SELECT_RESULT)) {
            if (id != null) {
                statement.setInt(1, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Map<String, Object> header = new HashMap<>();
                    for (String column : List.of("id", "id_caso", "minutos_totales", "camas", "camas_uci",
                            "quirofanos", "medicos", "enfermeros", "unidades_sangre_por_tipo", "capacidad_morgue",
                            "cantidad_pacientes", "duracion_minutos")) {
                        header.put(column, rows.getInt(column));
                    }
                    header.put("estrategia", rows.getString("estrategia"));
                    header.put("semilla", rows.getLong("semilla"));
                    header.put("escenario", rows.getString("escenario"));
                    Object base = rows.getObject("id_ejecucion_base");
                    header.put("id_ejecucion_base", base == null ? null : ((Number) base).intValue());
                    headers.add(header);
                }
            }
        }
        return headers;
    }

    private static Map<Integer, List<PatientOutcome>> readPatients(Connection connection, Integer id,
                                                                   Map<Integer, Disease> catalog)
            throws SQLException {
        Map<Integer, List<PatientOutcome>> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(id == null ? SELECT_PATIENTS : SELECT_PATIENTS_OF)) {
            if (id != null) {
                statement.setInt(1, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int resultId = rows.getInt("id_ejecucion");
                    result.computeIfAbsent(resultId, key -> new ArrayList<>()).add(toOutcome(rows, resultId, catalog));
                }
            }
        }
        return result;
    }

    private static Map<Integer, List<SimulationEvent>> readEvents(Connection connection, Integer id)
            throws SQLException {
        Map<Integer, List<SimulationEvent>> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(id == null ? SELECT_EVENTS : SELECT_EVENTS_OF)) {
            if (id != null) {
                statement.setInt(1, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int resultId = rows.getInt("id_ejecucion");
                    result.computeIfAbsent(resultId, key -> new ArrayList<>()).add(toEvent(rows, resultId));
                }
            }
        }
        return result;
    }

    private static Map<Integer, List<Specialty>> readExtraDoctors(Connection connection, Integer id)
            throws SQLException {
        Map<Integer, List<Specialty>> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                id == null ? SELECT_EXTRA_DOCTORS : SELECT_EXTRA_DOCTORS_OF)) {
            if (id != null) {
                statement.setInt(1, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    int resultId = rows.getInt("id_ejecucion");
                    Specialty specialty = parse(Specialty.class, rows.getString("especialidad"),
                            "Un médico extra de la ejecución " + resultId);
                    result.computeIfAbsent(resultId, key -> new ArrayList<>()).add(specialty);
                }
            }
        }
        return result;
    }

    /** Arma el resultado del paciente de la fila actual; si lo guardado no es válido, lo informa como error de datos. */
    private static PatientOutcome toOutcome(ResultSet row, int resultId, Map<Integer, Disease> catalog)
            throws SQLException {
        int patientId = row.getInt("id_paciente");
        String owner = "El paciente " + patientId + " de la ejecución " + resultId;
        Disease disease = catalog.get(row.getInt("id_enfermedad"));
        if (disease == null) {
            throw new DataAccessException(owner + " usa la enfermedad " + row.getInt("id_enfermedad")
                    + ", que no está guardada.");
        }
        try {
            return new PatientOutcome(patientId, row.getString("codigo_paciente"), row.getInt("edad"),
                    parse(Sex.class, row.getString("sexo"), owner), triage(row.getInt("nivel_triage"), owner), disease,
                    row.getInt("minuto_llegada"), row.getInt("minuto_inicio_atencion"), row.getInt("minuto_desenlace"),
                    parse(PatientStatus.class, row.getString("estado"), owner),
                    parseOrNull(CauseOfDeath.class, row.getString("causa_muerte"), owner),
                    parseOrNull(CompatibilityLevel.class, row.getString("nivel_atencion"), owner),
                    row.getInt("irrecuperable") == 1, row.getDouble("salud_final"));
        } catch (IllegalArgumentException e) {
            throw new DataAccessException(owner + " no tiene datos válidos: " + e.getMessage(), e);
        }
    }

    private static SimulationEvent toEvent(ResultSet row, int resultId) throws SQLException {
        String owner = "Un evento de la ejecución " + resultId;
        try {
            return new SimulationEvent(row.getInt("minuto_evento"),
                    parse(EventType.class, row.getString("tipo_evento"), owner), row.getString("codigo_paciente"),
                    row.getString("codigo_recurso"), row.getString("descripcion"));
        } catch (IllegalArgumentException e) {
            throw new DataAccessException(owner + " no tiene datos válidos: " + e.getMessage(), e);
        }
    }

    private static RunRecord toRun(Map<String, Object> header, List<Specialty> extraDoctors,
                                   List<PatientOutcome> outcomes, List<SimulationEvent> events) {
        int resultId = (Integer) header.get("id");
        String owner = "la ejecución " + resultId;
        try {
            CaseConfig config = new CaseConfig((Long) header.get("semilla"),
                    parse(Scenario.class, (String) header.get("escenario"), owner),
                    (Integer) header.get("cantidad_pacientes"), (Integer) header.get("duracion_minutos"));
            HospitalConfig hospital = new HospitalConfig((Integer) header.get("camas"),
                    (Integer) header.get("camas_uci"), (Integer) header.get("quirofanos"),
                    (Integer) header.get("medicos"), (Integer) header.get("enfermeros"),
                    (Integer) header.get("unidades_sangre_por_tipo"), (Integer) header.get("capacidad_morgue"),
                    extraDoctors);
            SimulationResult result = new SimulationResult((String) header.get("estrategia"), config,
                    (Integer) header.get("minutos_totales"), outcomes, events);
            return new RunRecord((Integer) header.get("id_caso"), hospital, result,
                    (Integer) header.get("id_ejecucion_base"));
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Los datos guardados de " + owner + " no son válidos: " + e.getMessage(), e);
        }
    }

    private static TriageLevel triage(int level, String owner) {
        return Arrays.stream(TriageLevel.values()).filter(candidate -> candidate.getLevel() == level).findFirst()
                .orElseThrow(() -> new DataAccessException(owner + " tiene un triage desconocido (" + level + ")."));
    }

    private static <E extends Enum<E>> E parseOrNull(Class<E> type, String name, String owner) {
        return name == null ? null : parse(type, name, owner);
    }

    // ---------- cambiar y borrar ----------

    /**
     * No está permitido: las ejecuciones guardadas no se modifican.
     *
     * @param run no se usa
     * @throws UnsupportedOperationException siempre
     */
    @Override
    public void update(RunRecord run) {
        throw new UnsupportedOperationException(
                "Las ejecuciones guardadas no se modifican: guarde una ejecución nueva.");
    }

    /**
     * Borra una ejecución con los resultados de sus pacientes y sus eventos.
     *
     * @param id identificador de la ejecución
     * @throws IllegalArgumentException si el identificador es nulo
     * @throws DataAccessException      si no existe o si Oracle rechaza la operación
     */
    @Override
    public void deleteById(Integer id) {
        requireId(id);
        transactions.run("borrar la ejecución " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_RESULT)) {
                statement.setInt(1, id);
                if (statement.executeUpdate() == 0) {
                    throw new DataAccessException("No existe la ejecución " + id + ".");
                }
            }
            return null;
        });
    }

    private static void requireRun(RunRecord run) {
        if (run == null) {
            throw new IllegalArgumentException("La ejecución es obligatoria.");
        }
    }

    private static void requireId(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("El identificador de la ejecución es obligatorio.");
        }
    }
}