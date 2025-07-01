package edu.pucmm.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Modelo central de la simulación que actúa como puente entre backend y frontend.
 * thread-safe para escritura concurrente desde múltiples hilos de simulación
 * y lectura desde el hilo de javafx.
 */
public class SimulationModel {
    
    private final ObservableMap<String, VehiculoState> vehiculos;
    private final List<SimulationObserver> observers;
    
    public SimulationModel() {
        // concurrentHashMap es seguro para que carlos escriba desde múltiples hilos.
        // fxCollections.observableMap lo "envuelve" para que jean pueda observarlo desde el hilo de javafx.
        this.vehiculos = FXCollections.observableMap(new ConcurrentHashMap<>());
        this.observers = new CopyOnWriteArrayList<>();
    }
    
    /**
     * añade un nuevo estado de vehículo.
     */
    public synchronized void addState(VehiculoState estado) {
        vehiculos.put(estado.id(), estado);
        notifyObservers();
    }
    
    /**
     * actualiza el estado de un vehículo existente.
     */
    public synchronized void updateState(VehiculoState estado) {
        vehiculos.put(estado.id(), estado);
        notifyObservers();
    }
    
    /**
     * remueve un vehículo del sistema.
     */
    public synchronized void removeState(String id) {
        vehiculos.remove(id);
        notifyObservers();
    }
    
    /**
     * obtiene el mapa observable de vehículos para binding con javafx.
     */
    public ObservableMap<String, VehiculoState> getVehiculos() {
        return vehiculos;
    }

    /**
     * registra un observador para cambios de estado.
     */
    public void addObserver(SimulationObserver observer) {
        observers.add(observer);
    }
    
    /**
     * desregistra un observador.
     */
    public void removeObserver(SimulationObserver observer) {
        observers.remove(observer);
    }
    
    /**
     * limpia todos los estados de vehículos.
     */
    public synchronized void clear() {
        vehiculos.clear();
        notifyObservers();
    }
    
    /**
     * notifica a todos los observadores del cambio de estado.
     */
    private void notifyObservers() {
        Map<String, VehiculoState> snapshot = Map.copyOf(vehiculos);
        for (SimulationObserver observer : observers) {
            observer.onStateChange(snapshot);
        }
    }
    public void simulacionFalsa() {
        new Thread(() -> {
            for (int i = 0; i < 2; i++) {
                String id = "veh" + i;
                TipoVehiculo tipo = i % 2 == 0 ? TipoVehiculo.normal : TipoVehiculo.emergencia;
                double y = 270+(i * 50);
                for (int x = 0; x < 100; x++) {
                    double posX =0;
                    if(!(i % 2 == 0))
                        posX = 20 + x * 4;
                    else{
                        posX = 800 - (20 + x * 4);
                    }

                    updateState(new VehiculoState(id, posX, y, tipo));
                    try { Thread.sleep(30); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

} 