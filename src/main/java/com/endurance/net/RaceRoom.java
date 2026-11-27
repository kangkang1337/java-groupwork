package com.endurance.net;

import com.endurance.data.GameData;
import com.endurance.data.JsonWriter;
import com.endurance.model.Car;
import com.endurance.model.TireCompound;
import com.endurance.model.Track;
import com.endurance.setup.RaceSetup;
import com.endurance.sim.PitDecision;
import com.endurance.sim.RaceEvent;
import com.endurance.sim.RaceResult;
import com.endurance.sim.RaceSimulator;
import com.endurance.util.AppLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 一个对战房间：**比赛由服务器推进**，客户端只能提交意图。
 *
 * <p>流程：加入 → 准备 → 开局 → 每 {@code lapMillis} 自动推进一圈并广播状态 → 冲线结算。</p>
 *
 * <p>断线重连靠一次性 token：玩家第一次加入时服务器发一个 token，
 * 断线后带着同一个 token 重连就能回到原来的座位，比赛不会因为掉线而作废。</p>
 */
public class RaceRoom {

    private final String id;
    private final GameData data;
    private final Track track;
    private final int laps;
    private final int minPlayers;
    private final long lapMillis;
    private final ScheduledExecutorService scheduler;

    private final Map<Integer, ServerConnection> seats = new ConcurrentHashMap<>();
    private final Map<String, Integer> tokens = new ConcurrentHashMap<>();
    private final Map<Integer, RemoteStrategy> players = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> seatCar = new ConcurrentHashMap<>();
    private final Set<Integer> ready = ConcurrentHashMap.newKeySet();

    private RaceSetup.RaceLineup lineup;
    private RaceSimulator simulator;
    private volatile String phase = Protocol.PHASE_WAITING;
    private int lastEventIndex;
    private ScheduledFuture<?> loop;

    public RaceRoom(String id, GameData data, Track track, int laps, int minPlayers,
                    long lapMillis, ScheduledExecutorService scheduler) {
        this.id = id;
        this.data = data;
        this.track = track;
        this.laps = laps;
        this.minPlayers = Math.max(1, minPlayers);
        this.lapMillis = Math.max(50, lapMillis);
        this.scheduler = scheduler;
    }

    public String id() {
        return id;
    }

    public String phase() {
        return phase;
    }

    public int connectedCount() {
        return seats.size();
    }

    // ------------------------------------------------------------------
    // 加入 / 重连
    // ------------------------------------------------------------------

    /** 处理 hello：新建座位或用 token 回到原座位。 */
    public synchronized void join(ServerConnection connection, String name, String token) {
        connection.setPlayerName(name);

        Integer reconnectedSeat = token == null ? null : tokens.get(token);
        if (reconnectedSeat != null) {
            reconnect(connection, reconnectedSeat, token);
            return;
        }
        if (Protocol.PHASE_FINISHED.equals(phase)) {
            connection.send(Protocol.error("比赛已结束，请换一个房间"));
            return;
        }

        int seat = firstFreeSeat();
        if (seat < 0) {
            connection.send(Protocol.error("房间已满（最多 " + minPlayers + " 人）"));
            return;
        }

        String newToken = UUID.randomUUID().toString().substring(0, 8);
        connection.setSeat(seat);
        connection.setToken(newToken);
        seats.put(seat, connection);
        tokens.put(newToken, seat);
        ready.remove(seat);

        connection.send(welcome(connection));
        broadcastPeer();
        broadcast(stateMessage());
        AppLog.info("房间 " + id + "：" + name + " 加入，座位 " + seat);
    }

    private void reconnect(ServerConnection connection, int seat, String token) {
        ServerConnection old = seats.get(seat);
        if (old != null && old != connection) {
            old.send(Protocol.error("你的座位已在别处重新连接"));
            old.close();
        }
        connection.setSeat(seat);
        connection.setToken(token);
        seats.put(seat, connection);

        RemoteStrategy strategy = players.get(seat);
        if (strategy != null) {
            strategy.setConnected(true);
        }

        connection.send(welcome(connection));
        connection.send(stateMessage());
        broadcastPeer();
        AppLog.info("房间 " + id + "：" + connection.playerName() + " 重连成功，座位 " + seat);
    }

    private int firstFreeSeat() {
        for (int seat = 0; seat < minPlayers; seat++) {
            if (!seats.containsKey(seat)) {
                return seat;
            }
        }
        return -1;
    }

    private String welcome(ServerConnection connection) {
        JsonWriter.Arr seatsInfo = JsonWriter.arr();
        for (Map.Entry<Integer, ServerConnection> entry : seats.entrySet()) {
            seatsInfo.add(JsonWriter.obj()
                    .put("seat", entry.getKey())
                    .put("name", entry.getValue().playerName())
                    .put("car", seatCar.getOrDefault(entry.getKey(), -1)));
        }
        return Protocol.line(JsonWriter.obj()
                .put("type", Protocol.WELCOME)
                .put("room", id)
                .put("seat", connection.seat())
                .put("token", connection.token())
                .put("phase", phase)
                .put("track", track.name())
                .put("totalLaps", laps)
                .put("players", seatsInfo));
    }

    /** 客户端断开：保留座位和 token，等人重连。 */
    public synchronized void onDisconnect(ServerConnection connection) {
        int seat = connection.seat();
        if (seat < 0) {
            return;
        }
        if (seats.get(seat) == connection) {
            seats.remove(seat);
            RemoteStrategy strategy = players.get(seat);
            if (strategy != null) {
                strategy.setConnected(false);
            }
            broadcastPeer();
            AppLog.info("房间 " + id + "：" + connection.playerName() + " 断线（可重连）");
        }
    }

    // ------------------------------------------------------------------
    // 消息分发
    // ------------------------------------------------------------------

    public synchronized void handle(ServerConnection connection, String type, com.endurance.data.Json.JsonValue msg) {
        if (!Protocol.isClientMessage(type)) {
            // 防作弊：客户端不允许伪造状态类消息
            connection.send(Protocol.error("客户端不能发送 " + type + " 消息"));
            AppLog.warn("房间 " + id + "：拒绝客户端伪造消息 " + type);
            return;
        }
        switch (type) {
            case Protocol.PING -> connection.send(Protocol.pong());
            case Protocol.READY -> markReady(connection);
            case Protocol.COMMAND -> handleCommand(connection, msg);
            default -> connection.send(Protocol.error("未知消息类型：" + type));
        }
    }

    private void markReady(ServerConnection connection) {
        int seat = connection.seat();
        if (seat < 0) {
            connection.send(Protocol.error("请先发送 hello 加入房间"));
            return;
        }
        ready.add(seat);
        broadcast(Protocol.line(JsonWriter.obj()
                .put("type", Protocol.PEER)
                .put("message", connection.playerName() + " 已准备")));
        maybeStart();
    }

    private void handleCommand(ServerConnection connection, com.endurance.data.Json.JsonValue msg) {
        int seat = connection.seat();
        if (seat < 0) {
            connection.send(Protocol.error("请先发送 hello 加入房间"));
            return;
        }
        if (!Protocol.PHASE_RUNNING.equals(phase)) {
            connection.send(Protocol.error("比赛还没开始或已经结束"));
            return;
        }
        RemoteStrategy strategy = players.get(seat);
        if (strategy == null) {
            connection.send(Protocol.error("本房间没有你的车"));
            return;
        }

        String tireId = msg.opt("tire", new com.endurance.data.Json.JsonValue.Str("")).asString();
        TireCompound tire = data.tire(tireId);
        if (tire == null) {
            connection.send(Protocol.error("未知轮胎：" + tireId));
            return;
        }
        double fuel = msg.opt("fuel", new com.endurance.data.Json.JsonValue.Num(0)).asDouble();
        if (fuel < 0 || fuel > 130) {
            connection.send(Protocol.error("加油量必须在 0~130 kg 之间"));
            return;
        }

        PitDecision decision = new PitDecision(tire, Math.round(fuel));
        strategy.queue(decision);
        connection.send(Protocol.ack("已排队：" + decision.describe() + "，下一圈执行"));
    }

    // ------------------------------------------------------------------
    // 比赛推进
    // ------------------------------------------------------------------

    private void maybeStart() {
        if (!Protocol.PHASE_WAITING.equals(phase)) {
            return;
        }
        if (ready.size() < minPlayers) {
            return;
        }
        startRace();
    }

    private void startRace() {
        long seed = System.currentTimeMillis();
        lineup = RaceSetup.build(data, track, laps, seed, false, false);

        List<Car> cars = lineup.config().cars();
        // 两个座位挑不同车队的车，避免两个人开成一辆车的颜色
        int[] pick = {0, Math.min(2, cars.size() - 1)};
        for (int seat = 0; seat < minPlayers; seat++) {
            Car car = cars.get(pick[seat % pick.length]);
            RemoteStrategy strategy = new RemoteStrategy(car.number());
            lineup.strategies().put(car, strategy);
            players.put(seat, strategy);
            seatCar.put(seat, car.number());
        }

        simulator = new RaceSimulator(lineup.config(), lineup.strategies());
        phase = Protocol.PHASE_RUNNING;
        lastEventIndex = 0;

        broadcast(Protocol.line(JsonWriter.obj()
                .put("type", Protocol.START)
                .put("track", track.name())
                .put("totalLaps", laps)
                .put("seatCars", JsonWriter.obj()
                        .put("0", seatCar.getOrDefault(0, -1))
                        .put("1", seatCar.getOrDefault(1, -1)))));
        broadcast(stateMessage());

        loop = scheduler.scheduleAtFixedRate(this::tick, lapMillis, lapMillis, TimeUnit.MILLISECONDS);
        AppLog.info("房间 " + id + "：开局，赛道 " + track.name() + "，共 " + laps + " 圈");
    }

    private synchronized void tick() {
        if (!Protocol.PHASE_RUNNING.equals(phase) || simulator == null) {
            return;
        }
        try {
            simulator.step();
            broadcastEvents();
            broadcast(stateMessage());
            if (simulator.isFinished()) {
                finishRace();
            }
        } catch (RuntimeException e) {
            AppLog.error("房间 " + id + " 推进异常", e);
        }
    }

    private void broadcastEvents() {
        List<RaceEvent> events = simulator.events();
        for (int i = lastEventIndex; i < events.size(); i++) {
            RaceEvent event = events.get(i);
            if (event.type() == RaceEvent.Type.PIT_STOP || event.type() == RaceEvent.Type.SAFETY_CAR
                    || event.type() == RaceEvent.Type.WEATHER_CHANGE
                    || event.type() == RaceEvent.Type.FUEL_EMPTY
                    || event.type() == RaceEvent.Type.MECHANICAL_FAILURE) {
                broadcast(Protocol.line(JsonWriter.obj()
                        .put("type", Protocol.EVENT)
                        .put("lap", event.lap())
                        .put("kind", event.type().name())
                        .put("message", event.message())));
            }
        }
        lastEventIndex = events.size();
    }

    private void finishRace() {
        phase = Protocol.PHASE_FINISHED;
        if (loop != null) {
            loop.cancel(false);
        }
        RaceResult result = simulator.result();

        JsonWriter.Arr standings = JsonWriter.arr();
        for (RaceResult.Entry entry : result.standings()) {
            standings.add(JsonWriter.obj()
                    .put("pos", entry.position())
                    .put("number", entry.car().number())
                    .put("driver", entry.car().driverName())
                    .put("team", entry.car().team().shortName())
                    .put("total", entry.totalTime())
                    .put("gap", entry.gapToWinner())
                    .put("retired", entry.retired()));
        }
        broadcast(Protocol.line(JsonWriter.obj()
                .put("type", Protocol.RESULT)
                .put("phase", phase)
                .put("standings", standings)));

        // 收尾再推一条状态，客户端可以据此把界面切到"已结束"
        broadcast(stateMessage());

        if (lineup != null) {
            lineup.close();
        }
        AppLog.info("房间 " + id + "：比赛结束");
    }

    // ------------------------------------------------------------------

    private String stateMessage() {
        JsonWriter.Arr cars = JsonWriter.arr();
        if (simulator != null) {
            List<Car> standings = simulator.liveStandings();
            int position = 1;
            for (Car car : standings) {
                cars.add(JsonWriter.obj()
                        .put("pos", position++)
                        .put("number", car.number())
                        .put("driver", car.driverName())
                        .put("team", car.team().shortName())
                        .put("color", car.team().colorHex())
                        .put("tire", car.compound().id())
                        .put("wear", car.tireWear())
                        .put("fuel", car.fuelKg())
                        .put("lastLap", car.lastLapTime())
                        .put("total", car.totalTime())
                        .put("pits", car.pitStops())
                        .put("retired", car.isRetired()));
            }
        }
        return Protocol.line(JsonWriter.obj()
                .put("type", Protocol.STATE)
                .put("room", id)
                .put("phase", phase)
                .put("lap", simulator == null ? 0 : simulator.completedLaps())
                .put("total", laps)
                .put("weather", simulator == null ? "DRY" : simulator.currentWeather().name())
                .put("safetyCar", simulator == null ? 0 : simulator.safetyCarRemaining())
                .put("cars", cars));
    }

    private void broadcastPeer() {
        JsonWriter.Arr list = JsonWriter.arr();
        for (Map.Entry<Integer, ServerConnection> entry : seats.entrySet()) {
            list.add(JsonWriter.obj()
                    .put("seat", entry.getKey())
                    .put("name", entry.getValue().playerName())
                    .put("ready", ready.contains(entry.getKey()))
                    .put("car", seatCar.getOrDefault(entry.getKey(), -1)));
        }
        broadcast(Protocol.line(JsonWriter.obj()
                .put("type", Protocol.PEER)
                .put("players", list)
                .put("message", "房间成员已更新")));
    }

    private void broadcast(String payload) {
        List<ServerConnection> targets = new ArrayList<>(seats.values());
        for (ServerConnection connection : targets) {
            connection.send(payload);
        }
    }

    /** 关服时清理。 */
    public synchronized void shutdown() {
        if (loop != null) {
            loop.cancel(false);
        }
        if (lineup != null) {
            lineup.close();
        }
        for (ServerConnection connection : seats.values()) {
            connection.close();
        }
        seats.clear();
    }
}
