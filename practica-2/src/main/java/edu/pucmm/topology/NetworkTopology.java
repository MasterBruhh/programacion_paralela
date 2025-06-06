package edu.pucmm.topology;

public interface NetworkTopology {
    // Aquí se configura la estructura de la red (crear nodos y conexiones)
    void configureNetwork(int numberOfNodes);

    // Aquí se envía un mensaje desde un nodo origen a un nodo destino
    void sendMessage(int sourceId, int destinationId, String message);

    // Este es el metodo para iniciar la simulación o procesamiento en la red
    void startSimulation();

    // Esto es para imprimir o para retornar el estado actual de la red (es opcional, solo lo agrego para debug o visualización)
    void printNetworkState();
}
