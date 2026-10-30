package com.medresource.services.management;

import com.medresource.model.Disease;
import com.medresource.persistence.GenericDao;

import java.util.List;
import java.util.Optional;

/**
 * Gestiona el catálogo de enfermedades: consultarlo, agregar, modificar y borrar enfermedades,
 * y cargar el catálogo inicial.
 *
 * <p>Aplica las reglas del catálogo (identificadores y nombres sin repetir, enfermedad existente)
 * antes de pedirle a la base de datos que guarde algo, para dar mensajes claros. Depende de
 * {@link GenericDao} y no de Oracle, así que se prueba con un DAO en memoria. Los errores de la base de
 * datos (por ejemplo, borrar una enfermedad que ya usa un caso guardado) llegan como
 * {@link com.medresource.persistence.DataAccessException}.</p>
 */
public class DiseaseService {

    private final GenericDao<Disease, Integer> dao;

    /**
     * Crea el servicio.
     *
     * @param dao acceso a los datos de las enfermedades
     * @throws IllegalArgumentException si el DAO es nulo
     */
    public DiseaseService(GenericDao<Disease, Integer> dao) {
        if (dao == null) {
            throw new IllegalArgumentException("El acceso a datos de enfermedades es obligatorio.");
        }
        this.dao = dao;
    }

    /**
     * Lista el catálogo completo.
     *
     * @return enfermedades ordenadas por identificador; lista vacía si el catálogo está vacío
     */
    public List<Disease> listAll() {
        return dao.findAll();
    }

    /**
     * Busca una enfermedad por su identificador.
     *
     * @param id identificador buscado
     * @return la enfermedad, o vacío si no existe
     */
    public Optional<Disease> find(int id) {
        return dao.findById(id);
    }

    /**
     * Calcula el identificador que debe tener una enfermedad nueva: el mayor que existe más uno.
     *
     * @return identificador libre; 1 si el catálogo está vacío
     */
    public int nextId() {
        return dao.findAll().stream().mapToInt(Disease::getId).max().orElse(0) + 1;
    }

    /**
     * Agrega una enfermedad nueva al catálogo.
     *
     * @param disease enfermedad nueva
     * @return el identificador con el que quedó guardada
     * @throws IllegalArgumentException si la enfermedad es nula o ya existe otra con ese identificador
     *                                  o con ese nombre (sin distinguir mayúsculas)
     */
    public int add(Disease disease) {
        requireDisease(disease);
        List<Disease> existing = dao.findAll();
        if (existing.stream().anyMatch(other -> other.getId() == disease.getId())) {
            throw new IllegalArgumentException("Ya existe una enfermedad con el identificador " + disease.getId() + ".");
        }
        requireFreeName(existing, disease);
        return dao.save(disease);
    }

    /**
     * Modifica una enfermedad que ya existe.
     *
     * @param disease enfermedad con los datos nuevos; se identifica por su {@code id}
     * @throws IllegalArgumentException si la enfermedad es nula, no existe, o su nombre ya lo usa otra
     */
    public void update(Disease disease) {
        requireDisease(disease);
        List<Disease> existing = dao.findAll();
        if (existing.stream().noneMatch(other -> other.getId() == disease.getId())) {
            throw new IllegalArgumentException("No existe la enfermedad con el identificador " + disease.getId() + ".");
        }
        requireFreeName(existing, disease);
        dao.update(disease);
    }

    /**
     * Borra una enfermedad del catálogo.
     *
     * @param id identificador de la enfermedad
     * @throws IllegalArgumentException si la enfermedad no existe
     */
    public void delete(int id) {
        if (dao.findById(id).isEmpty()) {
            throw new IllegalArgumentException("No existe la enfermedad con el identificador " + id + ".");
        }
        dao.deleteById(id);
    }

    private static void requireDisease(Disease disease) {
        if (disease == null) {
            throw new IllegalArgumentException("La enfermedad es obligatoria.");
        }
    }

    /** Comprueba que ninguna otra enfermedad (de distinto identificador) tenga el mismo nombre. */
    private static void requireFreeName(List<Disease> existing, Disease disease) {
        boolean taken = existing.stream().anyMatch(other -> other.getId() != disease.getId() && sameName(other, disease));
        if (taken) {
            throw new IllegalArgumentException("Ya existe otra enfermedad llamada '" + disease.getName() + "'.");
        }
    }

    private static boolean sameName(Disease first, Disease second) {
        return first.getName().strip().equalsIgnoreCase(second.getName().strip());
    }
}