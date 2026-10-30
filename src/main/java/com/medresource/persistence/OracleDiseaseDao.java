package com.medresource.persistence;

import com.medresource.model.Disease;
import com.medresource.model.Specialty;
import com.medresource.model.Symptom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Guarda y lee las enfermedades del catálogo en Oracle.
 *
 * <p>Una enfermedad ocupa tres tablas: {@code disease} (sus datos), {@code disease_secondary_specialty}
 * (sus especialidades secundarias) y {@code disease_symptom} (sus síntomas, en orden). Cada operación
 * se hace en una sola transacción: o se guarda todo o no se guarda nada. Los valores de las
 * enumeraciones se guardan con el nombre de la constante y los booleanos como 0 o 1.</p>
 */
public class OracleDiseaseDao {

    static final String INSERT_DISEASE = "INSERT INTO disease (id, name, main_specialty, treatment, curable, "
            + "requires_blood, requires_icu, deterioration_rate, irreducible_mortality, min_duration_minutes, "
            + "max_duration_minutes, min_age, max_age, frequency) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String INSERT_SECONDARY =
            "INSERT INTO disease_secondary_specialty (disease_id, specialty) VALUES (?, ?)";
    static final String INSERT_SYMPTOM =
            "INSERT INTO disease_symptom (disease_id, symptom_order, symptom) VALUES (?, ?, ?)";
    static final String UPDATE_DISEASE = "UPDATE disease SET name = ?, main_specialty = ?, treatment = ?, "
            + "curable = ?, requires_blood = ?, requires_icu = ?, deterioration_rate = ?, "
            + "irreducible_mortality = ?, min_duration_minutes = ?, max_duration_minutes = ?, min_age = ?, "
            + "max_age = ?, frequency = ? WHERE id = ?";
    static final String DELETE_SECONDARY = "DELETE FROM disease_secondary_specialty WHERE disease_id = ?";
    static final String DELETE_SYMPTOMS = "DELETE FROM disease_symptom WHERE disease_id = ?";
    static final String DELETE_DISEASE = "DELETE FROM disease WHERE id = ?";

    static final String SELECT_DISEASES = "SELECT id, name, main_specialty, treatment, curable, requires_blood, "
            + "requires_icu, deterioration_rate, irreducible_mortality, min_duration_minutes, max_duration_minutes, "
            + "min_age, max_age, frequency FROM disease ORDER BY id";
    static final String SELECT_DISEASE = "SELECT id, name, main_specialty, treatment, curable, requires_blood, "
            + "requires_icu, deterioration_rate, irreducible_mortality, min_duration_minutes, max_duration_minutes, "
            + "min_age, max_age, frequency FROM disease WHERE id = ?";
    static final String SELECT_SECONDARY = "SELECT disease_id, specialty FROM disease_secondary_specialty "
            + "ORDER BY disease_id, specialty";
    static final String SELECT_SECONDARY_OF = "SELECT disease_id, specialty FROM disease_secondary_specialty "
            + "WHERE disease_id = ? ORDER BY specialty";
    static final String SELECT_SYMPTOMS = "SELECT disease_id, symptom FROM disease_symptom "
            + "ORDER BY disease_id, symptom_order";
    static final String SELECT_SYMPTOMS_OF = "SELECT disease_id, symptom FROM disease_symptom "
            + "WHERE disease_id = ? ORDER BY symptom_order";

    /** Trabajo que se hace con una conexión dentro de una transacción. */
    @FunctionalInterface
    private interface SqlWork<R> {

        R run(Connection connection) throws SQLException;
    }

    private final ConnectionManager connections;

    /**
     * Crea el DAO.
     *
     * @param connections administrador de conexiones
     * @throws IllegalArgumentException si el administrador es nulo
     */
    public OracleDiseaseDao(ConnectionManager connections) {
        if (connections == null) {
            throw new IllegalArgumentException("El administrador de conexiones es obligatorio.");
        }
        this.connections = connections;
    }

    // ---------- guardar ----------

    /**
     * Guarda una enfermedad nueva con sus especialidades secundarias y sus síntomas.
     *
     * @param disease enfermedad que se guarda; su identificador lo elige quien la crea
     * @return el identificador de la enfermedad
     * @throws IllegalArgumentException si la enfermedad es nula
     * @throws DataAccessException      si ya existe una enfermedad con ese identificador o ese nombre,
     *                                  o si Oracle rechaza la operación
     */
    public Integer save(Disease disease) {
        requireDisease(disease);
        return inTransaction("guardar la enfermedad " + disease.getId(), connection -> {
            insertDisease(connection, disease);
            insertChildren(connection, disease);
            return disease.getId();
        });
    }

    private static void insertDisease(Connection connection, Disease disease) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_DISEASE)) {
            statement.setInt(1, disease.getId());
            bindAttributes(statement, disease, 2);
            statement.executeUpdate();
        }
    }

    private static void insertChildren(Connection connection, Disease disease) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SECONDARY)) {
            for (Specialty specialty : disease.getSecondarySpecialties()) {
                statement.setInt(1, disease.getId());
                statement.setString(2, specialty.name());
                statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SYMPTOM)) {
            int order = 0;
            for (Symptom symptom : disease.getSymptoms()) {
                statement.setInt(1, disease.getId());
                statement.setInt(2, order++);
                statement.setString(3, symptom.name());
                statement.executeUpdate();
            }
        }
    }

    /** Pone los 13 datos de la enfermedad que no son el identificador, empezando en la posición dada. */
    private static void bindAttributes(PreparedStatement statement, Disease disease, int first) throws SQLException {
        int index = first;
        statement.setString(index++, disease.getName());
        statement.setString(index++, disease.getMainSpecialty().name());
        statement.setString(index++, disease.getTreatment().name());
        statement.setInt(index++, disease.isCurable() ? 1 : 0);
        statement.setInt(index++, disease.requiresBlood() ? 1 : 0);
        statement.setInt(index++, disease.requiresIcu() ? 1 : 0);
        statement.setDouble(index++, disease.getDeteriorationRate());
        statement.setDouble(index++, disease.getIrreducibleMortality());
        statement.setInt(index++, disease.getMinDurationMinutes());
        statement.setInt(index++, disease.getMaxDurationMinutes());
        statement.setInt(index++, disease.getMinAge());
        statement.setInt(index++, disease.getMaxAge());
        statement.setDouble(index, disease.getFrequency());
    }

    // ---------- transacciones ----------

    /**
     * Hace un trabajo en una conexión nueva y dentro de una transacción: si termina bien confirma
     * los cambios y si falla los deshace. Siempre cierra la conexión.
     */
    private <R> R inTransaction(String action, SqlWork<R> work) {
        try (Connection connection = connections.getConnection()) {
            connection.setAutoCommit(false);
            try {
                R result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo " + action + ": " + e.getMessage(), e);
        }
    }

    private static void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
    }

    private static void requireDisease(Disease disease) {
        if (disease == null) {
            throw new IllegalArgumentException("La enfermedad es obligatoria.");
        }
    }
}