package gm.ui.screen;

import gm.engine.dto.ParticipationSummaryDto;
import gm.engine.dto.UserDetailDto;
import gm.engine.dto.UserSummaryDto;
import gm.ui.AppContext;
import gm.ui.component.BalanceChart;
import gm.ui.util.Animations;
import gm.ui.util.Money;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.List;

/** Users tab: a user table plus a detail pane (balance, participations, balance history chart). */
public class UsersScreen {

    private final AppContext context;
    private final BorderPane root = new BorderPane();
    private final TableView<UserSummaryDto> table = new TableView<>();

    private final Label nameLabel = new Label("Select a user to see their details.");
    private final Label balanceLabel = new Label();
    private final TableView<ParticipationSummaryDto> participationsTable = new TableView<>();
    private final BalanceChart balanceChart = new BalanceChart();
    private final VBox detailBox = new VBox(10);

    public UsersScreen(AppContext context) {
        this.context = context;
        buildTable();
        buildParticipationsTable();

        detailBox.setPadding(new Insets(10));
        detailBox.getStyleClass().add("detail-pane");
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        detailBox.getChildren().addAll(nameLabel, balanceLabel, new Label("Participations:"),
                participationsTable, balanceChart);

        SplitPane split = new SplitPane(wrapScroll(table), wrapScroll(detailBox));
        split.setDividerPositions(0.42);
        root.setCenter(split);
    }

    public javafx.scene.Node getRoot() {
        return root;
    }

    public void refresh() {
        List<UserSummaryDto> users = context.engine().isFileLoaded() ? context.engine().getUsers() : List.of();
        table.setItems(FXCollections.observableArrayList(users));
    }

    private void showUser(String username) {
        UserDetailDto detail = context.engine().getUserDetail(username);
        nameLabel.setText(detail.getName() + (detail.isBlocked() ? "  [BLOCKED]" : ""));
        balanceLabel.setText("Balance: " + Money.format(detail.getBalance()));
        participationsTable.setItems(FXCollections.observableArrayList(detail.getParticipations()));
        balanceChart.render(context.engine().getBalanceHistory(username));
        Animations.fadeIn(detailBox, context.animationsEnabled());
    }

    private void buildTable() {
        TableColumn<UserSummaryDto, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        TableColumn<UserSummaryDto, String> balanceCol = new TableColumn<>("Balance");
        balanceCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(Money.format(c.getValue().getBalance())));
        TableColumn<UserSummaryDto, String> mmCol = new TableColumn<>("Market Maker");
        mmCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().isMarketMakerOfAnyEvent() ? "Yes" : "No"));
        TableColumn<UserSummaryDto, String> blockedCol = new TableColumn<>("Blocked");
        blockedCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().isBlocked() ? "Yes" : "No"));
        table.getColumns().addAll(List.of(nameCol, balanceCol, mmCol, blockedCol));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                showUser(selected.getName());
            }
        });
    }

    private void buildParticipationsTable() {
        participationsTable.setPrefHeight(160);
        TableColumn<ParticipationSummaryDto, String> eventCol = new TableColumn<>("Event");
        eventCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getEventName()));
        TableColumn<ParticipationSummaryDto, String> methodCol = new TableColumn<>("Method");
        methodCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getMethod().name()));
        TableColumn<ParticipationSummaryDto, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getStatus().name()));
        TableColumn<ParticipationSummaryDto, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().isMarketMaker() ? "Market maker" : "Trader"));
        participationsTable.getColumns().addAll(List.of(eventCol, methodCol, statusCol, roleCol));
        participationsTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<ParticipationSummaryDto> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty() && e.getClickCount() == 1) {
                    context.openEventDetail(row.getItem().getEventId());
                }
            });
            return row;
        });
    }

    private ScrollPane wrapScroll(javafx.scene.Node node) {
        ScrollPane scroll = new ScrollPane(node);
        scroll.setFitToWidth(true);
        return scroll;
    }
}
