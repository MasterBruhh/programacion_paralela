package edu.pucmm.topology;

import edu.pucmm.core.Message;
import edu.pucmm.core.Node;
import edu.pucmm.core.NetworkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MeshNetwork implements NetworkTopology {

    private List<Node> nodes;
    private ExecutorService executor;

    @Override
    public void configureNetwork( int numberOfNodes) {
        nodes = new ArrayList<>();
        executor = Executors.newFixedThreadPool(numberOfNodes);

        for (int i = 0; i < numberOfNodes; i++) {
            Node node = new Node(i);
            nodes.add(node);
            NetworkManager.registerNode(node); // Aquí usas el manager que recibiste
        }

        for (Node node : nodes) {
            for (Node other : nodes) {
                if (node != other) {
                    node.addNeighbor(other);
                }
            }
        }
    }


    @Override
    public void sendMessage(int sourceId, int destinationId, String messageText) {
        Message msg = new Message(sourceId, destinationId, messageText, System.currentTimeMillis());
        Node source = NetworkManager.getNode(sourceId);
        if (source != null) {
            source.sendMessage(msg);
        }
    }

    @Override
    public void startSimulation() {
        for (Node node : nodes) {
            executor.submit(node); // Ejecutar cada nodo como hilo
        }
    }

    @Override
    public void printNetworkState() {
        System.out.println("Red Mesh (completamente conectada):");
        for (Node node : nodes) {
            System.out.println("Nodo " + node.getId() + " conectado a: " + node.getNeighborIds());
        }
    }
}
