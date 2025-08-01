package applied_computing.setu;

import javafx.animation.TranslateTransition;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import javafx.util.Duration;

public class PrimaryController {

    @FXML
    private Pane pane;

    @FXML
    private Button startPointButton, endPointButton, visitedStationsButton, avoidingStationsButton, routeCycleButton;

    @FXML
    private Label startPointLabel, endPointLabel;

    @FXML
    private VBox stationsVBox, stationsAvoidVbox;

    @FXML
    private ImageView imageView;

    @FXML
    private ChoiceBox<String> selectedModeChoiceBox;

    @FXML
    private TextField costPenaltyField;

    ArrayList<ArrayList<GraphNode<Station>>> multiplePathArrayList;
    private int pathCounter = 0;
    private final Graph<Station> graph = new Graph<>();

    private boolean isStartPointSelected = false;
    private boolean isMultipleSelectionAllowed = false;
    private boolean isVisitedButtonSelected = false;
    private boolean isAvoidingButtonSelected = false;

    private ArrayList<Station> stationList;
    private HashMap<RadioButton, GraphNode<Station>> stationHashMap;
    Map<GraphNode<Station>, RadioButton> reverseMap = new HashMap<>();
    private final ArrayList<GraphNode<Station>> waypointStations = new ArrayList<>();
    private final HashSet<GraphNode<Station>> stationsToAvoid = new HashSet<>();

    private final Color[] colors = new Color[10];
    private int colorIndex = 0;

    @FXML
    private void initialize() {
        setAllRadioButtonsDisabled(true);
        centerMap();
        stationList = StationManager.loadStations();
        stationHashMap = StationManager.getRadioButtonMap(getAllRadioButtons(), stationList);
        for (Map.Entry<RadioButton, GraphNode<Station>> entry : stationHashMap.entrySet()) {
            reverseMap.put(entry.getValue(), entry.getKey());
        }
        connectNodes();
        costPenaltyField.setVisible(false);
        routeCycleButton.setVisible(false);
        selectedModeChoiceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue.equals("All routes")) {
                routeCycleButton.setVisible(true);
                costPenaltyField.setVisible(false);
                findRoute();
            } else if (newValue.equals("Shortest route")) {
                costPenaltyField.setVisible(true);
                routeCycleButton.setVisible(false);
                findRoute();
            } else {
                costPenaltyField.setVisible(false);
                routeCycleButton.setVisible(false);
                findRoute();
            }
        });
        selectedModeChoiceBox.setValue("Shortest route");
        costPenaltyField.setOnKeyReleased(event -> {findRoute();});
        addColors();
    }

    @FXML
    private RadioButton[] getAllRadioButtons() {
        List<RadioButton> radioButtons = new ArrayList<>();
        for (Node node : pane.getChildren()) {
            if (node instanceof RadioButton) radioButtons.add((RadioButton) node);
        }
        return radioButtons.toArray(new RadioButton[0]);
    }

    private void connectNodes() {for (Map.Entry<RadioButton, GraphNode<Station>> entry : stationHashMap.entrySet()) entry.getValue().setAdjacencyList(getAdjacencyListForANode(entry.getValue()));}

    private HashMap<GraphNode<Station>, Double> getAdjacencyListForANode(GraphNode<Station> node) {
        HashMap<GraphNode<Station>, Double> adjacencyList = new HashMap<>();

        Station currentStation = node.getValue();
        if (currentStation == null) return adjacencyList;

        try (BufferedReader br = new BufferedReader(new FileReader("src/main/resources/applied_computing/setu/adjacencies.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 3) continue;

                String from = parts[0].trim();
                String to = parts[1].trim();
                double weight = Double.parseDouble(parts[2].trim());

                if (currentStation.getName().equals(from)) {
                    GraphNode<Station> neighbor = findGraphNodeByName(to);
                    if (neighbor != null) {
                        adjacencyList.put(neighbor, weight);
                    }
                } else if (currentStation.getName().equals(to)) {
                    GraphNode<Station> neighbor = findGraphNodeByName(from);
                    if (neighbor != null) {
                        adjacencyList.put(neighbor, weight);
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.out.println("Error reading adjacency list: " + e.getMessage());
        }

        return adjacencyList;
    }


    private GraphNode<Station> findGraphNodeByName(String name) {
        for (GraphNode<Station> node : stationHashMap.values()) {
            Station station = node.getValue();
            if (station != null && station.getName().equalsIgnoreCase(name)) {
                return node;
            }
        }
        return null;
    }


    private void centerMap() {
        pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((obsWindow, oldWindow, newWindow) -> {
                    if (newWindow != null) {
                        Stage stage = (Stage) newWindow;
                        stage.widthProperty().addListener((o, oldWidth, newWidth) -> {
                            double currentWidth = newWidth.doubleValue();
                            double defaultMargin = 1250;
                            double newLeftMargin;
                            if (currentWidth < defaultMargin) {
                                newLeftMargin = 0;
                            } else {
                                newLeftMargin = (currentWidth - defaultMargin) * 0.5;
                            }
                            TranslateTransition translate = new TranslateTransition(Duration.millis(1), pane);
                            translate.setToX(newLeftMargin);
                            translate.play();
                        });
                    }
                });
            }
        });
    }


    public void setAllRadioButtonsDisabled(boolean disable) {setRadioButtonsDisabledRecursive(pane, disable);}

    private void setRadioButtonsDisabledRecursive(Pane parent, boolean disable) {
        for (Node node : parent.getChildren()) {
            if (node instanceof RadioButton) {
                RadioButton radioButton = (RadioButton) node;
                radioButton.setDisable(disable);
            } else if (node instanceof Pane) {
                Pane nestedPane = (Pane) node;
                setRadioButtonsDisabledRecursive(nestedPane, disable);
            }
        }
    }

    @FXML
    private void selectingOneStation(javafx.event.ActionEvent event) {
        selectedModeChoiceBox.setDisable(false);
        clearLines();
        toGrayScale(false);
        // Disable buttons as needed
        startPointButton.setDisable(true);
        endPointButton.setDisable(true);
        setAllRadioButtonsDisabled(false);
        if (event.getSource() == startPointButton) {// Handling visited and avoiding stations button toggle
            isStartPointSelected = true;
            isMultipleSelectionAllowed = false; // Allowing only one selection
            keepSomeButtonsDisabled(stationsAvoidVbox);
        } else if (event.getSource() == endPointButton) {
            isStartPointSelected = false;
            isMultipleSelectionAllowed = false; // Allowing only one selection
            keepSomeButtonsDisabled(stationsAvoidVbox);
        } else if (event.getSource() == visitedStationsButton) {
            if (isVisitedButtonSelected) {// Toggle visited stations selection
                isVisitedButtonSelected = false;
                visitedStationsButton.setText("Start Selecting Waypoint Stations");
                setAllRadioButtonsDisabled(true);
                startPointButton.setDisable(false);
                endPointButton.setDisable(false);
                visitedStationsButton.setDisable(false);
                avoidingStationsButton.setDisable(false);
                selectedModeChoiceBox.setDisable(false);
                costPenaltyField.setDisable(false);
                findRoute();
            } else {
                isVisitedButtonSelected = true;// Start selecting
                isMultipleSelectionAllowed = true;
                visitedStationsButton.setText("Stop Selecting");
                avoidingStationsButton.setDisable(true); // Disable avoiding button while selecting
                selectedModeChoiceBox.setDisable(true);
                costPenaltyField.setDisable(true);
                keepSomeButtonsDisabled(stationsAvoidVbox);// so the user cannot create logical error and select waypoint station as a station to avoid or desselect it accidentally
            }
        } else if (event.getSource() == avoidingStationsButton) {// Toggle avoiding stations selection
            if (isAvoidingButtonSelected) {
                isAvoidingButtonSelected = false;// Stop selecting
                avoidingStationsButton.setText("Start Selecting Stations To Avoid");
                setAllRadioButtonsDisabled(true);
                startPointButton.setDisable(false);
                endPointButton.setDisable(false);
                visitedStationsButton.setDisable(false);
                avoidingStationsButton.setDisable(false);
                selectedModeChoiceBox.setDisable(false);
                costPenaltyField.setDisable(false);
                findRoute();
            } else {
                isAvoidingButtonSelected = true;// Start selecting
                isMultipleSelectionAllowed = true;
                avoidingStationsButton.setText("Stop Selecting");
                visitedStationsButton.setDisable(true);
                selectedModeChoiceBox.setDisable(true);
                costPenaltyField.setDisable(true);
                keepSomeButtonsDisabled(stationsVBox);// so the user cannot create logical error and select waypoint station as a station to avoid or desselect it accidentally
            }
        }
    }


    @FXML
    private void oneRadioButtonPressingWaiting(javafx.event.ActionEvent event) {
        // Getting the source of the event (the clicked RadioButton)
        RadioButton selectedRadioButton = (RadioButton) event.getSource();
        // Formating the station name (replace underscores with spaces)
        String formattedStationName = selectedRadioButton.getTooltip().getText().trim();
        VBox targetVBox;
        boolean waypointsSelection = false;
        // Checking the source of the event and determine the corresponding VBox
        if (isVisitedButtonSelected) {
            targetVBox = stationsVBox; // Use stationsVBox if visitedStationsButton is pressed
            waypointsSelection = true;
            selectedRadioButton.setStyle("-fx-mark-color: green;");
        } else if (isAvoidingButtonSelected) {
            targetVBox = stationsAvoidVbox;
            selectedRadioButton.setStyle("-fx-mark-color: red;");
        }
        else if(isStartPointSelected) {
            selectedRadioButton.setStyle("-fx-mark-color: blue");
            targetVBox = new VBox();
        }
        else {
            targetVBox = new VBox();
            selectedRadioButton.setStyle("-fx-mark-color: orange;");
        }
        // If multiple selection is allowed
        if (isMultipleSelectionAllowed) {
            // Creating a new label for the station
            Label stationLabel = new Label(formattedStationName);
            GraphNode<Station> stationNode = new GraphNode<>(new Station(formattedStationName, new byte[]{}));
            // Checking if the RadioButton is selected or deselected
            if (selectedRadioButton.isSelected()) {
                // Adding the label to the corresponding VBox (if not already added)
                if (!isLabelInVBox(stationLabel, targetVBox)) {
                    targetVBox.getChildren().add(stationLabel);
                    if (waypointsSelection) waypointStations.add(stationNode); else stationsToAvoid.add(stationNode);
                }
            } else {
                // Removing the label from the corresponding VBox if the RadioButton is deselected
                removeLabelFromVBox(formattedStationName, targetVBox);
                if (waypointsSelection) waypointStations.remove(stationNode); else stationsToAvoid.remove(stationNode);
            }
        } else {
            // If multiple selection is not allowed, updating the start or end point label
            if (isStartPointSelected) {
                startPointLabel.setText(formattedStationName);
            } else {
                endPointLabel.setText(formattedStationName);
            }
            // Disabling radio buttons when only one selection is allowed
            setAllRadioButtonsDisabled(true);

            // Re-enabling buttons after one radio button is selected
            startPointButton.setDisable(false);
            endPointButton.setDisable(false);
            visitedStationsButton.setDisable(false);
            avoidingStationsButton.setDisable(false);
            findRoute();
        }
        deselectExcessiveButtons();
    }

    private boolean isLabelInVBox(String text, VBox vbox) {
        for (Node node : vbox.getChildren()) {
            if (node instanceof Label && ((Label) node).getText().equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    @FXML
    private void keepSomeButtonsDisabled(VBox vbox) {//checking all the radiobutton nodes and if it belongs to any of the known lists, do not display it
        for (Node node : pane.getChildren()) {
            if (!(node instanceof RadioButton)) continue;
            RadioButton radioButton = (RadioButton) node;
            if (isLabelInVBox(radioButton.getTooltip().getText().trim(),vbox) ||
                    ((RadioButton) node).getTooltip().getText().equals(startPointLabel.getText()) ||
                    ((RadioButton) node).getTooltip().getText().equals(endPointLabel.getText())) radioButton.setDisable(true);
        }
    }

    @FXML
    private void deselectExcessiveButtons() {
        for (Node node : pane.getChildren()) {
            if (!(node instanceof RadioButton)) continue;
            RadioButton radioButton = (RadioButton) node;
            String tooltipText = radioButton.getTooltip().getText();
            boolean isStart = tooltipText.equalsIgnoreCase(startPointLabel.getText());
            boolean isEnd = tooltipText.equalsIgnoreCase(endPointLabel.getText());
            boolean isVisited = isLabelInVBox(tooltipText, stationsVBox);
            boolean isAvoided = isLabelInVBox(tooltipText, stationsAvoidVbox);
            if (!(isStart || isEnd || isVisited || isAvoided)) radioButton.setSelected(false);
        }
    }


    private boolean isLabelInVBox(Label stationLabel, VBox targetVBox) {
        for (javafx.scene.Node node : targetVBox.getChildren()) {
            if (node instanceof Label) {
                Label label = (Label) node;
                if (label.getText().equals(stationLabel.getText())) return true;
            }
        }
        return false;
    }

    private void removeLabelFromVBox(String labelText, VBox targetVBox) {
        for (javafx.scene.Node node : targetVBox.getChildren()) {
            if (node instanceof Label) {
                Label label = (Label) node;
                if (label.getText().equals(labelText)) {
                    targetVBox.getChildren().remove(label);
                    break;
                }
            }
        }
    }

    @FXML
    private void findRoute() {
        String startName = startPointLabel.getText();
        String endName = endPointLabel.getText();
        RadioButton startButton = null;
        RadioButton endButton = null;
        for (RadioButton rb : getAllRadioButtons()) {
            String stationName = rb.getTooltip().getText();
            if (stationName.equalsIgnoreCase(startName)) startButton = rb;
            if (stationName.equalsIgnoreCase(endName)) endButton = rb;
        }
        if (startButton == null || endButton == null) return;
        GraphNode<Station> startNode = stationHashMap.get(startButton);
        GraphNode<Station> endNode = stationHashMap.get(endButton);
        if (startNode == null || endNode == null) return;
        if (selectedModeChoiceBox.getValue().equals("All routes")) {
            multiplePathArrayList = graph.allPathsBetweenNodes(startNode, endNode, waypointStations, stationsToAvoid);
            drawLines(multiplePathArrayList.get(0));
        } else if (selectedModeChoiceBox.getValue().equals("Route with least stops")) {
            drawLines(graph.shortestPathByNodes(startNode, endNode,waypointStations, stationsToAvoid));
        } else {
            double laneChangePenalty = costPenaltyField.getText().isEmpty() ? 0 : Double.parseDouble(costPenaltyField.getText());
            drawLines(graph.shortestPathBetweenStationsWithOrder(startNode, endNode, laneChangePenalty, waypointStations, stationsToAvoid));
        }
        toGrayScale(true);//make a map to grayscale so the route is clearly visible
    }

    @FXML
    private void cycleMultiplePaths() {//a method for all paths, so you can see them one at a time
        if (multiplePathArrayList == null || multiplePathArrayList.isEmpty()) return;
        pathCounter = (pathCounter + 1) % multiplePathArrayList.size();
        drawLines(multiplePathArrayList.get(pathCounter));
    }

    @FXML
    private void drawLines(ArrayList<GraphNode<Station>> solutionPath) {
        clearLines();//clear previous lines
        HashSet<GraphNode<Station>> waypoints = new HashSet<>(waypointStations);//hashset for waypoints as order matters for them
        if (solutionPath == null || solutionPath.isEmpty()) return;
        Color currentColor = getNextColor();
        for (int i = 0; i < solutionPath.size() - 1; i++) {//cycling though all the array nodes and seek for all the paths, adn draw lines between them
            GraphNode<Station> fromNode = solutionPath.get(i);
            GraphNode<Station> toNode = solutionPath.get(i + 1);
            currentColor = waypoints.remove(fromNode) ? getNextColor() : currentColor;
            RadioButton fromButton = reverseMap.get(fromNode);
            RadioButton toButton = reverseMap.get(toNode);
            if (fromButton != null && toButton != null) {
                double startX = fromButton.getLayoutX() + fromButton.getWidth() / 2;
                double startY = fromButton.getLayoutY() + fromButton.getHeight() / 2;
                double endX = toButton.getLayoutX() + toButton.getWidth() / 2;
                double endY = toButton.getLayoutY() + toButton.getHeight() / 2;
                Line line = new Line(startX, startY, endX, endY);
                line.setStroke(currentColor);
                line.setStrokeWidth(5);
                line.setOpacity(0.6);
                pane.getChildren().add(line);
                line.setMouseTransparent(true);
            }
        }
        colorIndex = 0;
    }

    @FXML
    private void clearLines() {pane.getChildren().removeIf(node -> node instanceof Line);}

    private void toGrayScale(boolean state) {
        String imageFileName = state ? "grayImage.png" : "U-Bahn_Wien.png";//this method just changes original image to the greyscale one, for simplicity
        imageView.setImage(new Image(Objects.requireNonNull(getClass().getResourceAsStream(imageFileName))));
    }

    private void addColors() {//method for changing colors, when waypoints are added
        colors[0] = Color.RED;
        colors[1] = Color.AQUA;
        colors[2] = Color.BLUE;
        colors[3] = Color.ORANGE;
        colors[4] = Color.MAGENTA;
        colors[5] = Color.YELLOW;
        colors[6] = Color.CYAN;
        colors[7] = Color.PINK;
        colors[8] = Color.LIGHTGREEN;
        colors[9] = Color.PURPLE;
    }

    private Color getNextColor() {
        Color color = colors[colorIndex];
        colorIndex = (colorIndex + 1) % colors.length;
        return color;
    }

}
