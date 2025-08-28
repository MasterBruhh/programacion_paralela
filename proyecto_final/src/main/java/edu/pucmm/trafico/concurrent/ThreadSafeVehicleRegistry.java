package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.model.HighwayLane;
import edu.pucmm.trafico.model.StartPoint;
import edu.pucmm.trafico.model.Vehicle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registro thread-safe de vehículos por origen (calles) y por carril (autopista).
 * Corrige NPE cuando se usan nuevos StartPoint (NORTH_L, NORTH_D, SOUTH_L, SOUTH_D)
 * mediante inicialización perezosa y pre-población de colas.
 */
public class ThreadSafeVehicleRegistry {
    private static final Logger logger = Logger.getLogger(ThreadSafeVehicleRegistry.class.getName());

    // Colas por punto de inicio en calles (NORTH_L, NORTH_D, SOUTH_L, SOUTH_D)
    private final ConcurrentHashMap<StartPoint, ConcurrentLinkedQueue<Vehicle>> streetQueues = new ConcurrentHashMap<>();
    // Colas por carril en autopistas
    private final ConcurrentHashMap<HighwayLane, ConcurrentLinkedQueue<Vehicle>> highwayQueues = new ConcurrentHashMap<>();

    public ThreadSafeVehicleRegistry() {
        // Pre-poblar con todos los StartPoint conocidos (evita NPE y facilita stats)
        for (StartPoint sp : StartPoint.values()) {
            streetQueues.putIfAbsent(sp, new ConcurrentLinkedQueue<>());
        }
        // Pre-poblar con todos los carriles de autopista
        for (HighwayLane lane : HighwayLane.values()) {
            highwayQueues.putIfAbsent(lane, new ConcurrentLinkedQueue<>());
        }
        logger.info("ThreadSafeVehicleRegistry inicializado con colas para " +
                StartPoint.values().length + " StartPoint y " +
                HighwayLane.values().length + " carriles de autopista.");
    }

    /**
     * Registra un vehículo en su cola correspondiente (calle o autopista).
     * Usa computeIfAbsent para evitar NPE si se añadieron StartPoint nuevos.
     */
    public void registerVehicle(Vehicle v) {
        Objects.requireNonNull(v, "vehicle");
        if (v.isHighwayVehicle()) {
            HighwayLane lane = v.getHighwayLane();
            ConcurrentLinkedQueue<Vehicle> q = highwayQueues.computeIfAbsent(lane, k -> {
                logger.fine("Creando cola para carril de autopista: " + k);
                return new ConcurrentLinkedQueue<>();
            });
            q.offer(v);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Registrado vehículo " + v.getId() + " en carril " + lane);
            }
        } else {
            StartPoint sp = v.getStartPoint();
            ConcurrentLinkedQueue<Vehicle> q = streetQueues.computeIfAbsent(sp, k -> {
                logger.fine("Creando cola para StartPoint de calle: " + k);
                return new ConcurrentLinkedQueue<>();
            });
            q.offer(v);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Registrado vehículo " + v.getId() + " en calle " + sp);
            }
        }
    }

    /**
     * Elimina un vehículo de su cola actual.
     */
    public void unregisterVehicle(Vehicle v) {
        if (v == null) return;
        boolean removed = false;
        if (v.isHighwayVehicle()) {
            ConcurrentLinkedQueue<Vehicle> q = highwayQueues.get(v.getHighwayLane());
            if (q != null) removed = q.remove(v);
        } else {
            ConcurrentLinkedQueue<Vehicle> q = streetQueues.get(v.getStartPoint());
            if (q != null) removed = q.remove(v);
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("Unregister vehículo " + (v != null ? v.getId() : "null") + " -> removed=" + removed);
        }
    }

    /**
     * Snapshot de todos los vehículos registrados (no bloquea productores).
     */
    public Collection<Vehicle> snapshotAll() {
        Collection<Vehicle> all = new ArrayList<>();
        for (Queue<Vehicle> q : streetQueues.values()) {
            all.addAll(q);
        }
        for (Queue<Vehicle> q : highwayQueues.values()) {
            all.addAll(q);
        }
        return all;
    }

    /**
     * Obtiene la cola (solo lectura) del StartPoint dado. Puede ser null si el StartPoint no aplica.
     */
    public Queue<Vehicle> getStreetQueue(StartPoint sp) {
        return streetQueues.get(sp);
    }

    /**
     * Obtiene la cola (solo lectura) del carril dado. Puede ser null si el carril no aplica.
     */
    public Queue<Vehicle> getHighwayQueue(HighwayLane lane) {
        return highwayQueues.get(lane);
    }
}