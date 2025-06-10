package edu.pucmm.core;

import edu.pucmm.topology.*;

import java.util.HashMap;
import java.util.Map;

public class NetworkManager {

    private NetworkTopology topology;
    private static Map<Integer, Node> nodes = new HashMap<>();

    public void setupTopology(String type, int numberOfNodes) {
        switch (type.toLowerCase()) {
            case "bus":
                topology = new BusNetwork();
                break;
            case "ring":
                topology = new RingNetwork();
                break;
            case "tree":
                topology = new TreeNetwork();
                break;
            case "fully":
            case "fullyconnected":
                topology = new FullyConnectedNetwork();
                break;
            case "mesh":
                topology = new MeshNetwork();
                break;
            default:
                System.out.println("Topología no reconocida: " + type);
                return;
        }

        topology.configureNetwork(numberOfNodes);
    }

    public void startSimulation() {
        if (topology != null) {
            topology.startSimulation();
        }
    }

    public void sendMessage(int sourceId, int destinationId, String message) {
        if (topology != null) {
            topology.sendMessage(sourceId, destinationId, message);
        }
    }

    public void showState() {
        if (topology != null) {
            topology.printNetworkState();
        }
    }

    public static void registerNode(Node node) {
        nodes.put(node.getId(), node);
    }

    public static Node getNode(int sourceId) {
        return nodes.get(sourceId);
    }

}
