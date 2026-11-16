-- ============================================================================
-- MedResource AI - funciones, procedimientos y triggers PL/SQL
--
-- Se ejecuta después de schema.sql, con el mismo usuario. En SQL Developer:
-- abrir el archivo y ejecutarlo como script (F5). Cada unidad termina en una
-- línea con solo «/». Se puede volver a ejecutar: todo es CREATE OR REPLACE.
--
-- Las funciones calculan los indicadores de una ejecución con las mismas
-- definiciones que la aplicación (clase RunIndicators):
--   - atendido: minuto_inicio_atencion distinto de -1;
--   - sin atención: estado = 'UNATTENDED';
--   - espera: desde la llegada hasta el inicio de la atención, o hasta el
--     desenlace si nunca lo atendieron.
-- Si la ejecución no existe, lanzan el error ORA-20002.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Comprueba que una ejecución existe (ORA-20002 si no).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE pr_exigir_ejecucion (p_id_ejecucion IN NUMBER) IS
    v_cantidad NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cantidad FROM ejecucion WHERE id = p_id_ejecucion;
    IF v_cantidad = 0 THEN
        RAISE_APPLICATION_ERROR(-20002, 'No existe la ejecución #' || p_id_ejecucion || '.');
    END IF;
END pr_exigir_ejecucion;
/

-- ---------------------------------------------------------------------------
-- Espera promedio de una ejecución, en minutos (0 si no tiene pacientes).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_espera_promedio (p_id_ejecucion IN NUMBER) RETURN NUMBER IS
    v_promedio NUMBER;
BEGIN
    pr_exigir_ejecucion(p_id_ejecucion);
    SELECT AVG(CASE WHEN minuto_inicio_atencion >= 0 THEN minuto_inicio_atencion - minuto_llegada
                    ELSE minuto_desenlace - minuto_llegada END)
      INTO v_promedio
      FROM resultado_paciente
     WHERE id_ejecucion = p_id_ejecucion;
    RETURN NVL(v_promedio, 0);
END fn_espera_promedio;
/

-- ---------------------------------------------------------------------------
-- Espera más larga de una ejecución, en minutos (0 si no tiene pacientes).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_espera_maxima (p_id_ejecucion IN NUMBER) RETURN NUMBER IS
    v_maxima NUMBER;
BEGIN
    pr_exigir_ejecucion(p_id_ejecucion);
    SELECT MAX(CASE WHEN minuto_inicio_atencion >= 0 THEN minuto_inicio_atencion - minuto_llegada
                    ELSE minuto_desenlace - minuto_llegada END)
      INTO v_maxima
      FROM resultado_paciente
     WHERE id_ejecucion = p_id_ejecucion;
    RETURN NVL(v_maxima, 0);
END fn_espera_maxima;
/

-- ---------------------------------------------------------------------------
-- Porcentaje de pacientes que empezaron a recibir atención (0 a 100).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_porcentaje_atendidos (p_id_ejecucion IN NUMBER) RETURN NUMBER IS
    v_total     NUMBER;
    v_atendidos NUMBER;
BEGIN
    pr_exigir_ejecucion(p_id_ejecucion);
    SELECT COUNT(*), NVL(SUM(CASE WHEN minuto_inicio_atencion >= 0 THEN 1 ELSE 0 END), 0)
      INTO v_total, v_atendidos
      FROM resultado_paciente
     WHERE id_ejecucion = p_id_ejecucion;
    IF v_total = 0 THEN
        RETURN 0;
    END IF;
    RETURN 100 * v_atendidos / v_total;
END fn_porcentaje_atendidos;
/

-- ---------------------------------------------------------------------------
-- Mortalidad de una ejecución: fallecidos sobre pacientes (0 a 100).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_tasa_mortalidad (p_id_ejecucion IN NUMBER) RETURN NUMBER IS
    v_total      NUMBER;
    v_fallecidos NUMBER;
BEGIN
    pr_exigir_ejecucion(p_id_ejecucion);
    SELECT COUNT(*), NVL(SUM(CASE WHEN estado = 'DECEASED' THEN 1 ELSE 0 END), 0)
      INTO v_total, v_fallecidos
      FROM resultado_paciente
     WHERE id_ejecucion = p_id_ejecucion;
    IF v_total = 0 THEN
        RETURN 0;
    END IF;
    RETURN 100 * v_fallecidos / v_total;
END fn_tasa_mortalidad;
/

-- ---------------------------------------------------------------------------
-- Pacientes que se quedaron sin atención (estado UNATTENDED).
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_pacientes_sin_atencion (p_id_ejecucion IN NUMBER) RETURN NUMBER IS
    v_cantidad NUMBER;
BEGIN
    pr_exigir_ejecucion(p_id_ejecucion);
    SELECT COUNT(*) INTO v_cantidad
      FROM resultado_paciente
     WHERE id_ejecucion = p_id_ejecucion AND estado = 'UNATTENDED';
    RETURN v_cantidad;
END fn_pacientes_sin_atencion;
/

-- ---------------------------------------------------------------------------
-- Médicos extra de una ejecución, en orden y separados por comas
-- ('-' si no tiene). Sirve para comparar los recursos de dos ejecuciones.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_medicos_extra (p_id_ejecucion IN NUMBER) RETURN VARCHAR2 IS
    v_lista VARCHAR2(1000);
BEGIN
    SELECT LISTAGG(especialidad, ',') WITHIN GROUP (ORDER BY orden_medico)
      INTO v_lista
      FROM ejecucion_medico_extra
     WHERE id_ejecucion = p_id_ejecucion;
    RETURN NVL(v_lista, '-');
END fn_medicos_extra;
/

-- ---------------------------------------------------------------------------
-- Resumen de una ejecución, para verlo en SQL Developer (con SET SERVEROUTPUT
-- ON) o para leerlo desde un programa con los parámetros de salida.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE pr_resumen_simulacion (
    p_id_ejecucion    IN  NUMBER,
    p_pacientes       OUT NUMBER,
    p_atendidos       OUT NUMBER,
    p_fallecidos      OUT NUMBER,
    p_sin_atencion    OUT NUMBER,
    p_espera_promedio OUT NUMBER,
    p_espera_maxima   OUT NUMBER
) IS
    v_estrategia ejecucion.estrategia%TYPE;
BEGIN
    pr_exigir_ejecucion(p_id_ejecucion);
    SELECT estrategia INTO v_estrategia FROM ejecucion WHERE id = p_id_ejecucion;
    SELECT COUNT(*),
           NVL(SUM(CASE WHEN minuto_inicio_atencion >= 0 THEN 1 ELSE 0 END), 0),
           NVL(SUM(CASE WHEN estado = 'DECEASED' THEN 1 ELSE 0 END), 0)
      INTO p_pacientes, p_atendidos, p_fallecidos
      FROM resultado_paciente
     WHERE id_ejecucion = p_id_ejecucion;
    p_sin_atencion := fn_pacientes_sin_atencion(p_id_ejecucion);
    p_espera_promedio := fn_espera_promedio(p_id_ejecucion);
    p_espera_maxima := fn_espera_maxima(p_id_ejecucion);
    DBMS_OUTPUT.PUT_LINE('Ejecución #' || p_id_ejecucion || ' (' || v_estrategia || ')');
    DBMS_OUTPUT.PUT_LINE('  Pacientes:       ' || p_pacientes);
    DBMS_OUTPUT.PUT_LINE('  Atendidos:       ' || p_atendidos || ' ('
        || ROUND(fn_porcentaje_atendidos(p_id_ejecucion), 1) || ' %)');
    DBMS_OUTPUT.PUT_LINE('  Fallecidos:      ' || p_fallecidos || ' ('
        || ROUND(fn_tasa_mortalidad(p_id_ejecucion), 1) || ' %)');
    DBMS_OUTPUT.PUT_LINE('  Sin atención:    ' || p_sin_atencion);
    DBMS_OUTPUT.PUT_LINE('  Espera promedio: ' || ROUND(p_espera_promedio, 1) || ' min');
    DBMS_OUTPUT.PUT_LINE('  Espera máxima:   ' || p_espera_maxima || ' min');
END pr_resumen_simulacion;
/

-- ---------------------------------------------------------------------------
-- Valida si dos ejecuciones se pueden comparar, con las mismas reglas que la
-- aplicación (clase RunComparator). Si se pueden, p_tipo dice qué cambió
-- (STRATEGY, RESOURCES o MIXED) y p_mensaje es 'OK'; si no, p_tipo queda
-- vacío y p_mensaje explica por qué.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE pr_validar_comparacion (
    p_id_a    IN  NUMBER,
    p_id_b    IN  NUMBER,
    p_tipo    OUT VARCHAR2,
    p_mensaje OUT VARCHAR2
) IS
    v_caso_a       ejecucion.id_caso%TYPE;
    v_caso_b       ejecucion.id_caso%TYPE;
    v_estrategia_a ejecucion.estrategia%TYPE;
    v_estrategia_b ejecucion.estrategia%TYPE;
    v_recursos_a   VARCHAR2(2000);
    v_recursos_b   VARCHAR2(2000);
    v_cambia_estrategia BOOLEAN;
    v_cambian_recursos  BOOLEAN;
BEGIN
    p_tipo := NULL;
    IF p_id_a = p_id_b THEN
        p_mensaje := 'Elige dos ejecuciones distintas: una ejecución no se compara consigo misma.';
        RETURN;
    END IF;
    BEGIN
        SELECT id_caso, estrategia,
               camas || '/' || camas_uci || '/' || quirofanos || '/' || medicos || '/' || enfermeros || '/'
               || unidades_sangre_por_tipo || '/' || capacidad_morgue || '/' || fn_medicos_extra(id)
          INTO v_caso_a, v_estrategia_a, v_recursos_a
          FROM ejecucion WHERE id = p_id_a;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_mensaje := 'No existe la ejecución #' || p_id_a || '.';
            RETURN;
    END;
    BEGIN
        SELECT id_caso, estrategia,
               camas || '/' || camas_uci || '/' || quirofanos || '/' || medicos || '/' || enfermeros || '/'
               || unidades_sangre_por_tipo || '/' || capacidad_morgue || '/' || fn_medicos_extra(id)
          INTO v_caso_b, v_estrategia_b, v_recursos_b
          FROM ejecucion WHERE id = p_id_b;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            p_mensaje := 'No existe la ejecución #' || p_id_b || '.';
            RETURN;
    END;
    IF v_caso_a <> v_caso_b THEN
        p_mensaje := 'Las ejecuciones #' || p_id_a || ' y #' || p_id_b || ' son de casos distintos (#' || v_caso_a
            || ' y #' || v_caso_b || '): solo se comparan ejecuciones del mismo caso, con los mismos pacientes.';
        RETURN;
    END IF;
    v_cambia_estrategia := v_estrategia_a <> v_estrategia_b;
    v_cambian_recursos := v_recursos_a <> v_recursos_b;
    IF v_cambia_estrategia AND v_cambian_recursos THEN
        p_tipo := 'MIXED';
    ELSIF v_cambia_estrategia THEN
        p_tipo := 'STRATEGY';
    ELSIF v_cambian_recursos THEN
        p_tipo := 'RESOURCES';
    ELSE
        p_mensaje := 'Las dos ejecuciones tienen la misma estrategia y los mismos recursos: darían los mismos '
            || 'resultados.';
        RETURN;
    END IF;
    p_mensaje := 'OK';
END pr_validar_comparacion;
/

-- ---------------------------------------------------------------------------
-- Guarda una comparación después de validarla (ORA-20003 con el motivo si no
-- es válida). Si el mismo par de ejecuciones ya estaba guardado, en cualquier
-- orden, no lo repite: devuelve el identificador que ya tenía y p_nueva = 0.
-- No confirma la transacción: lo hace quien llama.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE pr_guardar_comparacion (
    p_id_a  IN  NUMBER,
    p_id_b  IN  NUMBER,
    p_json  IN  CLOB,
    p_id    OUT NUMBER,
    p_nueva OUT NUMBER
) IS
    v_tipo    VARCHAR2(20);
    v_mensaje VARCHAR2(400);
    v_caso    ejecucion.id_caso%TYPE;
BEGIN
    pr_validar_comparacion(p_id_a, p_id_b, v_tipo, v_mensaje);
    IF v_tipo IS NULL THEN
        RAISE_APPLICATION_ERROR(-20003, v_mensaje);
    END IF;
    BEGIN
        SELECT id INTO p_id
          FROM comparacion
         WHERE LEAST(id_ejecucion_a, id_ejecucion_b) = LEAST(p_id_a, p_id_b)
           AND GREATEST(id_ejecucion_a, id_ejecucion_b) = GREATEST(p_id_a, p_id_b);
        p_nueva := 0;
        RETURN;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            NULL;
    END;
    SELECT id_caso INTO v_caso FROM ejecucion WHERE id = p_id_a;
    INSERT INTO comparacion (id_caso, id_ejecucion_a, id_ejecucion_b, tipo, contenido_json)
    VALUES (v_caso, p_id_a, p_id_b, v_tipo, p_json)
    RETURNING id INTO p_id;
    p_nueva := 1;
END pr_guardar_comparacion;
/
