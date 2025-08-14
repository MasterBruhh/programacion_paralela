package edu.pucmm.trafico.model;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;

public class TrafficLight {
    private Group node;
    private Circle redLight;
    private Circle yellowLight;
    private Circle greenLight;
    private TrafficLightState state;
    private String id;

    public TrafficLight(double x, double y, double angle, String id) {
        double boxWidth = 16, boxHeight = 40, radius = 6;
        node = new Group();
        Rectangle fondo = new Rectangle(-boxWidth / 2, -boxHeight / 2, boxWidth, boxHeight);
        fondo.setArcWidth(10);
        fondo.setArcHeight(10);
        fondo.setFill(Color.BLACK);

        redLight = new Circle(0, -boxHeight / 2 + radius + 4, radius, Color.RED);
        yellowLight = new Circle(0, 0, radius, Color.web("#333300"));
        greenLight = new Circle(0, boxHeight / 2 - radius - 4, radius, Color.web("#003300"));

        node.getChildren().addAll(fondo, redLight, yellowLight, greenLight);
        node.setLayoutX(x);
        node.setLayoutY(y);
        node.getTransforms().add(new Rotate(angle, 0, 0));
        this.id = id;
        setState(TrafficLightState.RED);
    }

    public void setState(TrafficLightState state) {
        this.state = state;
        redLight.setFill(state == TrafficLightState.RED ? Color.RED : Color.web("#330000"));
        yellowLight.setFill(state == TrafficLightState.YELLOW ? Color.YELLOW : Color.web("#333300"));
        greenLight.setFill(state == TrafficLightState.GREEN ? Color.LIMEGREEN : Color.web("#003300"));
    }

    public TrafficLightState getState() {
        return state;
    }

    public Group getNode() {
        return node;
    }

    public String getId() {
        return id;
    }
}