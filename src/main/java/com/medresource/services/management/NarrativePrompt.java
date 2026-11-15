package com.medresource.services.management;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.medresource.model.ReportSection;

import java.util.StringJoiner;

/**
 * Instrucciones para que la IA redacte el texto del reporte a partir de sus hechos.
 *
 * <p>Le pide explicar los números, no calcularlos: usar solo las cifras y horas de los hechos, explicar las
 * recomendaciones que ya decidió el código en su mismo orden y sin agregar otras, y responder solo con un JSON
 * con el resumen, un texto por sección ({@link ReportSection#getKey()}), una explicación por recomendación y la
 * conclusión.</p>
 */
public final class NarrativePrompt {

    private NarrativePrompt() {
    }

    /**
     * Escribe las instrucciones.
     *
     * @param facts           hechos del reporte ({@link ReportFacts#of})
     * @param recommendations cantidad de recomendaciones que hay que explicar
     * @return instrucciones en español, con los hechos al final
     * @throws IllegalArgumentException si faltan los hechos o la cantidad es negativa
     */
    public static String of(JsonObject facts, int recommendations) {
        if (facts == null || recommendations < 0) {
            throw new IllegalArgumentException("Los hechos son obligatorios y las recomendaciones no son negativas.");
        }
        StringJoiner sections = new StringJoiner(",\n");
        for (ReportSection section : ReportSection.values()) {
            sections.add("    \"" + section.getKey() + "\": \"2 a 4 frases sobre " + section.getTitle().toLowerCase()
                    + "\"");
        }
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(facts);
        return """
                Eres un analista de gestión hospitalaria. Redacta el texto del reporte de fin de una simulación \
                del servicio de urgencias para el director del hospital. Los números ya están calculados en los \
                HECHOS: tu trabajo es explicarlos y relacionar sus causas.

                Reglas:
                1. Usa solo las cifras y horas que aparecen en los HECHOS, escritas igual (puedes usar coma \
                decimal: 45,9). No calcules, no redondees ni inventes cifras, porcentajes ni horas.
                2. No recomiendes nada que no esté en "analisis_y_si.recomendaciones". Explica cada una, en el \
                mismo orden, con 1 o 2 frases: por qué ayuda según los cuellos de botella y la carga del personal.
                3. Relaciona las causas: qué recurso faltó, cuándo y a quiénes afectó.
                4. Escribe en español, en tono de informe técnico, con frases cortas, sin markdown ni viñetas.
                5. No menciones que eres una IA ni hables de los HECHOS como un archivo.
                6. Los códigos como P024 o D03 identifican pacientes y médicos; puedes nombrarlos.
                7. Las horas (hh:mm) son del reloj de la simulación, que empieza a las 00:00. \
                "duracion_de_las_llegadas" es la hora hasta la que llegaron pacientes: escribe "hasta las" y esa \
                hora, nunca "en" y esa hora.

                Responde solo con un JSON con esta forma exacta:
                {
                  "resumen": "3 a 5 frases con lo más importante",
                  "secciones": {
                %s
                  },
                  "recomendaciones": [%d textos, uno por recomendación, en el mismo orden],
                  "conclusion": "2 o 3 frases"
                }

                HECHOS:
                %s
                """.formatted(sections, recommendations, json);
    }
}
