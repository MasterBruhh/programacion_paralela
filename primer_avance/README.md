# Sistema de Gestión de Tráfico - Simulación Concurrente

Este proyecto implementa una simulación avanzada de tráfico con gestión concurrente de vehículos en una intersección, desarrollado en Java con JavaFX y utilizando técnicas de programación paralela.

## Características

- **Simulación visual interactiva** con JavaFX
- **Programación concurrente** con hilos y semáforos
- **Detección de colisiones** avanzada (algoritmo AABB)
- **Múltiples modos de cruce**:
  - FIFO (Primero en llegar, primero en cruzar)
  - Rotativo (Por carriles)
  - Protocolo de emergencia
- **Tipos de vehículos**: Normal y de emergencia
- **Direcciones de movimiento**: Recto, izquierda, derecha, vuelta en U
- **Estadísticas en tiempo real** del tráfico

## Requisitos del Sistema

### Requisitos Mínimos
- **Java**: JDK 21 o superior
- **JavaFX**: 21.0.2 (incluido en las dependencias)
- **Gradle**: 8.0+ (incluido wrapper)

### Verificar Java
```bash
java --version
```
Debe mostrar Java 21 o superior.

## Instalación y Configuración

### 1. Clonar el Repositorio
```bash
git clone https://github.com/MasterBruhh/programacion_paralela.git
cd programacion_paralela
```

### 2. Verificar Permisos (Linux/macOS)
```bash
chmod +x gradlew
```

### 3. Construir el Proyecto
```bash
# En Windows
./gradlew.bat build

# En Linux/macOS
./gradlew build
```

### 4. Verificar la Construcción
El comando anterior debería completarse sin errores y crear:
- `build/libs/sistema-gestion-trafico-1.0-SNAPSHOT.jar`
- `build/distributions/sistema-gestion-trafico-1.0-SNAPSHOT.zip`

## Ejecución del Programa

### Método 1: Usando Gradle (Recomendado)
```bash
# En Windows
./gradlew.bat run

# En Linux/macOS
./gradlew run
```

### Método 2: Ejecutando el JAR
```bash
java --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml -jar build/libs/sistema-gestion-trafico-1.0-SNAPSHOT.jar
```

### Método 3: Usando Scripts Generados
```bash
# En Windows
build/scripts/sistema-gestion-trafico.bat

# En Linux/macOS
./build/scripts/sistema-gestion-trafico
```

## Cómo Usar la Simulación

### Interfaz Principal
Al ejecutar el programa se abrirá una ventana con:
- **Intersección visual**: Vista cenital de una intersección de 4 vías
- **Panel de estadísticas**: Información en tiempo real sobre el tráfico
- **Menús de configuración**: Para agregar vehículos y controlar la simulación

### Controles Disponibles

#### Menú "Vehículo"
- **Agregar Vehículo Normal**: Crea un vehículo estándar
- **Agregar Vehículo de Emergencia**: Crea un vehículo prioritario

#### Menú "Configuración"
- **Cambiar Punto de Inicio**: 
  - Norte (arriba)
  - Sur (abajo)
  - Este (derecha)
  - Oeste (izquierda)
- **Cambiar Dirección**:
  - Recto
  - Izquierda
  - Derecha
  - Vuelta en U

#### Menú "Simulación"
- **Iniciar**: Comienza la simulación
- **Detener**: Pausa la simulación
- **Reiniciar**: Limpia todos los vehículos

### Interpretación Visual
- **Círculos de colores**: Representan vehículos
  - 🔵 Azul: Vehículos normales
  - 🔴 Rojo: Vehículos de emergencia
- **Líneas grises**: Carriles de tráfico
- **Líneas de pare**: Donde los vehículos se detienen antes de cruzar

### Estadísticas en Tiempo Real
El panel muestra:
- **Modo actual**: FIFO, Rotativo, o Emergencia
- **Estado de carriles**: Cantidad de vehículos esperando por carril
- **Vehículos en pare**: Identificación del vehículo en la línea de pare
- **Alertas de congestión**: Cuando 3+ carriles tienen tráfico

## Estructura del Proyecto

```
src/main/java/edu/pucmm/trafico/
├── Main.java                           # Punto de entrada principal
├── concurrent/                         # Componentes de concurrencia
│   ├── CollisionDetector.java         # Detección de colisiones AABB
│   ├── IntersectionSemaphore.java     # Semáforo de intersección
│   ├── ThreadSafeVehicleRegistry.java # Registro thread-safe de vehículos
│   └── VehicleTask.java               # Tarea concurrente por vehículo
├── core/                              # Lógica central
│   ├── IntersectionController.java    # Controlador de intersección
│   ├── SimulationEngine.java          # Motor de simulación
│   └── VehicleLifecycleManager.java   # Gestor de ciclo de vida
├── model/                             # Modelos de datos
│   ├── Direction.java                 # Direcciones de movimiento
│   ├── IntersectionState.java         # Estado de intersección
│   ├── StartPoint.java                # Puntos de inicio
│   ├── Vehicle.java                   # Modelo de vehículo
│   └── VehicleType.java               # Tipos de vehículo
└── view/                              # Interfaz de usuario
    └── TrafficSimulationController.java # Controlador JavaFX
```

## Ejemplos de Uso

### Escenario 1: Tráfico Normal
1. Ejecutar la aplicación
2. Agregar vehículos desde diferentes puntos
3. Observar el comportamiento FIFO
4. Notar cómo se forman las colas

### Escenario 2: Congestión
1. Agregar múltiples vehículos rápidamente
2. Observar el cambio automático a modo rotativo
3. Ver las estadísticas de congestión

### Escenario 3: Emergencia
1. Agregar un vehículo de emergencia
2. Observar cómo obtiene prioridad inmediata
3. Ver el protocolo de emergencia en acción