package edu.pucmm.view;

import edu.pucmm.model.*;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.HashMap;
import java.util.Map;

public class SimulationViewModel {

    private final SimulationModel modelo;
    private final Pane lienzo;
    private final Map<String, Node> nodosVehiculos = new HashMap<>();

    public SimulationViewModel(SimulationModel modelo, Pane lienzo) {
        this.modelo = modelo;
        this.lienzo = lienzo;
    }

    public void render() {
        for (VehiculoState v : modelo.getVehiculos().values()) {
            Node nodo = nodosVehiculos.get(v.id());
            if (nodo == null) {
                nodo = new Circle(8, v.tipo() == TipoVehiculo.emergencia ? Color.RED : Color.BLUE);
                nodosVehiculos.put(v.id(), nodo);
                lienzo.getChildren().add(nodo);
            }
            nodo.setTranslateX(v.posX());
            nodo.setTranslateY(v.posY());
        }
    }
}
