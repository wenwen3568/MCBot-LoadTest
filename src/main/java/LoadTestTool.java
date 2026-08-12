import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.ServerPinger;
import com.github.steveice10.mc.protocol.data.game.entity.player.InteractAction;
import com.github.steveice10.mc.protocol.data.status.ServerStatusInfo;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundAddPlayerPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundPlayerInfoPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRemoveEntitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.PacketReceivedEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.tcp.TcpClientSession;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTestTool {

    private static final String LOGO =
        "\n" +
        "█████████████████████████████████████████████████████████████████████████\n" +
        "█                                                                       █\n" +
        "█   ███╗   ███╗ ██████╗    ██████╗  ██████╗ ████████╗                  █\n" +
        "█   ████╗ ████║██╔════╝    ██╔══██╗██╔═══██╗╚══██╔══╝                  █\n" +
        "█   ██╔████╔██║██║         ██████╔╝██║   ██║   ██║                     █\n" +
        "█   ██║╚██╔╝██║██║         ██╔══██╗██║   ██║   ██║                     █\n" +
        "█   ██║ ╚═╝ ██║╚██████╗    ██████╔╝╚██████╔╝   ██║                     █\n" +
        "█   ╚═╝     ╚═╝ ╚═════╝    ╚═════╝  ╚═════╝    ╚═╝                     █\n" +
        "█                                                                       █\n" +
        "█          Minecraft 压测工具 - 支持全版本 · 假人大军                █\n" +
        "█                                                                       █\n" +
        "█████████████████████████████████████████████████████████████████████████\n";

    private static final Map<UUID, PlayerInfo> onlinePlayers = new ConcurrentHashMap<>();
    private static final Map<String, BotController> bots = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService globalScheduler = Executors.newScheduledThreadPool(10);
    private static volatile boolean running = true;

    public static void main(String[] args) {
        System.out.println(LOGO);
        Scanner scanner = new Scanner(System.in);

        System.out.print("请输入服务器 IP (格式: IP:端口，默认25565): ");
        String input = scanner.nextLine().trim();
        if (!input.contains(":")) input += ":25565";
        String[] parts = input.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        System.out.println("正在获取服务器信息...");
        ServerStatusInfo status;
        try {
            ServerPinger pinger = new ServerPinger();
            pinger.setTimeout(5000);
            status = pinger.ping(host, port);
        } catch (Exception e) {
            System.err.println("无法连接服务器: " + e.getMessage());
            return;
        }

        String versionName = status.getVersionInfo().getVersionName();
        int protocolVersion = status.getVersionInfo().getProtocolVersion();
        int onlinePlayersCount = status.getPlayerInfo().getOnlinePlayers();
        int maxPlayers = status.getPlayerInfo().getMaxPlayers();
        System.out.println("服务器版本: " + versionName + " (协议 " + protocolVersion + ")");
        System.out.println("当前在线: " + onlinePlayersCount + "/" + maxPlayers);

        System.out.print("请输入每个假人上线的间隔(秒): ");
        int intervalSec = Integer.parseInt(scanner.nextLine().trim());
        System.out.print("请输入假人总数: ");
        int totalBots = Integer.parseInt(scanner.nextLine().trim());

        System.out.println("\n按回车开始加入假人...");
        scanner.nextLine();

        AtomicInteger joined = new AtomicInteger(0);
        ScheduledExecutorService joinScheduler = Executors.newScheduledThreadPool(Math.min(totalBots, 50));

        for (int i = 0; i < totalBots; i++) {
            int delay = i * intervalSec;
            final int proto = protocolVersion;
            joinScheduler.schedule(() -> {
                String name = RandomString.generate(6);
                try {
                    BotController bot = new BotController(name, host, port, proto);
                    bots.put(name, bot);
                    bot.connect();
                    int cur = joined.incrementAndGet();
                    System.out.println("[" + cur + "/" + totalBots + "] 假人 " + name + " 已加入");
                } catch (Exception e) {
                    System.err.println("假人 " + name + " 失败: " + e.getMessage());
                }
            }, delay, TimeUnit.SECONDS);
        }

        while (joined.get() < totalBots) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        joinScheduler.shutdown();
        System.out.println("\n所有假人已上线！进入命令模式 (输入 help 查看帮助)");

        commandLoop(scanner);

        for (BotController bot : bots.values()) {
            bot.disconnect();
        }
        globalScheduler.shutdownNow();
        System.exit(0);
    }

    private static void commandLoop(Scanner scanner) {
        while (running) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] cmdParts = line.split("\\s+");
            String cmd = cmdParts[0].toLowerCase();

            try {
                switch (cmd) {
                    case "help":
                        System.out.println("可用命令:");
                        System.out.println("  list                - 列出所有假人");
                        System.out.println("  players             - 列出当前在线玩家");
                        System.out.println("  say <bot> <消息>    - 让假人发送聊天消息");
                        System.out.println("  attack <bot> <玩家> - 让假人攻击指定玩家");
                        System.out.println("  stop <bot>          - 停止假人的攻击");
                        System.out.println("  exit                - 关闭所有假人并退出");
                        break;

                    case "list":
                        if (bots.isEmpty()) {
                            System.out.println("没有假人在线");
                        } else {
                            bots.forEach((name, bot) ->
                                System.out.println(name + (bot.isAttacking ? " (攻击中)" : "")));
                        }
                        break;

                    case "players":
                        if (onlinePlayers.isEmpty()) {
                            System.out.println("没有在线玩家信息（可能尚未收到服务器数据）");
                        } else {
                            System.out.println("在线玩家：");
                            onlinePlayers.values().forEach(p ->
                                System.out.println("  " + p.name + " (UUID: " + p.uuid + ")"));
                        }
                        break;

                    case "say":
                        if (cmdParts.length < 3) {
                            System.out.println("用法: say <bot名> <消息>");
                            break;
                        }
                        BotController sayBot = bots.get(cmdParts[1]);
                        if (sayBot == null) {
                            System.out.println("未找到假人: " + cmdParts[1]);
                            break;
                        }
                        String message = String.join(" ",
                            Arrays.copyOfRange(cmdParts, 2, cmdParts.length));
                        sayBot.sendChat(message);
                        System.out.println("消息已发送");
                        break;

                    case "attack":
                        if (cmdParts.length < 3) {
                            System.out.println("用法: attack <bot名> <玩家名>");
                            break;
                        }
                        BotController attackBot = bots.get(cmdParts[1]);
                        if (attackBot == null) {
                            System.out.println("未找到假人: " + cmdParts[1]);
                            break;
                        }
                        String target = cmdParts[2];
                        attackBot.startAttacking(target);
                        System.out.println("假人 " + cmdParts[1] + " 开始攻击 " + target);
                        break;

                    case "stop":
                        if (cmdParts.length < 2) {
                            System.out.println("用法: stop <bot名>");
                            break;
                        }
                        BotController stopBot = bots.get(cmdParts[1]);
                        if (stopBot == null) {
                            System.out.println("未找到假人: " + cmdParts[1]);
                            break;
                        }
                        stopBot.stopAttacking();
                        System.out.println("已停止攻击");
                        break;

                    case "exit":
                        running = false;
                        System.out.println("正在关闭所有假人...");
                        return;

                    default:
                        System.out.println("未知命令，输入 help 查看帮助");
                }
            } catch (Exception e) {
                System.err.println("执行命令出错: " + e.getMessage());
            }
        }
    }

    static class PlayerInfo {
        final UUID uuid;
        final String name;
        int entityId = -1;

        PlayerInfo(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    static class BotController {
        final String name;
        final Session session;
        volatile boolean isAttacking = false;
        private ScheduledFuture<?> attackTask;
        private String attackTarget;

        BotController(String name, String host, int port, int protocolVersion) {
            this.name = name;
            MinecraftProtocol protocol = new MinecraftProtocol(name, protocolVersion);
            this.session = new TcpClientSession(host, port, protocol);
            session.addListener(new SessionAdapter() {
                @Override
                public void packetReceived(PacketReceivedEvent event) {
                    handlePacket(event);
                }
            });
        }

        void connect() {
            session.connect();
        }

        void disconnect() {
            session.disconnect();
        }

        void sendChat(String msg) {
            session.send(new ServerboundChatCommandPacket("say " + msg));
        }

        void startAttacking(String playerName) {
            if (isAttacking) stopAttacking();
            attackTarget = playerName;
            isAttacking = true;
            attackTask = globalScheduler.scheduleAtFixedRate(() -> {
                if (!isAttacking) return;
                int targetId = -1;
                for (PlayerInfo info : onlinePlayers.values()) {
                    if (info.name.equalsIgnoreCase(attackTarget)) {
                        targetId = info.entityId;
                        break;
                    }
                }
                if (targetId > 0) {
                    session.send(new ServerboundInteractPacket(targetId, InteractAction.ATTACK, false));
                }
            }, 0, 500, TimeUnit.MILLISECONDS);
        }

        void stopAttacking() {
            isAttacking = false;
            if (attackTask != null) {
                attackTask.cancel(false);
                attackTask = null;
            }
        }

        private void handlePacket(PacketReceivedEvent event) {
            if (event.getPacket() instanceof ClientboundKeepAlivePacket keepAlive) {
                session.send(new ServerboundKeepAlivePacket(keepAlive.getPingId()));
            } else if (event.getPacket() instanceof ClientboundPlayerInfoPacket infoPacket) {
                for (com.github.steveice10.mc.protocol.data.game.PlayerInfo pi : infoPacket.getEntries()) {
                    if (infoPacket.getAction() == com.github.steveice10.mc.protocol.data.game.setting.PlayerInfoAction.ADD_PLAYER) {
                        onlinePlayers.putIfAbsent(pi.getProfile().getId(),
                            new PlayerInfo(pi.getProfile().getId(), pi.getProfile().getName()));
                    } else if (infoPacket.getAction() == com.github.steveice10.mc.protocol.data.game.setting.PlayerInfoAction.REMOVE_PLAYER) {
                        onlinePlayers.remove(pi.getProfile().getId());
                    }
                }
            } else if (event.getPacket() instanceof ClientboundAddPlayerPacket addPlayer) {
                UUID uuid = addPlayer.getUuid();
                PlayerInfo info = onlinePlayers.get(uuid);
                if (info != null) {
                    info.entityId = addPlayer.getEntityId();
                }
            } else if (event.getPacket() instanceof ClientboundRemoveEntitiesPacket remove) {
                for (int id : remove.getEntityIds()) {
                    onlinePlayers.values().removeIf(p -> p.entityId == id);
                }
            }
        }
    }

    static class RandomString {
        private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
        private static final Random random = new Random();

        static String generate(int length) {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
            }
            return sb.toString();
        }
    }
}
