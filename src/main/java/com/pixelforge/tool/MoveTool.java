package com.pixelforge.tool;

import com.pixelforge.model.FloatingContent;
import com.pixelforge.model.ToolType;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;

/**
 * 移动 / 变换工具。
 *
 * <p>没有浮动内容时，点击选区内会把它"抠"成浮动内容；之后：
 * 拖动内部 = 平移，拖动角点 = 等比缩放，拖动边中点 = 单轴缩放，
 * 拖动旋转手柄 = 旋转，按住 Alt 拖动角点 = 倾斜（扭曲）。</p>
 */
public class MoveTool extends Tool {

    private SelectionManager.Handle handle = SelectionManager.Handle.NONE;
    private boolean active;
    private double lastX;
    private double lastY;
    private double startAngle;
    private double startRotation;
    private double startScaleX = 1;
    private double startScaleY = 1;
    private double startSkew;
    private double startDistance = 1;
    private double startArmX = 1;
    private double startArmY = 1;
    private boolean altMode;

    public MoveTool(ToolContext ctx) {
        super(ctx);
    }

    @Override
    public ToolType type() {
        return ToolType.MOVE;
    }

    @Override
    public Cursor cursor() {
        return Cursor.MOVE;
    }

    @Override
    public void onPressed(double x, double y, MouseEvent event) {
        SelectionManager sm = ctx.getSelectionManager();
        double zoom = ctx.getCanvasView().getZoom();
        altMode = event.isAltDown();
        handle = sm.hitTest(x, y, zoom);

        if (sm.hasFloating()) {
            if (handle == SelectionManager.Handle.NONE) {
                // 点到浮动内容外面：先落地，再尝试重新选中/清空
                sm.commitFloating();
                if (!sm.hasSelection()) {
                    return;
                }
                if (!sm.getSelection().contains((int) x, (int) y)) {
                    sm.clearSelection();
                    ctx.setStatusMessage("已取消选择");
                    return;
                }
                sm.beginFloating();
                handle = SelectionManager.Handle.MOVE;
            }
        } else {
            if (!sm.hasSelection()) {
                // 没有选区时，整层内容都能拖
                sm.selectAll();
            }
            if (sm.getSelection() == null || sm.getSelection().isEmpty()) {
                return;
            }
            if (!sm.getSelection().contains((int) x, (int) y)) {
                sm.clearSelection();
                ctx.setStatusMessage("点击选区外，已取消选择");
                return;
            }
            if (!sm.beginFloating()) {
                return;
            }
            handle = SelectionManager.Handle.MOVE;
        }

        FloatingContent floating = sm.getFloating();
        if (floating == null) {
            return;
        }
        active = true;
        lastX = x;
        lastY = y;
        // 撤销记录由 SelectionManager 在"抠图 → 落地"这一整段里管理

        double[] center = floating.centerInDocument();
        startAngle = Math.atan2(y - center[1], x - center[0]);
        startRotation = floating.getRotation();
        startScaleX = floating.getScaleX();
        startScaleY = floating.getScaleY();
        startSkew = floating.getSkewX();
        startDistance = Math.max(1e-3, Math.hypot(x - center[0], y - center[1]));
        startArmX = Math.max(1e-3, Math.abs(x - center[0]));
        startArmY = Math.max(1e-3, Math.abs(y - center[1]));
    }

    @Override
    public void onDragged(double x, double y, MouseEvent event) {
        if (!active) {
            return;
        }
        SelectionManager sm = ctx.getSelectionManager();
        FloatingContent floating = sm.getFloating();
        if (floating == null) {
            return;
        }
        double[] center = floating.centerInDocument();

        switch (handle) {
            case MOVE -> sm.moveBy(x - lastX, y - lastY);
            case ROTATE -> {
                double angle = Math.atan2(y - center[1], x - center[0]);
                double rotation = startRotation + (angle - startAngle);
                if (event.isShiftDown()) {
                    double step = Math.PI / 12; // 15°
                    rotation = Math.round(rotation / step) * step;
                }
                sm.rotateTo(rotation);
                ctx.setStatusMessage("旋转 " + Math.round(Math.toDegrees(rotation) % 360) + "°");
            }
            case TL, TR, BR, BL -> {
                if (altMode) {
                    double skew = startSkew + (x - lastX) / Math.max(1, floating.getImage().getWidth());
                    sm.skewTo(skew);
                    ctx.setStatusMessage("倾斜 " + Math.round(Math.toDegrees(Math.atan(skew))) + "°");
                } else {
                    double distance = Math.max(1e-3, Math.hypot(x - center[0], y - center[1]));
                    double factor = distance / startDistance;
                    sm.scaleTo(startScaleX * factor, startScaleY * factor);
                    ctx.setStatusMessage("缩放 " + Math.round(startScaleX * factor * 100) + "%");
                }
            }
            case T, B -> {
                double arm = Math.max(1e-3, Math.abs(y - center[1]));
                double factor = arm / startArmY;
                sm.scaleTo(startScaleX, startScaleY * factor);
                ctx.setStatusMessage("纵向缩放 " + Math.round(startScaleY * factor * 100) + "%");
            }
            case L, R -> {
                double arm = Math.max(1e-3, Math.abs(x - center[0]));
                double factor = arm / startArmX;
                sm.scaleTo(startScaleX * factor, startScaleY);
                ctx.setStatusMessage("横向缩放 " + Math.round(startScaleX * factor * 100) + "%");
            }
            default -> {
            }
        }
        lastX = x;
        lastY = y;
    }

    @Override
    public void onReleased(double x, double y, MouseEvent event) {
        if (!active) {
            return;
        }
        active = false;
        handle = SelectionManager.Handle.NONE;
        ctx.setStatusMessage("回车确认变换，Esc 取消选择");
    }

    @Override
    public void cancel() {
        active = false;
        handle = SelectionManager.Handle.NONE;
        super.cancel();
    }
}
