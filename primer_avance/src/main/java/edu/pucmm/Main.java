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
        // si se pasa "demo" como argumento, ejecutar demostración de vehículos
        if (args.length > 0 && "demo".equals(args[0])) {
            // ejecutar demostración sin javafx
            System.out.println("ejecutando demostración de vehículos...");
            edu.pucmm.simulation.VehiculoDemoSimple.main(new String[0]);
            System.out.println("demo completado, terminando programa...");
            System.exit(0); // terminar explícitamente el programa
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