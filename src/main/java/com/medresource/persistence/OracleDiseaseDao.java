package com.medresource.persistence;

import com.medresource.model.Disease;
import com.medresource.model.Specialty;
import com.medresource.model.Symptom;
import com.medresource.model.TreatmentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    // ---------- leer ----------

    /**
     * Busca una enfermedad por su identificador.
     *
     * @param id identificador buscado
     * @return la enfermedad con sus especialidades secundarias y sus síntomas, o vacío si no existe
     * @throws IllegalArgumentException si el identificador es nulo
     * @throws DataAccessException      si Oracle rechaza la operación o los datos guardados no son válidos
     */
    public Optional<Disease> findById(Integer id) {
        requireId(id);
        return inTransaction("buscar la enfermedad " + id,
                connection -> loadDiseases(connection, id).stream().findFirst());
    }

    /**
     * Lista todas las enfermedades, ordenadas por identificador.
     *
     * @return enfermedades guardadas; lista vacía si no hay ninguna
     * @throws DataAccessException si Oracle rechaza la operación o los datos guardados no son válidos
     */
    public List<Disease> findAll() {
        return inTransaction("listar las enfermedades", connection -> loadDiseases(connection, null));
    }

    /** Lee una enfermedad (si se da su id) o todas, con sus especialidades secundarias y sus síntomas. */
    private static List<Disease> loadDiseases(Connection connection, Integer id) throws SQLException {
        Map<Integer, Set<Specialty>> secondary = readSecondarySpecialties(connection, id);
        Map<Integer, List<Symptom>> symptoms = readSymptoms(connection, id);
        List<Disease> diseases = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(id == null ? SELECT_DISEASES : SELECT_DISEASE)) {
            if (id != null) {
                statement.setInt(1, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    diseases.add(toDisease(rows, secondary, symptoms));
                }
            }
        }
        return diseases;
    }

    private static Map<Integer, Set<Specialty>> readSecondarySpecialties(Connection connection, Integer id)
            throws SQLException {
        Map<Integer, Set<Specialty>> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(id == null ? SELECT_SECONDARY : SELECT_SECONDARY_OF)) {
            if (id != null) {
                statement.setInt(1, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.computeIfAbsent(rows.getInt("disease_id"), key -> EnumSet.noneOf(Specialty.class))
                            .add(parse(Specialty.class, rows.getString("specialty"), rows.getInt("disease_id")));
                }
            }
        }
        return result;
    }

    private static Map<Integer, List<Symptom>> readSymptoms(Connection connection, Integer id) throws SQLException {
        Map<Integer, List<Symptom>> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(id == null ? SELECT_SYMPTOMS : SELECT_SYMPTOMS_OF)) {
            if (id != null) {
                statement.setInt(1, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.computeIfAbsent(rows.getInt("disease_id"), key -> new ArrayList<>())
                            .add(parse(Symptom.class, rows.getString("symptom"), rows.getInt("disease_id")));
                }
            }
        }
        return result;
    }

    /** Arma la enfermedad de la fila actual; si lo guardado no es válido, lo informa como error de datos. */
    private static Disease toDisease(ResultSet row, Map<Integer, Set<Specialty>> secondary,
                                     Map<Integer, List<Symptom>> symptoms) throws SQLException {
        int id = row.getInt("id");
        try {
            return Disease.builder(id, row.getString("name"))
                    .mainSpecialty(parse(Specialty.class, row.getString("main_specialty"), id))
                    .secondarySpecialties(secondary.getOrDefault(id, Set.of()).toArray(new Specialty[0]))
                    .treatment(parse(TreatmentType.class, row.getString("treatment"), id))
                    .curable(row.getInt("curable") == 1)
                    .requiresBlood(row.getInt("requires_blood") == 1)
                    .requiresIcu(row.getInt("requires_icu") == 1)
                    .deteriorationRate(row.getDouble("deterioration_rate"))
                    .irreducibleMortality(row.getDouble("irreducible_mortality"))
                    .durationMinutes(row.getInt("min_duration_minutes"), row.getInt("max_duration_minutes"))
                    .ageRange(row.getInt("min_age"), row.getInt("max_age"))
                    .frequency(row.getDouble("frequency"))
                    .symptoms(symptoms.getOrDefault(id, List.of()).toArray(new Symptom[0]))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Los datos guardados de la enfermedad " + id + " no son válidos: "
                    + e.getMessage(), e);
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, int diseaseId) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DataAccessException("La enfermedad " + diseaseId + " tiene un valor desconocido ("
                    + name + ") para " + type.getSimpleName() + ".", e);
        }
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

    private static void requireId(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("El identificador de la enfermedad es obligatorio.");
        }
    }
}