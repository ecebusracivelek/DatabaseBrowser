package com.example.project4;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;

import java.sql.*;

public class mainScreenController{
    @FXML
    private TreeView<String> treeView;

    @FXML
    private TableView<ObservableList<String>> tableView;

    @FXML
    private Button btnInsert;

    @FXML
    private Button btnUpdate;

    @FXML
    private Button btnDelete;

    private Connection connection;
    private String selectedTable;
    private String selectedDatabase;

    public void setConnection(Connection connection){
        this.connection = connection;
        loadDatabases();
    }

    public void initialize(){
        setupEditableTableView();
        setUpTreeViewListener();
    }

    private void loadDatabases() {
        try {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("SHOW DATABASES");

            TreeItem<String> root = new TreeItem<>("Databases");
            root.setExpanded(true);

            while (rs.next()) {
                String dbName = rs.getString(1);
                TreeItem<String> dbItem = new TreeItem<>(dbName);
                root.getChildren().add(dbItem);
            }
            treeView.setRoot(root);

        } catch (SQLException e) {
            showError("Error loading databases", e.getMessage());
        }
    }

    private void loadRelations(String databaseName, TreeItem<String> dbItem) {
        try{
            Statement stmt = connection.createStatement();
            //String databaseName = dbItem.getValue();
            stmt.execute("USE " + databaseName);
            ResultSet rs = connection.getMetaData().getTables(databaseName, null, "%", null);

            dbItem.getChildren().clear();
            while(rs.next()){
                String tableName = rs.getString("TABLE_NAME");
                TreeItem<String> tableItem = new TreeItem<>(tableName);
                dbItem.getChildren().add(tableItem);
            }
        } catch (SQLException e) {
            showError("Error loading relations", e.getMessage());
        }
    }

    private void loadTableData(String tableName) {
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName);
            ResultSetMetaData metaData = rs.getMetaData();

            tableView.getColumns().clear(); //clear previous columns
            tableView.getItems().clear();

            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                TableColumn<ObservableList<String>, String> column = new TableColumn<>(metaData.getColumnName(i));
                final int colIndex = i - 1;

                column.setCellFactory(TextFieldTableCell.forTableColumn());
                column.setCellValueFactory(data -> {
                    if(data.getValue().size() > colIndex){
                        return new SimpleStringProperty(data.getValue().get(colIndex));
                    }else{
                        return new SimpleStringProperty("");
                    }
                });
                column.setOnEditCommit(event -> {
                    ObservableList<String> row = event.getRowValue();
                    row.set(colIndex, event.getNewValue());
                    updateRowInDatabase(row, colIndex, event.getNewValue());
                });
                tableView.getColumns().add(column);
            }

            //Populate the rows
            ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
            while(rs.next()){
                ObservableList<String> row = FXCollections.observableArrayList();
                for(int i = 1; i <= metaData.getColumnCount(); i++){
                    row.add(rs.getString(i));
                }
                rows.add(row);
            }
            tableView.setItems(rows);

        } catch (SQLException e) {
            showError("Error loading table data", e.getMessage());
        }
    }

    private void setupEditableTableView(){
        tableView.setEditable(true);
    }

    private void setUpTreeViewListener(){
        treeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null){
                TreeItem<String> selectedItem = newValue;
                TreeItem<String> parentItem = selectedItem.getParent();

                if(parentItem != null && parentItem.getParent() == null){
                    selectedDatabase = selectedItem.getValue();
                    loadRelations(selectedDatabase, selectedItem);
                }else if(parentItem != null){
                    selectedTable = selectedItem.getValue();
                    loadTableData(selectedTable);
                }
            }
        });
    }

    private void updateRowInDatabase(ObservableList<String> row, int colIndex, String newValue) {
        if(selectedTable == null){
            showError("No Table Selected", "Please select a table to update.");
            return;
        }

        try{
            Statement statement = connection.createStatement();
            String columnName = tableView.getColumns().get(colIndex).getText();
            String primaryKeyValue = row.get(0); //!!!!!
            String primaryKeyColumn = tableView.getColumns().get(0).getText();
            String query = String.format(
                    "UPDATE %s SET %s = '%s' WHERE %s = '%s'",
                    selectedTable, columnName, newValue, primaryKeyColumn, primaryKeyValue
            );
            statement.executeUpdate(query);
        }catch(SQLException e){
            showError("Error Updating Row", e.getMessage());
        }
    }

    private void showError(String title, String message){
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.show();
    }

    @FXML
    private void insertData(){
        if(selectedTable == null){
            showError("No Table Selected", "Please select a table to insert into.");
            return;
        }

        try{
            Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
            StringBuilder columns = new StringBuilder();
            StringBuilder values = new StringBuilder();

            for(TableColumn<ObservableList<String>, ?> column : tableView.getColumns()){
                columns.append(column.getText()).append(",");

                String columnName = column.getText();
                String inputValue = getInputValueForColumn(columnName);

                if (inputValue.isEmpty() && !isNullableColumn(columnName)) {
                    showError("Invalid Input", "Column " + columnName + " cannot be empty.");
                    return;
                }

                if (isNumericColumn(columnName)) {
                    if (!inputValue.matches("\\d+")) {
                        showError("Invalid Input", "Column " + columnName + " requires a numeric value.");
                        return;
                    }
                    values.append(inputValue).append(",");
                } else {
                    values.append("'").append(inputValue).append("',");
                }
            }
            String columnList = columns.substring(0, columns.length() - 1);
            String valueList = values.substring(0, values.length() - 1);

            String query = String.format(
                    "INSERT INTO %s (%s) VALUES (%s)",
                    selectedTable,
                    columnList,
                    valueList
            );
            statement.executeUpdate(query);
            loadTableData(selectedTable);

        }catch(SQLException e){
            showError("Error Inserting Row", e.getMessage());
        }
    }

    private boolean isNullableColumn(String columnName){
        try{
            ResultSet rs = connection.getMetaData().getColumns(null, null, selectedTable, columnName);
            if(rs.next()){
                int nullable = rs.getInt("NULLABLE");
                return nullable == DatabaseMetaData.columnNullable;
            }
        }catch(SQLException e){
            showError("Error Checking Column Nullability", e.getMessage());
        }
        return false;
    }

    private String getInputValueForColumn(String columnName){
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Input Required");
        dialog.setHeaderText("Enter value for column " + columnName);
        dialog.setContentText("Value:");
        return dialog.showAndWait().orElse("");
    }

    private boolean isNumericColumn(String columnName){
        try{
            ResultSet rs = connection.getMetaData().getColumns(null, null, selectedTable, columnName);
            if(rs.next()){
                String columnType = rs.getString("TYPE_NAME");
                return columnType.equalsIgnoreCase("INT") || columnType.equalsIgnoreCase("BIGINT") || columnType.equalsIgnoreCase("DECIMAL") || columnType.equalsIgnoreCase("NUMERIC");
            }
        }catch(SQLException e){
            showError("Error Checking Column Type", e.getMessage());
        }
        return false;
    }

    @FXML
    private void updateData(){
        try{
            Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);

            String selectedTable = getSelectedTable();
            if(selectedTable == null){
                showError("No Table Selected", "Please select a table to update.");
                return;
            }
            String query = String.format("SELECT * FROM %s", selectedTable);
            ResultSet resultSet = statement.executeQuery(query);

            reloadTableData(selectedTable);
        }catch (SQLException e){
            showError("Error Updating Row", e.getMessage());
        }
    }

    @FXML
    private void deleteData(){
        if(selectedTable == null){
            showError("No Table Selected", "Please select a table to delete from.");
            return;
        }

        ObservableList<String> selectedRow = tableView.getSelectionModel().getSelectedItem();
        if(selectedRow == null){
            showError("No Row Selected", "Please select a row to delete.");
            return;
        }

        try{
            Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
            String primaryKey = tableView.getColumns().get(0).getText();
            String primaryKeyValue = selectedRow.get(0);
            String query = String.format(
                    "DELETE FROM %s WHERE %s = '%s'",
                    selectedTable, primaryKey, primaryKeyValue
            );
            statement.executeUpdate(query);
            tableView.getItems().remove(selectedRow);
        } catch(SQLException e){
            showError("Error Deleting Row", e.getMessage());
        }
    }

    private void reloadTableData(String tableName) throws SQLException {
        loadTableData(tableName);
    }

    private String getSelectedTable(){
        TreeItem<String> selectedItem = treeView.getSelectionModel().getSelectedItem();
        if(selectedItem == null || selectedItem.getParent() == null){
            return null;
        }
        return selectedItem.getValue();
    }
}
