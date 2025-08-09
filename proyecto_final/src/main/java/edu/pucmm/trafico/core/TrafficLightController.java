package edu.pucmm.trafico.core;

import edu.pucmm.trafico.model.TrafficLightGroup;
import edu.pucmm.trafico.model.TrafficLightState;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.List;

/**
 * Controlador de semáforos para múltiples grupos (avenida, arriba, abajo).
 * Usa un ciclo en el que cada grupo está en verde 8s, amarillo 1s, y rojo el resto,
 * en orden circular por la lista de grupos.
 */
public class TrafficLightController {
    private final List<TrafficLightGroup> groups; // Ej: "CalleIzq", "CalleDer", "ARRIBA", "ABAJO"
    private int currentGroup = 0;
    private Timeline timeline;

    public TrafficLightController(List<TrafficLightGroup> groups) {
        this.groups = groups;
    }

    public void start() {
        if (groups.isEmpty()) return;
        setGroupGreen(currentGroup);

        timeline = new Timeline(
                new KeyFrame(Duration.seconds(8), e -> setGroupYellow(currentGroup)),
                new KeyFrame(Duration.seconds(9), e -> {
                    setGroupRed(currentGroup);
                    currentGroup = (currentGroup + 1) % groups.size();
                    setGroupGreen(currentGroup);
                }),
                new KeyFrame(Duration.seconds(17), e -> setGroupYellow(currentGroup)),
                new KeyFrame(Duration.seconds(18), e -> {
                    setGroupRed(currentGroup);
                    currentGroup = (currentGroup + 1) % groups.size();
                    setGroupGreen(currentGroup);
                })
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void setGroupGreen(int index) {
        for (int i = 0; i < groups.size(); i++) {
            groups.get(i).setState(i == index ? TrafficLightState.GREEN : TrafficLightState.RED);
        }
    }

    private void setGroupYellow(int index) {
        groups.get(index).setState(TrafficLightState.YELLOW);
    }

    private void setGroupRed(int index) {
        groups.get(index).setState(TrafficLightState.RED);
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
        }
    }
}