package edu.pucmm.trafico;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.concurrent.ConcurrentHashMap;

public class SimulationModel {

    private final ObservableMap<String, VehiculoState> vehiculos;

    public SimulationModel() {
        this.vehiculos = FXCollections.observableMap(new ConcurrentHashMap<>());
    }

    public void updateState(VehiculoState estado) {
        vehiculos.put(estado.id(), estado);
    }

    public void removeState(String id) {
        vehiculos.remove(id);
    }

    public ObservableMap<String, VehiculoState> getVehiculos() {
        return vehiculos;
    }

    public void clear() {
        vehiculos.clear();
    }
}
