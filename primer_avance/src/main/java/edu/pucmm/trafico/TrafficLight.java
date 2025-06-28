package edu.pucmm.trafico;

import java.util.concurrent.atomic.AtomicBoolean;

public class TrafficLight {
    private String id;
    private AtomicBoolean green;

    public TrafficLight(String id) {
        this.id = id;
        this.green = new AtomicBoolean(false);
    }
    public void changeLight() {
        green.set(!green.get());
    }
    public boolean isGreen() {
        return green.get();
    }
}


