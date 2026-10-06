# MedResource AI

Simulador de gestión de recursos hospitalarios limitados (camas, UCI, médicos, enfermeros,
quirófanos, unidades de sangre y morgue) en el que distintas estrategias —FIFO, prioridad
clínica y optimización con inteligencia artificial— deciden qué paciente recibe cada recurso
y en qué momento, mientras llegan emergencias inesperadas.

Cada caso se genera a partir de una **semilla**, de modo que todas las estrategias se
comparan exactamente sobre el mismo escenario.

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
- Swing (interfaz gráfica)
- Oracle Database 18c XE
- JUnit 5

## Requisitos para ejecutar

1. JDK 25 instalado.
2. IntelliJ IDEA (incluye Maven).
3. Oracle Database 18c XE con el servicio `XEPDB1` (se necesita a partir de la etapa de persistencia).

## Cómo ejecutar

1. Clonar el repositorio y abrir el archivo `pom.xml` en IntelliJ IDEA como proyecto.
2. Esperar a que Maven descargue las dependencias.
3. Ejecutar las pruebas con `mvn test`.

## Estructura del proyecto

Arquitectura en tres capas con el modelo del dominio como módulo transversal.
Las carpetas padre (`services` y `ui`) solo agrupan; todas las clases van en las
carpetas hijas.

```text
com.medresource
├── model               Modelo del dominio (usado por las tres capas)
├── persistence         Capa de acceso a datos (Oracle, DAO)
├── services            Capa de lógica de negocio
│   ├── management      Servicios que gestionan los casos de uso
│   ├── simulation      Motor de simulación, reglas clínicas y métricas
│   └── strategy        Estrategias de asignación (FIFO, prioridad clínica, IA)
└── ui                  Capa de presentación
    ├── view            Ventanas Swing
    └── controller      Controladores de eventos
```

## Ramas

| Rama | Uso |
|---|---|
| `main` | Versiones estables |
| `develop` | Integración del trabajo |
| `feature/<nombre-tarea>` | Una rama por cada tarea del desarrollo |

Cada rama `feature` se crea desde `develop`, se sube a GitHub de inmediato y se integra
a `develop` mediante un *Pull Request*.

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