package edu.pucmm.controller;

import edu.pucmm.model.SimulationModel;
import edu.pucmm.view.SimulationViewModel;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.scene.layout.Pane;

public class SimulationController {

    @FXML private Pane lienzo;

    private SimulationModel modelo;
    private SimulationViewModel vista;
    private AnimationTimer timer;

    @FXML
    public void initialize() {
        modelo = new SimulationModel();
        vista = new SimulationViewModel(modelo, lienzo);

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                vista.render();
            }
        };
    }

    @FXML public void handlePlay() {
        timer.start();
        modelo.simulacionFalsa(); // método temporal
    }

    @FXML public void handlePause() {
        timer.stop();
    }

    @FXML public void handleReset() {
        timer.stop();
        modelo.clear();
        lienzo.getChildren().clear();
    }

    @FXML public void handleExit() {
        System.exit(0);
    }
}
