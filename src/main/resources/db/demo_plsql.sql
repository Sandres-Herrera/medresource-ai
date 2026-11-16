-- ============================================================================
-- MedResource AI - demostración del PL/SQL en SQL Developer
--
-- Antes: correr schema.sql y plsql.sql, y guardar desde la aplicación al menos
-- dos simulaciones del mismo caso (por ejemplo, la original y la que prueba las
-- recomendaciones). Ejecutar como script (F5). En los bloques 3, 4 y 5 cambie
-- los números 1 y 2 por las ejecuciones que quiera mostrar (columna id del
-- bloque 1). Nada de este script deja cambios en la base de datos.
-- ============================================================================

SET SERVEROUTPUT ON

-- 1. Ejecuciones guardadas, con su caso, sus recursos y la ejecución en que se basan
SELECT e.id, e.id_caso, c.semilla, c.escenario, e.estrategia, e.camas, e.camas_uci, e.quirofanos,
       e.medicos, fn_medicos_extra(e.id) AS medicos_extra, e.id_ejecucion_base
  FROM ejecucion e JOIN caso c ON c.id = e.id_caso
 ORDER BY e.id;

-- 2. Indicadores de cada ejecución calculados con las funciones PL/SQL
SELECT id, estrategia,
       ROUND(fn_porcentaje_atendidos(id), 1) AS atendidos_pct,
       ROUND(fn_tasa_mortalidad(id), 1) AS mortalidad_pct,
       fn_pacientes_sin_atencion(id) AS sin_atencion,
       ROUND(fn_espera_promedio(id), 1) AS espera_promedio_min,
       fn_espera_maxima(id) AS espera_maxima_min
  FROM ejecucion
 ORDER BY id;

-- 3. Resumen de una ejecución con el procedimiento (se ve en «Salida de DBMS»)
DECLARE
    v_pacientes       NUMBER;
    v_atendidos       NUMBER;
    v_fallecidos      NUMBER;
    v_sin_atencion    NUMBER;
    v_espera_promedio NUMBER;
    v_espera_maxima   NUMBER;
BEGIN
    pr_resumen_simulacion(1, v_pacientes, v_atendidos, v_fallecidos, v_sin_atencion, v_espera_promedio,
                          v_espera_maxima);
END;
/

-- 4. ¿Se pueden comparar dos ejecuciones? Dice el tipo (STRATEGY, RESOURCES, MIXED) o el motivo
DECLARE
    v_tipo    VARCHAR2(20);
    v_mensaje VARCHAR2(400);
BEGIN
    pr_validar_comparacion(1, 2, v_tipo, v_mensaje);
    DBMS_OUTPUT.PUT_LINE('Tipo:    ' || NVL(v_tipo, '(no se pueden comparar)'));
    DBMS_OUTPUT.PUT_LINE('Mensaje: ' || v_mensaje);
END;
/

-- 5. El trigger rechaza un inicio de atención antes de la llegada (el cambio se deshace)
BEGIN
    UPDATE resultado_paciente
       SET minuto_inicio_atencion = minuto_llegada - 1
     WHERE id_ejecucion = 1 AND minuto_llegada > 0 AND minuto_inicio_atencion > 0 AND ROWNUM = 1;
    DBMS_OUTPUT.PUT_LINE('No había un paciente atendido después de llegar en esa ejecución.');
    ROLLBACK;
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('Rechazado por trg_validar_tiempo_atencion: ' || SQLERRM);
        ROLLBACK;
END;
/

-- 6. Comparaciones guardadas desde la aplicación
SELECT id, id_caso, id_ejecucion_a, id_ejecucion_b, tipo, guardado_en
  FROM comparacion
 ORDER BY id;

-- 7. Auditoría de los reportes (la llena trg_auditoria_reporte)
SELECT id, id_reporte, id_ejecucion, operacion, usuario, fecha
  FROM auditoria_reporte
 ORDER BY id;
