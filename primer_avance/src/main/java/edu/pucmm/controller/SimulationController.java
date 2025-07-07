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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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

    public static List<Vehiculo> getVehiculosActivos() {
        return vehiculosActivos;
    }

    @FXML
    public void initialize() {
        dibujarLineasDeCarretera();
        System.out.println("Simulación inicializada. Configuración por defecto: Salida=ARRIBA, Tipo=NORMAL, Dirección=RECTO");
        modelo = new CruceSimulationModel(new SimulationModel(),"cruce-1");
        modelo.getSimulationModel().addObserver(snapshot -> {
            Platform.runLater(() -> {
                lienzo.getChildren().removeIf(n -> n instanceof Circle && !"stop".equals(n.getId())); // limpiar solo vehículos
                for (VehiculoState v : snapshot.values()) {
                    Circle vehiculo = new Circle(10, v.tipo() == TipoVehiculo.emergencia ? Color.WHITE : Color.BLUE);
                    vehiculo.setCenterX(v.posX());
                    vehiculo.setCenterY(v.posY());
                    lienzo.getChildren().add(vehiculo);
                }
            });
        });
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

        // Crea y muestra el círculo en la posición real de simulación
        Circle vehiculo = new Circle(10);
        vehiculo.setFill(tipoVehiculoSeleccionado == TipoVehiculo.emergencia ? Color.WHITE : Color.BLUE);
        vehiculo.setCenterX(coords[0]);
        vehiculo.setCenterY(coords[1]);
        lienzo.getChildren().add(vehiculo);

        // Crea el objeto de simulación con las mismas coordenadas
        Vehiculo vehiculoE = VehiculoFactory.createConfiguredVehicle(
                tipoVehiculoSeleccionado,
                salidaSeleccionada,
                direccionSeleccionada,
                modelo,
                coords[0],
                coords[1]
        );
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
