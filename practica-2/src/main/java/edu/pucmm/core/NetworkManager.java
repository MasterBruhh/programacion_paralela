package edu.pucmm.core;

import edu.pucmm.topology.*;

public class NetworkManager {

    private NetworkTopology topology;

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
}
