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

Para ejecutar el proyecto, solo es necesario compilar el código y ejecutar la clase main.