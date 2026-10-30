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
}