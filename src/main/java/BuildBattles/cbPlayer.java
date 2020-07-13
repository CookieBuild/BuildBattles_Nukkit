package BuildBattles;

import BuildBattles.Data.PlayerData;
import cn.nukkit.Player;
import cn.nukkit.item.ItemCompass;
import cn.nukkit.item.ItemCookie;
import cn.nukkit.level.particle.FloatingTextParticle;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.ScriptCustomEventPacket;
import cn.nukkit.network.protocol.TransferPacket;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.TextFormat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;


/**
 * Created by Guillaume on 17/01/2017.
 * ▒█▀▀█ █▀▀█ █▀▀█ █░█ ░▀░ █▀▀ 　 ▒█▀▀█ █░░█ ░▀░ █░░ █▀▀▄
 * ▒█░░░ █░░█ █░░█ █▀▄ ▀█▀ █▀▀ 　 ▒█▀▀▄ █░░█ ▀█▀ █░░ █░░█
 * ▒█▄▄█ ▀▀▀▀ ▀▀▀▀ ▀░▀ ▀▀▀ ▀▀▀ 　 ▒█▄▄█ ░▀▀▀ ▀▀▀ ▀▀▀ ▀▀▀░
 */
public class cbPlayer extends Player {


    public boolean isInGame = false;
    public int vip = -1;
    public int playerChat = 0, commandTimer = 0;
    public cbPlayer lastHitPlayer = null;
    private String hash = "undefined";
    private boolean isAuthenticatedCb = false;
    private boolean isRegistered = false;
    private String dataBaseIp = "";
    private TaskHandler task;
    public boolean isFetchingLoginData = true;
    public boolean isFetchingData = true;
    public int time = 0;
    public int coins = 0;
    public int lastFlyY = 128;
    public int Warning = 0;
    public int gameNumber;
    public int lastVote = 0;
    public int plot;
    public FloatingTextParticle statParticle;

    //public Party party, pendingInvite;
    public PlayerData storedPlayerData;

    public String serverName = "\u2584\u2580\u2580\u2584\u2500\u2584\u2580\u2580\u2584\u2500\u2584\u2580\u2580\u2584\u2500\u2588\u2500\u2584\u2580\u2500\u2588\u2500\u2588\u2580\u2580\u2500\u2500\u2500\u2588\u2580\u2584\u2500\u2588\u2500\u2500\u2588\u2500\u2588\u2500\u2588\u2500\u2500\u2500\u2588\u2580\u2584\n\u2588\u2500\u2500\u2584\u2500\u2588\u2500\u2500\u2588\u2500\u2588\u2500\u2500\u2588\u2500\u2588\u2580\u2584\u2500\u2500\u2588\u2500\u2588\u2580\u2580\u2500\u2500\u2500\u2588\u2580\u2588\u2500\u2588\u2500\u2500\u2588\u2500\u2588\u2500\u2588\u2500\u2500\u2500\u2588\u2500\u2588\n─▀▀───▀▀───▀▀──▀──▀─▀─▀▀▀───▀▀───▀▀──▀─▀▀▀─▀▀─";
    public String serverType = "█▄─▄█─█─▄▀▀▄─█▀▄─▄▀▀▄─█▀▄─▄▀▄─▀█▀─▀█▀─█───█▀▀─▄▀▀\n█─▀─█─█─█──▄─██▀─█──█─█▀█─█▀█──█───█──█───█▀▀──▀▄\n▀───▀─▀──▀▀──▀─▀──▀▀──▀▀──▀─▀──▀───▀──▀▀▀─▀▀▀─▀▀─";

    public cbPlayer(SourceInterface interfaz, Long clientID, String ip, int port) {
        super(interfaz, clientID, ip, port);
    }

    public void regenerateDisplayName() {
        String name = this.getName();
        this.setGamemode(2);

        if (vip >= 0) {
            switch (vip) {
                case 0:
                    name = TextFormat.AQUA + "[Youtube] " + name;
                    break;
                case 1:
                    name = TextFormat.GREEN + "[" + TextFormat.YELLOW + "VIP" + TextFormat.GREEN + "] " + name;
                    break;
                case 3:
                    name = TextFormat.GREEN + "[" + TextFormat.AQUA + "Ultra" + TextFormat.YELLOW + "VIP" + TextFormat.GREEN + "] " + name;
                    break;
            }
            this.setAllowFlight(true);
        }

        this.checkMovement = false;
        this.setGamemode(2);
        //this.setNameTag(name);

        //this.setDisplayName(name);
        this.getInventory().clearAll();
        this.getInventory().addItem(new ItemCookie());
        this.getInventory().addItem(new ItemCompass());

    }

    public boolean proxyTransfer(String destination) {
        ScriptCustomEventPacket pk = new ScriptCustomEventPacket();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream a = new DataOutputStream(out);
        try {
            a.writeUTF("Connect");
            a.writeUTF(destination);
            pk.eventName = "bungeecord:main";
            pk.eventData = out.toByteArray();
            this.dataPacket(pk);
        } catch (Exception e) {
            this.getServer().getLogger().warning("Error while transferring ( PLAYER: " + this.getName() + " | DEST: " + destination + " )");
            this.getServer().getLogger().logException(e);
            return false;
        }
        return true;
    }

    //@Override
    public void transfer(String ip, int port) {
        TransferPacket pk = new TransferPacket();
        pk.address = ip;
        pk.port = port;
        this.dataPacket(pk);
    }

    public void loginComplete() {
        this.getServer().getScheduler().scheduleDelayedTask(() -> {
            this.sendMessage(TextFormat.GREEN + "Welcome to " + TextFormat.AQUA + "Cookie " + TextFormat.YELLOW + "Build" + TextFormat.DARK_PURPLE + " BuildBattles!");

        }, 80);
    }

    public void setAuthenticated(boolean isAuthenticated) {
        this.isAuthenticatedCb = isAuthenticated;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public boolean isAuthenticated() {
        return this.isAuthenticatedCb;
    }

    public String getHash() {
        return this.hash;
    }

    public void setRegistered(Boolean isRegistered) {
        this.isRegistered = isRegistered;
    }

    public Boolean isRegistered() {
        return this.isRegistered;
    }

    public void setDataBaseIp(String s) {
        this.dataBaseIp = s;
    }

    public void setTaskId(TaskHandler task) {
        this.task = task;
    }

    public TaskHandler getTask() {
        return task;
    }

    public String getDataBaseIp() {
        return this.dataBaseIp;
    }

}
