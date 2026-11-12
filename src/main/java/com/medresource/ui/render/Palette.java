package com.medresource.ui.render;

import com.medresource.model.Specialty;
import com.medresource.model.TriageLevel;
import com.medresource.model.ZoneType;

import java.awt.Color;

/**
 * Colores de la interfaz del hospital, en un solo lugar para que todas las vistas se vean igual.
 *
 * <p>Cada zona tiene un color de fondo, cada nivel de triage un color de aviso (rojo para el más
 * urgente, azul para el menos urgente) y cada especialidad médica un color para el distintivo de la bata. La
 * salud de un paciente va de verde (bien) a rojo (grave), y el avance de su tratamiento se pinta en azul.</p>
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
    /** Silla de un médico o enfermero en la sala del personal. */
    public static final Color STAFF_SEAT = new Color(0x4F, 0x7C, 0xAC);
    /** Mesa de la sala del personal, con dos sillas. */
    public static final Color STAFF_TABLE = new Color(0xE6, 0xDC, 0xC8);
    /** Escritorio de un médico en los consultorios. */
    public static final Color CONSULT_DESK = new Color(0xF3, 0xEE, 0xFA);
    /** Puerta de una sala hacia un pasillo. */
    public static final Color DOOR = new Color(0x8D, 0x5B, 0x34);
    /** Marco de las puertas y línea que separa sus dos hojas: café más oscuro. */
    public static final Color DOOR_FRAME = new Color(0x5D, 0x3A, 0x1F);
    /** Sombra suave alrededor de cada puerta. */
    public static final Color DOOR_SHADOW = new Color(0x00, 0x00, 0x00, 70);
    /** Silla de la sala de espera. */
    public static final Color WAITING_SEAT = new Color(0xA1, 0x88, 0x7F);
    /** Puesto de triage. */
    public static final Color TRIAGE_STATION = new Color(0xE0, 0x7B, 0x39);
    /** Lugar de un tipo de sangre en el banco de sangre. */
    public static final Color BLOOD_UNIT = new Color(0xB7, 0x1C, 0x1C);
    /** Cabeza de los pacientes. */
    public static final Color PATIENT_HEAD = new Color(0xFF, 0xCC, 0x99);
    /** Cuerpo y cabeza de un paciente fallecido. */
    public static final Color DECEASED = new Color(0x75, 0x75, 0x75);
    /**
     * Contorno de las figuras (pacientes, médicos, enfermeros y camillero): gris carbón casi opaco, para que cada
     * figura se vea bien sobre cualquier color de sala.
     */
    public static final Color PATIENT_OUTLINE = new Color(0x26, 0x2B, 0x33, 230);
    /** Sombra suave bajo los pies de las figuras que están de pie. */
    public static final Color FIGURE_SHADOW = new Color(0x00, 0x00, 0x00, 55);
    /** Grosor del contorno de las figuras, en píxeles del mundo. */
    public static final float OUTLINE_WIDTH = 1.3f;
    /** Insignia con la cantidad de pacientes que no caben en la sala de espera. */
    public static final Color BADGE = new Color(0x21, 0x21, 0x21);
    /** Marca de la entrada de pacientes. */
    public static final Color ENTRANCE = new Color(0x2E, 0x9E, 0x4F);
    /** Bata de los médicos. */
    public static final Color DOCTOR_COAT = new Color(0xFA, 0xFA, 0xFA);
    /** Uniforme de los enfermeros: verde agua, lejos de los colores de triage para no confundirlos con pacientes. */
    public static final Color NURSE_UNIFORM = new Color(0x4D, 0xB6, 0xAC);

    /** Salud de {@value #GOOD_HEALTH} o más: verde. */
    public static final Color HEALTH_GOOD = new Color(0x43, 0xA0, 0x47);
    /** Salud desde {@value #FAIR_HEALTH} hasta antes de {@value #GOOD_HEALTH}: amarillo. */
    public static final Color HEALTH_FAIR = new Color(0xFD, 0xD8, 0x35);
    /** Salud desde {@value #POOR_HEALTH} hasta antes de {@value #FAIR_HEALTH}: naranja. */
    public static final Color HEALTH_POOR = new Color(0xFB, 0x8C, 0x00);
    /** Salud por debajo de {@value #POOR_HEALTH}: rojo. */
    public static final Color HEALTH_CRITICAL = new Color(0xE5, 0x39, 0x35);
    /** Fondo de las barras de salud y de avance, lo que todavía no se llena. */
    public static final Color BAR_TRACK = new Color(0x37, 0x47, 0x4F);
    /** Parte ya hecha del tratamiento. */
    public static final Color TREATMENT_PROGRESS = new Color(0x29, 0x79, 0xFF);

    /** Colchón de una camilla. */
    public static final Color STRETCHER = new Color(0xEC, 0xEF, 0xF1);
    /** Armazón y ruedas de una camilla. */
    public static final Color STRETCHER_FRAME = new Color(0x54, 0x6E, 0x7A);
    /** Uniforme del camillero que empuja la camilla: gris azulado, distinto del personal y de los pacientes. */
    public static final Color PORTER_UNIFORM = new Color(0x90, 0xA4, 0xAE);

    /** Anillo del paciente seleccionado: celeste brillante, distinto de los colores de triage y del personal. */
    public static final Color SELECTION = new Color(0x00, 0xE5, 0xFF);

    /** Desde esta salud el paciente está bien (verde). */
    public static final double GOOD_HEALTH = 70.0;
    /** Desde esta salud el paciente está regular (amarillo). */
    public static final double FAIR_HEALTH = 40.0;
    /** Desde esta salud el paciente está mal (naranja); por debajo, grave (rojo). */
    public static final double POOR_HEALTH = 20.0;

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
            case CONSULTATION -> new Color(0xD7, 0xCC, 0xEC);
            case CORRIDOR -> new Color(0xE4, 0xE4, 0xE0);
        };
    }

    /**
     * Color de aviso de un nivel de triage. Son colores vivos, casi los mismos de la ficha del paciente
     * (HomeTheme.triageColor), para que el paciente y su ficha se vean del mismo color. El verde del nivel IV es
     * un poco más oscuro que el de la ficha para no parecerse al uniforme de los enfermeros.
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
            case I -> new Color(0xE5, 0x48, 0x4D);
            case II -> new Color(0xF9, 0x73, 0x16);
            case III -> new Color(0xF5, 0xC5, 0x42);
            case IV -> new Color(0x2E, 0xA0, 0x43);
            case V -> new Color(0x4F, 0x8C, 0xFF);
        };
    }

    /**
     * Color de la salud de un paciente: verde si está bien, amarillo si está regular, naranja si está mal y rojo
     * si está grave.
     *
     * @param health salud, de 0 a 100
     * @return color de esa salud
     * @throws IllegalArgumentException si la salud no está entre 0 y 100
     */
    public static Color health(double health) {
        if (!(health >= 0.0 && health <= 100.0)) {
            throw new IllegalArgumentException("La salud debe estar entre 0 y 100.");
        }
        if (health >= GOOD_HEALTH) {
            return HEALTH_GOOD;
        }
        if (health >= FAIR_HEALTH) {
            return HEALTH_FAIR;
        }
        return health >= POOR_HEALTH ? HEALTH_POOR : HEALTH_CRITICAL;
    }

    /**
     * Color del distintivo de la bata de un médico según su especialidad.
     *
     * @param specialty especialidad médica
     * @return un color distinto para cada especialidad, distinto también de los colores de triage
     * @throws IllegalArgumentException si la especialidad es nula
     */
    public static Color specialty(Specialty specialty) {
        if (specialty == null) {
            throw new IllegalArgumentException("La especialidad es obligatoria.");
        }
        return switch (specialty) {
            case GENERAL_MEDICINE -> new Color(0x6D, 0x4C, 0x41);
            case PEDIATRICS -> new Color(0xF0, 0x62, 0x92);
            case INTERNAL_MEDICINE -> new Color(0x5E, 0x35, 0xB1);
            case SURGERY -> new Color(0x00, 0x83, 0x8F);
            case TRAUMATOLOGY -> new Color(0x45, 0x5A, 0x64);
            case EMERGENCY_MEDICINE -> new Color(0xC2, 0x18, 0x5B);
        };
    }
}
