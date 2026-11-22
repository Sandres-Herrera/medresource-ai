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

-- 2. Validación de cada caso y de cada ejecución guardada (1 = cumple, 0 = no cumple)
SELECT c.id AS caso,
       fn_caso_integro(c.id) AS caso_integro,
       fn_base_compatible(c.id, NULL) AS sin_base_valida
  FROM caso c
 ORDER BY c.id;

SELECT e.id, e.estrategia,
       fn_integridad_ejecucion(e.id) AS integridad_ejecucion
  FROM ejecucion e
 ORDER BY e.id;

-- 3. Pertenencia de pacientes y de eventos (cambie los números y códigos por los de su caso)
SELECT fn_paciente_pertenece_caso(1, 1) AS paciente_del_caso,
       fn_paciente_pertenece_caso(1, 999999) AS paciente_ajeno,
       fn_evento_paciente_valido(1, 'P001') AS evento_valido,
       fn_evento_paciente_valido(1, 'NO_EXISTE') AS evento_invalido
  FROM dual;

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
