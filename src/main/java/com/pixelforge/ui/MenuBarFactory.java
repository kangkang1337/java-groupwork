package com.pixelforge.ui;

import com.pixelforge.model.AppAction;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.util.function.Consumer;

/**
 * 菜单栏构建器。
 *
 * <p>菜单只负责"发出命令"，不负责实现；所有条目统一回调一个
 * {@code Consumer<AppAction>}，由主窗口集中分发。</p>
 */
public final class MenuBarFactory {

    private final Consumer<AppAction> handler;

    private MenuBarFactory(Consumer<AppAction> handler) {
        this.handler = handler;
    }

    public static MenuBar create(Consumer<AppAction> handler) {
        return new MenuBarFactory(handler).build();
    }

    private MenuBar build() {
        MenuBar bar = new MenuBar();
        bar.getStyleClass().add("app-menu-bar");
        bar.getMenus().addAll(fileMenu(), editMenu(), viewMenu(), layerMenu(), filterMenu(), helpMenu());
        return bar;
    }

    private Menu fileMenu() {
        return menu("文件",
                item(AppAction.FILE_NEW, ctrl(KeyCode.N)),
                item(AppAction.FILE_OPEN, ctrl(KeyCode.O)),
                new SeparatorMenuItem(),
                item(AppAction.FILE_SAVE, ctrl(KeyCode.S)),
                item(AppAction.FILE_SAVE_AS, new KeyCodeCombination(KeyCode.S,
                        KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)),
                new SeparatorMenuItem(),
                item(AppAction.FILE_EXPORT_PNG, null),
                item(AppAction.FILE_EXPORT_JPG, null),
                new SeparatorMenuItem(),
                item(AppAction.FILE_EXIT, new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN)));
    }

    private Menu editMenu() {
        return menu("编辑",
                item(AppAction.EDIT_UNDO, ctrl(KeyCode.Z)),
                item(AppAction.EDIT_REDO, new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN)),
                new SeparatorMenuItem(),
                item(AppAction.EDIT_CUT, ctrl(KeyCode.X)),
                item(AppAction.EDIT_COPY, ctrl(KeyCode.C)),
                item(AppAction.EDIT_PASTE, ctrl(KeyCode.V)),
                item(AppAction.EDIT_DELETE, new KeyCodeCombination(KeyCode.DELETE)),
                new SeparatorMenuItem(),
                item(AppAction.EDIT_SELECT_ALL, ctrl(KeyCode.A)),
                item(AppAction.EDIT_DESELECT, new KeyCodeCombination(KeyCode.D,
                        KeyCombination.SHORTCUT_DOWN)));
    }

    private Menu viewMenu() {
        return menu("视图",
                item(AppAction.VIEW_ZOOM_IN, new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.SHORTCUT_DOWN)),
                item(AppAction.VIEW_ZOOM_OUT, new KeyCodeCombination(KeyCode.MINUS, KeyCombination.SHORTCUT_DOWN)),
                item(AppAction.VIEW_ZOOM_FIT, new KeyCharacterCombination("0", KeyCombination.SHORTCUT_DOWN)),
                item(AppAction.VIEW_ZOOM_ACTUAL, new KeyCharacterCombination("1", KeyCombination.SHORTCUT_DOWN)),
                new SeparatorMenuItem(),
                item(AppAction.VIEW_TOGGLE_GRID, new KeyCodeCombination(KeyCode.QUOTE,
                        KeyCombination.SHORTCUT_DOWN)));
    }

    private Menu layerMenu() {
        return menu("图层",
                item(AppAction.LAYER_NEW, new KeyCodeCombination(KeyCode.N,
                        KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)),
                item(AppAction.LAYER_DUPLICATE, null),
                item(AppAction.LAYER_DELETE, null),
                new SeparatorMenuItem(),
                item(AppAction.LAYER_MERGE_DOWN, null));
    }

    private Menu filterMenu() {
        return menu("滤镜",
                item(AppAction.FILTER_GRAYSCALE, null),
                item(AppAction.FILTER_INVERT, ctrl(KeyCode.I)),
                new SeparatorMenuItem(),
                item(AppAction.FILTER_BLUR, null),
                item(AppAction.FILTER_SHARPEN, null),
                item(AppAction.FILTER_EMBOSS, null),
                item(AppAction.FILTER_EDGE, null),
                new SeparatorMenuItem(),
                item(AppAction.FILTER_MOSAIC, null),
                item(AppAction.FILTER_BRIGHTNESS, null));
    }

    private Menu helpMenu() {
        return menu("帮助",
                item(AppAction.HELP_SHORTCUTS, new KeyCodeCombination(KeyCode.F1)),
                item(AppAction.HELP_ABOUT, null));
    }

    private static Menu menu(String title, MenuItem... items) {
        Menu menu = new Menu(title);
        menu.getItems().addAll(items);
        return menu;
    }

    private MenuItem item(AppAction action, KeyCombination accelerator) {
        MenuItem item = new MenuItem(action.label());
        item.setOnAction(e -> handler.accept(action));
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        return item;
    }

    private static KeyCombination ctrl(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN);
    }
}
