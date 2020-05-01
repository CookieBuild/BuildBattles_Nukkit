import cn.nukkit.event.Listener;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;

public class Main extends PluginBase implements Listener {

    public BuildBattleGame game;

    public Vector3[] pedestals;

    public void teleportToGame(cbPlayer player){
        this.game.plotOwners[this.game.plotOwners.length] = player.getName();
    }
}
