package edu.pucmm.trafico.model;

import java.util.ArrayList;
import java.util.List;

public class TrafficLightGroup {
    private final String groupName;
    private TrafficLightState state = TrafficLightState.RED;
    private final List<TrafficLight> trafficLights = new ArrayList<>();
    
    public TrafficLightGroup(String groupName) {
        this.groupName = groupName;
    }

    public void setState(TrafficLightState state) {
        this.state = state;
        for (TrafficLight trafficLight : trafficLights) {
            trafficLight.setState(state);
        }
    }
    public TrafficLightState getState() {
        return state;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void addTrafficLight(TrafficLight trafficLight) {
        trafficLights.add(trafficLight);
    }
    
    public List<TrafficLight> getTrafficLights() {
        return trafficLights;
    }
}