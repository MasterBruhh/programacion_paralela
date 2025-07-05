package edu.pucmm.simulation;

import edu.pucmm.model.VehiculoState;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Detector de colisiones usando algoritmo AABB (Axis-Aligned Bounding Box).
 */
public class ColisionDetector {
    
    private static final Logger logger = Logger.getLogger(ColisionDetector.class.getName());
    
    // configuración de colisiones
    private static final double RADIO_SEGURIDAD_NORMAL = 3.0;
    private static final double RADIO_SEGURIDAD_EMERGENCIA = 2.0; // menor para más agilidad
    
    /**
     * Verifica si un vehículo puede moverse a una posición sin colisionar.
     * 
     * @param vehiculoId ID del vehículo que se quiere mover
     * @param nextX nueva posición X propuesta
     * @param nextY nueva posición Y propuesta
     * @param vehiculosActivos lista de todos los vehículos activos
     * @return true si puede moverse sin colisión, false si hay riesgo
     */
    public boolean puedeMoverse(String vehiculoId, double nextX, double nextY, 
                               Collection<VehiculoState> vehiculosActivos) {
        
        // buscar el vehículo que se quiere mover
        VehiculoState vehiculoMovimiento = vehiculosActivos.stream()
                .filter(v -> v.id().equals(vehiculoId))
                .findFirst()
                .orElse(null);
        
        if (vehiculoMovimiento == null) {
            // vehículo no encontrado, permitir movimiento
            return true;
        }
        
        double radioSeguridad = (vehiculoMovimiento.tipo() == edu.pucmm.model.TipoVehiculo.emergencia) ?
                               RADIO_SEGURIDAD_EMERGENCIA : RADIO_SEGURIDAD_NORMAL;
        
        // verificar colisión con todos los otros vehículos
        for (VehiculoState otroVehiculo : vehiculosActivos) {
            if (otroVehiculo.id().equals(vehiculoId)) {
                continue; // no verificar colisión consigo mismo
            }
            
            if (detectarColisionAABB(nextX, nextY, radioSeguridad,
                                   otroVehiculo.posX(), otroVehiculo.posY(), 
                                   obtenerRadioSeguridad(otroVehiculo))) {
                
                logger.warning("⚠️ COLISIÓN DETECTADA: vehículo " + vehiculoId + 
                           " no puede moverse a (" + String.format("%.2f", nextX) + 
                           ", " + String.format("%.2f", nextY) + ") - conflicto con " + 
                           otroVehiculo.id() + " en (" + 
                           String.format("%.2f", otroVehiculo.posX()) + ", " + 
                           String.format("%.2f", otroVehiculo.posY()) + ")");
                
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Detecta colisión usando algoritmo AABB.
     */
    private boolean detectarColisionAABB(double x1, double y1, double radio1,
                                        double x2, double y2, double radio2) {
        
        // calcular distancia entre centros
        double deltaX = Math.abs(x1 - x2);
        double deltaY = Math.abs(y1 - y2);
        
        // verificar si los bounding boxes se superponen
        double radiosCombinados = radio1 + radio2;
        
        return (deltaX < radiosCombinados) && (deltaY < radiosCombinados);
    }
    
    /**
     * Calcula la distancia euclidiana entre dos puntos.
     */
    public double calcularDistancia(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
    
    /**
     * Obtiene el radio de seguridad para un vehículo específico.
     */
    private double obtenerRadioSeguridad(VehiculoState vehiculo) {
        return (vehiculo.tipo() == edu.pucmm.model.TipoVehiculo.emergencia) ?
               RADIO_SEGURIDAD_EMERGENCIA : RADIO_SEGURIDAD_NORMAL;
    }
    
    /**
     * Genera un reporte de colisiones potenciales en el sistema.
     */
    public ReporteColisiones generarReporte(Collection<VehiculoState> vehiculos) {
        int colisionesPotenciales = 0;
        int vehiculosEnRiesgo = 0;
        
        for (VehiculoState vehiculo1 : vehiculos) {
            boolean vehiculoEnRiesgo = false;
            
            for (VehiculoState vehiculo2 : vehiculos) {
                if (vehiculo1.id().equals(vehiculo2.id())) continue;
                
                double distancia = calcularDistancia(
                    vehiculo1.posX(), vehiculo1.posY(),
                    vehiculo2.posX(), vehiculo2.posY()
                );
                
                double radioMinimo = obtenerRadioSeguridad(vehiculo1) + obtenerRadioSeguridad(vehiculo2);
                
                if (distancia < radioMinimo * 1.5) { // margen de advertencia
                    colisionesPotenciales++;
                    vehiculoEnRiesgo = true;
                }
            }
            
            if (vehiculoEnRiesgo) {
                vehiculosEnRiesgo++;
            }
        }
        
        return new ReporteColisiones(
            vehiculos.size(),
            colisionesPotenciales / 2, // dividir por 2 porque contamos cada par dos veces
            vehiculosEnRiesgo,
            System.currentTimeMillis()
        );
    }
    
    /**
     * Record para el reporte de colisiones.
     */
    public record ReporteColisiones(
        int totalVehiculos,
        int colisionesPotenciales,
        int vehiculosEnRiesgo,
        long timestamp
    ) {}
} 