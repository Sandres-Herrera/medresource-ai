package com.medresource.model;

/**
 * Sección del reporte de fin de simulación que lleva un texto explicativo además de sus números.
 *
 * <p>Cada sección tiene una clave corta en español sin tildes ({@link #getKey()}), que es la que se usa en el
 * JSON que se le pide a la IA.</p>
 */
public enum ReportSection {

    TRIAGE("triage", "Esperas por nivel de triage"),
    DEATHS("fallecidos", "Fallecidos y fallas"),
    BOTTLENECKS("cuellos_de_botella", "Cuellos de botella"),
    TIMELINE("linea_de_tiempo", "Línea de tiempo"),
    RESOURCES("recursos", "Ocupación de los recursos"),
    STAFF("personal", "Carga del personal"),
    BLOOD("sangre", "Banco de sangre"),
    MISSING_RESOURCES("recursos_que_faltaron", "Recursos que faltaron"),
    POLICY("politica", "Política de atención"),
    CASES("casos", "Casos para revisar");

    private final String key;
    private final String title;

    ReportSection(String key, String title) {
        this.key = key;
        this.title = title;
    }

    /**
     * Devuelve la clave de la sección en el JSON de la IA.
     *
     * @return clave en minúsculas, sin tildes ni espacios
     */
    public String getKey() {
        return key;
    }

    /**
     * Devuelve el título que se muestra al usuario.
     *
     * @return título en español
     */
    public String getTitle() {
        return title;
    }
}
