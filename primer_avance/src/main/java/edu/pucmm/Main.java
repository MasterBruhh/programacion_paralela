package edu.pucmm;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * clase principal de la aplicación de gestión de tráfico.
 */
public class Main extends Application {
    
    public static void main(String[] args) {
        if (args.length > 0) {
            switch (args[0]) {
                case "demo" -> {
                    // ejecutar demostración simple de vehículos
                    System.out.println("ejecutando demostración simple de vehículos...");
                    edu.pucmm.simulation.VehiculoDemoSimple.main(new String[0]);
                    System.out.println("demo completado, terminando programa...");
                    System.exit(0);
                }
                case "cruce" -> {
                    // ejecutar demostración del escenario 1 (cruce de calles)
                    System.out.println("ejecutando demostración del escenario 1: cruce de calles...");
                    edu.pucmm.simulation.CruceEscenario1Demo.main(new String[0]);
                    System.out.println("demostración del cruce completada, terminando programa...");
                    System.exit(0);
                }
                default -> {
                    System.out.println("argumentos disponibles:");
                    System.out.println("  demo  - demostración simple de vehículos");
                    System.out.println("  cruce - demostración del escenario 1 (cruce de calles)");
                    System.exit(0);
                }
            }
        } else {
            // ejecutar aplicación javafx normal
            launch(args);
        }
    }
    
    @Override
    public void start(Stage primaryStage) {
        // placeholder para la interfaz gráfica
        Label welcomeLabel = new Label("sistema de gestión de tráfico");
        welcomeLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        Label statusLabel = new Label("simulación: preparando...");
        
        VBox root = new VBox(10);
        root.getChildren().addAll(welcomeLabel, statusLabel);
        root.setStyle("-fx-padding: 20px; -fx-alignment: center;");
        
        Scene scene = new Scene(root, 400, 200);
        
        primaryStage.setTitle("sistema de gestión de tráfico");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // todo: inicializar el modelo de simulación aquí
        System.out.println("aplicación iniciada correctamente");
    }
}