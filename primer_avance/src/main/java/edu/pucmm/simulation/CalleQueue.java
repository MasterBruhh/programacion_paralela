package edu.pucmm.simulation;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Gestiona la cola de vehículos esperando en cada calle antes de la intersección.
 * Mantiene el orden por CREACIÓN (no por llegada) y las posiciones de espera con distancia de seguridad.
 */
public class CalleQueue {
    
    private static final Logger logger = Logger.getLogger(CalleQueue.class.getName());
    
    private final String calleId;
    private final List<VehiculoEnCola> colaVehiculos; // Cambiado a List para ordenamiento
    private final Map<String, Integer> posicionEnCola;
    private final double posXStop; // posición X del stop sign
    private final double posYStop; // posición Y del stop sign
    private final CruceManager.DireccionCruce direccion;
    
    private static final double DISTANCIA_ENTRE_VEHICULOS = 20.0; // distancia de seguridad en cola
    private long ultimoCambioEnCola = 0; // timestamp del último cambio en la cola

    public CalleQueue(String calleId, CruceManager.DireccionCruce direccion) {
        this.calleId = calleId;
        this.direccion = direccion;
        this.colaVehiculos = Collections.synchronizedList(new ArrayList<>());
        this.posicionEnCola = new ConcurrentHashMap<>();
        
        // calcular posición del stop sign basado en la dirección
        this.posXStop = direccion.posX;
        this.posYStop = direccion.posY;
    }
    
    /**
     * Añade un vehículo a la cola si no está ya en ella.
     * El orden se basa en el timestamp de CREACIÓN del vehículo.
     * 
     * @param vehiculoId ID del vehículo
     * @param timestampCreacion timestamp cuando se creó el vehículo
     * @return posición en la cola (0 = primero)
     */
    public synchronized int agregarVehiculo(String vehiculoId, long timestampCreacion) {
        // Verificar si ya está en la cola
        for (VehiculoEnCola v : colaVehiculos) {
            if (v.vehiculoId.equals(vehiculoId)) {
                return posicionEnCola.get(vehiculoId);
            }
        }
        
        // Crear nuevo vehículo en cola
        VehiculoEnCola nuevoVehiculo = new VehiculoEnCola(vehiculoId, timestampCreacion);
        colaVehiculos.add(nuevoVehiculo);
        
        // Ordenar por timestamp de creación (FIFO por creación)
        colaVehiculos.sort((v1, v2) -> Long.compare(v1.timestampCreacion, v2.timestampCreacion));
        
        // Recalcular posiciones
        recalcularPosiciones();
        
        int posicion = posicionEnCola.get(vehiculoId);
        logger.info("vehículo " + vehiculoId + " agregado a cola de " + calleId + 
                   " en posición " + posicion + " (orden de creación)");
        
        return posicion;
    }
    
    /**
     * Sobrecarga para compatibilidad con código existente.
     */
    public synchronized int agregarVehiculo(String vehiculoId) {
        // Usar timestamp actual como fallback si no se proporciona timestamp de creación
        return agregarVehiculo(vehiculoId, System.currentTimeMillis());
    }
    
    /**
     * Maneja la llegada de un vehículo de emergencia.
     * Según los requisitos: "si llega un carro de emergencia, que se vacíe una lista de vehiculos"
     */
    public synchronized void procesarVehiculoEmergencia(String vehiculoEmergenciaId, long timestampCreacion) {
        logger.warning("🚨 EMERGENCIA: vehículo " + vehiculoEmergenciaId + " prioridad absoluta en " + calleId);
        
        // Los vehículos normales deben dar paso - se mueven hacia atrás en sus posiciones
        // pero mantienen su orden relativo entre ellos
        
        // Agregar vehículo de emergencia al principio
        VehiculoEnCola emergencia = new VehiculoEnCola(vehiculoEmergenciaId, timestampCreacion);
        
        // Insertar emergencia al principio, pero respetando orden de creación entre emergencias
        int insertIndex = colaVehiculos.size();
        for (int i = 0; i < colaVehiculos.size(); i++) {
            VehiculoEnCola v = colaVehiculos.get(i);
            if (!isVehiculoEmergencia(v.vehiculoId)) {
                insertIndex = i + 1; // mantener a los normales por delante
            } else if (v.timestampCreacion <= timestampCreacion) {
                insertIndex = i + 1; // emergencias más antiguas mantienen prioridad
            } else {
                break;
            }
        }
        
        colaVehiculos.add(insertIndex, emergencia);
        recalcularPosiciones();
        ultimoCambioEnCola = System.currentTimeMillis();
        
        logger.info("vehículo de emergencia " + vehiculoEmergenciaId + " insertado en posición " + insertIndex);
    }
    
    /**
     * Verifica si un vehículo es de emergencia basándose en su ID.
     */
    private boolean isVehiculoEmergencia(String vehiculoId) {
        // Convención: vehículos de emergencia tienen "EMG" en su ID
        return vehiculoId.contains("EMG") || vehiculoId.contains("EMERGENCIA");
    }
    
    /**
     * Verifica si la cola ha cambiado recientemente (en los últimos 500ms)
     * para permitir reposicionamiento de vehículos.
     */
    public boolean hayCambioRecienteEnCola() {
        return (System.currentTimeMillis() - ultimoCambioEnCola) < 500;
    }
    
    /**
     * Remueve un vehículo de la cola (cuando cruza la intersección).
     * 
     * @param vehiculoId ID del vehículo
     */
    public synchronized void removerVehiculo(String vehiculoId) {
        boolean removed = colaVehiculos.removeIf(v -> v.vehiculoId.equals(vehiculoId));
        if (removed) {
            posicionEnCola.remove(vehiculoId);
            recalcularPosiciones();
            ultimoCambioEnCola = System.currentTimeMillis(); // Marcar cambio en la cola
            logger.info("vehículo " + vehiculoId + " removido de cola de " + calleId);
        }
    }
    
    /**
     * Verifica si un vehículo es el primero en la cola.
     */
    public boolean esPrimero(String vehiculoId) {
        synchronized (colaVehiculos) {
            return !colaVehiculos.isEmpty() && colaVehiculos.get(0).vehiculoId.equals(vehiculoId);
        }
    }

    /**
     * Obtiene la posición de un vehículo en la cola.
     * @return índice del vehículo o -1 si no está en la cola
     */
    public synchronized int getPosicion(String vehiculoId) {
        Integer pos = posicionEnCola.get(vehiculoId);
        return pos == null ? -1 : pos;
    }
    
    /**
     * Obtiene la posición donde debe esperar un vehículo en la cola.
     * 
     * @param vehiculoId ID del vehículo
     * @return array con [x, y] de la posición de espera, o null si no está en cola
     */
    public double[] obtenerPosicionEspera(String vehiculoId) {
        Integer posicion = posicionEnCola.get(vehiculoId);
        if (posicion == null) {
            return null;
        }
        
        // Si es el primer vehículo, usar posición del stop sign
        if (posicion == 0) {
            return new double[]{posXStop, posYStop};
        }
        
        // Para vehículos en segunda posición o más, calcular posición basada en offset fijo
        // Esto evita el jitter causado por intentar seguir dinámicamente al vehículo de adelante
        double offsetDistancia = posicion * DISTANCIA_ENTRE_VEHICULOS;
        double posX = posXStop;
        double posY = posYStop;
        
        // ajustar posición según la dirección de donde viene el vehículo
        switch (direccion) {
            case NORTE -> posY -= offsetDistancia; // vehículos esperan más arriba
            case SUR -> posY += offsetDistancia;   // vehículos esperan más abajo
            case ESTE -> posX += offsetDistancia;  // vehículos esperan más a la derecha
            case OESTE -> posX -= offsetDistancia; // vehículos esperan más a la izquierda
        }
        
        return new double[]{posX, posY};
    }
    
    /**
     * Recalcula las posiciones después de cambios en la cola.
     */
    private void recalcularPosiciones() {
        posicionEnCola.clear();
        for (int i = 0; i < colaVehiculos.size(); i++) {
            posicionEnCola.put(colaVehiculos.get(i).vehiculoId, i);
        }
    }
    
    /**
     * Obtiene el número de vehículos en la cola.
     */
    public int getTamanoCola() {
        return colaVehiculos.size();
    }
    
    /**
     * Verifica si hay espacio en la cola (límite para evitar congestión).
     */
    public boolean hayEspacio() {
        return colaVehiculos.size() < 10; // máximo 10 vehículos por cola
    }
    
    /**
     * Obtiene información del estado de la cola.
     */
    public EstadoCola getEstado() {
        String primerVehiculo = null;
        synchronized (colaVehiculos) {
            if (!colaVehiculos.isEmpty()) {
                primerVehiculo = colaVehiculos.get(0).vehiculoId;
            }
        }
        
        return new EstadoCola(
            calleId,
            direccion,
            colaVehiculos.size(),
            primerVehiculo,
            hayEspacio()
        );
    }
    
    /**
     * Clase interna para representar un vehículo en cola con su timestamp de creación.
     */
    private static class VehiculoEnCola {
        final String vehiculoId;
        final long timestampCreacion;
        
        VehiculoEnCola(String vehiculoId, long timestampCreacion) {
            this.vehiculoId = vehiculoId;
            this.timestampCreacion = timestampCreacion;
        }
    }
    
    /**
     * Record para representar el estado de una cola.
     */
    public record EstadoCola(
        String calleId,
        CruceManager.DireccionCruce direccion,
        int vehiculosEnCola,
        String primerVehiculo,
        boolean hayEspacio
    ) {}
} 