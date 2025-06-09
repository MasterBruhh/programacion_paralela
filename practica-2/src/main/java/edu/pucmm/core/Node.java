package edu.pucmm.core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Node implements Runnable {
    private final int id;
    private final BlockingQueue<Message> messageQueue;

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
            messageQueue.put(message); // Encola el mensaje para procesamiento
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Message message = messageQueue.take();
                if (message.getDestinationId() == id) {
                    System.out.println("Nodo " + id + " recibió mensaje de Nodo " + message.getSourceId()
                            + ": " + message.getContent());
                }
            } catch (InterruptedException e) {
                System.out.println("Nodo " + id + " detenido.");
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
