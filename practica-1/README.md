# Proyecto: Suma Secuencial y Paralela en Java

Este proyecto demuestra cómo calcular la suma de una gran cantidad de números utilizando dos enfoques:

- **Suma secuencial:** procesamiento de todos los números en un solo hilo.
- **Suma paralela:** procesamiento distribuido entre múltiples hilos para acelerar la ejecución.

Se aplica también un análisis de rendimiento comparando el tiempo de ejecución según la cantidad de hilos utilizados.

Solo hace falta ejecutar el archivo `Main.java` para iniciar el programa.

---

## Estructura del Proyecto

```bash
├── Main.java             # Punto de entrada del programa
├── Generador.java        # Genera un archivo con números aleatorios
├── SumaSecuencial.java   # Suma los números secuencialmente
├── SumaParalela.java     # Suma los números en paralelo usando hilos
├── Sumador.java          # Clase que representa cada hilo
