package com.medresource.model;

/**
 * Un dato de la configuración que es distinto en dos ejecuciones que se comparan, por ejemplo los médicos extra
 * o la estrategia.
 *
 * @param setting nombre del dato, por ejemplo {@code Médicos extra}
 * @param before  valor en la primera ejecución
 * @param after   valor en la segunda ejecución
 */
public record SettingChange(String setting, String before, String after) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta alguno o si los dos valores son iguales
     */
    public SettingChange {
        if (setting == null || setting.isBlank() || before == null || before.isBlank() || after == null
                || after.isBlank()) {
            throw new IllegalArgumentException("El dato y sus dos valores son obligatorios.");
        }
        if (before.equals(after)) {
            throw new IllegalArgumentException("Un cambio necesita dos valores distintos.");
        }
    }

    /**
     * Describe el cambio en una línea, por ejemplo «Médicos extra: ninguno → 1 de Cirugía».
     *
     * @return la descripción
     */
    public String describe() {
        return setting + ": " + before + " → " + after;
    }
}
