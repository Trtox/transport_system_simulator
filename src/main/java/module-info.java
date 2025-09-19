module TransportSystemSimulator {

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.desktop;
    requires gs.core;
    requires gs.ui.swing;
    requires annotations;
    requires org.json;


    exports net.etfbl.pj2.gui;
    exports net.etfbl.pj2.simulation;
    exports net.etfbl.pj2.model;

    opens net.etfbl.pj2.gui to javafx.fxml;
}