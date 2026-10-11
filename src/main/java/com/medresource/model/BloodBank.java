package com.medresource.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Banco de sangre del hospital, con unidades finitas por tipo.
 */
public class BloodBank {

    private final Map<BloodType, Integer> units = new EnumMap<>(BloodType.class);

    /**
     * @param initialUnits unidades por tipo de sangre
     * @throws IllegalArgumentException si el mapa es nulo o alguna cantidad es negativa
     */
    public BloodBank(Map<BloodType, Integer> initialUnits) {
        if (initialUnits == null) {
            throw new IllegalArgumentException("Las unidades iniciales de sangre son obligatorias.");
        }
        for (BloodType type : BloodType.values()) {
            int amount = initialUnits.getOrDefault(type, 0);
            if (amount < 0) {
                throw new IllegalArgumentException("Las unidades de " + type.getLabel() + " no pueden ser negativas.");
            }
            units.put(type, amount);
        }
    }

    public int available(BloodType type) {
        return units.get(type);
    }

    public int totalUnits() {
        return units.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Indica si, sumando todos los tipos compatibles, alcanzan las unidades.
     *
     * @param recipient tipo de sangre del paciente
     * @param needed    unidades necesarias, al menos 1
     * @return {@code true} si hay suficientes unidades compatibles
     * @throws IllegalArgumentException si {@code needed} es menor que 1
     */
    public boolean hasCompatibleUnits(BloodType recipient, int needed) {
        requirePositive(needed);
        return donorOrder(recipient).stream().mapToInt(units::get).sum() >= needed;
    }

    /**
     * Retira unidades para un paciente, usando primero el mismo tipo y dejando
     * de último el donante universal.
     *
     * @param recipient tipo de sangre del paciente
     * @param needed    unidades que se retiran, al menos 1
     * @throws IllegalArgumentException si {@code needed} es menor que 1
     * @throws IllegalStateException    si no hay suficientes unidades compatibles;
     *                                  en ese caso no se retira nada
     */
    public void withdraw(BloodType recipient, int needed) {
        if (!hasCompatibleUnits(recipient, needed)) {
            throw new IllegalStateException(
                    "No hay " + needed + " unidades compatibles con " + recipient.getLabel() + ".");
        }
        int pending = needed;
        for (BloodType donor : donorOrder(recipient)) {
            int taken = Math.min(pending, units.get(donor));
            units.put(donor, units.get(donor) - taken);
            pending -= taken;
            if (pending == 0) {
                return;
            }
        }
    }

    /**
     * Devuelve una copia de las unidades por tipo; los ocho tipos aparecen,
     * aunque estén en cero.
     */
    public Map<BloodType, Integer> getUnits() {
        return Collections.unmodifiableMap(new EnumMap<>(units));
    }

    /**
     * Tipos compatibles con el receptor, primero los que sirven a menos tipos
     * de pacientes. La ordenación es estable: si empatan, se respeta el orden
     * del enum, así el resultado siempre es el mismo.
     */
    private static List<BloodType> donorOrder(BloodType recipient) {
        List<BloodType> donors = new ArrayList<>();
        for (BloodType donor : BloodType.values()) {
            if (recipient.canReceiveFrom(donor)) {
                donors.add(donor);
            }
        }
        donors.sort(Comparator.comparingInt(BloodBank::recipientsServedBy));
        return donors;
    }

    private static int recipientsServedBy(BloodType donor) {
        int count = 0;
        for (BloodType recipient : BloodType.values()) {
            if (recipient.canReceiveFrom(donor)) {
                count++;
            }
        }
        return count;
    }

    private static void requirePositive(int needed) {
        if (needed < 1) {
            throw new IllegalArgumentException("Se necesita al menos una unidad de sangre.");
        }
    }
}