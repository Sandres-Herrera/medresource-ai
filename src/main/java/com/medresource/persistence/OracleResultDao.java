package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.HospitalConfig;
import com.medresource.model.PatientOutcome;
import com.medresource.model.RunRecord;
import com.medresource.model.Scenario;
import com.medresource.model.SimulationEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * Guarda y lee en Oracle las ejecuciones de la simulación, con el resultado de cada paciente y el
 * registro de eventos.
 *
 * <p>Una ejecución ocupa tres tablas: {@code sim_result} (la estrategia, la duración y los siete recursos
 * del hospital), {@code patient_result} (cómo terminó cada paciente) y {@code sim_event} (los eventos, en
 * orden). La configuración del caso no se repite: se lee de {@code sim_case}. Cada operación se hace en una
 * sola transacción y el identificador de la ejecución lo genera Oracle. Los pacientes guardan solo el
 * número de su enfermedad; al leer, la enfermedad se toma del {@link GenericDao} de enfermedades que recibe
 * el DAO.</p>
 *
 * <p>Las ejecuciones no se modifican: {@link (RunRecord)} no está permitido.</p>
 */
public class OracleResultDao {

    static final String INSERT_RESULT = "INSERT INTO sim_result (case_id, strategy_name, total_minutes, beds, "
            + "icu_beds, operating_rooms, doctors, nurses, blood_units_per_type, morgue_capacity) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String INSERT_PATIENT = "INSERT INTO patient_result (result_id, patient_id, patient_code, age, "
            + "sex, triage, disease_id, arrival_minute, treatment_start_minute, outcome_minute, status, "
            + "cause_of_death, care_level, irrecoverable, final_health) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String INSERT_EVENT = "INSERT INTO sim_event (result_id, event_order, event_minute, event_type, "
            + "patient_code, resource_code, description) VALUES (?, ?, ?, ?, ?, ?, ?)";
    static final String DELETE_RESULT = "DELETE FROM sim_result WHERE id = ?";

    static final String SELECT_CASE_CONFIG =
            "SELECT seed, scenario, patient_count, duration_minutes FROM sim_case WHERE id = ?";
    static final String SELECT_RESULTS = "SELECT r.id, r.case_id, r.strategy_name, r.total_minutes, r.beds, "
            + "r.icu_beds, r.operating_rooms, r.doctors, r.nurses, r.blood_units_per_type, r.morgue_capacity, "
            + "c.seed, c.scenario, c.patient_count, c.duration_minutes "
            + "FROM sim_result r JOIN sim_case c ON c.id = r.case_id ORDER BY r.id";
    static final String SELECT_RESULT = "SELECT r.id, r.case_id, r.strategy_name, r.total_minutes, r.beds, "
            + "r.icu_beds, r.operating_rooms, r.doctors, r.nurses, r.blood_units_per_type, r.morgue_capacity, "
            + "c.seed, c.scenario, c.patient_count, c.duration_minutes "
            + "FROM sim_result r JOIN sim_case c ON c.id = r.case_id WHERE r.id = ?";
    static final String SELECT_IDS_BY_CASE = "SELECT id FROM sim_result WHERE case_id = ? ORDER BY id";
    static final String SELECT_PATIENTS = "SELECT result_id, patient_id, patient_code, age, sex, triage, "
            + "disease_id, arrival_minute, treatment_start_minute, outcome_minute, status, cause_of_death, "
            + "care_level, irrecoverable, final_health FROM patient_result ORDER BY result_id, patient_id";
    static final String SELECT_PATIENTS_OF = "SELECT result_id, patient_id, patient_code, age, sex, triage, "
            + "disease_id, arrival_minute, treatment_start_minute, outcome_minute, status, cause_of_death, "
            + "care_level, irrecoverable, final_health FROM patient_result WHERE result_id = ? ORDER BY patient_id";
    static final String SELECT_EVENTS = "SELECT result_id, event_minute, event_type, patient_code, resource_code, "
            + "description FROM sim_event ORDER BY result_id, event_order";
    static final String SELECT_EVENTS_OF = "SELECT result_id, event_minute, event_type, patient_code, "
            + "resource_code, description FROM sim_event WHERE result_id = ? ORDER BY event_order";

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
    public Integer save(RunRecord run) {
        requireRun(run);
        return transactions.run("guardar la ejecución de " + run.result().strategyName(), connection -> {
            requireSameCase(connection, run);
            int resultId = insertResult(connection, run);
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
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Oracle no devolvió el identificador de la ejecución.");
                }
                return keys.getInt(1);
            }
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
            return new CaseConfig(row.getLong("seed"), parse(Scenario.class, row.getString("scenario"), owner),
                    row.getInt("patient_count"), row.getInt("duration_minutes"));
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

    private static void requireRun(RunRecord run) {
        if (run == null) {
            throw new IllegalArgumentException("La ejecución es obligatoria.");
        }
    }
}