package com.pixelforge.tool;

import com.pixelforge.model.ToolType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 工具管理器：持有全部工具实例，并把画布事件转发给当前工具。
 *
 * <p>工具实例常驻（而不是每次切换都新建），这样画笔的笔画状态、多边形的顶点
 * 都可以跨事件保留。</p>
 */
public class ToolManager implements CanvasInputHandler {

    private final Map<ToolType, Tool> tools = new EnumMap<>(ToolType.class);
    private Tool current;
    private final SelectionManager selectionManager;

    public ToolManager(ToolContext ctx, SelectionManager selectionManager) {
        this.selectionManager = selectionManager;
        tools.put(ToolType.BRUSH, new BrushTool(ctx));
        tools.put(ToolType.ERASER, new EraserTool(ctx));
        tools.put(ToolType.LINE, new LineTool(ctx));
        tools.put(ToolType.RECTANGLE, new RectangleTool(ctx));
        tools.put(ToolType.ELLIPSE, new EllipseTool(ctx));
        tools.put(ToolType.POLYGON, new PolygonTool(ctx));
        tools.put(ToolType.TEXT, new TextTool(ctx));
        tools.put(ToolType.FILL, new FillTool(ctx));
        tools.put(ToolType.PICKER, new PickerTool(ctx));
        tools.put(ToolType.SELECT, new MarqueeTool(ctx));
        tools.put(ToolType.LASSO, new LassoTool(ctx));
        tools.put(ToolType.MOVE, new MoveTool(ctx));
        tools.put(ToolType.HAND, new HandTool(ctx));
        tools.put(ToolType.ZOOM, new ZoomTool(ctx));
        current = tools.get(ToolType.BRUSH);
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    /**
     * 切换工具。
     *
     * @return 该工具是否已经实现
     */
    public boolean select(ToolType type) {
        Tool tool = tools.get(type);
        if (tool == null) {
            return false;
        }
        if (current != null) {
            current.cancel();
        }
        current = tool;
        return true;
    }

    /** 注册一个工具（后续步骤补充选框 / 移动工具时使用）。 */
    public void register(Tool tool) {
        tools.put(tool.type(), tool);
    }

    public boolean has(ToolType type) {
        return tools.containsKey(type);
    }

    public Tool getCurrent() {
        return current;
    }

    @Override
    public ToolType currentToolType() {
        return current == null ? ToolType.BRUSH : current.type();
    }

    @Override
    public void onPressed(double x, double y, javafx.scene.input.MouseEvent event) {
        if (current != null) {
            current.onPressed(x, y, event);
        }
    }

    @Override
    public void onDragged(double x, double y, javafx.scene.input.MouseEvent event) {
        if (current != null) {
            current.onDragged(x, y, event);
        }
    }

    @Override
    public void onReleased(double x, double y, javafx.scene.input.MouseEvent event) {
        if (current != null) {
            current.onReleased(x, y, event);
        }
    }

    @Override
    public void onMoved(double x, double y, javafx.scene.input.MouseEvent event) {
        if (current != null) {
            current.onMoved(x, y, event);
        }
    }

    @Override
    public void onDoubleClick(double x, double y, javafx.scene.input.MouseEvent event) {
        if (current != null) {
            current.onDoubleClick(x, y, event);
        }
    }
}
