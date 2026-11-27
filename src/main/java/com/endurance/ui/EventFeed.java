package com.endurance.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 左上角的赛事播报条：紧挨着旗语，最近几条事件一直可见。
 *
 * <p>原来事件只在底部状态栏闪一行小字，跑起来根本来不及看。
 * 挪到旗语下面之后，眼睛扫一眼左上角就知道刚才发生了什么。</p>
 */
public class EventFeed extends VBox {

    private static final int MAX_ROWS = 4;

    private final Deque<HBox> rows = new ArrayDeque<>();

    public EventFeed() {
        setSpacing(4);
        getStyleClass().add("event-feed");
        setMaxWidth(Region.USE_PREF_SIZE);
    }

    /**
     * 推入一条事件。
     *
     * @param lap   圈号
     * @param text  事件内容
     * @param tone  色调：safety / penalty / weather / pit / normal
     */
    public void push(int lap, String text, String tone) {
        Label lapLabel = new Label(lap > 0 ? "L" + lap : "—");
        lapLabel.getStyleClass().add("event-lap");

        Label textLabel = new Label(text);
        textLabel.getStyleClass().add("event-text");
        textLabel.setWrapText(false);
        // 太长就截断，播报条不能撑爆画面
        if (textLabel.getText().length() > 34) {
            textLabel.setText(textLabel.getText().substring(0, 33) + "…");
        }

        Region bar = new Region();
        bar.getStyleClass().addAll("event-tone", "event-tone-" + tone);
        bar.setPrefWidth(3);
        bar.setMinWidth(3);

        HBox row = new HBox(8, bar, lapLabel, textLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("event-row");
        HBox.setHgrow(textLabel, Priority.ALWAYS);

        rows.addFirst(row);
        getChildren().add(0, row);
        while (rows.size() > MAX_ROWS) {
            HBox removed = rows.removeLast();
            getChildren().remove(removed);
        }
        // 最新一条高亮一下
        for (int i = 0; i < getChildren().size(); i++) {
            getChildren().get(i).setOpacity(i == 0 ? 1.0 : Math.max(0.35, 1.0 - i * 0.2));
        }
    }

    /** 清空（换站/重开时用）。 */
    public void clear() {
        rows.clear();
        getChildren().clear();
    }

    /** 当前条数，测试用。 */
    public int size() {
        return rows.size();
    }

    /** 最新一条的文字，测试用。 */
    public String latestText() {
        HBox first = rows.peekFirst();
        if (first == null) {
            return "";
        }
        for (var node : first.getChildren()) {
            if (node instanceof Label label && label.getStyleClass().contains("event-text")) {
                return label.getText();
            }
        }
        return "";
    }
}
