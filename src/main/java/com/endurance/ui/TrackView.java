package com.endurance.ui;

import com.endurance.model.Car;
import com.endurance.sim.RaceSimulator;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * 赛道俯视图：画一条闭合赛道，并让每辆车沿着赛道跑。
 *
 * <p>车辆位置怎么算：仿真器是"一圈一步"推进的，所以赛道本身代表**一整圈**。
 * 当前圈的进度由外部的 {@code phase}（0~1）驱动，车辆在这个基础上
 * 按落后秒数往回退——领先的车在最前面，落后 10 秒的车就退后约 10% 的赛道长度。
 * 这样既能看出名次，也能一眼看出谁掉队了。</p>
 */
public class TrackView extends Region {

    private static final double PADDING = 42;
    private static final double TRACK_WIDTH = 26;
    private static final double CAR_RADIUS = 12;

    private final Canvas canvas = new Canvas(1, 1);

    private List<double[]> polyline;
    /** 带速度剖面的路径：直道快、弯道慢。 */
    private TrackPath path;
    private String shapeId = "";
    private double boundMinX;
    private double boundMaxX;
    private double boundMinY;
    private double boundMaxY;

    private RaceSimulator simulator;
    private double phase;
    private Integer selectedCarNumber;
    /** 每台车这一帧的屏幕坐标，用于鼠标点选。 */
    private final java.util.Map<Integer, double[]> screenPositions = new java.util.HashMap<>();
    /** 点中某台车时的回调。 */
    private java.util.function.IntConsumer onCarClick;

    public TrackView() {
        getChildren().add(canvas);
        setMinSize(360, 280);
        setStyle("-fx-background-color: #14161a;");
        widthProperty().addListener((obs, oldValue, newValue) -> redraw());
        heightProperty().addListener((obs, oldValue, newValue) -> redraw());
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        redraw();
    }

    /** 绑定数据并重绘。 */
    public void update(RaceSimulator simulator, double phase, Integer selectedCarNumber) {
        this.simulator = simulator;
        this.phase = phase;
        this.selectedCarNumber = selectedCarNumber;
        if (simulator != null) {
            String id = simulator.config().track().id();
            if (!id.equals(shapeId)) {
                shapeId = id;
                polyline = TrackShapes.sample(id, 24);
        path = TrackPath.of(polyline);
                computeBounds();
            }
        }
        redraw();
    }

    /** 计算赛道的实际包围盒，用于把图形撑满画布。 */
    private void computeBounds() {
        boundMinX = Double.MAX_VALUE;
        boundMinY = Double.MAX_VALUE;
        boundMaxX = -Double.MAX_VALUE;
        boundMaxY = -Double.MAX_VALUE;
        for (double[] point : polyline) {
            boundMinX = Math.min(boundMinX, point[0]);
            boundMaxX = Math.max(boundMaxX, point[0]);
            boundMinY = Math.min(boundMinY, point[1]);
            boundMaxY = Math.max(boundMaxY, point[1]);
        }
    }

    // ------------------------------------------------------------------

    private void redraw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width < 10 || height < 10) {
            return;
        }
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#14161a"));
        g.fillRect(0, 0, width, height);

        drawBackdropGrid(g, width, height);

        if (simulator == null || polyline == null) {
            return;
        }

        double trackWidth = Math.max(0.05, boundMaxX - boundMinX);
        double trackHeight = Math.max(0.05, boundMaxY - boundMinY);
        double scale = Math.min((width - PADDING * 2) / trackWidth,
                (height - PADDING * 2) / trackHeight);
        double offsetX = (width - trackWidth * scale) / 2.0 - boundMinX * scale;
        double offsetY = (height - trackHeight * scale) / 2.0 - boundMinY * scale;

        drawTrack(g, scale, offsetX, offsetY);
        drawStartLine(g, scale, offsetX, offsetY);
        drawCars(g, scale, offsetX, offsetY);
    }

    private void drawBackdropGrid(GraphicsContext g, double width, double height) {
        g.setFill(Color.web("#1b1e24"));
        for (double x = 24; x < width; x += 48) {
            for (double y = 24; y < height; y += 48) {
                g.fillOval(x, y, 2, 2);
            }
        }
    }

    private void drawTrack(GraphicsContext g, double scale, double offsetX, double offsetY) {
        g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        g.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);

        // 路面
        g.setStroke(Color.web("#262c35"));
        g.setLineWidth(TRACK_WIDTH + 8);
        strokeTrack(g, scale, offsetX, offsetY);

        // 内圈
        g.setStroke(Color.web("#3a434f"));
        g.setLineWidth(TRACK_WIDTH);
        strokeTrack(g, scale, offsetX, offsetY);

        // 中心虚线
        g.setStroke(Color.web("#4a5563", 0.85));
        g.setLineWidth(1.6);
        g.setLineDashes(10, 16);
        strokeTrack(g, scale, offsetX, offsetY);
        g.setLineDashes();
    }

    private void strokeTrack(GraphicsContext g, double scale, double offsetX, double offsetY) {
        g.beginPath();
        for (int i = 0; i < polyline.size(); i++) {
            double[] point = polyline.get(i);
            double x = offsetX + point[0] * scale;
            double y = offsetY + point[1] * scale;
            if (i == 0) {
                g.moveTo(x, y);
            } else {
                g.lineTo(x, y);
            }
        }
        g.closePath();
        g.stroke();
    }

    private void drawStartLine(GraphicsContext g, double scale, double offsetX, double offsetY) {
        double[] start = TrackShapes.pointAt(polyline, 0.0);
        double[] aheadPoint = TrackShapes.pointAt(polyline, 0.01);
        double dx = aheadPoint[0] - start[0];
        double dy = aheadPoint[1] - start[1];
        double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return;
        }
        // 垂直于赛道的方向
        double nx = -dy / len;
        double ny = dx / len;
        double sx = offsetX + start[0] * scale;
        double sy = offsetY + start[1] * scale;
        double half = (TRACK_WIDTH + 8) / 2.0;

        g.setStroke(Color.web("#E8EDF3", 0.9));
        g.setLineWidth(3);
        g.strokeLine(sx - nx * half, sy - ny * half, sx + nx * half, sy + ny * half);
    }

    /**
     * 一台车在当前相位下的赛道进度（0~1，沿路径正向，越大越靠前）。
     *
     * <p>用时会**更长**的车才算落后，所以「落后圈数」一定是正数。
     * 取小数部分是为了让落后整圈的车绕回赛道前面的位置，而不是堆在起点。</p>
     */
    public static double progressOf(Car car, double phase, double leadLaps, double avgLap) {
        if (car.isRetired()) {
            // 退赛车停在维修区入口
            return 0.995;
        }
        double behindLaps = car.totalTime() / avgLap - leadLaps;
        double fraction = phase - behindLaps;
        return fraction - Math.floor(fraction);
    }

    private void drawCars(GraphicsContext g, double scale, double offsetX, double offsetY) {
        List<Car> cars = simulator.cars();
        double avgLap = simulator.config().track().baseLapSeconds() + 4;
        double leaderTime = Double.MAX_VALUE;
        for (Car car : cars) {
            if (!car.isRetired()) {
                leaderTime = Math.min(leaderTime, car.totalTime());
            }
        }
        if (leaderTime == Double.MAX_VALUE) {
            leaderTime = 0;
        }

        double leadLaps = leaderTime / avgLap;

        screenPositions.clear();

        for (int index = 0; index < cars.size(); index++) {
            Car car = cars.get(index);
            // 真实相位 = 当前圈内进度 - 落后圈数，再取小数部分。
            // 原来把「落后圈数」写成 leadLaps - 车圈数，算出来是负数，
            // 等于把慢车往前推——赛道上的前后顺序和排名表完全相反。
            double fraction = progressOf(car, phase, leadLaps, avgLap);
            double[] point = path.pointAt(fraction);

            // 同一位置附近的车沿法线错开，避免完全重叠看不出是一队车
            double lateral = lateralOffset(index);
            double[] normal = normalAt(fraction);
            double x = offsetX + (point[0] + normal[0] * lateral) * scale;
            double y = offsetY + (point[1] + normal[1] * lateral) * scale;

            screenPositions.put(car.number(), new double[]{x, y});
            boolean selected = selectedCarNumber != null && selectedCarNumber == car.number();
            Color teamColor = parseColor(car.team().colorHex());

            if (selected) {
                g.setStroke(Color.web("#E8EDF3", 0.95));
                g.setLineWidth(2.5);
                g.strokeOval(x - CAR_RADIUS - 5, y - CAR_RADIUS - 5,
                        (CAR_RADIUS + 5) * 2, (CAR_RADIUS + 5) * 2);
            }

            g.setFill(car.isRetired() ? Color.web("#4A5563") : teamColor);
            g.fillOval(x - CAR_RADIUS, y - CAR_RADIUS, CAR_RADIUS * 2, CAR_RADIUS * 2);
            g.setStroke(Color.web("#0F1115"));
            g.setLineWidth(1.5);
            g.strokeOval(x - CAR_RADIUS, y - CAR_RADIUS, CAR_RADIUS * 2, CAR_RADIUS * 2);

            g.setFill(Color.web("#0F1115"));
            g.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(String.valueOf(car.number()), x, y + 4.2);
        }
        g.setTextAlign(TextAlignment.LEFT);
    }

    private static Color parseColor(String hex) {
        try {
            return Color.web(hex);
        } catch (IllegalArgumentException e) {
            return Color.web("#9AA4B2");
        }
    }
/** 每台车一个固定的横向错位量，避免同位置的车完全重叠。 */
    private double lateralOffset(int index) {
        int slot = index % 3;
        return (slot - 1) * 0.008;
    }

    /** 路径在该点的单位法线方向。 */
    private double[] normalAt(double fraction) {
        double[] a = path.pointAt(fraction - 0.003);
        double[] b = path.pointAt(fraction + 0.003);
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) {
            return new double[]{0, 0};
        }
        // 切线旋转 90°
        return new double[]{-dy / len, dx / len};
    }
/** 注册点选回调（点中赛道上的车就切到那台车）。 */
    public void setOnCarClick(java.util.function.IntConsumer onCarClick) {
        this.onCarClick = onCarClick;
        setOnMouseClicked(event -> {
            Integer hit = carAt(event.getX(), event.getY());
            if (hit != null && this.onCarClick != null) {
                this.onCarClick.accept(hit);
            }
        });
    }

    /** 找鼠标附近最近的一台车；都没有就返回 null。 */
    public Integer carAt(double x, double y) {
        Integer best = null;
        double bestDistance = 22 * 22;
        for (var entry : screenPositions.entrySet()) {
            double[] point = entry.getValue();
            double dx = point[0] - x;
            double dy = point[1] - y;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry.getKey();
            }
        }
        return best;
    }
}