package com.medresource.ui.render;

import com.medresource.model.ZoneType;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * Tema de la vista 2.5D: los colores del terreno, los pisos, los muros, las puertas, los letreros y los muebles.
 *
 * <p>Hay dos: {@link #LIGHT} (baldosas blancas, muros azules cortados a media altura, césped y árboles) y
 * {@link #DARK} (la misma escena de noche, con muros azul marino, luz de lámparas en el piso y faroles encendidos).
 * Cambiar de tema solo cambia los colores: el edificio, los muebles y las personas son los mismos. Las personas
 * tampoco cambian de color: el color del cuerpo dice quién es en los dos temas ({@link Palette}).</p>
 */
public enum IsoTheme {

    /** Tema claro: «Clínica clara». */
    LIGHT("Claro", false),
    /** Tema oscuro: «Clínica nocturna». */
    DARK("Oscuro", true);

    /** Partes de la vista que cambian de color con el tema. */
    public enum Part {
        /** Fondo del panel alrededor del terreno. */
        BACKGROUND,
        /** Césped del terreno. */
        GRASS,
        /** Manchas del césped, un poco más oscuras. */
        GRASS_SHADE,
        /** Tierra del borde izquierdo del terreno. */
        SOIL_LEFT,
        /** Tierra del borde derecho del terreno. */
        SOIL_RIGHT,
        /** Acera alrededor del edificio. */
        SIDEWALK,
        /** Vía de acceso. */
        ROAD,
        /** Marcas pintadas en la vía y letreros del piso. */
        ROAD_MARK,
        /** Líneas entre las baldosas. */
        GROUT,
        /** Borde del piso del pasillo junto a los muros. */
        CORRIDOR_BAND,
        /** Cara izquierda de los muros. */
        WALL_LEFT,
        /** Cara derecha de los muros. */
        WALL_RIGHT,
        /** Remate de arriba de los muros. */
        WALL_TOP,
        /** Zócalo de los muros. */
        BASEBOARD,
        /** Enchape de baldosa de las paredes por dentro. */
        WALL_TILE,
        /** Líneas del enchape. */
        WALL_TILE_GROUT,
        /** Vidrio de los ventanales que dan al pasillo. */
        WINDOW,
        /** Marco y parteluz de los ventanales. */
        WINDOW_FRAME,
        /** Vidrio de las puertas corredizas. */
        DOOR_GLASS,
        /** Marco de las puertas. */
        DOOR_FRAME,
        /** Tapete frente a cada puerta. */
        DOOR_MAT,
        /** Números, pictogramas y nombres pintados en las paredes. */
        SIGN,
        /** Sombra al pie de los muros. */
        OCCLUSION,
        /** Tronco de los árboles. */
        TRUNK,
        /** Hojas de los árboles y arbustos. */
        LEAF,
        /** Poste de los faroles. */
        LAMP_POST,
        /** Luz de los faroles y de las lámparas del techo. */
        LAMP_LIGHT,
        /** Armazón de las camas y camillas. */
        BED_FRAME,
        /** Colchón. */
        MATTRESS,
        /** Almohada. */
        PILLOW,
        /** Sillas. */
        CHAIR,
        /** Asiento de las sillas, visto desde arriba. */
        CHAIR_TOP,
        /** Escritorios y mesas. */
        DESK,
        /** Cubierta de los escritorios y mesas. */
        DESK_TOP,
        /** Metal de soportes, patas y lámparas. */
        METAL,
        /** Acero de la mesa de operaciones y de la morgue. */
        STEEL,
        /** Pantallas y equipos. */
        DEVICE,
        /** Gabinetes, lavamanos y vitrinas. */
        CABINET,
        /** Cubierta de los gabinetes. */
        COUNTER_TOP,
        /** Madera de estantes y bancas. */
        WOOD,
        /** Sofá de la sala del personal. */
        SOFA,
        /** Neveras del banco de sangre. */
        FRIDGE,
        /** Materas. */
        POT
    }

    private final String displayName;
    private final boolean dark;
    private final Map<Part, Color> colors = new EnumMap<>(Part.class);
    private final Map<ZoneType, Color> floors = new EnumMap<>(ZoneType.class);

    IsoTheme(String displayName, boolean dark) {
        this.displayName = displayName;
        this.dark = dark;
        if (dark) {
            fillDark();
        } else {
            fillLight();
        }
    }

    private void fillLight() {
        put(Part.BACKGROUND, 0xEEF2F7);
        put(Part.GRASS, 0x9CCB6E);
        put(Part.GRASS_SHADE, 0x86B85A);
        put(Part.SOIL_LEFT, 0x7A5C3E);
        put(Part.SOIL_RIGHT, 0x5E452D);
        put(Part.SIDEWALK, 0xD9DDE3);
        put(Part.ROAD, 0x5B6270);
        put(Part.ROAD_MARK, 0xF8FAFC);
        colors.put(Part.GROUT, new Color(255, 255, 255, 220));
        put(Part.CORRIDOR_BAND, 0x9DB6DE);
        put(Part.WALL_LEFT, 0x86A6D8);
        put(Part.WALL_RIGHT, 0x6F90C6);
        put(Part.WALL_TOP, 0xF4F7FB);
        put(Part.BASEBOARD, 0x5B79A8);
        put(Part.WALL_TILE, 0xD9DEE4);
        put(Part.WALL_TILE_GROUT, 0xBAC2CC);
        colors.put(Part.WINDOW, new Color(0xCF, 0xEA, 0xF5, 140));
        put(Part.WINDOW_FRAME, 0xE8EEF6);
        colors.put(Part.DOOR_GLASS, new Color(0xBF, 0xE3, 0xF5, 115));
        put(Part.DOOR_FRAME, 0x64748B);
        put(Part.DOOR_MAT, 0x94A3B8);
        put(Part.SIGN, 0xFFFFFF);
        colors.put(Part.OCCLUSION, new Color(0, 0, 0, 26));
        put(Part.TRUNK, 0x7A5534);
        put(Part.LEAF, 0x5FA34A);
        put(Part.LAMP_POST, 0x475569);
        put(Part.LAMP_LIGHT, 0xFDE68A);
        put(Part.BED_FRAME, 0xCBD5E1);
        put(Part.MATTRESS, 0xF8FAFC);
        put(Part.PILLOW, 0xFFFFFF);
        put(Part.CHAIR, 0x4F6D9A);
        put(Part.CHAIR_TOP, 0x6F8DBB);
        put(Part.DESK, 0x9CA3AF);
        put(Part.DESK_TOP, 0xEEF0F3);
        put(Part.METAL, 0x94A3B8);
        put(Part.STEEL, 0xCBD5E1);
        put(Part.DEVICE, 0x1F2937);
        put(Part.CABINET, 0xE2E8F0);
        put(Part.COUNTER_TOP, 0xF8FAFC);
        put(Part.WOOD, 0xA87C52);
        put(Part.SOFA, 0x64748B);
        put(Part.FRIDGE, 0xE2E8F0);
        put(Part.POT, 0xA16207);
        floor(ZoneType.TRIAGE, 0xE8ECF1);
        floor(ZoneType.WAITING_ROOM, 0xECEEF2);
        floor(ZoneType.HOSPITALIZATION, 0xE7EBF0);
        floor(ZoneType.ICU, 0xE9EBF1);
        floor(ZoneType.OPERATING_ROOM, 0xE6ECEC);
        floor(ZoneType.BLOOD_BANK, 0xECE9EC);
        floor(ZoneType.MORGUE, 0xE3E5E8);
        floor(ZoneType.DISCHARGE, 0xE7EDE9);
        floor(ZoneType.STAFF_ROOM, 0xEFEBE5);
        floor(ZoneType.CONSULTATION, 0xEAEDF1);
        floor(ZoneType.CORRIDOR, 0xF3F5F8);
    }

    private void fillDark() {
        put(Part.BACKGROUND, 0x0A0E15);
        put(Part.GRASS, 0x22392A);
        put(Part.GRASS_SHADE, 0x1C3123);
        put(Part.SOIL_LEFT, 0x2A2119);
        put(Part.SOIL_RIGHT, 0x1E1812);
        put(Part.SIDEWALK, 0x2A313C);
        put(Part.ROAD, 0x171B22);
        put(Part.ROAD_MARK, 0xCBD5E1);
        colors.put(Part.GROUT, new Color(255, 255, 255, 18));
        put(Part.CORRIDOR_BAND, 0x3B5B92);
        put(Part.WALL_LEFT, 0x2F4B7C);
        put(Part.WALL_RIGHT, 0x243C66);
        put(Part.WALL_TOP, 0xA9C2EA);
        put(Part.BASEBOARD, 0x1B2D4F);
        put(Part.WALL_TILE, 0x3A4250);
        put(Part.WALL_TILE_GROUT, 0x2B313C);
        colors.put(Part.WINDOW, new Color(0x5B, 0x8F, 0xC7, 72));
        put(Part.WINDOW_FRAME, 0x8FA8CF);
        colors.put(Part.DOOR_GLASS, new Color(0x7D, 0xB4, 0xE6, 76));
        put(Part.DOOR_FRAME, 0x94A3B8);
        put(Part.DOOR_MAT, 0x3B4352);
        put(Part.SIGN, 0xDCE6F5);
        colors.put(Part.OCCLUSION, new Color(0, 0, 0, 72));
        put(Part.TRUNK, 0x4A3524);
        put(Part.LEAF, 0x2F5A37);
        put(Part.LAMP_POST, 0x334155);
        put(Part.LAMP_LIGHT, 0xFFE9B0);
        put(Part.BED_FRAME, 0x7C8796);
        put(Part.MATTRESS, 0xC9D2DE);
        put(Part.PILLOW, 0xE5EAF1);
        put(Part.CHAIR, 0x3C4A63);
        put(Part.CHAIR_TOP, 0x56657F);
        put(Part.DESK, 0x3F4757);
        put(Part.DESK_TOP, 0x677186);
        put(Part.METAL, 0x7C8796);
        put(Part.STEEL, 0x5B6576);
        put(Part.DEVICE, 0x0B0F17);
        put(Part.CABINET, 0x3F4757);
        put(Part.COUNTER_TOP, 0x677186);
        put(Part.WOOD, 0x5C4430);
        put(Part.SOFA, 0x3B4762);
        put(Part.FRIDGE, 0x475163);
        put(Part.POT, 0x5C4430);
        floor(ZoneType.TRIAGE, 0x272C3E);
        floor(ZoneType.WAITING_ROOM, 0x2B2C34);
        floor(ZoneType.HOSPITALIZATION, 0x22313B);
        floor(ZoneType.ICU, 0x2A273E);
        floor(ZoneType.OPERATING_ROOM, 0x1E3439);
        floor(ZoneType.BLOOD_BANK, 0x34242C);
        floor(ZoneType.MORGUE, 0x1F2127);
        floor(ZoneType.DISCHARGE, 0x203327);
        floor(ZoneType.STAFF_ROOM, 0x302B25);
        floor(ZoneType.CONSULTATION, 0x272D3B);
        floor(ZoneType.CORRIDOR, 0x2D333F);
    }

    private void put(Part part, int rgb) {
        colors.put(part, new Color(rgb));
    }

    private void floor(ZoneType zone, int rgb) {
        floors.put(zone, new Color(rgb));
    }

    /** Nombre que se muestra en el selector, por ejemplo «Claro». */
    public String getDisplayName() {
        return displayName;
    }

    /** Indica si es el tema oscuro: de noche se ve la luz de las lámparas y de los faroles. */
    public boolean isDark() {
        return dark;
    }

    /**
     * Color de una parte de la vista.
     *
     * @param part parte
     * @return su color en este tema
     * @throws IllegalArgumentException si la parte es nula
     */
    public Color color(Part part) {
        if (part == null) {
            throw new IllegalArgumentException("La parte es obligatoria.");
        }
        return colors.get(part);
    }

    /**
     * Color del piso de un área.
     *
     * @param zone tipo de zona
     * @return color de sus baldosas en este tema
     * @throws IllegalArgumentException si la zona es nula
     */
    public Color floor(ZoneType zone) {
        if (zone == null) {
            throw new IllegalArgumentException("La zona es obligatoria.");
        }
        return floors.get(zone);
    }
}
