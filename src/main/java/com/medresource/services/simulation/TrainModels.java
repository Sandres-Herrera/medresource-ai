package com.medresource.services.simulation;

import com.medresource.model.HospitalConfig;
import com.medresource.model.Objective;
import com.medresource.services.strategy.ModelStore;
import com.medresource.services.strategy.TrainedModel;
import com.medresource.services.strategy.TrainingSettings;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Programa que entrena la red de cada objetivo con el hospital estándar y guarda los pesos.
 *
 * <p>Se ejecuta una sola vez (o cuando cambie el simulador): escribe un archivo por objetivo
 * en la carpeta {@code models} y muestra cuánto error tuvo cada red al entrenar y al evaluarla
 * con casos que no vio. Si se le pasa un argumento, ese es la carpeta de destino.</p>
 */
public final class TrainModels {

    private TrainModels() {
    }

    /**
     * Entrena y guarda las redes.
     *
     * @param args opcional: carpeta donde guardar las redes (por defecto {@code models})
     */
    public static void main(String[] args) {
        Path directory = args.length > 0 ? Path.of(args[0]) : ModelStore.DEFAULT_DIRECTORY;
        run(directory, TrainingSettings.standard(), System.out);
    }

    /**
     * Entrena una red por objetivo, las guarda y escribe un resumen.
     *
     * @param directory carpeta donde se guardan las redes
     * @param settings  parámetros del entrenamiento
     * @param out       donde se escribe el resumen
     * @throws IllegalArgumentException si algún argumento es nulo
     */
    static void run(Path directory, TrainingSettings settings, PrintStream out) {
        if (directory == null || settings == null || out == null) {
            throw new IllegalArgumentException("La carpeta, los parámetros y la salida son obligatorios.");
        }
        out.println("Entrenando una red por objetivo (puede tardar un minuto)...");
        ModelTrainingService service = new ModelTrainingService(HospitalConfig.standard(), settings);
        List<TrainedModel> models = service.trainAll(new ModelStore(directory));
        for (TrainedModel model : models) {
            Objective objective = model.objective();
            out.println(String.format(Locale.ROOT, "%s: %d ejemplos, error de entrenamiento %.4f -> %.4f, error de evaluación %.4f",
                    objective.name(), model.report().examples(), model.report().firstLoss(),
                    model.trainingLoss(), model.evaluationLoss()));
        }
        out.println("Redes guardadas en " + directory.toAbsolutePath());
    }
}