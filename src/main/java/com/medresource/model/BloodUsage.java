package com.medresource.model;

/**
 * Uso de un tipo de sangre del banco durante la simulación.
 *
 * @param type             tipo de sangre
 * @param initialUnits     unidades al empezar
 * @param usedUnits        unidades de este tipo que se retiraron
 * @param remainingUnits   unidades al terminar
 * @param firstEmptyMinute primer minuto en que no quedó ninguna unidad; {@code -1} si nunca se agotó
 * @param emptyMinutes     minutos en que no quedó ninguna unidad
 */
public record BloodUsage(BloodType type, int initialUnits, int usedUnits, int remainingUnits, int firstEmptyMinute,
                         int emptyMinutes) {

    /**
     * Valida los datos.
     *
     * @throws IllegalArgumentException si falta el tipo, si hay números negativos, si las unidades no cuadran o
     *                                  si el minuto en que se agotó no corresponde con los minutos sin sangre
     */
    public BloodUsage {
        if (type == null) {
            throw new IllegalArgumentException("El tipo de sangre es obligatorio.");
        }
        if (initialUnits < 0 || usedUnits < 0 || remainingUnits < 0 || emptyMinutes < 0 || firstEmptyMinute < -1) {
            throw new IllegalArgumentException("El uso de sangre no puede ser negativo.");
        }
        if (initialUnits - usedUnits != remainingUnits) {
            throw new IllegalArgumentException("Las unidades restantes deben ser las iniciales menos las usadas.");
        }
        if ((firstEmptyMinute == -1) != (emptyMinutes == 0)) {
            throw new IllegalArgumentException("Un tipo agotado debe tener minuto y minutos sin sangre.");
        }
    }

    /**
     * Indica si el tipo se agotó en algún momento.
     *
     * @return {@code true} si hubo minutos sin unidades
     */
    public boolean ranOut() {
        return firstEmptyMinute >= 0;
    }
}
