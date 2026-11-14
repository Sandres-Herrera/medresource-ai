package com.medresource.ui.render;

import com.medresource.model.ZoneType;

import java.awt.Color;

/**
 * Colores de la vista 2.5D que no cambian con el {@linkplain IsoTheme tema}: el contorno y la sombra de las
 * personas, el gorro de los médicos, el color de cada área y los de algunos objetos (bolsas de sangre, monitores,
 * lámpara del quirófano), y las operaciones para aclarar u oscurecer un color.
 *
 * <p>Las personas usan los mismos colores que en la vista 2D ({@link Palette}): el cuerpo dice quién es (el color del
 * triage en un paciente, la bata blanca de un médico, el verde agua de un enfermero…) y el gorro, más oscuro,
 * distingue al personal de los pacientes. El contorno casi negro las separa del piso en los dos temas.</p>
 */
public final class IsoPalette {

    /** Flecha y letrero de la entrada. */
    public static final Color ENTRANCE = new Color(0x4A, 0xDE, 0x80);

    /** Trazo verde de los monitores. */
    public static final Color MONITOR_LINE = new Color(0x34, 0xD3, 0x99);
    /** Luz de la lámpara del quirófano. */
    public static final Color LAMP_LIGHT = new Color(0xFF, 0xF3, 0xC4);
    /** Colchoneta verde agua de la mesa de operaciones. */
    public static final Color SURGICAL_PAD = new Color(0x7D, 0xD3, 0xC0);
    /** Vidrio de la puerta de la nevera. */
    public static final Color FRIDGE_GLASS = new Color(0x2A, 0x0B, 0x12);
    /** Bolsas de sangre. */
    public static final Color BLOOD_BAG = new Color(0xE1, 0x1D, 0x48);
    /** Sábana que cubre a un fallecido. */
    public static final Color SHEET = new Color(0xA3, 0xAB, 0xB8);
    /** Sombra bajo las personas. */
    public static final Color SHADOW = new Color(0, 0, 0, 100);
    /** Contorno de las personas: casi negro, para que se vean sobre cualquier color. */
    public static final Color OUTLINE = new Color(0x05, 0x07, 0x0B, 235);
    /** Gorro de los médicos. */
    public static final Color DOCTOR_CAP = new Color(0x33, 0x41, 0x55);

    private IsoPalette() {
    }

    /**
     * Color de un área: el del mueble de su puesto, por ejemplo el del puesto de triage.
     *
     * @param zone tipo de zona
     * @return color de la zona
     * @throws IllegalArgumentException si la zona es nula
     */
    public static Color accent(ZoneType zone) {
        if (zone == null) {
            throw new IllegalArgumentException("La zona es obligatoria.");
        }
        return switch (zone) {
            case TRIAGE -> new Color(0xF2, 0x84, 0x3A);
            case WAITING_ROOM -> new Color(0xA5, 0xB4, 0xCF);
            case HOSPITALIZATION -> new Color(0x38, 0xBD, 0xF8);
            case ICU -> new Color(0xA7, 0x8B, 0xFA);
            case OPERATING_ROOM -> new Color(0x2D, 0xD4, 0xBF);
            case BLOOD_BANK -> new Color(0xFB, 0x71, 0x85);
            case MORGUE -> new Color(0x94, 0xA3, 0xB8);
            case DISCHARGE -> new Color(0x4A, 0xDE, 0x80);
            case CONSULTATION -> new Color(0x60, 0xA5, 0xFA);
            case STAFF_ROOM -> new Color(0xFB, 0xBF, 0x24);
            case CORRIDOR -> new Color(0x64, 0x74, 0x8B);
        };
    }

    /**
     * Gorro de una persona del personal: el color de su uniforme más oscuro.
     *
     * @param uniform color del uniforme
     * @return color del gorro
     * @throws IllegalArgumentException si el color es nulo
     */
    public static Color cap(Color uniform) {
        if (uniform == null) {
            throw new IllegalArgumentException("El color del uniforme es obligatorio.");
        }
        return darker(uniform, 0.62);
    }

    /**
     * Oscurece un color multiplicando sus tres componentes; conserva la transparencia.
     *
     * @param color  color
     * @param factor de 0 (negro) a 1 (igual)
     * @return el color oscurecido
     * @throws IllegalArgumentException si el color es nulo o el factor no está entre 0 y 1
     */
    public static Color darker(Color color, double factor) {
        if (color == null || !(factor >= 0 && factor <= 1)) {
            throw new IllegalArgumentException("Se necesita un color y un factor entre 0 y 1.");
        }
        return new Color((int) Math.round(color.getRed() * factor), (int) Math.round(color.getGreen() * factor),
                (int) Math.round(color.getBlue() * factor), color.getAlpha());
    }

    /**
     * Aclara un color mezclándolo con blanco; conserva la transparencia.
     *
     * @param color color
     * @param share de 0 (igual) a 1 (blanco)
     * @return el color aclarado
     * @throws IllegalArgumentException si el color es nulo o la proporción no está entre 0 y 1
     */
    public static Color lighter(Color color, double share) {
        if (color == null || !(share >= 0 && share <= 1)) {
            throw new IllegalArgumentException("Se necesita un color y una proporción entre 0 y 1.");
        }
        return new Color(mix(color.getRed(), share), mix(color.getGreen(), share), mix(color.getBlue(), share),
                color.getAlpha());
    }

    private static int mix(int component, double share) {
        return (int) Math.round(component + (255 - component) * share);
    }
}
