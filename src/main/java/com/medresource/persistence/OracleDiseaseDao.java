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
 * <p>Una enfermedad ocupa tres tablas: {@code enfermedad} (sus datos), {@code enfermedad_especialidad_sec}
 * (sus especialidades secundarias) y {@code enfermedad_sintoma} (sus síntomas, en orden). Cada operación
 * se hace en una sola transacción: o se guarda todo o no se guarda nada. Los valores de las
 * enumeraciones se guardan con el nombre de la constante y los booleanos como 0 o 1.</p>
 */
public class OracleDiseaseDao implements GenericDao<Disease, Integer> {

    static final String INSERT_DISEASE = "INSERT INTO enfermedad (id, nombre, especialidad_principal, tratamiento, "
            + "curable, requiere_sangre, requiere_uci, tasa_deterioro, mortalidad_irreducible, duracion_min_minutos, "
            + "duracion_max_minutos, edad_minima, edad_maxima, frecuencia) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String INSERT_SECONDARY =
            "INSERT INTO enfermedad_especialidad_sec (id_enfermedad, especialidad) VALUES (?, ?)";
    static final String INSERT_SYMPTOM =
            "INSERT INTO enfermedad_sintoma (id_enfermedad, orden_sintoma, sintoma) VALUES (?, ?, ?)";
    static final String UPDATE_DISEASE = "UPDATE enfermedad SET nombre = ?, especialidad_principal = ?, "
            + "tratamiento = ?, curable = ?, requiere_sangre = ?, requiere_uci = ?, tasa_deterioro = ?, "
            + "mortalidad_irreducible = ?, duracion_min_minutos = ?, duracion_max_minutos = ?, edad_minima = ?, "
            + "edad_maxima = ?, frecuencia = ? WHERE id = ?";
    static final String DELETE_SECONDARY = "DELETE FROM enfermedad_especialidad_sec WHERE id_enfermedad = ?";
    static final String DELETE_SYMPTOMS = "DELETE FROM enfermedad_sintoma WHERE id_enfermedad = ?";
    static final String DELETE_DISEASE = "DELETE FROM enfermedad WHERE id = ?";

    static final String SELECT_DISEASES = "SELECT id, nombre, especialidad_principal, tratamiento, curable, "
            + "requiere_sangre, requiere_uci, tasa_deterioro, mortalidad_irreducible, duracion_min_minutos, duracion_max_minutos, "
            + "edad_minima, edad_maxima, frecuencia FROM enfermedad ORDER BY id";
    static final String SELECT_DISEASE = "SELECT id, nombre, especialidad_principal, tratamiento, curable, "
            + "requiere_sangre, requiere_uci, tasa_deterioro, mortalidad_irreducible, duracion_min_minutos, duracion_max_minutos, "
            + "edad_minima, edad_maxima, frecuencia FROM enfermedad WHERE id = ?";
    static final String SELECT_SECONDARY = "SELECT id_enfermedad, especialidad FROM enfermedad_especialidad_sec "
            + "ORDER BY id_enfermedad, especialidad";
    static final String SELECT_SECONDARY_OF = "SELECT id_enfermedad, especialidad FROM enfermedad_especialidad_sec "
            + "WHERE id_enfermedad = ? ORDER BY especialidad";
    static final String SELECT_SYMPTOMS = "SELECT id_enfermedad, sintoma FROM enfermedad_sintoma "
            + "ORDER BY id_enfermedad, orden_sintoma";
    static final String SELECT_SYMPTOMS_OF = "SELECT id_enfermedad, sintoma FROM enfermedad_sintoma "
            + "WHERE id_enfermedad = ? ORDER BY orden_sintoma";

    private final TransactionRunner transactions;

    /**
     * Crea el DAO.
     *
     * @param connections administrador de conexiones
     * @throws IllegalArgumentException si el administrador es nulo
     */
    public OracleDiseaseDao(ConnectionManager connections) {
        this.transactions = new TransactionRunner(connections);
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
    @Override
    public Integer save(Disease disease) {
        requireDisease(disease);
        return transactions.run("guardar la enfermedad " + disease.getId(), connection -> {
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
    @Override
    public Optional<Disease> findById(Integer id) {
        requireId(id);
        return transactions.run("buscar la enfermedad " + id,
                connection -> loadDiseases(connection, id).stream().findFirst());
    }

    /**
     * Lista todas las enfermedades, ordenadas por identificador.
     *
     * @return enfermedades guardadas; lista vacía si no hay ninguna
     * @throws DataAccessException si Oracle rechaza la operación o los datos guardados no son válidos
     */
    @Override
    public List<Disease> findAll() {
        return transactions.run("listar las enfermedades", connection -> loadDiseases(connection, null));
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
                    result.computeIfAbsent(rows.getInt("id_enfermedad"), key -> EnumSet.noneOf(Specialty.class))
                            .add(parse(Specialty.class, rows.getString("especialidad"),
                                    rows.getInt("id_enfermedad")));
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
                    result.computeIfAbsent(rows.getInt("id_enfermedad"), key -> new ArrayList<>())
                            .add(parse(Symptom.class, rows.getString("sintoma"), rows.getInt("id_enfermedad")));
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
            return Disease.builder(id, row.getString("nombre"))
                    .mainSpecialty(parse(Specialty.class, row.getString("especialidad_principal"), id))
                    .secondarySpecialties(secondary.getOrDefault(id, Set.of()).toArray(new Specialty[0]))
                    .treatment(parse(TreatmentType.class, row.getString("tratamiento"), id))
                    .curable(row.getInt("curable") == 1)
                    .requiresBlood(row.getInt("requiere_sangre") == 1)
                    .requiresIcu(row.getInt("requiere_uci") == 1)
                    .deteriorationRate(row.getDouble("tasa_deterioro"))
                    .irreducibleMortality(row.getDouble("mortalidad_irreducible"))
                    .durationMinutes(row.getInt("duracion_min_minutos"), row.getInt("duracion_max_minutos"))
                    .ageRange(row.getInt("edad_minima"), row.getInt("edad_maxima"))
                    .frequency(row.getDouble("frecuencia"))
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

    // ---------- cambiar y borrar ----------

    /**
     * Reemplaza los datos de una enfermedad que ya existe, incluidas sus especialidades secundarias
     * y sus síntomas.
     *
     * @param disease enfermedad con los datos nuevos; se identifica por su {@code id}
     * @throws IllegalArgumentException si la enfermedad es nula
     * @throws DataAccessException      si no existe, si el nombre ya lo usa otra enfermedad o si Oracle
     *                                  rechaza la operación
     */
    @Override
    public void update(Disease disease) {
        requireDisease(disease);
        transactions.run("actualizar la enfermedad " + disease.getId(), connection -> {
            updateDisease(connection, disease);
            deleteChildren(connection, disease.getId());
            insertChildren(connection, disease);
            return null;
        });
    }

    /**
     * Borra una enfermedad con sus especialidades secundarias y sus síntomas.
     *
     * @param id identificador de la enfermedad
     * @throws IllegalArgumentException si el identificador es nulo
     * @throws DataAccessException      si no existe, si algún caso o resultado guardado la usa, o si Oracle
     *                                  rechaza la operación
     */
    @Override
    public void deleteById(Integer id) {
        requireId(id);
        transactions.run("borrar la enfermedad " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_DISEASE)) {
                statement.setInt(1, id);
                requireOneRow(statement.executeUpdate(), id);
            }
            return null;
        });
    }

    private static void updateDisease(Connection connection, Disease disease) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_DISEASE)) {
            bindAttributes(statement, disease, 1);
            statement.setInt(14, disease.getId());
            requireOneRow(statement.executeUpdate(), disease.getId());
        }
    }

    private static void deleteChildren(Connection connection, int diseaseId) throws SQLException {
        for (String sql : List.of(DELETE_SECONDARY, DELETE_SYMPTOMS)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, diseaseId);
                statement.executeUpdate();
            }
        }
    }

    private static void requireOneRow(int changedRows, int diseaseId) {
        if (changedRows == 0) {
            throw new DataAccessException("No existe la enfermedad " + diseaseId + ".");
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