# MedResource AI

Simulador de gestión de recursos hospitalarios limitados: camas, UCI, médicos, enfermeros,
quirófanos, unidades de sangre y morgue. Mientras llegan pacientes y emergencias inesperadas,
distintas estrategias deciden qué paciente recibe cada recurso y en qué momento:

- FIFO (orden de llegada).
- Prioridad clínica (triage).
- Inteligencia artificial: una **red neuronal propia** entrenada con el simulador.

Cada caso se genera a partir de una **semilla**. Así, todas las estrategias se comparan sobre
exactamente el mismo escenario, y una ejecución se puede repetir y obtener el mismo resultado.
La simulación se puede ver en una **vista 2D animada**, donde los pacientes se mueven por el plano
del hospital.

## Proyecto de aula

- **Universidad:** Universidad Popular del Cesar
- **Asignatura:** Programación de Computadores III (SS462)
- **Docente:** Ing. Esp. Alfredo Bautista

## Integrantes

| Nombre | Usuario de GitHub | Rol |
|---|---|---|
| Santiago Herrera | @Sandres-Herrera | Líder |
| Omar Gámez | @Osgh1015 | Desarrollador |

## Tecnologías

- Java 25 (Temurin)
- Maven
- Swing y Java 2D (interfaz gráfica y animación)
- Oracle Database 18c XE (JDBC con `ojdbc11`)
- JUnit 5
- Red neuronal implementada en Java puro (sin librerías externas ni servicios en línea)

## Requisitos

1. JDK 25.
2. IntelliJ IDEA (incluye Maven).
3. Oracle Database 18c XE con el servicio `XEPDB1`. Solo hace falta para las funciones que
   guardan datos; la simulación y la vista 2D funcionan sin base de datos.

## Cómo ejecutar

1. Clonar el repositorio y abrir `pom.xml` en IntelliJ IDEA como proyecto.
2. Esperar a que Maven descargue las dependencias.
3. Ejecutar la aplicación: clase **`com.medresource.Main`**.
4. En la ventana principal elegir una opción:
    - **Nueva simulación…** abre un diálogo para configurar el hospital, el caso y la estrategia.
    - **Simulación rápida** usa el hospital estándar, 60 pacientes y la estrategia de prioridad clínica.
    - **Demostración: catástrofe** usa 100 pacientes en el escenario de catástrofe.
5. En la ventana de simulación pulsar **Iniciar**. La simulación abre en pausa, en el minuto 0.
   Con el selector **Velocidad** se elige entre ×1, ×5, ×20 y ×60 minutos simulados por segundo.

### Ventana de simulación

- Plano del hospital con los pacientes animados. El color indica el nivel de triage (rojo I … azul V),
  la figura indica el sexo y las figuras más pequeñas son menores de edad. Los fallecidos se ven en gris.
- Botones Iniciar/Pausar, Paso (+1 minuto) y Reiniciar.
- Contadores en vivo:
    - Pacientes: llegados, en espera, en tratamiento, en UCI, altas, remitidos, fallecidos y sin atender.
    - Recursos libres: camas, UCI, quirófanos, médicos, enfermeros, sangre y morgue.
- Registro de eventos, barra de progreso y botón **Guardar captura** (PNG del plano).
- Al terminar, un resumen con la mortalidad, las muertes evitables, la espera promedio y los años de vida salvados.

### Otros programas

| Clase | Para qué sirve |
|---|---|
| `com.medresource.ui.view.AnimationDemo` | Vista previa de la animación sin la ventana principal. Argumentos opcionales: `ESCENARIO PACIENTES SEMILLA`, por ejemplo `CATASTROPHE 100 7`. |
| `com.medresource.ui.view.HospitalMapDemo` | Muestra el plano del hospital estándar, sin pacientes. |
| `com.medresource.services.simulation.TrainModels` | Entrena las cuatro redes neuronales y guarda sus pesos en `models/`. Solo hace falta volver a ejecutarlo si cambia el simulador. |
| `com.medresource.services.simulation.CompareStrategies` | Compara todas las estrategias en los cuatro escenarios y en tres tamaños de hospital. Calcula intervalos de confianza del 95 %. Argumentos opcionales: casos por escenario y carpeta de modelos. |

> Las estrategias de IA leen sus redes de la carpeta `models/` (en la raíz del proyecto), así que
> la aplicación debe ejecutarse con la raíz del proyecto como carpeta de trabajo. Es lo normal en IntelliJ.

## Pruebas

Panel Maven → *Lifecycle* → **test**, o `mvn test` si Maven está en el `PATH`.

El proyecto tiene alrededor de 1000 pruebas. Las 4 pruebas de Oracle (`OracleConnectionTest` y las tres
`Oracle…IntegrationTest`) aparecen como *Skipped* cuando no existe `db.properties`. Es lo esperado y no
es un error.

## Base de datos (Oracle)

1. Crear el usuario `medresource` en `XEPDB1`.
2. Copiar `src/main/resources/db.properties.example` como `src/main/resources/db.properties` y completar
   la clave. El archivo `db.properties` está en `.gitignore` y nunca se sube.
3. Crear las tablas con `src/main/resources/db/schema.sql`. Para borrarlas está `drop_schema.sql`.

El esquema tiene 9 tablas:

- **Catálogo de enfermedades:** `disease`, `disease_secondary_specialty` y `disease_symptom`.
- **Casos generados:** `sim_case`, `case_patient` y `case_patient_symptom`.
- **Resultados de cada ejecución:** `sim_result`, que también guarda la configuración del hospital,
  `patient_result` y `sim_event`.

El acceso a datos usa el patrón DAO con una interfaz genérica `GenericDao<T, ID>`.

## Cómo funciona la simulación

### Hospital y caso

- **Hospital (`HospitalConfig`)**: 7 valores configurables con su rango.

  | Recurso | Rango | Estándar |
    |---|---|---|
  | Camas de hospitalización | 1–200 | 30 |
  | Camas de UCI | 1–50 | 6 |
  | Quirófanos | 1–20 | 4 |
  | Médicos | 1–50 | 16 |
  | Enfermeros | 1–100 | 24 |
  | Unidades de sangre por tipo | 0–100 | 10 |
  | Cupos de la morgue | 1–50 | 5 |

- **Caso (`CaseConfig`)**: escenario, semilla, cantidad de pacientes (1–1000) y duración de las
  llegadas (60–1440 minutos). El escenario solo cambia cómo llegan los pacientes, no el hospital:

  | Escenario | Llegadas |
    |---|---|
  | Hospital normal | Repartidas de forma uniforme. |
  | Alta demanda | Concentradas en la primera mitad. |
  | Catástrofe | El 70 % llega en el primer 10 % del tiempo, con el triple de urgencias. |
  | Emergencias inesperadas | Un 20 % de casos urgentes desde el primer cuarto. |

### Motor

`SimulationEngine` avanza minuto a minuto y no usa azar. En cada minuto, en este orden:

1. Salen de la morgue los cuerpos que cumplieron su tiempo.
2. Empeoran los pacientes que esperan.
3. Terminan los tratamientos que llegaron a su fin.
4. Llegan los pacientes nuevos, se clasifican (triage) y se diagnostican.
5. La estrategia reparte los recursos libres.
6. Los fallecidos pasan a la morgue.

Reglas clínicas principales:

- El triage es el del síntoma más grave. La edad y el sexo nunca lo cambian.
- La compatibilidad del médico depende de su especialidad. El pediatra solo atiende a menores de 15 años
  y el médico general nunca opera.
- Las transfusiones respetan la compatibilidad de sangre.
- Si ningún médico puede tratar una enfermedad, el paciente se remite.

Las estrategias nunca ven la enfermedad real del paciente, solo su diagnóstico.

### Estrategias

| Estrategia | Criterio |
|---|---|
| FIFO | Orden de llegada. |
| Prioridad clínica | Nivel de triage. El triage sube un nivel por cada 120 minutos de espera. |
| Aleatoria | Orden al azar con semilla. Se usa para generar datos de entrenamiento. |
| IA (4 objetivos) | Red neuronal entrenada para maximizar vidas, maximizar años de vida, equidad o minimizar la espera. |

### Inteligencia artificial

- Perceptrón multicapa **14 → 16 → 8 → 1**, implementado en Java (`NeuralNetwork`) y repetible con semilla.
- Recibe 14 características de cada paciente y del hospital (`FeatureExtractor`), por ejemplo la salud,
  la edad, la urgencia, la espera y los recursos disponibles.
- Prioridad = predicción de la red × urgencia del paciente. Sola, la red tendía a atender primero a los
  pacientes más sanos; multiplicar por la urgencia lo corrige.
- Entrenamiento:
    - `RecordingStrategy` anota cada decisión y `OutcomeScorer` puntúa el desenlace.
    - `Trainer` ajusta los pesos y `ModelTrainingService` entrena una red por objetivo.
    - Las redes se entrenan con las semillas 1…N y se evalúan con semillas a partir de 1 000 000.
- Los pesos se guardan en texto en `models/<objetivo>.txt` mediante `ModelStore`.
- Limitación conocida: se entrena con el hospital estándar, y para los pacientes que no fueron elegidos
  no se sabe qué habría pasado si los hubieran atendido.

### Vista 2D

- El hospital se dibuja como un tablero de áreas (`HospitalLayout`, generado por `LayoutFactory` a partir
  de `HospitalConfig`). Las áreas son: triage con la entrada, sala de espera, hospitalización, UCI,
  banco de sangre, quirófanos, morgue, alta/salida y sala del personal.
- `SceneState` deduce de los eventos del motor dónde está cada paciente. `Motion` lo mueve suavemente
  entre tarjetas y `ScenePlayer` une el motor con la escena.
- La vista **nunca cambia el resultado**: el motor corre igual con o sin interfaz, y moverse no consume
  minutos simulados.
- Doctores y enfermeros se muestran como tarjetas fijas en la sala del personal.

## Estructura del proyecto

Arquitectura en tres capas, con el modelo del dominio como módulo transversal. Las carpetas padre
(`services` y `ui`) solo agrupan; todas las clases van en las carpetas hijas.

```text
com.medresource
├── Main                  Punto de entrada: abre la ventana principal
├── model                 Modelo del dominio (usado por las tres capas)
├── persistence           Capa de acceso a datos (Oracle, DAO)
├── services              Capa de lógica de negocio
│   ├── management        Servicios de los casos de uso (catálogo, casos, resultados)
│   ├── simulation        Motor, generador de casos, métricas, comparación y entrenamiento
│   └── strategy          Estrategias de asignación y red neuronal
└── ui                    Capa de presentación
    ├── controller        Controladores (reloj de la simulación, estrategias, reproducción)
    ├── render            Dibujo del plano y de los pacientes con Java 2D
    └── view              Ventanas y paneles Swing
```

### Clases principales por paquete

| Paquete | Clases |
|---|---|
| `model` | `Patient`, `Person`, `Disease`, `DiseaseCatalog`, `Symptom`, `TriageLevel`, `Doctor`, `Nurse`, `StaffMember`, `Occupancy`, `Bed`, `Room`, `OperatingRoom`, `BloodBank`, `BloodType`, `Morgue`, `Hospital`, `HospitalConfig`, `CaseConfig`, `Scenario`, `SimulationCase`, `SimulationEvent`, `SimulationResult`, `HealthModel`, `RunMetrics`, `HospitalLayout`, `Area`, `ZoneType` |
| `persistence` | `GenericDao`, `OracleDiseaseDao`, `OracleCaseDao`, `OracleResultDao`, `ConnectionManager`, `DatabaseConfig`, `SchemaInstaller`, `TransactionRunner` |
| `services.management` | `DiseaseService`, `CaseService`, `SimulationService` |
| `services.simulation` | `SimulationEngine`, `CaseGenerator`, `HospitalFactory`, `LayoutFactory`, `TriageService`, `MetricsCalculator`, `StrategyComparator`, `ModelTrainingService`, `TrainingDataGenerator`, `TrainModels`, `CompareStrategies` |
| `services.strategy` | `AllocationStrategy`, `PriorityBasedStrategy`, `FifoStrategy`, `ClinicalPriorityStrategy`, `RandomStrategy`, `OptimizationStrategy`, `NeuralNetwork`, `FeatureExtractor`, `Trainer`, `RecordingStrategy`, `OutcomeScorer`, `SurvivalEstimator`, `ModelStore` |
| `ui.controller` | `SimulationController`, `ScenePlayer`, `StrategyOption`, `SimulationSetup`, `LiveCounters` |
| `ui.render` | `SceneRenderer`, `Renderer2D`, `Camera`, `Palette`, `SceneState`, `Motion`, `PatientPainter`, `PatientMarker` |
| `ui.view` | `MainWindow`, `CaseConfigDialog`, `SimulationWindow`, `HospitalMapPanel`, `AnimationDemo`, `HospitalMapDemo` |

### Otras carpetas

| Carpeta | Contenido |
|---|---|
| `models/` | Pesos de las cuatro redes neuronales entrenadas |
| `src/main/resources/db/` | Scripts SQL del esquema de Oracle |
| `src/test/java/` | Pruebas JUnit, en el mismo paquete que la clase que prueban |

## Estado del desarrollo

| Etapa | Estado |
|---|---|
| Modelo del dominio | ✔ |
| Reglas clínicas, generador de casos, motor y estrategias | ✔ |
| Inteligencia artificial (red neuronal, entrenamiento y modelos) | ✔ |
| Métricas y comparación de estrategias | ✔ |
| Persistencia en Oracle | ✔ |
| Plano del hospital y vista 2D animada | ✔ |
| Ventana principal, configuración y ventana de simulación | ✔ |
| Ventana del catálogo de enfermedades | Pendiente |
| Ventana de resultados y comparación | Pendiente |
| Vista 2.5D (isométrica) | Pendiente |

## Ramas

| Rama | Uso |
|---|---|
| `main` | Versiones estables |
| `develop` | Integración del trabajo |
| `feature/<nombre-tarea>` | Una rama por cada tarea del desarrollo |

Cada rama `feature` se crea desde `develop`, se sube a GitHub de inmediato y se integra a `develop`
mediante un *Pull Request* con *merge commit*. Quien abre el *Pull Request* no lo aprueba: lo aprueba
el otro integrante.

## Convención de commits

Se usa *Conventional Commits* con mensajes **en inglés**, en modo imperativo y sin punto final.
Cada commit se sube a GitHub inmediatamente después de crearlo.

| Prefijo | Uso | Ejemplo |
|---|---|---|
| `feat:` | Nueva funcionalidad | `feat: add disease catalog` |
| `fix:` | Corrección de errores | `fix: prevent pediatrician from treating adults` |
| `test:` | Pruebas | `test: add case generator tests` |
| `refactor:` | Mejora sin cambiar el comportamiento | `refactor: extract resource creation` |
| `docs:` | Documentación | `docs: update README` |
| `chore:` | Configuración y mantenimiento | `chore: update dependencies` |