module edu.pucmm.trafico {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;
    
    opens edu.pucmm.trafico to javafx.fxml;
    opens edu.pucmm.trafico.view to javafx.fxml;
    
    exports edu.pucmm.trafico;
    exports edu.pucmm.trafico.model;
    exports edu.pucmm.trafico.view;
}