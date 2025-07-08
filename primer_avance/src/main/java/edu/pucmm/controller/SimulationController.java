package edu.pucmm.controller;

import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.SimulationModel;
import edu.pucmm.model.TipoVehiculo;
import edu.pucmm.model.VehiculoState;
import edu.pucmm.simulation.CruceSimulationModel;
import edu.pucmm.simulation.Direccion;
import edu.pucmm.simulation.Vehiculo;
import edu.pucmm.simulation.VehiculoFactory;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.animation.TranslateTransition;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashSet;

public class SimulationController {

    @FXML
    private Pane lienzo;

    // Variables para almacenar la configuración del próximo vehículo
    private PuntoSalida salidaSeleccionada = PuntoSalida.ARRIBA;
    private TipoVehiculo tipoVehiculoSeleccionado = TipoVehiculo.normal;
    private Direccion direccionSeleccionada = Direccion.recto;
    private CruceSimulationModel modelo;
    private static final List<Vehiculo> vehiculosActivos = new ArrayList<>();
    private boolean simulacionEnMarcha = false;
    private final java.util.Map<String, Circle> nodosVehiculo = new HashMap<>();
    private final java.util.Map<String, VehiculoState> ultimoEstado = new HashMap<>();
    private final java.util.Set<String> vehiculosAnimandoSalida = new java.util.HashSet<>();

    public static List<Vehiculo> getVehiculosActivos() {
        return vehiculosActivos;
    }

    @FXML
    public void initialize() {
        dibujarLineasDeCarretera();
        System.out.println("Simulación inicializada. Configuración por defecto: Salida=ARRIBA, Tipo=NORMAL, Dirección=RECTO");
        modelo = new CruceSimulationModel(new SimulationModel(),"cruce-1");
            modelo.getSimulationModel().addObserver(snapshot ->
            Platform.runLater(() -> actualizarVehiculos(snapshot))
        );
    }

    // --- Métodos para el Menú de Configuración ---

    @FXML
    public void handleConfiguracion(ActionEvent event) {
        // Obtenemos el texto del MenuItem que disparó el evento.
        String opcion = ((MenuItem) event.getSource()).getId();

        // Actualizamos la configuración basada en el texto.
        // El bloque try-catch maneja el caso de que se añada un MenuItem sin lógica aquí.
        try {
            switch (opcion) {
                case "Arriba": salidaSeleccionada = PuntoSalida.ARRIBA; break;
                case "Abajo": salidaSeleccionada = PuntoSalida.ABAJO; break;
                case "Izquierda": salidaSeleccionada = PuntoSalida.IZQUIERDA; break;
                case "Derecha": salidaSeleccionada = PuntoSalida.DERECHA; break;

                case "Normal": tipoVehiculoSeleccionado = TipoVehiculo.normal; break;
                case "Emergencia": tipoVehiculoSeleccionado = TipoVehiculo.emergencia; break;

                case "Recto": direccionSeleccionada = Direccion.recto; break;
                case "VIzquierda": direccionSeleccionada = Direccion.izquierda; break;
                case "VDerecha": direccionSeleccionada = Direccion.derecha; break;
                case "U": direccionSeleccionada = Direccion.vuelta_u; break;
                default:
                    // Si el texto es "Izquierda" o "Derecha", necesitamos más contexto.
                    // Una forma simple es ver el menú padre, pero por ahora lo dejamos así
                    // ya que el FXML no diferencia los onAction.
                    // Este es un buen ejemplo de por qué un solo handler puede ser complejo.
                    // La solución del siguiente comentario es aún mejor.
                    System.out.println("Opción ambigua o no manejada: " + opcion);
            }
        } catch (Exception e) {
            System.err.println("Error al procesar la opción del menú: " + opcion);
        }

        System.out.println("Configuración actualizada: " + getConfiguracionActual());
    }

    private String getConfiguracionActual() {
        return String.format("Salida=%s, Tipo=%s, Dirección=%s",
                salidaSeleccionada, tipoVehiculoSeleccionado, direccionSeleccionada);
    }

    /**
     * Actualiza la representación visual de los vehículos.
     * Si un vehículo desaparece del modelo, se anima su salida y se elimina su nodo.
     */
    private void actualizarVehiculos(java.util.Map<String, VehiculoState> snapshot) {
        // eliminar vehículos que ya no están en el snapshot
        for (String id : new java.util.ArrayList<>(nodosVehiculo.keySet())) {
            if (!snapshot.containsKey(id) && !vehiculosAnimandoSalida.contains(id)) {
                Circle nodo = nodosVehiculo.remove(id);
                VehiculoState estado = ultimoEstado.remove(id);
                if (nodo != null && estado != null) {
                    vehiculosAnimandoSalida.add(id); // Marcar como en animación de salida
                    PuntoSalida salida = modelo.getPuntoSalidaVehiculo(id);
                    animarYEliminarNodo(nodo, estado, salida, id);
                } else if (nodo != null) {
                    lienzo.getChildren().remove(nodo);
                }
            }
        }

        // actualizar o crear nodos para los vehículos actuales
        for (VehiculoState v : snapshot.values()) {
            // NO crear un nuevo círculo si el vehículo está en proceso de animación de salida
            if (vehiculosAnimandoSalida.contains(v.id())) {
                continue;
            }
            
            // SAFETY: Don't create circles for vehicles that are not in active list and running
            boolean vehiculoActivo = vehiculosActivos.stream()
                .anyMatch(vehiculo -> vehiculo.getId().equals(v.id()) && vehiculo.isRunning());
            
            if (!vehiculoActivo) {
                System.out.println("Ignorando estado de vehículo inactivo: " + v.id());
                // También removerlo del modelo para garantizar limpieza completa
                modelo.eliminarVehiculo(v.id());
                continue;
            }
            
            Circle nodo = nodosVehiculo.get(v.id());
            if (nodo == null) {
                nodo = new Circle(10, v.tipo() == TipoVehiculo.emergencia ? Color.WHITE : Color.BLUE);
                nodosVehiculo.put(v.id(), nodo);
                lienzo.getChildren().add(nodo);
            }
            nodo.setCenterX(v.posX());
            nodo.setCenterY(v.posY());
            ultimoEstado.put(v.id(), v);
        }
    }

    /**
     * Mueve el nodo fuera del lienzo con un desvanecido y lo elimina al finalizar.
     */
    private void animarYEliminarNodo(Circle nodo, VehiculoState estado, PuntoSalida salida, String id) {
        double startX = nodo.getCenterX();
        double startY = nodo.getCenterY();
        
        // Obtener dimensiones del lienzo con fallback
        double ancho = lienzo.getWidth() > 0 ? lienzo.getWidth() : lienzo.getPrefWidth();
        if (ancho <= 0) ancho = 800;
        double alto = lienzo.getHeight() > 0 ? lienzo.getHeight() : lienzo.getPrefHeight();
        if (alto <= 0) alto = 600;

        double destinoX = startX;
        double destinoY = startY;

        // Calcular destino basado en PuntoSalida o posición actual
        if (salida != null) {
            switch (salida) {
                case ARRIBA -> destinoY = alto + 50;  // Salir por abajo
                case ABAJO -> destinoY = -50;         // Salir por arriba
                case IZQUIERDA -> destinoX = ancho + 50;  // Salir por derecha
                case DERECHA -> destinoX = -50;       // Salir por izquierda
            }
        } else {
            // Fallback: calcular dirección hacia el borde más cercano
            double distLeft = startX;
            double distRight = ancho - startX;
            double distTop = startY;
            double distBottom = alto - startY;
            
            double minDist = Math.min(Math.min(distLeft, distRight), Math.min(distTop, distBottom));
            
            if (minDist == distLeft) {
                destinoX = -50;
            } else if (minDist == distRight) {
                destinoX = ancho + 50;
            } else if (minDist == distTop) {
                destinoY = -50;
            } else {
                destinoY = alto + 50;
            }
        }

        // Crear animación de movimiento usando setTo en lugar de setBy
        TranslateTransition tt = new TranslateTransition(Duration.seconds(1.5), nodo);
        tt.setToX(destinoX - startX);  // TranslateTransition usa offsets relativos
        tt.setToY(destinoY - startY);

        // Crear animación de desvanecimiento
        FadeTransition ft = new FadeTransition(Duration.seconds(1.5), nodo);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);

        // Combinar ambas animaciones
        ParallelTransition pt = new ParallelTransition(tt, ft);
        pt.setOnFinished(e -> {
            lienzo.getChildren().remove(nodo);
            System.out.println("Vehículo " + estado.id() + " eliminado del lienzo tras animación");
            vehiculosAnimandoSalida.remove(id);
            
            // CRITICAL: Remove vehicle from active list to prevent reappearance
            vehiculosActivos.removeIf(v -> v.getId().equals(id));
            
            // Also ensure it's completely removed from the model
            modelo.eliminarVehiculo(id);
            
            // Remove from all local tracking maps
            ultimoEstado.remove(id);
        });
        
        System.out.println("Iniciando animación de salida para " + estado.id() + 
                          " desde (" + startX + "," + startY + ") hacia (" + destinoX + "," + destinoY + ")");
        pt.play();
    }

    /**
     * Esta es la función principal que se llama desde el menú "Acción > Crear Vehículo".
     * Utiliza los valores guardados en las variables de estado para crear un vehículo.
     */
    @FXML
    public void crearVehiculo() {
        System.out.println("--- Creando Vehículo ---");
        System.out.printf("Parámetros: Salida=%s, Tipo=%s, Dirección=%s\n",
                salidaSeleccionada, tipoVehiculoSeleccionado, direccionSeleccionada);

        // Consigue coordenadas iniciales desde la fábrica
        double[] coords = VehiculoFactory.getStartingCoordinates(salidaSeleccionada);

        // Crea el objeto de simulación con las mismas coordenadas
        Vehiculo vehiculoE = VehiculoFactory.createConfiguredVehicle(
                tipoVehiculoSeleccionado,
                salidaSeleccionada,
                direccionSeleccionada,
                modelo,
                coords[0],
                coords[1]
        );
        
        // Register the PuntoSalida with the model for proper exit animation
        modelo.registrarPuntoSalidaVehiculo(vehiculoE.getId(), salidaSeleccionada);

        // Ya no creamos el círculo manualmente aquí - el observer lo hará automáticamente
        // cuando el vehículo publique su primer estado
        
        vehiculosActivos.add(vehiculoE);
        if (simulacionEnMarcha) {
            new Thread(vehiculoE).start();
            vehiculoE.setRunning(true);
        }
        // Muestra una alerta de confirmación
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Vehículo Creado");
        alert.setHeaderText(null);
        alert.setContentText(String.format("Se ha creado un vehículo %s saliendo desde %s con dirección %s.",
                tipoVehiculoSeleccionado,
                salidaSeleccionada,
                direccionSeleccionada));
        alert.showAndWait();
    }


    // --- Métodos de control de la simulación y otros ---

    @FXML public void handlePlay() {
        simulacionEnMarcha = true;
        for (Vehiculo v : vehiculosActivos) {
            if (!v.isRunning()) { // Debes agregar este método en Vehiculo
                new Thread(v).start();
                v.setRunning(true);
            }
        }
    }


    @FXML private void handleExit() { Platform.exit(); }

    private void dibujarLineasDeCarretera() {
        // parametros carretera
        double anchoCarretera = 800.0;
        double altoCarretera = 600.0;
        double largoLinea = 30.0;
        double grosorLinea = 5.0;
        double espaciadoLinea = 20.0; // Espacio entre el final de una linea y el inicio de la siguiente

        // lineas horizontales
        double yLineaHorizontal = 292.0; // Posición Y fija para las líneas horizontales
        for (double x = 10; x < anchoCarretera; x += (largoLinea + espaciadoLinea)) {
            // No dibujar en la intersección
            if (x > 310 && x < 460) {
                continue;
            }
            Rectangle linea = new Rectangle(x, yLineaHorizontal, largoLinea, grosorLinea);
            linea.setFill(Color.YELLOW);
            lienzo.getChildren().add(linea);
        }

        // lineas verticales
        double xLineaVertical = 395.0; // Posición X fija para las líneas verticales
        for (double y = 10; y < altoCarretera; y += (largoLinea + espaciadoLinea)) {
            // No dibujar en la intersección
            if (y > 210 && y < 360) {
                continue;
            }
            Rectangle linea = new Rectangle(xLineaVertical, y, grosorLinea, largoLinea);
            linea.setFill(Color.YELLOW);
            lienzo.getChildren().add(linea);
        }
    }
}
