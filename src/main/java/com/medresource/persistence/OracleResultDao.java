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
import java.sql.CallableStatement;
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

    /** {@code pr_leer_config_caso(id_caso, cursor)}: la configuración del caso. */
    static final String SELECT_CASE_CONFIG = "{call pr_leer_config_caso(?, ?)}";
    /** {@code pr_leer_caso_de_ejecucion(id, cursor)}: el caso de una ejecución. */
    static final String SELECT_BASE_CASE = "{call pr_leer_caso_de_ejecucion(?, ?)}";
    /** {@code pr_crear_ejecucion(...)}: los 11 datos y, al final, el identificador que sale. */
    static final String INSERT_RESULT = "{call pr_crear_ejecucion(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    /** {@code pr_agregar_medico_extra(ejecucion, orden, especialidad)}. */
    static final String INSERT_EXTRA_DOCTOR = "{call pr_agregar_medico_extra(?, ?, ?)}";
    /** {@code pr_agregar_resultado_paciente(...)}: los 15 datos del resultado de un paciente. */
    static final String INSERT_PATIENT =
            "{call pr_agregar_resultado_paciente(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    /** {@code pr_agregar_evento(...)}: los 7 datos de un evento. */
    static final String INSERT_EVENT = "{call pr_agregar_evento(?, ?, ?, ?, ?, ?, ?)}";
    /** {@code pr_borrar_ejecucion(id, borradas)}: el segundo parámetro dice cuántas filas se borraron. */
    static final String DELETE_RESULT = "{call pr_borrar_ejecucion(?, ?)}";
    /** {@code pr_leer_ejecuciones(id, cursor)}: si el id es nulo, devuelve todas. */
    static final String SELECT_RESULTS = "{call pr_leer_ejecuciones(?, ?)}";
    /** {@code pr_ids_ejecuciones_de_caso(id_caso, cursor)}. */
    static final String SELECT_IDS_BY_CASE = "{call pr_ids_ejecuciones_de_caso(?, ?)}";
    /** {@code pr_leer_resultados_pacientes(id_ejecucion, cursor)}: si el id es nulo, devuelve todos. */
    static final String SELECT_PATIENTS = "{call pr_leer_resultados_pacientes(?, ?)}";
    /** {@code pr_leer_eventos(id_ejecucion, cursor)}: si el id es nulo, devuelve todos. */
    static final String SELECT_EVENTS = "{call pr_leer_eventos(?, ?)}";
    /** {@code pr_leer_medicos_extra(id_ejecucion, cursor)}: si el id es nulo, devuelve todos. */
    static final String SELECT_EXTRA_DOCTORS = "{call pr_leer_medicos_extra(?, ?)}";

    /** Posiciones de los parámetros de salida y de los cursores. */
    private static final int RESULT_ID_POSITION = 12;
    private static final int DELETED_COUNT_POSITION = 2;
    private static final int CURSOR_POSITION = 2;

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
        try (CallableStatement call = connection.prepareCall(SELECT_CASE_CONFIG)) {
            call.setInt(1, run.caseId());
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
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
        try (CallableStatement call = connection.prepareCall(SELECT_BASE_CASE)) {
            call.setInt(1, run.baseRunId());
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
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
        try (CallableStatement call = connection.prepareCall(INSERT_RESULT)) {
            HospitalConfig hospital = run.hospital();
            call.setInt(1, run.caseId());
            call.setString(2, run.result().strategyName());
            call.setInt(3, run.result().totalMinutes());
            call.setInt(4, hospital.hospitalizationBeds());
            call.setInt(5, hospital.icuBeds());
            call.setInt(6, hospital.operatingRooms());
            call.setInt(7, hospital.doctors());
            call.setInt(8, hospital.nurses());
            call.setInt(9, hospital.bloodUnitsPerType());
            call.setInt(10, hospital.morgueCapacity());
            if (run.baseRunId() == null) {
                call.setNull(11, Types.NUMERIC);
            } else {
                call.setInt(11, run.baseRunId());
            }
            call.registerOutParameter(RESULT_ID_POSITION, Types.INTEGER);
            call.execute();
            return call.getInt(RESULT_ID_POSITION);
        }
    }

    /** Guarda los médicos extra del hospital, en orden; si no hay, no hace nada. */
    private static void insertExtraDoctors(Connection connection, int resultId, List<Specialty> doctors)
            throws SQLException {
        if (doctors.isEmpty()) {
            return;
        }
        try (CallableStatement call = connection.prepareCall(INSERT_EXTRA_DOCTOR)) {
            int order = 0;
            for (Specialty specialty : doctors) {
                call.setInt(1, resultId);
                call.setInt(2, order++);
                call.setString(3, specialty.name());
                call.addBatch();
            }
            call.executeBatch();
        }
    }

    private static void insertPatients(Connection connection, int resultId, List<PatientOutcome> outcomes)
            throws SQLException {
        try (CallableStatement call = connection.prepareCall(INSERT_PATIENT)) {
            for (PatientOutcome outcome : outcomes) {
                call.setInt(1, resultId);
                call.setInt(2, outcome.patientId());
                call.setString(3, outcome.patientCode());
                call.setInt(4, outcome.age());
                call.setString(5, outcome.sex().name());
                call.setInt(6, outcome.triage().getLevel());
                call.setInt(7, outcome.disease().getId());
                call.setInt(8, outcome.arrivalMinute());
                call.setInt(9, outcome.treatmentStartMinute());
                call.setInt(10, outcome.outcomeMinute());
                call.setString(11, outcome.status().name());
                setNullableName(call, 12, outcome.causeOfDeath());
                setNullableName(call, 13, outcome.careLevel());
                call.setInt(14, outcome.irrecoverable() ? 1 : 0);
                call.setDouble(15, outcome.finalHealth());
                call.addBatch();
            }
            call.executeBatch();
        }
    }

    private static void insertEvents(Connection connection, int resultId, List<SimulationEvent> events)
            throws SQLException {
        try (CallableStatement call = connection.prepareCall(INSERT_EVENT)) {
            int order = 0;
            for (SimulationEvent event : events) {
                call.setInt(1, resultId);
                call.setInt(2, order++);
                call.setInt(3, event.minute());
                call.setString(4, event.type().name());
                call.setString(5, event.patientCode());
                if (event.resourceCode() == null) {
                    call.setNull(6, Types.VARCHAR);
                } else {
                    call.setString(6, event.resourceCode());
                }
                call.setString(7, event.description());
                call.addBatch();
            }
            call.executeBatch();
        }
    }

    /** Guarda el nombre de una enumeración, o NULL si no hay valor. */
    private static void setNullableName(CallableStatement call, int index, Enum<?> value) throws SQLException {
        if (value == null) {
            call.setNull(index, Types.VARCHAR);
        } else {
            call.setString(index, value.name());
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
            try (CallableStatement call = connection.prepareCall(SELECT_IDS_BY_CASE)) {
                call.setInt(1, caseId);
                call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
                call.execute();
                try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
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
        try (CallableStatement call = connection.prepareCall(SELECT_RESULTS)) {
            setOptionalId(call, id);
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
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
        try (CallableStatement call = connection.prepareCall(SELECT_PATIENTS)) {
            setOptionalId(call, id);
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
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
        try (CallableStatement call = connection.prepareCall(SELECT_EVENTS)) {
            setOptionalId(call, id);
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
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
        try (CallableStatement call = connection.prepareCall(SELECT_EXTRA_DOCTORS)) {
            setOptionalId(call, id);
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
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

    /** Pone el identificador del filtro, o NULL si se quieren todas. */
    private static void setOptionalId(CallableStatement call, Integer id) throws SQLException {
        if (id == null) {
            call.setNull(1, Types.INTEGER);
        } else {
            call.setInt(1, id);
        }
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
            try (CallableStatement call = connection.prepareCall(DELETE_RESULT)) {
                call.setInt(1, id);
                call.registerOutParameter(DELETED_COUNT_POSITION, Types.INTEGER);
                call.execute();
                if (call.getInt(DELETED_COUNT_POSITION) == 0) {
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
