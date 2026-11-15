package com.medresource.persistence;

import com.medresource.model.DocumentInfo;
import com.medresource.model.ExportFormat;

import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Recuerda entre sesiones los datos de la portada que el usuario escribió al descargar un reporte (autores,
 * institución, programa, curso y docente) y el último formato que eligió, para no tener que escribirlos cada vez.
 *
 * <p>Los guarda con las preferencias de Java del usuario ({@link Preferences}), no en el proyecto. El título no
 * se recuerda porque cambia con cada reporte. Recordar es una ayuda: si no se puede guardar, no pasa nada.</p>
 */
public final class CoverPreferences {

    private static final String AUTHORS = "autores";
    private static final String INSTITUTION = "institucion";
    private static final String PROGRAM = "programa";
    private static final String COURSE = "curso";
    private static final String TEACHER = "docente";
    private static final String FORMAT = "formato";
    private static final String SEPARATOR = "\n";

    private final Preferences preferences;

    /**
     * Crea el recordatorio sobre un nodo de preferencias.
     *
     * @param preferences nodo donde se guardan los datos
     * @throws IllegalArgumentException si es nulo
     */
    public CoverPreferences(Preferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("El nodo de preferencias es obligatorio.");
        }
        this.preferences = preferences;
    }

    /**
     * Crea el recordatorio en las preferencias del usuario para MedResource AI.
     *
     * @return recordatorio de la portada
     */
    public static CoverPreferences forUser() {
        return new CoverPreferences(Preferences.userRoot().node("medresource-ai/portada-del-reporte"));
    }

    /**
     * Completa los datos propuestos con los que se guardaron la última vez.
     *
     * @param draft datos propuestos (el título y la fecha se dejan como están)
     * @return datos con lo guardado; los propuestos si no hay nada guardado
     * @throws IllegalArgumentException si faltan los datos propuestos
     */
    public DocumentInfo apply(DocumentInfo draft) {
        if (draft == null) {
            throw new IllegalArgumentException("Los datos propuestos son obligatorios.");
        }
        String savedAuthors = preferences.get(AUTHORS, "");
        List<String> authors = savedAuthors.isBlank() ? draft.authors()
                : List.of(savedAuthors.split(SEPARATOR));
        return new DocumentInfo(draft.title(), authors, preferences.get(INSTITUTION, draft.institution()),
                preferences.get(PROGRAM, draft.program()), preferences.get(COURSE, draft.course()),
                preferences.get(TEACHER, draft.teacher()), draft.date());
    }

    /**
     * Devuelve el último formato elegido.
     *
     * @param fallback formato si nunca se eligió uno
     * @return formato guardado o el de respaldo
     */
    public ExportFormat format(ExportFormat fallback) {
        String saved = preferences.get(FORMAT, "");
        for (ExportFormat format : ExportFormat.values()) {
            if (format.name().equals(saved)) {
                return format;
            }
        }
        return fallback;
    }

    /**
     * Guarda los datos de la portada y el formato para la próxima vez.
     *
     * @param info   datos de la portada
     * @param format formato elegido
     * @return {@code true} si quedaron guardados; {@code false} si no se pudo (no es un error grave)
     * @throws IllegalArgumentException si falta alguno
     */
    public boolean save(DocumentInfo info, ExportFormat format) {
        if (info == null || format == null) {
            throw new IllegalArgumentException("Los datos de la portada y el formato son obligatorios.");
        }
        preferences.put(AUTHORS, String.join(SEPARATOR, info.authors()));
        preferences.put(INSTITUTION, info.institution());
        preferences.put(PROGRAM, info.program());
        preferences.put(COURSE, info.course());
        preferences.put(TEACHER, info.teacher());
        preferences.put(FORMAT, format.name());
        try {
            preferences.flush();
            return true;
        } catch (BackingStoreException notSaved) {
            return false;
        }
    }
}
