package edu.pucmm.view;

import edu.pucmm.model.*;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

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
        Map<String, VehiculoState> estados = modelo.getVehiculos();

        // eliminar nodos de vehículos que ya no existen en el modelo
        nodosVehiculos.keySet().removeIf(id -> {
            if (!estados.containsKey(id)) {
                Node nodo = nodosVehiculos.get(id);
                if (nodo != null) {
                    lienzo.getChildren().remove(nodo);
                }
                return true;
            }
            return false;
        });

        // actualizar o crear nodos para los vehículos actuales
        for (VehiculoState v : estados.values()) {
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

    /**
     * Utilidad para animar el movimiento de un nodo y eliminarlo al finalizar.
     * La animación se reproduce y, al completarse, el nodo se retira del lienzo.
     * NOTA: Este método ya no se usa - la animación se maneja en SimulationController
     */
    public void animarVehiculoYEliminar(String id, Node nodo, double toX, double toY) {
        TranslateTransition tt = new TranslateTransition(Duration.seconds(3), nodo);
        tt.setToX(toX);
        tt.setToY(toY);
        tt.setOnFinished(e -> {
            lienzo.getChildren().remove(nodo);
            modelo.removeState(id);
            nodosVehiculos.remove(id);
        });
        tt.play();
    }
}
