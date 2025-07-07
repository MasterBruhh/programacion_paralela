package edu.pucmm.simulation;

import edu.pucmm.model.TipoVehiculo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manager para el cruce completo de calles (Escenario 1).
 * Agrupa 4 intersecciones individuales para formar un cruce en cruz.
 * 
 * Distribución de intersecciones:
 *     Norte
 *       |
 * Oeste-+-Este
 *       |
 *     Sur
 */
public class CruceManager {
    
    private static final Logger logger = Logger.getLogger(CruceManager.class.getName());
    
    private final String id;
    final Map<DireccionCruce, InterseccionManager> intersecciones;
    private final Map<DireccionCruce, CalleQueue> colasPorCalle;
    private final ColisionDetector colisionDetector;
    private final CruceCoordinador coordinadorGlobal;
    
    /**
     * Direcciones de entrada al cruce.
     */
    public enum DireccionCruce {
        // Coordenadas de las señales de stop usadas en la vista (main.fxml)
        // Estas posiciones se usan para que los vehículos se detengan justo
        // donde se muestran las señales en pantalla.
        NORTE(330, 235),    // Stop ubicado al lado norte del cruce
        SUR(470, 355),      // Stop ubicado al lado sur del cruce
        ESTE(460, 230),     // Stop ubicado al lado este del cruce
        OESTE(340, 360);    // Stop ubicado al lado oeste del cruce


        public final double posX, posY;
        
        DireccionCruce(double posX, double posY) {
            this.posX = posX;
            this.posY = posY;
        }
    }
    
    public CruceManager(String id) {
        this.id = id;
        this.intersecciones = new ConcurrentHashMap<>();
        this.colasPorCalle = new ConcurrentHashMap<>();
        this.colisionDetector = new ColisionDetector();
        this.coordinadorGlobal = new CruceCoordinador(id);
        
        // crear las 4 intersecciones del cruce
        inicializarIntersecciones();
        
        logger.info("✅ CruceManager " + id + " inicializado con 4 intersecciones y coordinador global");
    }
    
    /**
     * Inicializa las 4 intersecciones del cruce.
     */
    private void inicializarIntersecciones() {
        for (DireccionCruce direccion : DireccionCruce.values()) {
            String interseccionId = id + "-" + direccion.name().toLowerCase();
            InterseccionManager interseccion = new InterseccionManager(interseccionId);
            intersecciones.put(direccion, interseccion);
            
            // crear cola para esta calle
            String calleId = "calle-" + direccion.name().toLowerCase();
            CalleQueue cola = new CalleQueue(calleId, direccion);
            colasPorCalle.put(direccion, cola);
        }
    }
    
    /**
     * Agrega un vehículo a la cola de la calle correspondiente.
     * También lo registra en el coordinador global para respetar orden de creación.
     * 
     * @param vehiculoId ID del vehículo
     * @param timestampCreacion timestamp de cuando se creó el vehículo
     * @param direccion dirección desde la que se aproxima
     * @return posición en la cola
     */
    public int agregarVehiculoACola(String vehiculoId, long timestampCreacion, DireccionCruce direccion) {
        CalleQueue cola = colasPorCalle.get(direccion);
        if (cola == null) {
            throw new IllegalArgumentException("dirección inválida: " + direccion);
        }
        
        // Registrar en el coordinador global para orden de creación
        coordinadorGlobal.registrarVehiculo(vehiculoId, timestampCreacion, TipoVehiculo.normal, direccion);
        
        return cola.agregarVehiculo(vehiculoId, timestampCreacion);
    }
    
    /**
     * Agrega un vehículo de emergencia con prioridad absoluta.
     * También lo registra en el coordinador global con prioridad de emergencia.
     * 
     * @param vehiculoId ID del vehículo de emergencia
     * @param timestampCreacion timestamp de cuando se creó el vehículo
     * @param direccion dirección desde la que se aproxima
     */
    public void agregarVehiculoEmergenciaACola(String vehiculoId, long timestampCreacion, DireccionCruce direccion) {
        CalleQueue cola = colasPorCalle.get(direccion);
        if (cola == null) {
            throw new IllegalArgumentException("dirección inválida: " + direccion);
        }
        
        // Registrar en el coordinador global con prioridad de emergencia
        coordinadorGlobal.registrarVehiculo(vehiculoId, timestampCreacion, TipoVehiculo.emergencia, direccion);
        
        cola.procesarVehiculoEmergencia(vehiculoId, timestampCreacion);
    }
    
    /**
     * Sobrecarga para compatibilidad con código existente.
     */
    public int agregarVehiculoACola(String vehiculoId, DireccionCruce direccion) {
        CalleQueue cola = colasPorCalle.get(direccion);
        if (cola == null) {
            throw new IllegalArgumentException("dirección inválida: " + direccion);
        }
        
        return cola.agregarVehiculo(vehiculoId);
    }
    
    /**
     * Obtiene la posición donde debe esperar un vehículo.
     */
    public double[] obtenerPosicionEspera(String vehiculoId, DireccionCruce direccion) {
        CalleQueue cola = colasPorCalle.get(direccion);
        if (cola == null) {
            return null;
        }
        
        return cola.obtenerPosicionEspera(vehiculoId);
    }
    
    /**
     * Verifica si un vehículo puede solicitar cruce (es el primero en su cola).
     */
    public boolean puedesolicitarCruce(String vehiculoId, DireccionCruce direccion) {
        CalleQueue cola = colasPorCalle.get(direccion);
        return cola != null && cola.esPrimero(vehiculoId);
    }
    
    /**
     * Solicita cruzar el cruce desde una dirección específica.
     * Ahora usa el coordinador global para respetar orden de creación.
     * 
     * @param vehiculoId ID del vehículo
     * @param tipo tipo del vehículo  
     * @param direccionEntrada dirección desde la que entra el vehículo
     * @throws InterruptedException si el hilo es interrumpido
     */
    public void solicitarCruce(String vehiculoId, TipoVehiculo tipo, 
                              DireccionCruce direccionEntrada) throws InterruptedException {
        
        InterseccionManager interseccion = intersecciones.get(direccionEntrada);
        CalleQueue cola = colasPorCalle.get(direccionEntrada);
        
        if (interseccion == null || cola == null) {
            throw new IllegalArgumentException("dirección de entrada inválida: " + direccionEntrada);
        }
        
        // 1. Verificar que es el primero en su cola local
        if (!cola.esPrimero(vehiculoId)) {
            logger.warning("vehículo " + vehiculoId + " no es el primero en la cola de " + 
                         direccionEntrada + ", no puede cruzar aún");
            return;
        }
        
        // 2. NUEVO: Verificar orden global de creación
        if (!coordinadorGlobal.puedeProcedeSegunOrdenGlobal(vehiculoId)) {
            logger.info("⏸️ vehículo " + vehiculoId + " debe esperar su turno según orden global de creación");
            return;
        }
        
        logger.info("🚦 vehículo " + vehiculoId + " solicita cruzar desde " + direccionEntrada + 
                   " en cruce " + id + " (orden global respetado)");
        
        // 3. Solicitar permiso al coordinador global
        coordinadorGlobal.solicitarCruceGlobal(vehiculoId, tipo);
        
        // 4. Si llegó aquí, tiene permiso global, proceder con intersección local
        interseccion.solicitarCruce(vehiculoId, tipo);
        
        // 5. Remover de la cola local después de obtener permisos
        cola.removerVehiculo(vehiculoId);
    }
    
    /**
     * Determina automáticamente la intersección más cercana para un vehículo.
     */
    public DireccionCruce determinarInterseccionMasCercana(double vehiculoPosX, double vehiculoPosY) {
        DireccionCruce masCercana = DireccionCruce.NORTE;
        double menorDistancia = Double.MAX_VALUE;
        
        for (DireccionCruce direccion : DireccionCruce.values()) {
            double distancia = Math.sqrt(
                Math.pow(vehiculoPosX - direccion.posX, 2) + 
                Math.pow(vehiculoPosY - direccion.posY, 2)
            );
            
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                masCercana = direccion;
            }
        }
        
        return masCercana;
    }
    
    /**
     * Verifica si alguna intersección tiene vehículos esperando.
     */
    public boolean tieneVehiculosEsperando() {
        return intersecciones.values().stream()
                .anyMatch(interseccion -> interseccion.getVehiculosEnEspera() > 0);
    }
    
    /**
     * Obtiene el total de vehículos procesados en todo el cruce.
     */
    public int getTotalVehiculosProcessed() {
        return intersecciones.values().stream()
                .mapToInt(InterseccionManager::getVehiculosProcessed)
                .sum();
    }
    
    /**
     * Obtiene información del estado del cruce completo.
     */
    public EstadoCruce getEstadoCruce() {
        Map<DireccionCruce, InterseccionManager.EstadoInterseccion> estadosIntersecciones = 
            new ConcurrentHashMap<>();
        
        for (Map.Entry<DireccionCruce, InterseccionManager> entry : intersecciones.entrySet()) {
            estadosIntersecciones.put(entry.getKey(), entry.getValue().getEstado());
        }
        
        return new EstadoCruce(
            id,
            estadosIntersecciones,
            getTotalVehiculosProcessed(),
            tieneVehiculosEsperando()
        );
    }
    
    /**
     * Obtiene la intersección menos congestionada.
     */
    public DireccionCruce getInterseccionMenosCongestionada() {
        return intersecciones.entrySet().stream()
                .min((e1, e2) -> Integer.compare(
                    e1.getValue().getVehiculosEnEspera(),
                    e2.getValue().getVehiculosEnEspera()
                ))
                .map(Map.Entry::getKey)
                .orElse(DireccionCruce.NORTE);
    }
    
    public String getId() {
        return id;
    }
    
    /**
     * Obtiene el coordinador global del cruce.
     * @return coordinador global
     */
    public CruceCoordinador getCoordinadorGlobal() {
        return coordinadorGlobal;
    }
    
    /**
     * Record para representar el estado completo del cruce.
     */
    public record EstadoCruce(
        String id,
        Map<DireccionCruce, InterseccionManager.EstadoInterseccion> intersecciones,
        int totalVehiculosProcessed,
        boolean tieneVehiculosEsperando
    ) {}

    public InterseccionManager getInterseccionManager(CruceManager.DireccionCruce direccion) {
        return intersecciones.get(direccion);
    }

    /**
     * Libera el cruce cuando un vehículo termina de cruzar.
     * Notifica al siguiente vehículo en orden que puede intentar cruzar.
     * 
     * @param vehiculoId ID del vehículo que termina
     * @param direccionEntrada dirección desde la que entró el vehículo
     */
    public void liberarCruce(String vehiculoId, DireccionCruce direccionEntrada) {
        InterseccionManager interseccion = intersecciones.get(direccionEntrada);
        
        if (interseccion != null) {
            // Liberar intersección local
            interseccion.liberarCruce(vehiculoId);
            
            // Liberar coordinador global
            coordinadorGlobal.liberarCruceGlobal(vehiculoId);
            
            logger.info("🏁 vehículo " + vehiculoId + " liberó cruce desde " + direccionEntrada + " (global y local)");
            
            // Log del siguiente vehículo en la cola global para debugging
            var siguienteVehiculo = coordinadorGlobal.getSiguienteEnOrden();
            if (siguienteVehiculo != null) {
                logger.info("🚦 Siguiente vehículo en orden global: " + siguienteVehiculo.vehiculoId() + 
                           " (creado en t=" + siguienteVehiculo.timestampCreacion() + 
                           ", desde=" + siguienteVehiculo.direccionEntrada() + ")");
                
                // IMPORTANTE: Notificar a TODAS las direcciones
                // Esto es crucial ya que cualquier dirección puede tener el siguiente vehículo
                logger.info("📢 Notificando a TODAS las direcciones sobre cambio de estado");
                
                // Primero notificar a la dirección del siguiente vehículo para priorizar
                DireccionCruce direccionSiguiente = siguienteVehiculo.direccionEntrada();
                InterseccionManager interseccionSiguiente = intersecciones.get(direccionSiguiente);
                if (interseccionSiguiente != null) {
                    logger.info("📣 Notificando prioritariamente a vehículos en " + direccionSiguiente);
                    interseccionSiguiente.notificarVehiculosEsperando();
                    
                    // Pequeña pausa para permitir que el siguiente vehículo reaccione primero
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                // Luego notificar al resto de direcciones
                for (DireccionCruce direccion : DireccionCruce.values()) {
                    if (direccion != direccionSiguiente) {  // No notificar nuevamente a la dirección prioritaria
                        InterseccionManager interseccionDir = intersecciones.get(direccion);
                        if (interseccionDir != null) {
                            logger.info("📣 Notificando a vehículos en " + direccion);
                            interseccionDir.notificarVehiculosEsperando();
                        }
                    }
                }
            } else {
                logger.info("🚦 No hay más vehículos esperando en el cruce");
            }
        }
    }
} 