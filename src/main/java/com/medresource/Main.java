package com.medresource;

import com.medresource.ui.view.MainWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.GraphicsEnvironment;

/**
 * Punto de entrada de MedResource AI: abre la ventana principal de la aplicación.
 *
 * <p>Con el argumento {@value #CHECK_ARGUMENT}, o si no hay pantalla, solo muestra la versión de Java y
 * termina; así lo usan las pruebas.</p>
 */
public class Main {

    /** Argumento que solo verifica el entorno, sin abrir ventanas. */
    public static final String CHECK_ARGUMENT = "--check";

    private static final String APP_NAME = "MedResource AI";

    /**
     * Inicia la aplicación.
     *
     * @param args {@value #CHECK_ARGUMENT} para solo verificar el entorno
     */
    public static void main(String[] args) {
        boolean onlyCheck = args.length > 0 && CHECK_ARGUMENT.equals(args[0]);
        if (onlyCheck || GraphicsEnvironment.isHeadless()) {
            System.out.println(APP_NAME + " — entorno listo.");
            System.out.println("Versión de Java: " + Runtime.version());
            return;
        }
        SwingUtilities.invokeLater(() -> {
            useSystemLookAndFeel();
            new MainWindow().setVisible(true);
        });
    }

    private static void useSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException error) {
            // Si no se puede, se queda el aspecto por defecto de Swing.
        }
    }
}
