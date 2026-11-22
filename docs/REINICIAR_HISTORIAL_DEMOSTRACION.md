# Reinicio del historial de demostración (Oracle 18c XE)

`src/main/resources/db/reiniciar_historial_demostracion.sql` es una herramienta **manual**, no una migración. **No se ejecuta al instalar, iniciar Java, hacer `git pull` o ejecutar Maven.**

## Qué conserva y qué elimina

- Conserva todo el catálogo de enfermedades (`ENFERMEDAD`, `ENFERMEDAD_SINTOMA`, `ENFERMEDAD_ESPECIALIDAD_SEC`), las tablas, las restricciones, las funciones PL/SQL, los procedimientos, los triggers y los índices.
- **Elimina definitivamente** los casos y sus pacientes generados, las ejecuciones, los resultados de pacientes, los eventos, los médicos extra, los reportes, las comparaciones y el historial de auditoría de reportes.
- Reinicia los IDENTITY de `CASO`, `EJECUCION`, `REPORTE`, `COMPARACION` y `AUDITORIA_REPORTE` en 1, con `NOCACHE`.

## Aplicación

1. Haz copia de seguridad de la base de prueba. Cierra el simulador y asegúrate de que ningún compañero use ese esquema.
2. Ejecuta primero las pruebas Maven; una prueba de integración puede consumir números de las secuencias.
3. En Oracle SQL Developer **verifica el usuario conectado**: `SELECT USER FROM DUAL;` Debe ser el esquema que deseas vaciar, no `SYSTEM`.
4. Abre el script y cambia **únicamente** `v_confirmacion CONSTANT VARCHAR2(30) := 'NO';` por `v_confirmacion CONSTANT VARCHAR2(30) := 'BORRAR_HISTORIAL';`.
5. Ejecuta el **script completo con F5**, no con Ctrl+Enter. Comprueba que diga «Historial vacio e identidades reiniciadas desde 1» y que la consulta final devuelva ceros.
6. Haz una simulación, guarda el reporte y confirma que el primer `CASO.ID` y `EJECUCION.ID` sean 1. No vuelvas a ejecutar la migración de integridad ni `schema.sql`.

## Restricciones

Los identificadores de bases de datos **no son contadores visuales consecutivos**. `NOCACHE` reduce saltos al reiniciar Oracle, pero **un `ROLLBACK`, una inserción rechazada u otra sesión pueden consumir valores**. El orden de los IDs es ascendente para inserciones normales en esta instancia, pero no se promete ausencia absoluta de huecos. Las ejecuciones diferentes de un mismo caso compartirán `ID_CASO` porque esa es la relación correcta del modelo.

**Precaución:** el bloque confirma la operación antes de borrar y usa `COMMIT`. Las instrucciones DDL `ALTER TABLE` hacen confirmaciones implícitas en Oracle; si una falla a mitad, no existe una única transacción reversible para toda la limpieza. Aplícalo solo en una base de demostración o con respaldo.
