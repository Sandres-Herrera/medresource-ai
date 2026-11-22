-- MedResource AI: migración segura para una base Oracle 18c XE EXISTENTE.
-- Ejecutar con F5 (Ejecutar script) en SQL Developer, conectado con el usuario
-- propietario de las tablas, después de respaldar la base de datos.
-- No elimina tablas, datos, vistas, funciones ni reportes.
-- La migración puede repetirse: CREATE OR REPLACE e índices condicionales.
-- IMPORTANTE: Oracle confirma automáticamente las instrucciones DDL.
-- NO ejecutar schema.sql ni drop_schema.sql sobre una base con datos.
-- Mantener sincronizada esta migración con db/plsql.sql y db/schema.sql.

CREATE OR REPLACE FUNCTION fn_integridad_ejecucion (p_id_ejecucion IN NUMBER) RETURN NUMBER IS
    v_caso        NUMBER;
    v_pacientes   NUMBER;
    v_resultados  NUMBER;
    v_faltan      NUMBER;
    v_datos_mal   NUMBER;
    v_codigos_rep NUMBER;
    v_eventos_mal NUMBER;
    v_eventos     NUMBER;
BEGIN
    SELECT id_caso INTO v_caso FROM ejecucion WHERE id = p_id_ejecucion;
    SELECT COUNT(*) INTO v_pacientes FROM caso_paciente WHERE id_caso = v_caso;
    SELECT COUNT(*) INTO v_resultados FROM resultado_paciente WHERE id_ejecucion = p_id_ejecucion;
    IF v_resultados <> v_pacientes THEN
        RETURN 0;
    END IF;
    SELECT COUNT(*) INTO v_faltan
      FROM caso_paciente cp
     WHERE cp.id_caso = v_caso
       AND NOT EXISTS (SELECT 1 FROM resultado_paciente r
                        WHERE r.id_ejecucion = p_id_ejecucion AND r.id_paciente = cp.id_paciente);
    SELECT COUNT(*) INTO v_datos_mal
      FROM resultado_paciente r
      JOIN caso_paciente cp ON cp.id_caso = v_caso AND cp.id_paciente = r.id_paciente
     WHERE r.id_ejecucion = p_id_ejecucion
       AND (r.edad <> cp.edad OR r.sexo <> cp.sexo OR r.id_enfermedad <> cp.id_enfermedad
            OR r.minuto_llegada <> cp.minuto_llegada OR r.irrecuperable <> cp.irrecuperable);
    SELECT COUNT(*) INTO v_codigos_rep
      FROM (SELECT codigo_paciente
              FROM resultado_paciente
             WHERE id_ejecucion = p_id_ejecucion
             GROUP BY codigo_paciente
            HAVING COUNT(*) > 1);
    -- Every generated case has patients and the engine emits arrival events.
    -- A run with no events is not a complete saved simulation.
    SELECT COUNT(*) INTO v_eventos
      FROM evento
     WHERE id_ejecucion = p_id_ejecucion;
    IF v_eventos = 0 THEN
        RETURN 0;
    END IF;
    -- Mismo criterio que fn_evento_paciente_valido: debe haber exactamente
    -- un resultado por código. Se valida en SQL sin llamar PL/SQL por evento.
    SELECT COUNT(*) INTO v_eventos_mal
      FROM evento ev
     WHERE ev.id_ejecucion = p_id_ejecucion
       AND NOT EXISTS (
           SELECT 1
             FROM resultado_paciente r
            WHERE r.id_ejecucion = ev.id_ejecucion
              AND r.codigo_paciente = ev.codigo_paciente
            GROUP BY r.id_ejecucion, r.codigo_paciente
           HAVING COUNT(*) = 1
       );
    IF v_faltan > 0 OR v_datos_mal > 0 OR v_codigos_rep > 0 OR v_eventos_mal > 0 THEN
        RETURN 0;
    END IF;
    RETURN 1;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        RETURN 0;
END fn_integridad_ejecucion;
/

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
    BEGIN
        INSERT INTO comparacion (id_caso, id_ejecucion_a, id_ejecucion_b, tipo, contenido_json)
        VALUES (v_caso, p_id_a, p_id_b, v_tipo, p_json)
        RETURNING id INTO p_id;
        p_nueva := 1;
    EXCEPTION
        WHEN DUP_VAL_ON_INDEX THEN
            -- Una segunda sesión pudo insertar el mismo par tras el SELECT.
            -- El índice único por LEAST/GREATEST arbitra esa concurrencia.
            -- Solo reutilizamos un ID si se encuentra exactamente ese par;
            -- otras violaciones de unicidad siguen siendo errores.
            SELECT MIN(id) INTO p_id
              FROM comparacion
             WHERE LEAST(id_ejecucion_a, id_ejecucion_b) = LEAST(p_id_a, p_id_b)
               AND GREATEST(id_ejecucion_a, id_ejecucion_b) = GREATEST(p_id_a, p_id_b);
            IF p_id IS NULL THEN
                RAISE;
            END IF;
            p_nueva := 0;
    END;
END pr_guardar_comparacion;
/

CREATE OR REPLACE TRIGGER trg_validar_tiempo_atencion
BEFORE INSERT OR UPDATE OF minuto_inicio_atencion, minuto_llegada, minuto_desenlace ON resultado_paciente
FOR EACH ROW
BEGIN
    IF :NEW.minuto_inicio_atencion <> -1
       AND (:NEW.minuto_inicio_atencion < :NEW.minuto_llegada
            OR :NEW.minuto_inicio_atencion > :NEW.minuto_desenlace) THEN
        RAISE_APPLICATION_ERROR(-20001, 'El paciente ' || :NEW.codigo_paciente
            || ' debe empezar su atención entre la llegada (minuto ' || :NEW.minuto_llegada
            || ') y el desenlace (minuto ' || :NEW.minuto_desenlace || ').');
    END IF;
END trg_validar_tiempo_atencion;
/

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
    v_pacientes_incompatibles NUMBER;
BEGIN
    -- El catálogo puede editarse, pero no debe invalidar casos ya guardados.
    -- El motor de simulación no modifica las enfermedades: esta protección
    -- solo se aplica al mantenimiento explícito del catálogo.
    SELECT COUNT(*) INTO v_pacientes_incompatibles
      FROM caso_paciente
     WHERE id_enfermedad = p_id
       AND (edad < p_edad_min OR edad > p_edad_max);
    IF v_pacientes_incompatibles > 0 THEN
        RAISE_APPLICATION_ERROR(-20009,
            'El nuevo rango de edad de la enfermedad #' || p_id
            || ' deja fuera a pacientes de casos guardados.');
    END IF;
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

-- Índices: el CREATE INDEX se realiza solo si el nombre todavía no existe.
DECLARE
    PROCEDURE asegurar_indice(p_nombre IN VARCHAR2, p_ddl IN VARCHAR2) IS
        v_existe NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_existe
          FROM user_indexes
         WHERE index_name = UPPER(p_nombre);
        IF v_existe = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
        END IF;
    END asegurar_indice;
BEGIN
    asegurar_indice('IDX_CASO_PAC_ENF', 'CREATE INDEX idx_caso_pac_enf ON caso_paciente (id_enfermedad)');
    asegurar_indice('IDX_RESULTADO_PAC_ENF', 'CREATE INDEX idx_resultado_pac_enf ON resultado_paciente (id_enfermedad)');
    asegurar_indice('IDX_RESULTADO_CODIGO', 'CREATE INDEX idx_resultado_codigo ON resultado_paciente (id_ejecucion, codigo_paciente)');
    asegurar_indice('IDX_COMPARACION_CASO', 'CREATE INDEX idx_comparacion_caso ON comparacion (id_caso)');
    asegurar_indice('IDX_COMPARACION_EJEC_A', 'CREATE INDEX idx_comparacion_ejec_a ON comparacion (id_ejecucion_a)');
    asegurar_indice('IDX_COMPARACION_EJEC_B', 'CREATE INDEX idx_comparacion_ejec_b ON comparacion (id_ejecucion_b)');
END;
/

-- Falla de forma visible si alguna unidad no existe o quedó INVALID.
DECLARE
    v_total NUMBER;
    v_invalidos NUMBER;
BEGIN
    SELECT COUNT(*), SUM(CASE WHEN status <> 'VALID' THEN 1 ELSE 0 END)
      INTO v_total, v_invalidos
      FROM user_objects
     WHERE object_name IN (
         'FN_INTEGRIDAD_EJECUCION',
         'PR_GUARDAR_COMPARACION',
         'TRG_VALIDAR_TIEMPO_ATENCION',
         'PR_ACTUALIZAR_ENFERMEDAD');
    IF v_total <> 4 OR NVL(v_invalidos, 0) > 0 THEN
        RAISE_APPLICATION_ERROR(-20010,
            'Migración incompleta: consultar USER_ERRORS y USER_OBJECTS.');
    END IF;
END;
/

SELECT object_name, object_type, status
  FROM user_objects
 WHERE object_name IN (
     'FN_INTEGRIDAD_EJECUCION',
     'PR_GUARDAR_COMPARACION',
     'TRG_VALIDAR_TIEMPO_ATENCION',
     'PR_ACTUALIZAR_ENFERMEDAD')
 ORDER BY object_name;

SELECT index_name, table_name
  FROM user_indexes
 WHERE index_name IN (
     'IDX_CASO_PAC_ENF', 'IDX_RESULTADO_PAC_ENF', 'IDX_RESULTADO_CODIGO',
     'IDX_COMPARACION_CASO', 'IDX_COMPARACION_EJEC_A', 'IDX_COMPARACION_EJEC_B')
 ORDER BY index_name;
