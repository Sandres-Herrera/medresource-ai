package com.medresource.model;

import java.util.List;
import java.util.Optional;

/**
 * Comparación de dos ejecuciones del mismo caso: qué cambió entre ellas, sus indicadores lado a lado, una
 * conclusión basada en esos números y las limitaciones que hay que tener en cuenta al leerla.
 *
 * <p>La primera ejecución (A) es la de referencia, normalmente la original; la segunda (B) es la que se mide
 * contra ella, por ejemplo la que prueba las recomendaciones del reporte. Una comparación no es una simulación
 * nueva: solo pone dos ejecuciones guardadas una al lado de la otra.</p>
 *
 * @param caseId      caso de las dos ejecuciones
 * @param runA        identificador de la ejecución de referencia
 * @param runB        identificador de la ejecución que se compara con ella
 * @param strategyA   estrategia de la ejecución A
 * @param strategyB   estrategia de la ejecución B
 * @param type        qué cambió: la estrategia, los recursos o las dos cosas
 * @param changes     cada dato de la configuración que cambió, de A a B
 * @param indicators  indicadores de las dos ejecuciones, en el orden en que se muestran
 * @param conclusion  lectura de los números en lenguaje sencillo
 * @param limitations advertencias para leer la comparación
 */
public record RunComparison(int caseId, int runA, int runB, String strategyA, String strategyB,
                            ComparisonType type, List<SettingChange> changes, List<IndicatorComparison> indicators,
                            String conclusion, List<String> limitations) {

    /**
     * Valida los datos y copia las listas.
     *
     * @throws IllegalArgumentException si algún identificador no es positivo, si las dos ejecuciones son la misma,
     *                                  si falta algún dato o si no hay cambios o indicadores
     */
    public RunComparison {
        if (caseId < 1 || runA < 1 || runB < 1) {
            throw new IllegalArgumentException("El caso y las ejecuciones necesitan identificadores positivos.");
        }
        if (runA == runB) {
            throw new IllegalArgumentException("Una ejecución no se compara consigo misma.");
        }
        if (strategyA == null || strategyA.isBlank() || strategyB == null || strategyB.isBlank() || type == null
                || changes == null || indicators == null || conclusion == null || conclusion.isBlank()
                || limitations == null) {
            throw new IllegalArgumentException("Faltan datos de la comparación.");
        }
        if (changes.isEmpty() || indicators.isEmpty()) {
            throw new IllegalArgumentException("Una comparación necesita al menos un cambio y un indicador.");
        }
        changes = List.copyOf(changes);
        indicators = List.copyOf(indicators);
        limitations = List.copyOf(limitations);
    }

    /**
     * Busca un indicador por su nombre.
     *
     * @param name nombre del indicador
     * @return el indicador, o vacío si no está
     */
    public Optional<IndicatorComparison> indicator(String name) {
        return indicators.stream().filter(indicator -> indicator.name().equals(name)).findFirst();
    }

    /**
     * Título corto para las listas, por ejemplo «Ejecución #1 vs #2 · Estrategias».
     *
     * @return el título
     */
    public String title() {
        return "Ejecución #" + runA + " vs #" + runB + " · " + type.getDisplayName();
    }
}
