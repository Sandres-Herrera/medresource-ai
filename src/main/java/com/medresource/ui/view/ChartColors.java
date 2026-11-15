package com.medresource.ui.view;

import java.awt.Color;

/**
 * Colores con los que se dibujan las gráficas del reporte: los textos, el fondo de las barras y las marcas.
 *
 * <p>En pantalla se usan los del tema oscuro ({@link #SCREEN}); en el documento descargado se usan los de papel
 * ({@link #PAPER}), con textos oscuros sobre fondo blanco. Los colores de cada barra no cambian: son los datos.</p>
 *
 * @param text   valores escritos a la derecha de las barras
 * @param muted  nombres de las barras y horas de la línea de tiempo
 * @param faint  rayitas de las horas
 * @param track  fondo de cada barra
 * @param marker marca del final de las llegadas
 */
public record ChartColors(Color text, Color muted, Color faint, Color track, Color marker) {

    /** Colores del tema oscuro de la ventana. */
    public static final ChartColors SCREEN = new ChartColors(HomeTheme.TEXT, HomeTheme.MUTED, HomeTheme.FAINT,
            MeterBar.TRACK, HomeTheme.AMBER);
    /** Colores para imprimir: textos oscuros sobre fondo blanco. */
    public static final ChartColors PAPER = new ChartColors(new Color(0x11, 0x18, 0x27), new Color(0x37, 0x41, 0x51),
            new Color(0x9C, 0xA3, 0xAF), new Color(0xE5, 0xE7, 0xEB), new Color(0xB4, 0x53, 0x09));

    /**
     * Valida que no falte ningún color.
     *
     * @throws IllegalArgumentException si falta alguno
     */
    public ChartColors {
        if (text == null || muted == null || faint == null || track == null || marker == null) {
            throw new IllegalArgumentException("Faltan colores de la gráfica.");
        }
    }
}
