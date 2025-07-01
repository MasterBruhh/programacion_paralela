package edu.pucmm.simulation;

import edu.pucmm.model.TipoVehiculo;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementación de vehículo normal con reglas estándar de tráfico.
 * Respeta semáforos y restricciones de velocidad.
 */
public class VehiculoNormal extends Vehiculo {
    
    private static final double VELOCIDAD_MIN = 0.5;
    private static final double VELOCIDAD_MAX = 2.0;
    
    private long lastDirectionChange = 0;
    private static final long DIRECTION_CHANGE_COOLDOWN = 5000; // 5 segundos
    
    public VehiculoNormal(String id, double posX, double posY, Direccion direccion, 
                         ISimulationModel simulationModel) {
        super(id, TipoVehiculo.normal, posX, posY, 
              generateRandomVelocity(), direccion, simulationModel);
    }
    
    private static double generateRandomVelocity() {
        return ThreadLocalRandom.current().nextDouble(VELOCIDAD_MIN, VELOCIDAD_MAX);
    }
    
    @Override
    protected void executeTypeSpecificLogic() {
        // lógica específica para vehículos normales
        
        // cambiar dirección ocasionalmente
        if (shouldChangeDirection()) {
            changeDirectionRandomly();
        }
        
        // ajustar velocidad basándose en condiciones
        adjustVelocity();
    }
    
    /**
     * Determina si el vehículo debería cambiar de dirección.
     */
    private boolean shouldChangeDirection() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDirectionChange < DIRECTION_CHANGE_COOLDOWN) {
            return false;
        }
        
        // 2% de probabilidad por tick de cambiar dirección
        return ThreadLocalRandom.current().nextDouble() < 0.02;
    }
    
    /**
     * Cambia la dirección aleatoriamente.
     */
    private void changeDirectionRandomly() {
        Direccion[] direcciones = Direccion.values();
        Direccion nuevaDireccion = direcciones[ThreadLocalRandom.current().nextInt(direcciones.length)];
        
        // evitar vuelta en u muy frecuente
        if (nuevaDireccion != Direccion.vuelta_u || ThreadLocalRandom.current().nextDouble() < 0.1) {
            this.direccion = nuevaDireccion;
            this.lastDirectionChange = System.currentTimeMillis();
        }
    }
    
    /**
     * Ajusta la velocidad basándose en condiciones del entorno.
     */
    private void adjustVelocity() {
        // simulación simple: reducir velocidad ocasionalmente (tráfico, semáforos, etc.)
        if (ThreadLocalRandom.current().nextDouble() < 0.05) { // 5% probabilidad
            // reducir velocidad temporalmente
            double factor = ThreadLocalRandom.current().nextDouble(0.5, 0.8);
            this.velocidad = Math.max(VELOCIDAD_MIN, this.velocidad * factor);
        } else if (ThreadLocalRandom.current().nextDouble() < 0.03) { // 3% probabilidad
            // acelerar gradualmente
            double factor = ThreadLocalRandom.current().nextDouble(1.1, 1.3);
            this.velocidad = Math.min(VELOCIDAD_MAX, this.velocidad * factor);
        }
    }
    
    @Override
    protected boolean puedeRealizarMovimiento(MovimientoInfo movimiento) {
        // vehículos normales respetan todas las restricciones
        return super.puedeRealizarMovimiento(movimiento) && respectsTrafficRules(movimiento);
    }
    
    /**
     * Verifica que el movimiento respete las reglas de tráfico.
     */
    private boolean respectsTrafficRules(MovimientoInfo movimiento) {
        // aquí se implementarían verificaciones adicionales:
        // - respeto de semáforos rojos
        // - límites de velocidad por zona
        // - señales de stop
        // por ahora, simplificamos retornando true
        return true;
    }
    
    @Override
    public String toString() {
        return String.format("VehiculoNormal{id='%s', pos=(%.2f, %.2f), vel=%.2f, dir=%s}", 
                           id, posX, posY, velocidad, direccion);
    }
}
