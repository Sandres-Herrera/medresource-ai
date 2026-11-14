package com.medresource.ui.render;

import com.medresource.model.ZoneType;

import java.awt.Color;

/**
 * Colores de la vista 2.5D, estilo «vidrio y neón»: pisos grafito iguales en todo el hospital, un borde de neón y
 * una etiqueta del color de cada área, muros de vidrio y muebles claros.
 *
 * <p>Los pisos son oscuros (luminosidad del 10 al 15 %) y las personas son claras y de colores vivos, así que
 * siempre resaltan. Las personas usan los mismos colores que en la vista 2D ({@link Palette}): el cuerpo dice quién
 * es (el color del triage en un paciente, la bata blanca de un médico, el verde agua de un enfermero…) y el gorro,
 * más oscuro, distingue al personal de los pacientes.</p>
 */
public final class IsoPalette {

    /** Fondo del panel en la vista 2.5D. */
    public static final Color BACKGROUND = new Color(0x07, 0x09, 0x0E);
    /** Terreno alrededor del edificio. */
    public static final Color SITE = new Color(0x0E, 0x11, 0x17);
    /** Borde izquierdo del terreno. */
    public static final Color SITE_LEFT = new Color(0x0A, 0x0C, 0x11);
    /** Borde derecho del terreno. */
    public static final Color SITE_RIGHT = new Color(0x06, 0x08, 0x0C);
    /** Línea del contorno del terreno. */
    public static final Color SITE_EDGE = new Color(0x1A, 0x1F, 0x2A);
    /** Acera de la entrada. */
    public static final Color SIDEWALK = new Color(0x15, 0x1A, 0x23);
    /** Piso de las salas. */
    public static final Color FLOOR = new Color(0x1A, 0x1D, 0x26);
    /** Piso de los pasillos, un poco más claro que el de las salas. */
    public static final Color CORRIDOR_FLOOR = new Color(0x22, 0x26, 0x2F);
    /** Piso de la morgue, un poco más oscuro. */
    public static final Color MORGUE_FLOOR = new Color(0x16, 0x18, 0x1F);
    /** Líneas de las baldosas: blanco casi transparente. */
    public static final Color TILE_LINE = new Color(255, 255, 255, 10);
    /** Relleno de los muros de vidrio. */
    public static final Color GLASS = new Color(0x9C, 0xC7, 0xFF, 34);
    /** Bordes de los muros de vidrio. */
    public static final Color GLASS_EDGE = new Color(0x9C, 0xC7, 0xFF, 150);
    /** Borde de arriba de los muros de vidrio, el más brillante. */
    public static final Color GLASS_TOP = new Color(0xD7, 0xE8, 0xFF, 220);
    /** Umbral de una puerta. */
    public static final Color THRESHOLD = new Color(0xE2, 0xE8, 0xF0, 120);
    /** Flecha y letrero de la entrada. */
    public static final Color ENTRANCE = new Color(0x4A, 0xDE, 0x80);
    /** Texto de las etiquetas de las salas, sobre su color. */
    public static final Color TAG_TEXT = new Color(0x0B, 0x0F, 0x17);

    /** Armazón de las camas y camillas. */
    public static final Color BED_FRAME = new Color(0x9A, 0xA6, 0xB8);
    /** Colchón. */
    public static final Color MATTRESS = new Color(0xE6, 0xEC, 0xF4);
    /** Almohada. */
    public static final Color PILLOW = new Color(0xFF, 0xFF, 0xFF);
    /** Sillas. */
    public static final Color CHAIR = new Color(0x3C, 0x46, 0x5A);
    /** Asiento de las sillas, visto desde arriba. */
    public static final Color CHAIR_TOP = new Color(0x58, 0x64, 0x7C);
    /** Escritorios y mesas. */
    public static final Color DESK = new Color(0x3A, 0x44, 0x58);
    /** Cubierta de los escritorios y mesas. */
    public static final Color DESK_TOP = new Color(0x6B, 0x78, 0x91);
    /** Metal de los soportes, la lámpara y los monitores. */
    public static final Color METAL = new Color(0x9A, 0xA6, 0xB8);
    /** Acero de la mesa de operaciones y de la morgue. */
    public static final Color STEEL = new Color(0x87, 0x92, 0xA4);
    /** Pantallas y equipos oscuros. */
    public static final Color DEVICE = new Color(0x1C, 0x24, 0x33);
    /** Trazo verde de los monitores. */
    public static final Color MONITOR_LINE = new Color(0x34, 0xD3, 0x99);
    /** Luz de la lámpara del quirófano. */
    public static final Color LAMP_LIGHT = new Color(0xFF, 0xF3, 0xC4);
    /** Colchoneta verde agua de la mesa de operaciones. */
    public static final Color SURGICAL_PAD = new Color(0x7D, 0xD3, 0xC0);
    /** Nevera del banco de sangre. */
    public static final Color FRIDGE = new Color(0xC9, 0xD2, 0xDE);
    /** Vidrio de la puerta de la nevera. */
    public static final Color FRIDGE_GLASS = new Color(0x2A, 0x0B, 0x12);
    /** Bolsas de sangre. */
    public static final Color BLOOD_BAG = new Color(0xE1, 0x1D, 0x48);
    /** Sábana que cubre a un fallecido. */
    public static final Color SHEET = new Color(0xA3, 0xAB, 0xB8);
    /** Marcas de la fila del triage en el piso. */
    public static final Color QUEUE_MARK = new Color(255, 255, 255, 70);
    /** Sombra bajo las personas. */
    public static final Color SHADOW = new Color(0, 0, 0, 100);
    /** Contorno de las personas: casi negro, para que se vean sobre cualquier color. */
    public static final Color OUTLINE = new Color(0x05, 0x07, 0x0B, 235);
    /** Gorro de los médicos. */
    public static final Color DOCTOR_CAP = new Color(0x33, 0x41, 0x55);

    private IsoPalette() {
    }

    /**
     * Color de neón de un área: el de su borde y su etiqueta.
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
     * Color del piso de un área: los pasillos un poco más claros y la morgue un poco más oscura.
     *
     * @param zone tipo de zona
     * @return color del piso
     * @throws IllegalArgumentException si la zona es nula
     */
    public static Color floor(ZoneType zone) {
        if (zone == null) {
            throw new IllegalArgumentException("La zona es obligatoria.");
        }
        return switch (zone) {
            case CORRIDOR -> CORRIDOR_FLOOR;
            case MORGUE -> MORGUE_FLOOR;
            default -> FLOOR;
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
