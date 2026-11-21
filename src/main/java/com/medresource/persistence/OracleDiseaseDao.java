package com.medresource.persistence;

import com.medresource.model.Disease;
import com.medresource.model.Specialty;
import com.medresource.model.Symptom;
import com.medresource.model.TreatmentType;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Guarda y lee las enfermedades del catálogo en Oracle.
 *
 * <p>Una enfermedad ocupa tres tablas: {@code enfermedad} (sus datos), {@code enfermedad_especialidad_sec}
 * (sus especialidades secundarias) y {@code enfermedad_sintoma} (sus síntomas, en orden). Cada operación
 * se hace en una sola transacción: o se guarda todo o no se guarda nada. Los valores de las
 * enumeraciones se guardan con el nombre de la constante y los booleanos como 0 o 1.</p>
 */
public class OracleDiseaseDao implements GenericDao<Disease, Integer> {

    /** {@code pr_guardar_enfermedad}: los 14 datos de la enfermedad y, al final, sus listas separadas por coma. */
    static final String INSERT_DISEASE = "{call pr_guardar_enfermedad(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    /** {@code pr_actualizar_enfermedad}: los mismos parámetros, y el último sale con las filas actualizadas. */
    static final String UPDATE_DISEASE =
            "{call pr_actualizar_enfermedad(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)}";
    /** {@code pr_borrar_enfermedad(id, borradas)}: el segundo parámetro dice cuántas filas se borraron. */
    static final String DELETE_DISEASE = "{call pr_borrar_enfermedad(?, ?)}";
    /** {@code pr_leer_enfermedades(id, cursor)}: si el id es nulo, devuelve todas. */
    static final String SELECT_DISEASES = "{call pr_leer_enfermedades(?, ?)}";
    /** {@code pr_leer_especialidades_enfermedad(id, cursor)}: si el id es nulo, devuelve las de todas. */
    static final String SELECT_SECONDARY = "{call pr_leer_especialidades_enfermedad(?, ?)}";
    /** {@code pr_leer_sintomas_enfermedad(id, cursor)}: si el id es nulo, devuelve los de todas. */
    static final String SELECT_SYMPTOMS = "{call pr_leer_sintomas_enfermedad(?, ?)}";

    /** Posiciones de los parámetros de salida y de los cursores. */
    private static final int UPDATED_COUNT_POSITION = 17;
    private static final int DELETED_COUNT_POSITION = 2;
    private static final int CURSOR_POSITION = 2;

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
     * Guarda una enfermedad nueva con sus especialidades secundarias y sus síntomas, en una sola llamada.
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
            try (CallableStatement call = connection.prepareCall(INSERT_DISEASE)) {
                bindDisease(call, disease);
                call.execute();
            }
            return disease.getId();
        });
    }

    /** Pone los datos de la enfermedad en las posiciones 1 a 16 del procedimiento. */
    private static void bindDisease(CallableStatement call, Disease disease) throws SQLException {
        call.setInt(1, disease.getId());
        call.setString(2, disease.getName());
        call.setString(3, disease.getMainSpecialty().name());
        call.setString(4, disease.getTreatment().name());
        call.setInt(5, disease.isCurable() ? 1 : 0);
        call.setInt(6, disease.requiresBlood() ? 1 : 0);
        call.setInt(7, disease.requiresIcu() ? 1 : 0);
        call.setDouble(8, disease.getDeteriorationRate());
        call.setDouble(9, disease.getIrreducibleMortality());
        call.setInt(10, disease.getMinDurationMinutes());
        call.setInt(11, disease.getMaxDurationMinutes());
        call.setInt(12, disease.getMinAge());
        call.setInt(13, disease.getMaxAge());
        call.setDouble(14, disease.getFrequency());
        call.setString(15, disease.getSecondarySpecialties().stream().map(Specialty::name)
                .collect(Collectors.joining(",")));
        call.setString(16, disease.getSymptoms().stream().map(Symptom::name).collect(Collectors.joining(",")));
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
        try (CallableStatement call = connection.prepareCall(SELECT_DISEASES)) {
            setOptionalId(call, id);
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
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
        try (CallableStatement call = connection.prepareCall(SELECT_SECONDARY)) {
            setOptionalId(call, id);
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
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
        try (CallableStatement call = connection.prepareCall(SELECT_SYMPTOMS)) {
            setOptionalId(call, id);
            call.registerOutParameter(CURSOR_POSITION, Types.REF_CURSOR);
            call.execute();
            try (ResultSet rows = (ResultSet) call.getObject(CURSOR_POSITION)) {
                while (rows.next()) {
                    result.computeIfAbsent(rows.getInt("id_enfermedad"), key -> new ArrayList<>())
                            .add(parse(Symptom.class, rows.getString("sintoma"), rows.getInt("id_enfermedad")));
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
            try (CallableStatement call = connection.prepareCall(UPDATE_DISEASE)) {
                bindDisease(call, disease);
                call.registerOutParameter(UPDATED_COUNT_POSITION, Types.INTEGER);
                call.execute();
                requireOneRow(call.getInt(UPDATED_COUNT_POSITION), disease.getId());
            }
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
            try (CallableStatement call = connection.prepareCall(DELETE_DISEASE)) {
                call.setInt(1, id);
                call.registerOutParameter(DELETED_COUNT_POSITION, Types.INTEGER);
                call.execute();
                requireOneRow(call.getInt(DELETED_COUNT_POSITION), id);
            }
            return null;
        });
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
