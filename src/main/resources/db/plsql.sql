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

-- ---------------------------------------------------------------------------
-- Impide guardar un paciente que empezó su atención antes de llegar
-- (ORA-20001). El valor -1 significa que nunca lo atendieron y se acepta.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_validar_tiempo_atencion
BEFORE INSERT OR UPDATE OF minuto_inicio_atencion, minuto_llegada ON resultado_paciente
FOR EACH ROW
BEGIN
    IF :NEW.minuto_inicio_atencion <> -1 AND :NEW.minuto_inicio_atencion < :NEW.minuto_llegada THEN
        RAISE_APPLICATION_ERROR(-20001, 'El paciente ' || :NEW.codigo_paciente
            || ' no puede empezar su atención (minuto ' || :NEW.minuto_inicio_atencion
            || ') antes de llegar (minuto ' || :NEW.minuto_llegada || ').');
    END IF;
END trg_validar_tiempo_atencion;
/

-- ---------------------------------------------------------------------------
-- Registra en auditoria_reporte cada reporte que se guarda o se borra (también
-- los que se borran en cascada con su ejecución). Los reportes no se
-- modifican, así que no se audita UPDATE.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_auditoria_reporte
AFTER INSERT OR DELETE ON reporte
FOR EACH ROW
BEGIN
    IF INSERTING THEN
        INSERT INTO auditoria_reporte (id_reporte, id_ejecucion, operacion, usuario)
        VALUES (:NEW.id, :NEW.id_ejecucion, 'INSERT', USER);
    ELSE
        INSERT INTO auditoria_reporte (id_reporte, id_ejecucion, operacion, usuario)
        VALUES (:OLD.id, :OLD.id_ejecucion, 'DELETE', USER);
    END IF;
END trg_auditoria_reporte;
/

-- ---------------------------------------------------------------------------
-- Vistas: concentran los JOIN y los cálculos de las consultas complejas.
-- Java solo hace SELECT ... FROM vista. Si cambia una consulta, se cambia aquí.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_reporte_resumen AS
SELECT p.id,
       p.id_ejecucion,
       p.guardado_en,
       p.origen,
       r.estrategia,
       r.id_ejecucion_base,
       c.id AS id_caso,
       c.semilla,
       c.escenario,
       c.cantidad_pacientes,
       c.duracion_minutos,
       (SELECT COUNT(*) FROM resultado_paciente d
         WHERE d.id_ejecucion = p.id_ejecucion AND d.estado = 'DECEASED') AS fallecidos
  FROM reporte p
  JOIN ejecucion r ON r.id = p.id_ejecucion
  JOIN caso c ON c.id = r.id_caso
/

CREATE OR REPLACE VIEW v_ejecucion_resumen AS
SELECT r.id,
       r.id_caso,
       r.estrategia,
       r.minutos_totales,
       r.camas,
       r.camas_uci,
       r.quirofanos,
       r.medicos,
       r.enfermeros,
       r.unidades_sangre_por_tipo,
       r.capacidad_morgue,
       r.id_ejecucion_base,
       c.semilla,
       c.escenario,
       c.cantidad_pacientes,
       c.duracion_minutos
  FROM ejecucion r
  JOIN caso c ON c.id = r.id_caso
/

-- ---------------------------------------------------------------------------
-- Reportes: procedimientos que usa OracleReportDao. Java solo los llama. Las
-- lecturas devuelven un cursor (SYS_REFCURSOR) con las columnas que lee el DAO.
-- Ninguno confirma (COMMIT): lo hace la transacción de Java.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE pr_guardar_reporte (
    p_id_ejecucion   IN  NUMBER,
    p_origen         IN  VARCHAR2,
    p_contenido_json IN  CLOB,
    p_id             OUT NUMBER
) IS
BEGIN
    INSERT INTO reporte (id_ejecucion, origen, contenido_json)
    VALUES (p_id_ejecucion, p_origen, p_contenido_json)
    RETURNING id INTO p_id;
END pr_guardar_reporte;
/

CREATE OR REPLACE PROCEDURE pr_borrar_reporte (
    p_id        IN  NUMBER,
    p_borrados  OUT NUMBER
) IS
BEGIN
    DELETE FROM reporte WHERE id = p_id;
    p_borrados := SQL%ROWCOUNT;
END pr_borrar_reporte;
/

CREATE OR REPLACE PROCEDURE pr_buscar_reporte (
    p_id      IN  NUMBER,
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id, id_ejecucion, guardado_en, contenido_json
          FROM reporte
         WHERE id = p_id;
END pr_buscar_reporte;
/

CREATE OR REPLACE PROCEDURE pr_reporte_de_ejecucion (
    p_id_ejecucion  IN  NUMBER,
    p_cursor        OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id, id_ejecucion, guardado_en, contenido_json
          FROM reporte
         WHERE id_ejecucion = p_id_ejecucion;
END pr_reporte_de_ejecucion;
/

CREATE OR REPLACE PROCEDURE pr_listar_reportes (
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id, id_ejecucion, guardado_en, contenido_json
          FROM reporte
         ORDER BY id;
END pr_listar_reportes;
/

CREATE OR REPLACE PROCEDURE pr_listar_resumen_reportes (
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id, id_ejecucion, guardado_en, origen, estrategia, id_ejecucion_base, id_caso,
               semilla, escenario, cantidad_pacientes, duracion_minutos, fallecidos
          FROM v_reporte_resumen
         ORDER BY guardado_en DESC, id DESC;
END pr_listar_resumen_reportes;
/

-- ---------------------------------------------------------------------------
-- Comparaciones: lecturas y borrado que usa OracleComparisonDao. Guardar ya lo
-- hace pr_guardar_comparacion. Las lecturas devuelven un cursor (SYS_REFCURSOR).
-- Ninguno confirma (COMMIT): lo hace la transacción de Java.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE pr_buscar_comparacion (
    p_id      IN  NUMBER,
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id, guardado_en, contenido_json
          FROM comparacion
         WHERE id = p_id;
END pr_buscar_comparacion;
/

CREATE OR REPLACE PROCEDURE pr_comparacion_de_ejecuciones (
    p_id_a    IN  NUMBER,
    p_id_b    IN  NUMBER,
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id, guardado_en, contenido_json
          FROM comparacion
         WHERE LEAST(id_ejecucion_a, id_ejecucion_b) = LEAST(p_id_a, p_id_b)
           AND GREATEST(id_ejecucion_a, id_ejecucion_b) = GREATEST(p_id_a, p_id_b);
END pr_comparacion_de_ejecuciones;
/

CREATE OR REPLACE PROCEDURE pr_listar_comparaciones (
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id, guardado_en, contenido_json
          FROM comparacion
         ORDER BY guardado_en DESC, id DESC;
END pr_listar_comparaciones;
/

CREATE OR REPLACE PROCEDURE pr_borrar_comparacion (
    p_id        IN  NUMBER,
    p_borrados  OUT NUMBER
) IS
BEGIN
    DELETE FROM comparacion WHERE id = p_id;
    p_borrados := SQL%ROWCOUNT;
END pr_borrar_comparacion;
/

-- ---------------------------------------------------------------------------
-- Casos: procedimientos que usa OracleCaseDao. Guardar un caso son dos pasos
-- dentro de la misma transacción de Java: pr_crear_caso y, por cada paciente,
-- pr_agregar_paciente (que inserta también sus síntomas, separados por coma).
-- Las lecturas reciben un identificador opcional: si es NULL devuelven todo.
-- Ninguno confirma (COMMIT): lo hace la transacción de Java.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE pr_crear_caso (
    p_semilla    IN  NUMBER,
    p_escenario  IN  VARCHAR2,
    p_cantidad   IN  NUMBER,
    p_duracion   IN  NUMBER,
    p_id         OUT NUMBER
) IS
BEGIN
    INSERT INTO caso (semilla, escenario, cantidad_pacientes, duracion_minutos)
    VALUES (p_semilla, p_escenario, p_cantidad, p_duracion)
    RETURNING id INTO p_id;
END pr_crear_caso;
/

CREATE OR REPLACE PROCEDURE pr_agregar_paciente (
    p_id_caso        IN NUMBER,
    p_id_paciente    IN NUMBER,
    p_edad           IN NUMBER,
    p_sexo           IN VARCHAR2,
    p_tipo_sangre    IN VARCHAR2,
    p_id_enfermedad  IN NUMBER,
    p_minuto_llegada IN NUMBER,
    p_salud_inicial  IN BINARY_DOUBLE,
    p_irrecuperable  IN NUMBER,
    p_sintomas       IN VARCHAR2
) IS
    v_orden   NUMBER := 0;
    v_sintoma VARCHAR2(30);
BEGIN
    INSERT INTO caso_paciente (id_caso, id_paciente, edad, sexo, tipo_sangre, id_enfermedad,
                               minuto_llegada, salud_inicial, irrecuperable)
    VALUES (p_id_caso, p_id_paciente, p_edad, p_sexo, p_tipo_sangre, p_id_enfermedad,
            p_minuto_llegada, p_salud_inicial, p_irrecuperable);
    LOOP
        v_sintoma := REGEXP_SUBSTR(p_sintomas, '[^,]+', 1, v_orden + 1);
        EXIT WHEN v_sintoma IS NULL;
        INSERT INTO caso_paciente_sintoma (id_caso, id_paciente, orden_sintoma, sintoma)
        VALUES (p_id_caso, p_id_paciente, v_orden, v_sintoma);
        v_orden := v_orden + 1;
    END LOOP;
END pr_agregar_paciente;
/

CREATE OR REPLACE PROCEDURE pr_buscar_id_caso (
    p_semilla    IN  NUMBER,
    p_escenario  IN  VARCHAR2,
    p_cantidad   IN  NUMBER,
    p_duracion   IN  NUMBER,
    p_id         OUT NUMBER
) IS
BEGIN
    SELECT id INTO p_id
      FROM caso
     WHERE semilla = p_semilla
       AND escenario = p_escenario
       AND cantidad_pacientes = p_cantidad
       AND duracion_minutos = p_duracion;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        p_id := NULL;
END pr_buscar_id_caso;
/

CREATE OR REPLACE PROCEDURE pr_leer_casos (
    p_id      IN  NUMBER,
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id, semilla, escenario, cantidad_pacientes, duracion_minutos
          FROM caso
         WHERE p_id IS NULL OR id = p_id
         ORDER BY id;
END pr_leer_casos;
/

CREATE OR REPLACE PROCEDURE pr_leer_pacientes (
    p_id_caso  IN  NUMBER,
    p_cursor   OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id_caso, id_paciente, edad, sexo, tipo_sangre, id_enfermedad,
               minuto_llegada, salud_inicial, irrecuperable
          FROM caso_paciente
         WHERE p_id_caso IS NULL OR id_caso = p_id_caso
         ORDER BY id_caso, id_paciente;
END pr_leer_pacientes;
/

CREATE OR REPLACE PROCEDURE pr_leer_sintomas (
    p_id_caso  IN  NUMBER,
    p_cursor   OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id_caso, id_paciente, sintoma
          FROM caso_paciente_sintoma
         WHERE p_id_caso IS NULL OR id_caso = p_id_caso
         ORDER BY id_caso, id_paciente, orden_sintoma;
END pr_leer_sintomas;
/

CREATE OR REPLACE PROCEDURE pr_borrar_caso (
    p_id        IN  NUMBER,
    p_borrados  OUT NUMBER
) IS
BEGIN
    DELETE FROM caso WHERE id = p_id;
    p_borrados := SQL%ROWCOUNT;
END pr_borrar_caso;
/

-- ---------------------------------------------------------------------------
-- Enfermedades: procedimientos que usa OracleDiseaseDao. Una enfermedad son
-- tres tablas; sus especialidades secundarias y sus síntomas llegan como texto
-- separado por comas, y pr_insertar_datos_enfermedad los guarda en orden.
-- Las lecturas reciben un identificador opcional: si es NULL devuelven todo.
-- Ninguno confirma (COMMIT): lo hace la transacción de Java.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE pr_insertar_datos_enfermedad (
    p_id              IN NUMBER,
    p_especialidades  IN VARCHAR2,
    p_sintomas        IN VARCHAR2
) IS
    v_n     NUMBER := 0;
    v_texto VARCHAR2(60);
BEGIN
    LOOP
        v_texto := REGEXP_SUBSTR(p_especialidades, '[^,]+', 1, v_n + 1);
        EXIT WHEN v_texto IS NULL;
        INSERT INTO enfermedad_especialidad_sec (id_enfermedad, especialidad) VALUES (p_id, v_texto);
        v_n := v_n + 1;
    END LOOP;
    v_n := 0;
    LOOP
        v_texto := REGEXP_SUBSTR(p_sintomas, '[^,]+', 1, v_n + 1);
        EXIT WHEN v_texto IS NULL;
        INSERT INTO enfermedad_sintoma (id_enfermedad, orden_sintoma, sintoma) VALUES (p_id, v_n, v_texto);
        v_n := v_n + 1;
    END LOOP;
END pr_insertar_datos_enfermedad;
/

CREATE OR REPLACE PROCEDURE pr_guardar_enfermedad (
    p_id                IN NUMBER,
    p_nombre            IN VARCHAR2,
    p_especialidad      IN VARCHAR2,
    p_tratamiento       IN VARCHAR2,
    p_curable           IN NUMBER,
    p_requiere_sangre   IN NUMBER,
    p_requiere_uci      IN NUMBER,
    p_tasa              IN BINARY_DOUBLE,
    p_mortalidad        IN BINARY_DOUBLE,
    p_duracion_min      IN NUMBER,
    p_duracion_max      IN NUMBER,
    p_edad_min          IN NUMBER,
    p_edad_max          IN NUMBER,
    p_frecuencia        IN BINARY_DOUBLE,
    p_especialidades    IN VARCHAR2,
    p_sintomas          IN VARCHAR2
) IS
BEGIN
    INSERT INTO enfermedad (id, nombre, especialidad_principal, tratamiento, curable, requiere_sangre,
                            requiere_uci, tasa_deterioro, mortalidad_irreducible, duracion_min_minutos,
                            duracion_max_minutos, edad_minima, edad_maxima, frecuencia)
    VALUES (p_id, p_nombre, p_especialidad, p_tratamiento, p_curable, p_requiere_sangre, p_requiere_uci,
            p_tasa, p_mortalidad, p_duracion_min, p_duracion_max, p_edad_min, p_edad_max, p_frecuencia);
    pr_insertar_datos_enfermedad(p_id, p_especialidades, p_sintomas);
END pr_guardar_enfermedad;
/

-- p_actualizadas sale 0 si la enfermedad no existe (y no toca sus hijos) o 1 si se actualizó.
CREATE OR REPLACE PROCEDURE pr_actualizar_enfermedad (
    p_id                IN NUMBER,
    p_nombre            IN VARCHAR2,
    p_especialidad      IN VARCHAR2,
    p_tratamiento       IN VARCHAR2,
    p_curable           IN NUMBER,
    p_requiere_sangre   IN NUMBER,
    p_requiere_uci      IN NUMBER,
    p_tasa              IN BINARY_DOUBLE,
    p_mortalidad        IN BINARY_DOUBLE,
    p_duracion_min      IN NUMBER,
    p_duracion_max      IN NUMBER,
    p_edad_min          IN NUMBER,
    p_edad_max          IN NUMBER,
    p_frecuencia        IN BINARY_DOUBLE,
    p_especialidades    IN VARCHAR2,
    p_sintomas          IN VARCHAR2,
    p_actualizadas      OUT NUMBER
) IS
BEGIN
    UPDATE enfermedad
       SET nombre = p_nombre,
           especialidad_principal = p_especialidad,
           tratamiento = p_tratamiento,
           curable = p_curable,
           requiere_sangre = p_requiere_sangre,
           requiere_uci = p_requiere_uci,
           tasa_deterioro = p_tasa,
           mortalidad_irreducible = p_mortalidad,
           duracion_min_minutos = p_duracion_min,
           duracion_max_minutos = p_duracion_max,
           edad_minima = p_edad_min,
           edad_maxima = p_edad_max,
           frecuencia = p_frecuencia
     WHERE id = p_id;
    IF SQL%ROWCOUNT = 0 THEN
        p_actualizadas := 0;
        RETURN;
    END IF;
    DELETE FROM enfermedad_especialidad_sec WHERE id_enfermedad = p_id;
    DELETE FROM enfermedad_sintoma WHERE id_enfermedad = p_id;
    pr_insertar_datos_enfermedad(p_id, p_especialidades, p_sintomas);
    p_actualizadas := 1;
END pr_actualizar_enfermedad;
/

CREATE OR REPLACE PROCEDURE pr_borrar_enfermedad (
    p_id        IN  NUMBER,
    p_borradas  OUT NUMBER
) IS
BEGIN
    DELETE FROM enfermedad WHERE id = p_id;
    p_borradas := SQL%ROWCOUNT;
END pr_borrar_enfermedad;
/

CREATE OR REPLACE PROCEDURE pr_leer_enfermedades (
    p_id      IN  NUMBER,
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id, nombre, especialidad_principal, tratamiento, curable, requiere_sangre, requiere_uci,
               tasa_deterioro, mortalidad_irreducible, duracion_min_minutos, duracion_max_minutos,
               edad_minima, edad_maxima, frecuencia
          FROM enfermedad
         WHERE p_id IS NULL OR id = p_id
         ORDER BY id;
END pr_leer_enfermedades;
/

CREATE OR REPLACE PROCEDURE pr_leer_especialidades_enfermedad (
    p_id      IN  NUMBER,
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id_enfermedad, especialidad
          FROM enfermedad_especialidad_sec
         WHERE p_id IS NULL OR id_enfermedad = p_id
         ORDER BY id_enfermedad, especialidad;
END pr_leer_especialidades_enfermedad;
/

CREATE OR REPLACE PROCEDURE pr_leer_sintomas_enfermedad (
    p_id      IN  NUMBER,
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id_enfermedad, sintoma
          FROM enfermedad_sintoma
         WHERE p_id IS NULL OR id_enfermedad = p_id
         ORDER BY id_enfermedad, orden_sintoma;
END pr_leer_sintomas_enfermedad;
/

-- ---------------------------------------------------------------------------
-- Ejecuciones: procedimientos que usa OracleResultDao. Guardar una ejecución
-- son varias llamadas dentro de la misma transacción de Java: la cabecera
-- (pr_crear_ejecucion) y, en lote, sus médicos extra, pacientes y eventos.
-- Las lecturas reciben un identificador opcional: si es NULL devuelven todo.
-- Ninguno confirma (COMMIT): lo hace la transacción de Java.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE pr_leer_config_caso (
    p_id_caso  IN  NUMBER,
    p_cursor   OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT semilla, escenario, cantidad_pacientes, duracion_minutos
          FROM caso
         WHERE id = p_id_caso;
END pr_leer_config_caso;
/

CREATE OR REPLACE PROCEDURE pr_leer_caso_de_ejecucion (
    p_id      IN  NUMBER,
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id_caso
          FROM ejecucion
         WHERE id = p_id;
END pr_leer_caso_de_ejecucion;
/

CREATE OR REPLACE PROCEDURE pr_crear_ejecucion (
    p_id_caso        IN  NUMBER,
    p_estrategia     IN  VARCHAR2,
    p_minutos        IN  NUMBER,
    p_camas          IN  NUMBER,
    p_camas_uci      IN  NUMBER,
    p_quirofanos     IN  NUMBER,
    p_medicos        IN  NUMBER,
    p_enfermeros     IN  NUMBER,
    p_sangre         IN  NUMBER,
    p_morgue         IN  NUMBER,
    p_base           IN  NUMBER,
    p_id             OUT NUMBER
) IS
BEGIN
    INSERT INTO ejecucion (id_caso, estrategia, minutos_totales, camas, camas_uci, quirofanos, medicos,
                           enfermeros, unidades_sangre_por_tipo, capacidad_morgue, id_ejecucion_base)
    VALUES (p_id_caso, p_estrategia, p_minutos, p_camas, p_camas_uci, p_quirofanos, p_medicos,
            p_enfermeros, p_sangre, p_morgue, p_base)
    RETURNING id INTO p_id;
END pr_crear_ejecucion;
/

CREATE OR REPLACE PROCEDURE pr_agregar_medico_extra (
    p_id_ejecucion  IN NUMBER,
    p_orden         IN NUMBER,
    p_especialidad  IN VARCHAR2
) IS
BEGIN
    INSERT INTO ejecucion_medico_extra (id_ejecucion, orden_medico, especialidad)
    VALUES (p_id_ejecucion, p_orden, p_especialidad);
END pr_agregar_medico_extra;
/

CREATE OR REPLACE PROCEDURE pr_agregar_resultado_paciente (
    p_id_ejecucion           IN NUMBER,
    p_id_paciente            IN NUMBER,
    p_codigo_paciente        IN VARCHAR2,
    p_edad                   IN NUMBER,
    p_sexo                   IN VARCHAR2,
    p_nivel_triage           IN NUMBER,
    p_id_enfermedad          IN NUMBER,
    p_minuto_llegada         IN NUMBER,
    p_minuto_inicio_atencion IN NUMBER,
    p_minuto_desenlace       IN NUMBER,
    p_estado                 IN VARCHAR2,
    p_causa_muerte           IN VARCHAR2,
    p_nivel_atencion         IN VARCHAR2,
    p_irrecuperable          IN NUMBER,
    p_salud_final            IN BINARY_DOUBLE
) IS
BEGIN
    INSERT INTO resultado_paciente (id_ejecucion, id_paciente, codigo_paciente, edad, sexo, nivel_triage,
                                    id_enfermedad, minuto_llegada, minuto_inicio_atencion, minuto_desenlace,
                                    estado, causa_muerte, nivel_atencion, irrecuperable, salud_final)
    VALUES (p_id_ejecucion, p_id_paciente, p_codigo_paciente, p_edad, p_sexo, p_nivel_triage,
            p_id_enfermedad, p_minuto_llegada, p_minuto_inicio_atencion, p_minuto_desenlace,
            p_estado, p_causa_muerte, p_nivel_atencion, p_irrecuperable, p_salud_final);
END pr_agregar_resultado_paciente;
/

CREATE OR REPLACE PROCEDURE pr_agregar_evento (
    p_id_ejecucion    IN NUMBER,
    p_orden           IN NUMBER,
    p_minuto          IN NUMBER,
    p_tipo            IN VARCHAR2,
    p_codigo_paciente IN VARCHAR2,
    p_codigo_recurso  IN VARCHAR2,
    p_descripcion     IN VARCHAR2
) IS
BEGIN
    INSERT INTO evento (id_ejecucion, orden_evento, minuto_evento, tipo_evento, codigo_paciente,
                        codigo_recurso, descripcion)
    VALUES (p_id_ejecucion, p_orden, p_minuto, p_tipo, p_codigo_paciente, p_codigo_recurso, p_descripcion);
END pr_agregar_evento;
/

CREATE OR REPLACE PROCEDURE pr_borrar_ejecucion (
    p_id        IN  NUMBER,
    p_borradas  OUT NUMBER
) IS
BEGIN
    DELETE FROM ejecucion WHERE id = p_id;
    p_borradas := SQL%ROWCOUNT;
END pr_borrar_ejecucion;
/

CREATE OR REPLACE PROCEDURE pr_leer_ejecuciones (
    p_id      IN  NUMBER,
    p_cursor  OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id, id_caso, estrategia, minutos_totales, camas, camas_uci, quirofanos, medicos,
               enfermeros, unidades_sangre_por_tipo, capacidad_morgue, id_ejecucion_base,
               semilla, escenario, cantidad_pacientes, duracion_minutos
          FROM v_ejecucion_resumen
         WHERE p_id IS NULL OR id = p_id
         ORDER BY id;
END pr_leer_ejecuciones;
/

CREATE OR REPLACE PROCEDURE pr_ids_ejecuciones_de_caso (
    p_id_caso  IN  NUMBER,
    p_cursor   OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id
          FROM ejecucion
         WHERE id_caso = p_id_caso
         ORDER BY id;
END pr_ids_ejecuciones_de_caso;
/

CREATE OR REPLACE PROCEDURE pr_leer_resultados_pacientes (
    p_id_ejecucion  IN  NUMBER,
    p_cursor        OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id_ejecucion, id_paciente, codigo_paciente, edad, sexo, nivel_triage, id_enfermedad,
               minuto_llegada, minuto_inicio_atencion, minuto_desenlace, estado, causa_muerte,
               nivel_atencion, irrecuperable, salud_final
          FROM resultado_paciente
         WHERE p_id_ejecucion IS NULL OR id_ejecucion = p_id_ejecucion
         ORDER BY id_ejecucion, id_paciente;
END pr_leer_resultados_pacientes;
/

CREATE OR REPLACE PROCEDURE pr_leer_eventos (
    p_id_ejecucion  IN  NUMBER,
    p_cursor        OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id_ejecucion, minuto_evento, tipo_evento, codigo_paciente, codigo_recurso, descripcion
          FROM evento
         WHERE p_id_ejecucion IS NULL OR id_ejecucion = p_id_ejecucion
         ORDER BY id_ejecucion, orden_evento;
END pr_leer_eventos;
/

CREATE OR REPLACE PROCEDURE pr_leer_medicos_extra (
    p_id_ejecucion  IN  NUMBER,
    p_cursor        OUT SYS_REFCURSOR
) IS
BEGIN
    OPEN p_cursor FOR
        SELECT id_ejecucion, especialidad
          FROM ejecucion_medico_extra
         WHERE p_id_ejecucion IS NULL OR id_ejecucion = p_id_ejecucion
         ORDER BY id_ejecucion, orden_medico;
END pr_leer_medicos_extra;
/
