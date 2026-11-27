# MedResource AI

Simulador de **gestión de recursos hospitalarios limitados** (camas de hospitalización, UCI, médicos, enfermeros, quirófanos, unidades de sangre y morgue). Cada simulación recibe pacientes con síntomas, los pasa por triage y decide —según una estrategia de asignación— quién recibe cada recurso y en qué momento, mientras llegan emergencias inesperadas. Al terminar, el sistema genera un **reporte en normas APA 7.ª edición** que explica qué pasó, dónde se formaron los cuellos de botella, qué recursos faltaron y qué cambiaría el resultado.

Cada caso se genera a partir de una **semilla**: con la misma semilla se obtiene exactamente el mismo escenario, de modo que las estrategias (y las configuraciones del hospital) se comparan sobre los mismos pacientes.

La idea de producto es que un hospital pueda simular sus propios recursos y encontrar, antes de que ocurra en la vida real, qué funciones fallan y dónde se acumulan las esperas.

## Proyecto de aula

- **Universidad:** Universidad Popular del Cesar (Unicesar) — Ingeniería de Sistemas
- **Asignatura:** Programación de Computadores III (SS462)
- **Docente:** Ing. Esp. Alfredo Bautista
- **Presentación:** feria ExpoSoftware

## Equipo

| Nombre | Usuario de GitHub | Rol |
|---|---|---|
| Santiago Herrera | [@Sandres-Herrera](https://github.com/Sandres-Herrera) | Líder |
| Omar Gámez | [@Osgh1015](https://github.com/Osgh1015) | Desarrollador |

El equipo no tiene responsabilidades fijas por módulo: cada integrante toma el siguiente paso pendiente del plan cuando puede. La base de datos Oracle (esquema, PL/SQL y conexión) la trabajó principalmente Omar.

## Funcionalidades

**Configuración de la simulación** (ventana «Nueva simulación»)
- Hospital con tamaño **Pequeño**, **Estándar** o **Grande**, y recursos editables: camas, camas de UCI, quirófanos, médicos, enfermeros y morgue.
- Especialistas extra (médicos adicionales) para probar «¿qué pasa si contrato más personal?».
- Cuatro escenarios de llegada de pacientes: **A – Hospital normal**, **B – Alta demanda**, **C – Catástrofe** y **D – Emergencias inesperadas**.
- Caso configurable: número de pacientes, duración de las llegadas (min) y **semilla** (con botón para sortear otra).
- Velocidad inicial de la simulación (×1 es tiempo real: un segundo en pantalla es un segundo).

**Estrategias de asignación**

| Estrategia | Criterio |
|---|---|
| Prioridad clínica (triage) | El más grave primero: nivel de triage (que sube por espera para evitar la inanición), salud actual y orden de llegada. No usa edad ni sexo. |
| FIFO | Línea base: atiende por orden de llegada. |
| Aleatoria | Orden al azar, repetible con la semilla; sirve de punto de comparación. |

Todas comparten las mismas reglas de elección de recursos; solo cambia el orden en que se atiende a los pacientes.

**Simulación en vivo**
- Plano del hospital con triage (3 estaciones en paralelo con fila), sala de espera, consultorios, hospitalización, UCI, quirófanos, banco de sangre, morgue y sala del personal.
- Los pacientes caminan por los pasillos en tiempo real (velocidades ×1, ×2, ×5, ×10 y avance rápido), con médicos y enfermeros visibles, puertas que se abren al pasar alguien y atención realista (médico ocupado durante la atención, cirugía con recuperación, triage con duración según el nivel).
- Tres vistas del mismo hospital: **2D** (tablero de salas), **2.5D** (se puede girar 360°, inclinar hasta verlo desde arriba y restablecer la vista) e **isométrica** detallada, en tema claro u oscuro.
- Tarjetas de pacientes y de personal, panel de recursos, registro de eventos con filtros, alertas, línea de tiempo, repetición de los últimos 30 s, siguiente evento, reinicio y captura del plano.

**Reporte de fin de simulación**
- Resumen con indicadores (pacientes, fallecidos y mortalidad, espera promedio, uso de recursos, carga del personal, desglose por nivel de triage y causas de muerte).
- Análisis de cuellos de botella, banco de sangre y pacientes sin terminar de atender.
- Análisis **«¿y si…?»**: vuelve a simular el mismo caso cambiando recursos (más camas, médicos, etc.) para estimar qué mejoraría.
- Recomendaciones, conclusión y supuestos y límites del simulador.
- Redacción con **IA (Gemini)**; si no hay clave configurada o la API no responde, el reporte se redacta con plantillas, sin IA.
- Descarga en **Word (.docx)** o **PDF**, con indicador de progreso y tiempo estimado mientras se genera.

**Historial y comparaciones** (requieren Oracle)
- Al terminar cualquier simulación (nueva, rápida o demostración) se pregunta si se desea guardarla en el historial; también se puede guardar más tarde.
- **Reportes guardados** y **comparaciones guardadas**, cada uno en su sección.
- **Comparar simulaciones:** elige una ejecución de referencia (A) y otra (B) del mismo caso y compara sus indicadores.
- Desde un reporte guardado se puede volver a simular el mismo caso cambiando lo que indican sus recomendaciones.

## Arquitectura

Tres capas más el modelo del dominio como paquete transversal, bajo el paquete raíz `com.medresource`. Las carpetas `services` y `ui` solo agrupan; las clases van en sus carpetas hijas.

```text
com.medresource
├── model               Modelo del dominio (usado por las tres capas)
├── persistence         Capa de datos: DAO sobre Oracle, JSON, reportes Word/PDF y cliente de IA
├── services            Capa de lógica de negocio
│   ├── management      Casos de uso: casos, reportes, comparaciones, narración del reporte
│   ├── simulation      Motor de simulación, triage, reglas clínicas y métricas
│   └── strategy        Estrategias de asignación (prioridad clínica, FIFO, aleatoria)
├── ui                  Capa de presentación (Swing)
│   ├── view            Ventanas y paneles
│   ├── controller      Controladores, reloj y reproducción de la simulación
│   └── render          Dibujo del plano: 2D, 2.5D e isométrico
└── Main.java           Punto de entrada
```

Dependencias permitidas: `ui → services → persistence → model`. El modelo no depende de ninguna otra capa.

**Patrones y decisiones de diseño**
- **Strategy:** `AllocationStrategy` y sus implementaciones (`ClinicalPriorityStrategy`, `FifoStrategy`, `RandomStrategy`) sobre la base `PriorityBasedStrategy`.
- **DAO:** interfaces (`CaseDao`, `ResultDao`, `ReportDao`, `ComparisonDao`) con implementaciones Oracle; la lógica de negocio no conoce JDBC.
- **Repetibilidad:** todo el azar (llegadas, enfermedades, decisiones aleatorias) sale de la semilla.
- **Reporte con respaldo:** `ReportNarrator` usa Gemini y cae a `TemplateNarrator` si la IA no está disponible.

## Tecnologías

- Java 25 (Temurin)
- Maven
- Swing (interfaz gráfica)
- Oracle Database 18c XE con JDBC `ojdbc11`
- Gson (JSON)
- Apache PDFBox (exportación a PDF)
- Google Gemini API (redacción opcional del reporte)
- JUnit 5

## Requisitos previos

1. JDK 25.
2. Apache Maven 3.8+ (viene incluido en IntelliJ IDEA).
3. Oracle Database 18c XE con el servicio `XEPDB1`. Es necesaria solo para guardar y comparar simulaciones; ver [Base de datos](#base-de-datos-oracle).
4. *(Opcional)* Una clave de la API de Gemini para que la IA redacte el reporte.

## Instalación y ejecución

```bash
git clone https://github.com/Sandres-Herrera/medresource-ai.git
cd medresource-ai
mvn clean install
```

**Ejecutar la aplicación:** abrir `pom.xml` en IntelliJ IDEA como proyecto Maven, esperar a que descargue las dependencias y ejecutar la clase `com.medresource.Main`.

**Verificar el entorno sin abrir ventanas:**

```bash
java -cp target/classes com.medresource.Main --check
```

Imprime la versión de Java y termina (es lo que usan las pruebas; también es lo que ocurre si no hay pantalla).

## Base de datos (Oracle)

Los scripts están en [`src/main/resources/db/`](./src/main/resources/db).

1. Crear un usuario/esquema propio (por ejemplo `MEDRESOURCE_APP`) en el servicio `XEPDB1` de Oracle 18c XE.
2. Conectado con ese usuario, ejecutar **completos (F5 en SQL Developer)** y en este orden:
   - `schema.sql` — tablas, restricciones e índices.
   - `plsql.sql` — funciones, procedimientos y triggers.
   - `demo_plsql.sql` *(opcional)* — datos de demostración.
3. Copiar `src/main/resources/db.properties.example` como `db.properties` (misma carpeta) y completar la conexión:

```properties
db.url=jdbc:oracle:thin:@//localhost:1521/XEPDB1
db.user=MEDRESOURCE_APP
db.password=tu_clave
```

`db.properties` está en `.gitignore`: **las credenciales nunca se suben al repositorio.**

**Modelo de datos** (nombres de tablas y columnas en español; los valores de las enumeraciones de Java se conservan en inglés): `enfermedad`, `enfermedad_especialidad_sec`, `enfermedad_sintoma`, `caso`, `caso_paciente`, `caso_paciente_sintoma`, `ejecucion`, `ejecucion_medico_extra`, `resultado_paciente`, `evento`, `reporte`, `comparacion` y `auditoria_reporte`. La simulación de cada semilla se guarda para poder repetirla y comparar resultados.

**Lógica en la base de datos:** el acceso se hace mediante procedimientos y funciones PL/SQL (`pr_crear_caso`, `pr_guardar_reporte`, `pr_guardar_comparacion`, `fn_integridad_ejecucion`, …) y triggers que validan la integridad (por ejemplo, `trg_validar_tiempo_atencion`).

**Scripts de mantenimiento** (manuales; nunca se ejecutan solos):
- `migracion_integridad_20261122.sql` — correcciones de integridad e índices para instalaciones existentes. Ver [`docs/ORACLE_MIGRACION_INTEGRIDAD.md`](./docs/ORACLE_MIGRACION_INTEGRIDAD.md).
- `reiniciar_historial_demostracion.sql` — borra el historial y reinicia los identificadores. Ver [`docs/REINICIAR_HISTORIAL_DEMOSTRACION.md`](./docs/REINICIAR_HISTORIAL_DEMOSTRACION.md).
- `drop_schema.sql` — elimina el esquema completo. **No ejecutarlo sobre una instalación con datos.**

## Configuración de la IA (opcional)

1. Crear una clave en [Google AI Studio](https://aistudio.google.com/) (Dashboard → API Keys).
2. Copiar `src/main/resources/ai.properties.example` como `ai.properties` y pegar la clave:

```properties
gemini.apiKey=TU_CLAVE
gemini.model=gemini-flash-lite-latest
gemini.timeoutSeconds=30
```

También se acepta la variable de entorno `GEMINI_API_KEY`. `ai.properties` está en `.gitignore`. Sin clave, el reporte se redacta con plantillas y todo lo demás funciona igual. Las respuestas de la IA se guardan en una caché local (`ai-cache/`, ignorada por Git) para no repetir llamadas.

> La IA **solo redacta el reporte**; no decide a qué paciente se atiende. Las decisiones de la simulación las toman las estrategias descritas arriba.

## Pruebas

```bash
mvn test
```

El proyecto tiene más de 2 200 pruebas JUnit 5 en 258 clases, organizadas igual que el código (`model`, `persistence`, `services`, `ui`). Las pruebas de persistencia se ejecutan contra Oracle, por lo que necesitan `db.properties` configurado.

## Control de versiones

Modelo Git Flow simplificado:

| Rama | Uso |
|---|---|
| `main` | Versiones estables; solo recibe la versión final del proyecto |
| `develop` | Integración del trabajo |
| `feature/<nombre-tarea>` | Una rama por cada tarea del desarrollo |

Cada rama `feature` se crea desde `develop`, se sube a GitHub de inmediato y se integra a `develop` mediante *Pull Request*. Cada commit se sube (`git push`) inmediatamente después de crearlo.

**Convención de commits:** [Conventional Commits](https://www.conventionalcommits.org/) con mensajes **en inglés**, en modo imperativo y sin punto final.

| Prefijo | Uso | Ejemplo |
|---|---|---|
| `feat:` | Nueva funcionalidad | `feat: add disease catalog` |
| `fix:` | Corrección de errores | `fix: prevent pediatrician from treating adults` |
| `test:` | Pruebas | `test: add case generator tests` |
| `refactor:` | Mejora sin cambiar el comportamiento | `refactor: extract resource creation` |
| `docs:` | Documentación | `docs: update README` |
| `chore:` | Configuración y mantenimiento | `chore: update dependencies` |

## Estructura del repositorio

```text
medresource-ai/
├── README.md
├── pom.xml
├── .gitignore
├── docs/
│   ├── ORACLE_MIGRACION_INTEGRIDAD.md
│   └── REINICIAR_HISTORIAL_DEMOSTRACION.md
└── src/
    ├── main/
    │   ├── java/com/medresource/
    │   │   ├── model/
    │   │   ├── persistence/
    │   │   ├── services/{management,simulation,strategy}/
    │   │   ├── ui/{view,controller,render}/
    │   │   └── Main.java
    │   └── resources/
    │       ├── db/                   Scripts SQL y PL/SQL de Oracle
    │       ├── db.properties.example
    │       └── ai.properties.example
    └── test/java/com/medresource/    Pruebas JUnit 5
```
