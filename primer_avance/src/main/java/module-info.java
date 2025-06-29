/**
 * módulo del sistema de gestión de tráfico.
 */
module edu.pucmm {
    // dependencias de javafx
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    
    // logging
    requires ch.qos.logback.classic;
    
    // exportar paquetes públicos
    exports edu.pucmm.view;
    exports edu.pucmm.controller;
    exports edu.pucmm;
    
    // abrir el modelo a javafx para reflexión
    opens edu.pucmm.model to javafx.fxml;
    opens edu.pucmm.view to javafx.fxml;
    opens edu.pucmm.controller to javafx.fxml;
    opens edu.pucmm to javafx.graphics;
} 