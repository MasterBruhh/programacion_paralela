# Sistema de Gestión de Tráfico - Proyecto Final
## Escenario 2: Control Avanzado de Intersecciones con Protocolos de Emergencia

Este proyecto implementa un sistema completo de gestión de tráfico que simula el **Escenario 2** con todas las características avanzadas requeridas, incluyendo protocolos de emergencia, múltiples estrategias de gestión de intersecciones, y concurrencia robusta.

## Características Implementadas

### **Gestión Avanzada de Intersecciones**
- **Tres modos de operación dinámicos:**
  - **FIFO**: Primero en llegar, primero en cruzar
  - **ROTATIVO**: Rotación automática por carriles (Norte→Oeste→Sur→Este)  
  - **EMERGENCIA**: Protocolo especial para vehículos de emergencia

- **Cambio automático de estrategias** basado en nivel de congestión
- **Umbral de congestión configurable** (3+ vehículos por defecto)

### **Protocolos de Emergencia Avanzados**
- **Activación automática** cuando vehículos de emergencia ingresan al sistema
- **Prioridad absoluta** para vehículos de emergencia en intersecciones
- **Bloqueo temporal** de otros carriles durante emergencias
- **Desactivación inteligente** tras el paso del vehículo de emergencia
- **Soporte para múltiples emergencias** simultáneas por grupo
- **Timeout de seguridad** para evitar bloqueos permanentes

### **Tipos de Vehículos**
- **NORMAL**: Vehículos estándar con comportamiento básico
- **HEAVY**: Vehículos pesados con velocidad reducida
- **PUBLIC_TRANSPORT**: Transporte público con prioridad parcial
- **EMERGENCY**: Vehículos de emergencia con máxima prioridad

### **Sistema de Concurrencia Robusto**
- **ThreadSafeVehicleRegistry**: Registro thread-safe de vehículos activos
- **IntersectionSemaphore**: Control de acceso con prioridad por tipo de vehículo
- **IntersectionController**: Gestor centralizado de intersecciones con sincronización
- **VehicleLifecycleManager**: Coordinador completo del ciclo de vida vehicular
- **CollisionDetector**: Prevención de colisiones en tiempo real

### **Interfaz Gráfica Avanzada**
- **Simulación visual en tiempo real** con JavaFX
- **Diferentes colores por tipo de vehículo:**
  - 🔴 Rojo: Vehículos de emergencia
  - 🟡 Amarillo: Transporte público
  - 🔵 Azul: Vehículos normales
  - ⚫ Negro: Vehículos pesados
- **Visualización de autopistas y calles urbanas**
- **Indicadores de estado de intersecciones**

### **Gestión de Autopistas**
- **6 carriles de autopista** bidireccionales:
  - WEST_LEFT, WEST_CENTER, WEST_RIGHT
  - EAST_LEFT, EAST_CENTER, EAST_RIGHT
- **Cambios de carril inteligentes**
- **Gestión de velocidades diferenciadas**
- **Integración con intersecciones urbanas**

## Arquitectura del Sistema

### **Componentes Principales**

#### **Core (Núcleo)**
- `SimulationEngine`: Motor principal de la simulación
- `VehicleLifecycleManager`: Gestor completo del ciclo de vida
- `IntersectionController`: Controlador avanzado de intersecciones
- `TrafficLightController`: Control de semáforos inteligente

#### **Concurrencia**
- `ThreadSafeVehicleRegistry`: Registro thread-safe con colas por carril
- `IntersectionSemaphore`: Semáforo con sistema de prioridades
- `VehicleTask`: Tareas concurrentes para movimiento vehicular
- `CollisionDetector`: Detección de colisiones en tiempo real

#### **Modelos**
- `Vehicle`: Entidad principal con estados avanzados
- `StartPoint`: Puntos de entrada configurables
- `VehicleType`: Enumeración con tipos especializados
- `TrafficLightState`: Estados de semáforos inteligentes
- `HighwayLane`: Gestión de carriles de autopista

## Instalación y Ejecución

### **Requisitos**
- Java 21 o superior
- Gradle 8.0+
- JavaFX 21.0.2

### **Compilación**
```bash
./gradlew build
```

### **Ejecución**
```bash
./gradlew run
```

## 📊 Funcionalidades Destacadas del Escenario 2

### **1. Protocolos de Emergencia Inteligentes**
- **Detección automática:** El sistema identifica vehículos EMERGENCY al ingresar
- **Activación inmediata:** Protocolo activado instantáneamente en el grupo correspondiente  
- **Bloqueo selectivo:** Solo afecta carriles necesarios, optimizando flujo
- **Múltiples emergencias:** Soporte para varias emergencias simultáneas
- **Desactivación limpia:** Retorno automático al modo anterior tras paso de emergencia

### **2. Estrategias de Gestión Dinámicas**
- **Análisis de congestión:** Monitoreo continuo del número de vehículos por carril
- **Cambio adaptativo:** Transición automática entre modos FIFO y ROTATIVO
- **Optimización de flujo:** Cada estrategia optimizada para diferentes escenarios
- **Métricas en tiempo real:** Seguimiento de efectividad de cada estrategia

### **3. Concurrencia Avanzada**
- **Sincronización robusta:** Uso de locks específicos para cada operación crítica
- **Thread safety completo:** Todas las estructuras de datos protegidas
- **Manejo de interrupciones:** Gestión elegante de cancelación de hilos
- **Pool de threads dinámico:** Escalamiento automático según demanda

### **4. Gestión de Estado Compleja**
- **Estados vehiculares:** Seguimiento detallado del estado de cada vehículo
- **Estados de intersección:** Control preciso del estado de cada punto de cruce
- **Persistencia de estado:** Mantenimiento de coherencia durante cambios de modo
- **Recuperación de estado:** Capacidad de reanudar operaciones tras interrupciones

## Métricas y Monitoreo

### **Logs Informativos**
El sistema genera logs detallados que incluyen:
- Creación y registro de vehículos por tipo
- Activación/desactivación de protocolos de emergencia
- Cambios de modo de operación de intersecciones
- Estadísticas de tiempo de cruce y espera
- Eventos de colisión evitadas

### **Ejemplo de Logs de Emergencia**
```
WARNING: EMERGENCIA: Vehículo #33 entrando al sistema
WARNING: PROTOCOLO DE EMERGENCIA ACTIVADO para grupo ARRIBA - Emergencias=[33]
INFO: Turno otorgado a 33 desde EAST_RIGHT (Modo: EMERGENCY)
WARNING: PROTOCOLO DE EMERGENCIA DESACTIVADO para grupo ARRIBA
```

### **Ejemplo de Gestión de Intersecciones**
```
INFO: MODO CAMBIADO A ROTATIVO debido a congestión - Iniciando con NORTH
INFO: Rotación avanzada a WEST
INFO: Turno otorgado a 26 desde WEST (Modo: ROTATIVE)
INFO: MODO CAMBIADO A FIFO - 1 vehículos en cola global
```

## Configuración Avanzada

### **Parámetros de Simulación**
- **Umbral de congestión:** Modificable en `IntersectionController.CONGESTION_THRESHOLD`
- **Timeout de emergencia:** Configurable en protocolos de emergencia (30 segundos por defecto)
- **Velocidades por tipo:** Personalizables en clase `Vehicle`
- **Frecuencia de spawn:** Ajustable en `SimulationEngine`

### **Puntos de Entrada**
El sistema soporta múltiples puntos de entrada:
- **Urbanos:** NORTH_L, SOUTH_L, EAST, WEST, NORTH_D, SOUTH_D
- **Autopista:** WEST_LEFT, WEST_CENTER, WEST_RIGHT, EAST_LEFT, EAST_CENTER, EAST_RIGHT

## Pruebas y Validación

### **Escenarios Probados**
1. **Operación normal** con múltiples tipos de vehículos
2. **Congestión alta** con cambio automático a modo ROTATIVO
3. **Emergencias simples** con activación/desactivación correcta
4. **Múltiples emergencias** simultáneas en diferentes grupos
5. **Recuperación tras emergencias** con retorno al modo previo
6. **Manejo de interrupciones** y cancelación elegante

### **Validación de Concurrencia**
- ✅ Sin deadlocks observados durante pruebas extendidas
- ✅ Sincronización correcta entre todos los componentes
- ✅ Manejo seguro de recursos compartidos
- ✅ Escalamiento eficiente con múltiples vehículos

## Cumplimiento de Requisitos del Escenario 2

### **Requisitos Básicos**
- ✅ **Simulación de tráfico:** Sistema completo de simulación vehicular
- ✅ **Múltiples tipos de vehículos:** 4 tipos implementados con comportamientos únicos
- ✅ **Gestión de intersecciones:** 3 estrategias dinámicas implementadas
- ✅ **Interfaz gráfica:** Visualización en tiempo real con JavaFX

### **Requisitos Avanzados**
- ✅ **Protocolos de emergencia:** Sistema completo y robusto implementado
- ✅ **Prioridades dinámicas:** Cambio de prioridades basado en tipo y emergencias
- ✅ **Concurrencia robusta:** Thread safety completo en todo el sistema
- ✅ **Gestión de autopistas:** 6 carriles bidireccionales con lógica especializada
- ✅ **Adaptabilidad:** Cambio dinámico de estrategias según congestión

### **Características Adicionales**
- ✅ **Logging avanzado:** Sistema completo de logs informativos
- ✅ **Métricas de rendimiento:** Seguimiento de tiempos y estadísticas
- ✅ **Manejo de errores:** Gestión robusta de excepciones y estados inválidos
- ✅ **Configurabilidad:** Parámetros ajustables para diferentes escenarios

## Tecnologías Utilizadas

- **Java 21:** Lenguaje principal con características modernas
- **JavaFX 21.0.2:** Interfaz gráfica avanzada y responsiva
- **Gradle:** Sistema de construcción y gestión de dependencias
- **Concurrent Collections:** Estructuras de datos thread-safe
- **ExecutorService:** Gestión avanzada de pools de threads
- **Logging API:** Sistema de logs integrado de Java

---

**Desarrollado como Proyecto Final de Programación Paralela**  
*Implementación completa del Escenario 2 con todas las características avanzadas requeridas*
