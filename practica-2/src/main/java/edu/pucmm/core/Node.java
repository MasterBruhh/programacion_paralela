package edu.pucmm.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Node implements Runnable {
    private final int id;
    private final BlockingQueue<Message> messageQueue;

    // Para RingNetwork
    private Node neighbor;

    // Para TreeNetwork
    private Node parent;
    private List<Node> children = new ArrayList<>();

    public Node(int id) {
        this.id = id;
        this.messageQueue = new LinkedBlockingQueue<>();
    }

    public int getId() {
        return id;
    }

    public void receiveMessage(Message message) {
        // Simula que todos reciben, pero solo el destinatario procesa
        try {
            messageQueue.put(message); // Encola el mensaje
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void setNeighbor(Node neighbor) {
        this.neighbor = neighbor;
    }

    public Node getNeighbor() {
        return neighbor;
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    public Node getParent() {
        return parent;
    }

    public void addChild(Node child) {
        children.add(child);
    }

    public List<Node> getChildren() {
        return children;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Message message = messageQueue.take();

                if (message.getDestinationId() == id) {
                    System.out.println("Nodo " + id + " recibió mensaje de Nodo " + message.getSourceId()
                            + ": " + message.getContent());
                } else {
                    // Reenvío según el contexto de topología
                    if (neighbor != null) {
                        neighbor.receiveMessage(message); // Ring
                    }

                    if (parent != null) {
                        parent.receiveMessage(message); // Tree (hacia arriba)
                    }

                    for (Node child : children) {
                        child.receiveMessage(message); // Tree (hacia abajo)
                    }
                }

            } catch (InterruptedException e) {
                System.out.println("Nodo " + id + " detenido.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
