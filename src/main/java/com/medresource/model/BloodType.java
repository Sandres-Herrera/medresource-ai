package com.medresource.model;

/**
 * Tipo de sangre de un paciente según los sistemas ABO y Rh.
 *
 * <p>Cada tipo conoce los antígenos que tiene, lo que permite decidir si una
 * unidad de sangre es compatible con un receptor.</p>
 */
public enum BloodType {

    O_POSITIVE("O+", false, false, true),
    O_NEGATIVE("O-", false, false, false),
    A_POSITIVE("A+", true, false, true),
    A_NEGATIVE("A-", true, false, false),
    B_POSITIVE("B+", false, true, true),
    B_NEGATIVE("B-", false, true, false),
    AB_POSITIVE("AB+", true, true, true),
    AB_NEGATIVE("AB-", true, true, false);

    private final String label;
    private final boolean hasAntigenA;
    private final boolean hasAntigenB;
    private final boolean rhPositive;

    BloodType(String label, boolean hasAntigenA, boolean hasAntigenB, boolean rhPositive) {
        this.label = label;
        this.hasAntigenA = hasAntigenA;
        this.hasAntigenB = hasAntigenB;
        this.rhPositive = rhPositive;
    }

    /**
     * Devuelve la etiqueta clínica del tipo de sangre.
     *
     * @return etiqueta, por ejemplo {@code "O-"}
     */
    public String getLabel() {
        return label;
    }

    /**
     * Indica si este tipo de sangre (receptor) puede recibir sangre del tipo dado.
     *
     * <p>Un receptor puede recibir de un donante si el donante no aporta ningún
     * antígeno (A, B o Rh positivo) que el receptor no tenga.</p>
     *
     * @param donor tipo de sangre del donante
     * @return {@code true} si la transfusión es compatible
     */
    public boolean canReceiveFrom(BloodType donor) {
        boolean antigenACompatible = !donor.hasAntigenA || this.hasAntigenA;
        boolean antigenBCompatible = !donor.hasAntigenB || this.hasAntigenB;
        boolean rhCompatible = !donor.rhPositive || this.rhPositive;
        return antigenACompatible && antigenBCompatible && rhCompatible;
    }
}