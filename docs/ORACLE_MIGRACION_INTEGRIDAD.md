# Correcciones Oracle: integridad y rendimiento (22-11-2026)

Este cambio se preparó sobre el ZIP de `develop` con identificador
`6502db8ffaa30d697ae2f23e7386e216d8f89643`. No modifica el motor Java,
las pantallas ni las firmas de las llamadas JDBC. Los cambios de pruebas JUnit
son exclusivos de `src/test`.

## Cambios

1. `pr_actualizar_enfermedad` rechaza con `ORA-20009` un cambio de edades que
   dejaría fuera de rango a pacientes de casos existentes. El generador de
   pacientes **no** modifica las enfermedades; esto protege ediciones manuales.
2. `trg_validar_tiempo_atencion` conserva `-1` (nunca atendido), rechaza la
   atención anterior a la llegada o posterior al desenlace y comprueba también
   cambios en `minuto_desenlace`; utiliza `ORA-20001` como antes.
3. `fn_integridad_ejecucion` comprueba la asociación evento/resultado con
   `NOT EXISTS` en SQL. Conserva la regla de **exactamente un** resultado por
   código y mantiene intacta `fn_evento_paciente_valido` para el guardado.
4. `pr_guardar_comparacion` devuelve el ID del par ya guardado en caso de
   colisión simultánea (`DUP_VAL_ON_INDEX`). Si el duplicado no corresponde al
   par solicitado, vuelve a lanzar el error. No agrega `COMMIT` ni `ROLLBACK`.
5. Se añaden seis índices para consultas por enfermedad, códigos de pacientes,
   ejecuciones de comparaciones y casos de comparaciones. La PK ya cubre otras FK.

## Aplicación en SQL Developer

**Antes:** respaldar la base, asegurarse de estar conectado con el propietario
real de las tablas (`MEDRESOURCE_APP` u otro esquema de la instalación).
Ejecutar el siguiente script con **F5**:

`src/main/resources/db/migracion_integridad_20261122.sql`

Esta migración utiliza `CREATE OR REPLACE` e índices condicionales. No hace
`DROP TABLE`, no borra los datos guardados y se puede volver a ejecutar.
Las sentencias DDL de Oracle confirman automáticamente su ejecución, por lo
que **no** puede deshacerse toda la migración simplemente con `ROLLBACK`.
**NO** ejecutar `drop_schema.sql` ni `schema.sql` sobre la instalación actual.
El archivo `schema.sql` actualizado es solo para instalaciones nuevas.

Verificar que no haya objetos inválidos:

```sql
SELECT object_name, object_type, status
FROM user_objects
WHERE status = 'INVALID';
```

Verificar los índices creados:

```sql
SELECT index_name, table_name
FROM user_indexes
WHERE index_name IN (
  'IDX_CASO_PAC_ENF', 'IDX_RESULTADO_PAC_ENF', 'IDX_RESULTADO_CODIGO',
  'IDX_COMPARACION_CASO', 'IDX_COMPARACION_EJEC_A', 'IDX_COMPARACION_EJEC_B');
```

## Pruebas

En el entorno del proyecto, con Java 25 y Maven:

```powershell
mvn test
```

Con `src/main/resources/db.properties` conectado a Oracle, las pruebas de
integración también verifican el trigger con datos reales. Sin conexión, las
pruebas marcadas con precondiciones se omiten. Comprobar además en la aplicación:
crear un caso, guardarlo, abrir una ejecución histórica, guardar un reporte y
comparar dos ejecuciones del mismo caso.

### Limitaciones y compatibilidad

- Las nuevas validaciones **sí** rechazan ediciones históricamente incompatibles.
  Java propagará un error si alguien intenta hacer esas modificaciones.
- Las nuevas reglas no modifican resultados ya guardados ni recrean reportes.
- La protección del rango de edad se aplica al procedimiento de actualización;
  quien tenga permisos para hacer `UPDATE enfermedad` directamente aún puede
  saltarse esa protección. Limitar las escrituras a procedimientos o agregar
  un trigger independiente si se requiere proteger todos los accesos SQL.
- La resolución de comparaciones concurrentes depende de que exista el índice
  único `UQ_COMPARACION_PAR` incluido en `schema.sql`.
- El script se preparó y comprobó estáticamente sin acceso a una instancia
  Oracle de la máquina del usuario. Validar la compilación y el comportamiento
  contra Oracle 18c XE antes de integrarlo en producción.
