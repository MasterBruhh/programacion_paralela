package edu.pucmm.simulation;

import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.TipoVehiculo;

import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Implementación de vehículo de emergencia con prioridad absoluta.
 * Puede ignorar semáforos rojos y tiene velocidad superior.
 */
public class VehiculoEmergencia extends Vehiculo {
    
    private static final Logger logger = Logger.getLogger(VehiculoEmergencia.class.getName());
    
    private static final double VELOCIDAD_MIN = 20.0;
    private static final double VELOCIDAD_MAX = 60.0;
    private static final long SIRENA_INTERVAL = 2000; // notificar presencia cada 2 segundos
    
    private long lastSirenaNotification = 0;
    private boolean sirenActive = true;
    
    public VehiculoEmergencia(String id, double posX, double posY, Direccion direccion,
                              ISimulationModel simulationModel, PuntoSalida puntoSalida) {
        super(id, TipoVehiculo.emergencia, posX, posY, 
              generateRandomVelocity(), direccion, simulationModel,puntoSalida);
    }
    
    private static double generateRandomVelocity() {
        return ThreadLocalRandom.current().nextDouble(VELOCIDAD_MIN, VELOCIDAD_MAX);
    }
    
    @Override
    protected void executeTypeSpecificLogic() {
        // notificar presencia con sirena
        notifyPresence();
        
        // mantener velocidad alta
        maintainEmergencySpeed();
    }
    
    /**
     * Notifica la presencia del vehículo de emergencia.
     */
    private void notifyPresence() {
        long currentTime = System.currentTimeMillis();
        if (sirenActive && currentTime - lastSirenaNotification >= SIRENA_INTERVAL) {
            logger.info("🚨 VEHÍCULO DE EMERGENCIA " + id + " EN POSICIÓN (" + 
                       String.format("%.1f", posX) + ", " + String.format("%.1f", posY) + ")");
            lastSirenaNotification = currentTime;
        }
    }
    
    /**
     * Mantiene velocidad de emergencia.
     */
    private void maintainEmergencySpeed() {
        // los vehículos de emergencia tienden a mantener velocidad alta
        if (this.velocidad < VELOCIDAD_MAX * 0.8) {
            this.velocidad = Math.min(VELOCIDAD_MAX, this.velocidad * 1.2);
        }
    }
    
    /**
     * Ajusta dirección para emergencia (más directo).
     */
    
    @Override
    protected boolean puedeRealizarMovimiento(MovimientoInfo movimiento) {
        // vehículos de emergencia tienen prioridad absoluta
        // pueden ignorar algunas restricciones (como semáforos rojos)
        return simulationModel.puedeAvanzar(id, movimiento.nextX(), movimiento.nextY()) || 
               canOverrideRestrictions(movimiento);
    }
    
    /**
     * Determina si el vehículo de emergencia puede anular restricciones.
     */
    private boolean canOverrideRestrictions(MovimientoInfo movimiento) {
        // los vehículos de emergencia pueden avanzar incluso con restricciones
        // pero deben evitar colisiones físicas
        return true; // simplificado por ahora
    }
    
    public boolean isSirenActive() {
        return sirenActive;
    }
    
    @Override
    public String toString() {
        return String.format("VehiculoEmergencia{id='%s', pos=(%.2f, %.2f), vel=%.2f, dir=%s, sirena=%s}", 
                           id, posX, posY, velocidad, direccion, sirenActive ? "🚨" : "🔇");
    }
} 