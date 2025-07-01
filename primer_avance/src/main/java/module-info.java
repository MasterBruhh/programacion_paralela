module edu.pucmm {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;
    
    opens edu.pucmm to javafx.fxml;
    exports edu.pucmm;
    exports edu.pucmm.model;
    exports edu.pucmm.simulation;
    exports edu.pucmm.controller;
    exports edu.pucmm.view;
    exports edu.pucmm.util;
} 