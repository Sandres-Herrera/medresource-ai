package com.medresource.persistence;

/**
 * Servicio de IA que redacta un texto a partir de unas instrucciones.
 *
 * <p>La aplicación solo conoce esta interfaz: así se puede cambiar de proveedor sin tocar el resto, y las
 * pruebas usan una IA falsa que no necesita clave ni internet.</p>
 */
public interface AiClient {

    /**
     * Pide un texto a la IA.
     *
     * @param prompt instrucciones y datos
     * @return texto que respondió la IA
     * @throws AiClientException si la IA no respondió o su respuesta no sirve
     */
    String generate(String prompt);

    /**
     * Devuelve el nombre del modelo, para guardarlo con la respuesta.
     *
     * @return nombre del modelo
     */
    String modelName();
}
