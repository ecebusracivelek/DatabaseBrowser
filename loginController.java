package com.example.project4;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ResourceBundle;

public class loginController{
    @FXML
    private TextField urlField;

    @FXML
    private TextField usernameField;

    @FXML
    private TextField passwordField;

    @FXML
    private Button connectButton;

    private Connection connection;

    @FXML
    private void onConnect(){
        String url = urlField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if(url.isEmpty() || username.isEmpty()){
            showError("Input Error", "Please enter a valid URL and username");
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(url, username, password);
            if (connection == null || connection.isClosed()) {
                showError("Connection Error", "Failed to connect to the database");
                return;
            }
            goToMainPage(connection);
        }catch (ClassNotFoundException e){
            showError("Driver Error", e.getMessage());
        }catch(SQLException e){
            showError("Connection Error", e.getMessage());
        }
    }

    private void goToMainPage(Connection connection) {
        try{
            FXMLLoader loader = new FXMLLoader(getClass().getResource("mainScreen.fxml"));
            Scene scene = new Scene(loader.load());

            mainScreenController controller = loader.getController();
            controller.setConnection(connection);

            Stage stage = (Stage) urlField.getScene().getWindow();
            stage.setScene(scene);
            stage.show();
        }catch(Exception e){
            showError("Error", "Failed to load the main page: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showError(String title, String message){
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
