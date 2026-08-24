package gm.ui;

import gm.engine.GmEngineImpl;
import gm.engine.dto.LoadResultDto;
import gm.engine.exception.GmException;
import gm.ui.component.CreateEventDialog;
import gm.ui.screen.EventsScreen;
import gm.ui.screen.UsersScreen;
import gm.ui.util.Alerts;
import gm.ui.util.Animations;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * Root of the JavaFX application: the top toolbar (file loading, "acting as" user, skin +
 * animation toggles, new-event bonus) and the Events/Users navigation, per the layout sketched in
 * targil01/ex2/ex 2 scetch.pptx.
 * <p>
 * This exercise has no login/session concept, so the "Acting as" combo box stands in for it: it
 * lets the grader drive actions (buying, opening/closing an event, ...) as whichever loaded user
 * they want to play, without a client-server split (that's Exercise 3).
 */
public class MarketApp extends Application {

    private static final String[] SKIN_NAMES = {"Default", "Dark", "Ocean"};
    private static final String[] SKIN_FILES = {"default.css", "dark.css", "ocean.css"};

    private AppContext context;
    private Label filePathLabel;
    private ProgressBar progressBar;
    private ComboBox<String> actingUserCombo;
    private Button newEventButton;
    private Scene scene;

    private EventsScreen eventsScreen;
    private UsersScreen usersScreen;
    private StackPane centerArea;

    @Override
    public void start(Stage stage) {
        actingUserCombo = new ComboBox<>();
        actingUserCombo.setPromptText("Acting as...");

        context = new AppContext(new GmEngineImpl(), actingUserCombo);

        eventsScreen = new EventsScreen(context);
        usersScreen = new UsersScreen(context);
        context.setOpenEventDetail(eventId -> {
            showScreen(eventsScreen.getRoot());
            eventsScreen.selectEvent(eventId);
        });
        context.setRefreshAll(this::refreshAll);

        centerArea = new StackPane(eventsScreen.getRoot(), usersScreen.getRoot());

        BorderPane root = new BorderPane();
        root.setTop(new javafx.scene.layout.VBox(buildToolBar(stage), buildNavBar()));
        root.setCenter(centerArea);

        scene = new Scene(root, 1100, 720);
        applySkin(SKIN_FILES[0]);

        stage.setScene(scene);
        stage.setTitle("Guess Market");
        stage.setMinWidth(480);
        stage.setMinHeight(360);
        stage.show();

        showScreen(eventsScreen.getRoot());
    }

    private HBox buildToolBar(Stage stage) {
        filePathLabel = new Label("No file loaded.");
        filePathLabel.setMaxWidth(360);

        Button loadButton = new Button("Load File...");
        loadButton.setOnAction(e -> onLoadFile(stage));

        progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        actingUserCombo.setDisable(true);

        ComboBox<String> skinCombo = new ComboBox<>();
        skinCombo.getItems().addAll(SKIN_NAMES);
        skinCombo.setValue(SKIN_NAMES[0]);
        skinCombo.setOnAction(e -> {
            int index = skinCombo.getSelectionModel().getSelectedIndex();
            applySkin(SKIN_FILES[index]);
        });

        CheckBox animationsCheck = new CheckBox("Enable animations");
        animationsCheck.selectedProperty().bindBidirectional(context.animationsEnabledProperty());

        newEventButton = new Button("+ New Event");
        newEventButton.setDisable(true);
        newEventButton.setOnAction(e -> new CreateEventDialog(context).showAndCreate());

        HBox box = new HBox(10, filePathLabel, loadButton, progressBar, spacer(), new Label("Acting as:"),
                actingUserCombo, newEventButton, new Label("Skin:"), skinCombo, animationsCheck);
        box.getStyleClass().add("toolbar-bar");
        box.setPadding(new Insets(6));
        return box;
    }

    private HBox buildNavBar() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton eventsBtn = new ToggleButton("Events");
        ToggleButton usersBtn = new ToggleButton("Users");
        eventsBtn.getStyleClass().add("nav-button");
        usersBtn.getStyleClass().add("nav-button");
        eventsBtn.setToggleGroup(group);
        usersBtn.setToggleGroup(group);
        eventsBtn.setSelected(true);

        eventsBtn.setOnAction(e -> showScreen(eventsScreen.getRoot()));
        usersBtn.setOnAction(e -> {
            usersScreen.refresh();
            showScreen(usersScreen.getRoot());
        });

        HBox box = new HBox(6, eventsBtn, usersBtn);
        box.getStyleClass().add("toolbar-bar");
        box.setPadding(new Insets(4, 6, 8, 6));
        return box;
    }

    private void showScreen(javafx.scene.Node node) {
        node.setVisible(true);
        node.setManaged(true);
        for (javafx.scene.Node sibling : centerArea.getChildren()) {
            if (sibling != node) {
                sibling.setVisible(false);
                sibling.setManaged(false);
            }
        }
        Animations.fadeIn(node, context.animationsEnabled());
    }

    private javafx.scene.layout.Region spacer() {
        javafx.scene.layout.Region region = new javafx.scene.layout.Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private void onLoadFile(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a Guess Market events file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        Task<LoadResultDto> task = new Task<>() {
            @Override
            protected LoadResultDto call() throws Exception {
                updateProgress(0, 100);
                updateMessage("Reading file...");
                Thread.sleep(350);
                updateProgress(45, 100);
                updateMessage("Validating...");
                LoadResultDto result = context.engine().loadEventsFile(file.getAbsolutePath());
                Thread.sleep(350);
                updateProgress(100, 100);
                return result;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setVisible(true);
        progressBar.setManaged(true);

        task.setOnSucceeded(e -> {
            progressBar.setVisible(false);
            progressBar.setManaged(false);
            LoadResultDto result = task.getValue();
            filePathLabel.setText(file.getAbsolutePath());
            filePathLabel.setTooltip(new javafx.scene.control.Tooltip(file.getAbsolutePath()));
            actingUserCombo.setDisable(false);
            newEventButton.setDisable(false);
            refreshAll();
            Alerts.info("File loaded", "Loaded " + result.getEventCount() + " event(s) and "
                    + result.getUserCount() + " user(s).");
        });

        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            progressBar.setManaged(false);
            Throwable ex = task.getException();
            String message = ex instanceof GmException ? ex.getMessage() : "Unexpected error: " + ex;
            Alerts.error("Could not load file", message);
        });

        Thread thread = new Thread(task, "gm-file-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void refreshAll() {
        String previouslyActing = actingUserCombo.getValue();
        actingUserCombo.getItems().setAll(
                context.engine().getUsers().stream().map(gm.engine.dto.UserSummaryDto::getName).toList());
        if (previouslyActing != null && actingUserCombo.getItems().contains(previouslyActing)) {
            actingUserCombo.setValue(previouslyActing);
        } else if (!actingUserCombo.getItems().isEmpty()) {
            actingUserCombo.setValue(actingUserCombo.getItems().get(0));
        }
        eventsScreen.refresh();
        usersScreen.refresh();
    }

    private void applySkin(String cssFile) {
        scene.getStylesheets().setAll(getClass().getResource("/gm/ui/css/" + cssFile).toExternalForm());
    }
}
