package edu.pucmm.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Node implements Runnable {

    private final int id;
    private final BlockingQueue<Message> messageQueue;

    // Ring
    private Node neighbor;

    // Tree
    private Node parent;
    private List<Node> children = new ArrayList<>();

    // Fully Connected
    private List<Node> neighbors = new ArrayList<>();

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
            messageQueue.put(message);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Ring
    public void setNeighbor(Node neighbor) {
        this.neighbor = neighbor;
    }

    public Node getNeighbor() {
        return neighbor;
    }

    // Tree
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

    // Fully Connected
    public void addNeighbor(Node neighbor) {
        neighbors.add(neighbor);
    }

    public List<Node> getNeighbors() {
        return neighbors;
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
                    // Ring
                    if (neighbor != null) {
                        neighbor.receiveMessage(message);
                    }

                    // Tree
                    if (parent != null) {
                        parent.receiveMessage(message);
                    }
                    for (Node child : children) {
                        child.receiveMessage(message);
                    }

                    // Fully Connected
                    for (Node neighbor : neighbors) {
                        neighbor.receiveMessage(message);
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
