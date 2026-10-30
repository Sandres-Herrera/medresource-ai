package com.medresource.persistence;

import com.medresource.model.CaseConfig;
import com.medresource.model.Disease;
import com.medresource.model.PatientProfile;
import com.medresource.model.SimulationCase;
import com.medresource.model.Symptom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Guarda y lee en Oracle los casos de simulación, con todos sus pacientes.
 *
 * <p>Un caso ocupa tres tablas: {@code sim_case} (la configuración), {@code case_patient} (los pacientes)
 * y {@code case_patient_symptom} (los síntomas de cada paciente, en orden). Cada operación se hace en una
 * sola transacción. El identificador del caso lo genera Oracle. Los pacientes guardan solo el número de su
 * enfermedad; al leer, la enfermedad se toma del {@link GenericDao} de enfermedades que recibe el DAO.</p>
 *
 * <p>Los casos no se modifican: con la misma configuración el generador produce siempre los mismos
 * pacientes, así que {@link (SimulationCase)} no está permitido.</p>
 */
public class OracleCaseDao {

    static final String INSERT_CASE =
            "INSERT INTO sim_case (seed, scenario, patient_count, duration_minutes) VALUES (?, ?, ?, ?)";
    static final String INSERT_PATIENT = "INSERT INTO case_patient (case_id, patient_id, age, sex, blood_type, "
            + "disease_id, arrival_minute, initial_health, irrecoverable) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String INSERT_SYMPTOM =
            "INSERT INTO case_patient_symptom (case_id, patient_id, symptom_order, symptom) VALUES (?, ?, ?, ?)";
    static final String DELETE_CASE = "DELETE FROM sim_case WHERE id = ?";

    static final String SELECT_CASES =
            "SELECT id, seed, scenario, patient_count, duration_minutes FROM sim_case ORDER BY id";
    static final String SELECT_CASE =
            "SELECT id, seed, scenario, patient_count, duration_minutes FROM sim_case WHERE id = ?";
    static final String SELECT_ID_BY_CONFIG = "SELECT id FROM sim_case WHERE seed = ? AND scenario = ? "
            + "AND patient_count = ? AND duration_minutes = ?";
    static final String SELECT_PATIENTS = "SELECT case_id, patient_id, age, sex, blood_type, disease_id, "
            + "arrival_minute, initial_health, irrecoverable FROM case_patient ORDER BY case_id, patient_id";
    static final String SELECT_PATIENTS_OF = "SELECT case_id, patient_id, age, sex, blood_type, disease_id, "
            + "arrival_minute, initial_health, irrecoverable FROM case_patient WHERE case_id = ? ORDER BY patient_id";
    static final String SELECT_SYMPTOMS = "SELECT case_id, patient_id, symptom FROM case_patient_symptom "
            + "ORDER BY case_id, patient_id, symptom_order";
    static final String SELECT_SYMPTOMS_OF = "SELECT case_id, patient_id, symptom FROM case_patient_symptom "
            + "WHERE case_id = ? ORDER BY patient_id, symptom_order";

    /** Nombre de la columna que Oracle genera al insertar un caso. */
    private static final String GENERATED_COLUMN = "ID";

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

    private static void requireCase(SimulationCase simulationCase) {
        if (simulationCase == null) {
            throw new IllegalArgumentException("El caso es obligatorio.");
        }
    }
}