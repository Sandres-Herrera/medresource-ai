package com.medresource.ui.view;

import com.medresource.model.Scenario;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

/**
 * Colores y letras de la pantalla de inicio, en un solo lugar para que todas sus piezas se vean igual.
 *
 * <p>Es un tema oscuro: fondo azul noche, tarjetas un poco más claras con un borde fino y un color de acento
 * verde agua (el del logo, el botón principal y la palabra «AI»). Cada opción tiene además su propio color: azul
 * para la simulación rápida y rojo para la demostración de catástrofe.</p>
 *
 * <p>El formulario de una simulación nueva usa el mismo tema: sus secciones son tarjetas, sus campos tienen un
 * fondo más oscuro y cada escenario lleva su color ({@link #scenarioColor(Scenario)}).</p>
 *
 * <p>Las letras usan la primera familia moderna que tenga el equipo (Segoe UI en Windows, Inter o Helvetica
 * Neue en otros sistemas) y, si no hay ninguna, la sans serif de Java.</p>
 */
public final class HomeTheme {

    /** Fondo de la pantalla. */
    public static final Color BACKGROUND = new Color(0x0B, 0x11, 0x20);
    /** Resplandor verde agua detrás del logo, sobre el fondo. */
    public static final Color GLOW = new Color(0x2D, 0xD4, 0xBF, 46);
    /** Fondo de una tarjeta de opción. */
    public static final Color CARD = new Color(0x11, 0x1A, 0x2E);
    /** Fondo de una tarjeta con el ratón encima. */
    public static final Color CARD_HOVER = new Color(0x17, 0x23, 0x3B);
    /** Borde fino de las tarjetas. */
    public static final Color CARD_BORDER = new Color(0x1F, 0x2B, 0x45);
    /** Color de acento: logo, botón principal y la palabra «AI». */
    public static final Color ACCENT = new Color(0x2D, 0xD4, 0xBF);
    /** Acento más claro, para el botón principal con el ratón encima. */
    public static final Color ACCENT_HOVER = new Color(0x5E, 0xEA, 0xD4);
    /** Texto sobre el acento: casi negro verdoso, para que se lea bien. */
    public static final Color ACCENT_TEXT = new Color(0x04, 0x2F, 0x2A);
    /** Fondo del cuadro del logo. */
    public static final Color LOGO_TILE = new Color(0x0F, 0x2A, 0x2E);
    /** Texto principal. */
    public static final Color TEXT = new Color(0xF1, 0xF5, 0xF9);
    /** Texto secundario: subtítulos, datos y enlaces. */
    public static final Color MUTED = new Color(0x94, 0xA3, 0xB8);
    /** Texto más tenue: el pie de la pantalla. */
    public static final Color FAINT = new Color(0x5B, 0x6B, 0x84);
    /** Icono de la simulación rápida. */
    public static final Color QUICK = new Color(0x60, 0xA5, 0xFA);
    /** Cuadro del icono de la simulación rápida. */
    public static final Color QUICK_TILE = new Color(0x1C, 0x2A, 0x55);
    /** Icono y etiqueta de la demostración de catástrofe. */
    public static final Color DANGER = new Color(0xF8, 0x71, 0x71);
    /** Cuadro del icono y fondo de la etiqueta de la demostración. */
    public static final Color DANGER_TILE = new Color(0x3F, 0x16, 0x1F);
    /** Fondo de las etiquetas con los datos del hospital. */
    public static final Color CHIP = new Color(0x13, 0x1E, 0x33);
    /** Fondo de una opción elegida: el acento muy oscuro. */
    public static final Color SELECTED = new Color(0x0F, 0x2A, 0x2E);
    /** Fondo de los campos donde se escriben números. */
    public static final Color FIELD = new Color(0x0B, 0x11, 0x20);
    /** Línea que separa el pie del formulario. */
    public static final Color DIVIDER = new Color(0x1F, 0x2B, 0x45);
    /** Escenario de alta demanda: ámbar. */
    public static final Color AMBER = new Color(0xFB, 0xBF, 0x24);
    /** Escenario de emergencias inesperadas: violeta. */
    public static final Color VIOLET = new Color(0xA7, 0x8B, 0xFA);

    /**
     * Color de un escenario en el formulario: verde agua el normal, ámbar la alta demanda, rojo la catástrofe y
     * violeta las emergencias inesperadas.
     *
     * @param scenario escenario
     * @return su color
     * @throws IllegalArgumentException si el escenario es nulo
     */
    public static Color scenarioColor(Scenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("El escenario es obligatorio.");
        }
        return switch (scenario) {
            case NORMAL -> ACCENT;
            case HIGH_DEMAND -> AMBER;
            case CATASTROPHE -> DANGER;
            case UNEXPECTED_EMERGENCIES -> VIOLET;
        };
    }

    /** Familias de letra preferidas, en orden. */
    private static final List<String> PREFERRED_FAMILIES = List.of("Segoe UI", "Inter", "Helvetica Neue");

    private static String family;

    private HomeTheme() {
    }

    /**
     * Letra de la pantalla de inicio.
     *
     * @param style {@link Font#PLAIN}, {@link Font#BOLD} o {@link Font#ITALIC}
     * @param size  tamaño en puntos, mayor que cero
     * @return letra de la familia preferida que tenga el equipo
     * @throws IllegalArgumentException si el tamaño no es positivo
     */
    public static Font font(int style, float size) {
        if (!(size > 0)) {
            throw new IllegalArgumentException("El tamaño de la letra debe ser positivo.");
        }
        return new Font(family(), style, 1).deriveFont(style, size);
    }

    /** Familia de letra que se usa: la primera preferida que esté instalada, o la sans serif de Java. */
    public static synchronized String family() {
        if (family == null) {
            List<String> installed = installedFamilies();
            family = PREFERRED_FAMILIES.stream().filter(installed::contains).findFirst().orElse(Font.SANS_SERIF);
        }
        return family;
    }

    private static List<String> installedFamilies() {
        try {
            return Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        } catch (RuntimeException | Error noFonts) {
            return List.of();
        }
    }
}
