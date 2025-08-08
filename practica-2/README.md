# Framework de Topologías de Redes para Procesamiento Paralelo

Este proyecto implementa un **framework educativo** en Java que permite simular múltiples **topologías de redes** orientadas al procesamiento paralelo, integrando **concurrencia con hilos** y simulación de **comunicación entre nodos**.

---

## Objetivo

Diseñar e implementar un entorno que permita configurar distintas topologías de redes, simular la comunicación entre nodos de forma concurrente, y observar el comportamiento de cada estructura de red bajo mensajes distribuidos.

---

## Topologías implementadas

El sistema actualmente soporta las siguientes **8 topologías**:

- 🔸 `BusNetwork`: todos los nodos comparten un medio común.
- 🔸 `RingNetwork`: los nodos están conectados en un anillo cerrado.
- 🔸 `TreeNetwork`: estructura jerárquica como árbol binario.
- 🔸 `FullyConnectedNetwork`: cada nodo tiene enlace directo con todos los demás.
- 🔹 `MeshNetwork`: los nodos están conectados con sus vecinos en una malla 2D.
- 🔹 `StarNetwork`: un nodo central conectado a todos los demás.
- 🔹 `HypercubeNetwork`: nodos conectados según diferencias de bits en el ID.
- 🔹 `SwitchedNetwork`: comunicación a través de switches simulados.
---

Nota: se implementó Map<Integer, Node> en topologías más complejas, donde la eficiencia de acceso directo por ID y la flexibilidad en las conexiones es clave para un manejo óptimo de la estructura de red.

---

## Estructura del proyecto

```bash
src/
├── main/
│   └── java/
│       └── edu/
│           └── pucmm/
│               ├── core/
│               │   ├── Node.java
│               │   ├── Message.java
│               │   └── NetworkManager.java
│               ├── topology/
│               │   ├── NetworkTopology.java
│               │   ├── BusNetwork.java
│               │   ├── RingNetwork.java
│               │   ├── TreeNetwork.java
│               │   ├── FullyConnectedNetwork.java
│               │   ├── MeshNetwork.java
│               │   ├── StarNetwork.java
│               │   ├── HypercubeNetwork.java
│               |  └── SwitchedNetwork.java
│               └── main/
│                   └── Main.java
```

## Funcionamiento

- Seleccionas una topología de red. 
- Ingresas el número de nodos. 
- Ingresas el nodo de origen y destino. 
- El mensaje es enviado y procesado usando hilos concurrentes.

## Ejecución

## Ejecutando Main.java

Para ejecutar el proyecto, solo es necesario compilar el código y ejecutar la clase main.

### Con Maven

```bash
# Compila el proyecto (descarga dependencias y genera target/)
mvn compile

# Lanza la clase principal
autocompletado opcional: mvn exec:java
mvn exec:java -Dexec.mainClass=edu.pucmm.Main
```

### Con Gradle

```bash
# Compila y ejecuta en una sola orden
gradle run
# o usando el wrapper
gradlew run
```

Ambos comandos abrirán un menú interactivo donde podrás elegir la topología, número de nodos y enviar mensajes entre ellos.

