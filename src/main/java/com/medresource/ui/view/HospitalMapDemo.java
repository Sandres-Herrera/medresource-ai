package com.medresource.ui.view;

import com.medresource.model.HospitalConfig;
import com.medresource.model.HospitalLayout;
import com.medresource.services.simulation.HospitalFactory;
import com.medresource.services.simulation.LayoutFactory;
import com.medresource.ui.render.Renderer2D;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Vista previa del plano del hospital: abre una ventana con la vista 2D.
 *
 * <p>Es un programa de prueba para ver el plano antes de que existan las ventanas de la aplicación. Sin
 * argumentos muestra el hospital estándar; con siete números muestra un hospital de ese tamaño, en este
 * orden: camas, camas de UCI, quirófanos, médicos, enfermeros, unidades de sangre por tipo y capacidad de la
 * morgue (por ejemplo {@code 60 12 8 32 48 20 10}).</p>
 */
public final class HospitalMapDemo {

    private static final int RESOURCE_COUNT = 7;

    private HospitalMapDemo() {
    }

    /**
     * Abre la ventana.
     *
     * @param args vacío para el hospital estándar, o los siete tamaños del hospital
     */
    public static void main(String[] args) {
        HospitalConfig config;
        try {
            config = configFrom(args);
        } catch (IllegalArgumentException error) {
            System.err.println(error.getMessage());
            return;
        }
        HospitalLayout layout = new LayoutFactory().create(new HospitalFactory().create(config));
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("MedResource AI — plano del hospital (vista previa)");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new HospitalMapPanel(layout, new Renderer2D()));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    /**
     * Interpreta los argumentos de la línea de comandos.
     *
     * @param args vacío para el hospital estándar, o siete números enteros
     * @return configuración del hospital
     * @throws IllegalArgumentException si no son cero ni siete argumentos, si alguno no es un número o si
     *                                  algún valor está fuera de su rango
     */
    static HospitalConfig configFrom(String[] args) {
        if (args.length == 0) {
            return HospitalConfig.standard();
        }
        if (args.length != RESOURCE_COUNT) {
            throw new IllegalArgumentException("Use 0 argumentos (hospital estándar) o " + RESOURCE_COUNT
                    + ": camas, UCI, quirófanos, médicos, enfermeros, sangre por tipo y morgue.");
        }
        int[] values = new int[RESOURCE_COUNT];
        for (int i = 0; i < RESOURCE_COUNT; i++) {
            try {
                values[i] = Integer.parseInt(args[i].trim());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("El argumento " + (i + 1) + " no es un número entero: " + args[i]);
            }
        }
        return new HospitalConfig(values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
    }
}