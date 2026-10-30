package com.medresource.ui.render;

import com.medresource.model.TriageLevel;
import com.medresource.model.ZoneType;

import java.awt.Color;

/**
 * Colores de la interfaz del hospital, en un solo lugar para que todas las vistas se vean igual.
 *
 * <p>Cada zona tiene un color de fondo y cada nivel de triage un color de aviso (rojo para el más
 * urgente, azul para el menos urgente).</p>
 */
public final class Palette {

    /** Fondo del panel, fuera del plano. */
    public static final Color BACKGROUND = new Color(0x1E, 0x23, 0x2B);
    /** Texto de los títulos y de los códigos. */
    public static final Color LABEL = new Color(0x1F, 0x29, 0x33);
    /** Tarjeta de una cama o de un puesto libre. */
    public static final Color CARD = new Color(0xFF, 0xFF, 0xFF);
    /** Borde de las tarjetas. */
    public static final Color CARD_BORDER = new Color(0x00, 0x00, 0x00, 60);
    /** Mesa de operaciones. */
    public static final Color OPERATING_TABLE = new Color(0xB0, 0xBE, 0xC5);
    /** Lugar de la morgue. */
    public static final Color MORGUE_SLAB = new Color(0x78, 0x90, 0x9C);
    /** Puesto del personal en su sala. */
    public static final Color STAFF_SEAT = new Color(0x4F, 0x7C, 0xAC);
    /** Silla de la sala de espera. */
    public static final Color WAITING_SEAT = new Color(0xA1, 0x88, 0x7F);
    /** Puesto de triage. */
    public static final Color TRIAGE_STATION = new Color(0xE0, 0x7B, 0x39);
    /** Lugar de un tipo de sangre en el banco de sangre. */
    public static final Color BLOOD_UNIT = new Color(0xB7, 0x1C, 0x1C);
    /** Marca de la entrada de pacientes. */
    public static final Color ENTRANCE = new Color(0x2E, 0x9E, 0x4F);

    private Palette() {
    }

    /**
     * Color de fondo de las áreas de una zona.
     *
     * @param zone tipo de zona
     * @return color de la zona
     * @throws IllegalArgumentException si la zona es nula
     */
    public static Color zone(ZoneType zone) {
        if (zone == null) {
            throw new IllegalArgumentException("La zona es obligatoria.");
        }
        return switch (zone) {
            case TRIAGE -> new Color(0xF4, 0xB9, 0x8A);
            case WAITING_ROOM -> new Color(0xF2, 0xE3, 0xB3);
            case HOSPITALIZATION -> new Color(0xB3, 0xD9, 0xF2);
            case ICU -> new Color(0xE8, 0xA0, 0xA0);
            case OPERATING_ROOM -> new Color(0x9F, 0xD8, 0xCF);
            case BLOOD_BANK -> new Color(0xE3, 0x9B, 0xB0);
            case MORGUE -> new Color(0xB8, 0xB0, 0xC9);
            case DISCHARGE -> new Color(0x8D, 0xC9, 0x8F);
            case STAFF_ROOM -> new Color(0xCF, 0xE0, 0xA8);
        };
    }

    /**
     * Color de aviso de un nivel de triage.
     *
     * @param level nivel de triage
     * @return rojo para el nivel I, naranja, amarillo, verde y azul para el nivel V
     * @throws IllegalArgumentException si el nivel es nulo
     */
    public static Color triage(TriageLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("El nivel de triage es obligatorio.");
        }
        return switch (level) {
            case I -> new Color(0xD3, 0x2F, 0x2F);
            case II -> new Color(0xF5, 0x7C, 0x00);
            case III -> new Color(0xFB, 0xC0, 0x2D);
            case IV -> new Color(0x38, 0x8E, 0x3C);
            case V -> new Color(0x19, 0x76, 0xD2);
        };
    }
}