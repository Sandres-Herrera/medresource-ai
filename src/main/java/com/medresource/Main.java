package com.medresource;

/**
 * Punto de entrada de MedResource AI.
 *
 * <p>Por ahora solo verifica que el entorno esté listo; en pasos posteriores
 * abrirá la ventana principal de la aplicación.</p>
 */
public class Main {

    private static final String APP_NAME = "MedResource AI";

    /**
     * Inicia la aplicación.
     *
     * @param args argumentos de la línea de comandos (no se usan)
     */
    public static void main(String[] args) {
        System.out.println(APP_NAME + " — entorno listo.");
        System.out.println("Versión de Java: " + Runtime.version());
    }
}