package com.medresource.model;

/**
 * Recurso que se puede agregar al hospital para probar si mejora los resultados, con la cantidad que se agrega
 * en cada prueba.
 *
 * <p>Las cantidades son supuestos del análisis: las camas y la sangre se prueban de a varias porque una sola
 * casi nunca cambia nada; los quirófanos, las camas de UCI y los médicos, de a uno.</p>
 */
public enum ExtraResource {

    BEDS("camas de hospitalización", "cama de hospitalización", 5),
    ICU_BEDS("camas de UCI", "cama de UCI", 1),
    OPERATING_ROOMS("quirófanos", "quirófano", 1),
    DOCTOR("médicos", "médico", 1),
    NURSES("enfermeros", "enfermero", 2),
    BLOOD("unidades de cada tipo de sangre", "unidad de cada tipo de sangre", 5),
    MORGUE("cupos de la morgue", "cupo de la morgue", 2);

    private final String pluralName;
    private final String singularName;
    private final int amount;

    ExtraResource(String pluralName, String singularName, int amount) {
        this.pluralName = pluralName;
        this.singularName = singularName;
        this.amount = amount;
    }

    /**
     * Devuelve cuántos se agregan en cada prueba.
     *
     * @return cantidad que se agrega, al menos 1
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Nombra la cantidad que se agrega, en singular o en plural según corresponda, por ejemplo
     * {@code 5 camas de hospitalización} o {@code 1 quirófano}.
     *
     * @return cantidad y nombre en español
     */
    public String describeAmount() {
        return amount + " " + (amount == 1 ? singularName : pluralName);
    }
}
