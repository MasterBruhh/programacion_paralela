package edu.pucmm.trafico.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Grupo lógico de semáforos sincronizados (por ejemplo, todos los de AVENIDA, ARRIBA, ABAJO).
 */
public class TrafficLightGroup {
    private final List<TrafficLight> trafficLights = new ArrayList<>();
    private final String groupName; // Ej: "CalleIzq", "CalleDer", "ARRIBA", "ABAJO"
    private TrafficLightState state = TrafficLightState.RED;

    public TrafficLightGroup(String groupName) {
        this.groupName = groupName;
    }

    public void addTrafficLight(TrafficLight light) {
        trafficLights.add(light);
    }

    public void setState(TrafficLightState state) {
        this.state = state;
        for (TrafficLight light : trafficLights) {
            light.setState(state);
        }
    }

    public TrafficLightState getState() {
        return state;
    }

    public List<TrafficLight> getTrafficLights() {
        return trafficLights;
    }

    public String getGroupName() {
        return groupName;
    }
}