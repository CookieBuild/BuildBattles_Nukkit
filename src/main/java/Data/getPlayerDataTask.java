package main.java.Data;

import cn.nukkit.Server;
import cn.nukkit.scheduler.AsyncTask;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;
import main.java.Main;
import main.java.cbPlayer;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by Guillaume Claverie on 14/01/2017.
 * ▒█▀▀█ █▀▀█ █▀▀█ █░█ ░▀░ █▀▀ 　 ▒█▀▀█ █░░█ ░▀░ █░░ █▀▀▄
 * ▒█░░░ █░░█ █░░█ █▀▄ ▀█▀ █▀▀ 　 ▒█▀▀▄ █░░█ ▀█▀ █░░ █░░█
 * ▒█▄▄█ ▀▀▀▀ ▀▀▀▀ ▀░▀ ▀▀▀ ▀▀▀ 　 ▒█▄▄█ ░▀▀▀ ▀▀▀ ▀▀▀ ▀▀▀░
 */
public class getPlayerDataTask extends AsyncTask {

    String address;
    String databaseName;
    String username;
    String password;
    private String playerName;

    public getPlayerDataTask(String playerName, String address, String databaseName, String username, String password) {
        this.playerName = playerName;
        this.address = address;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
    }

    @Override
    public void onRun() {
        PlayerData playerData;
        try {
            // Setup the connection with the DBConnection connect = (Connection) DriverManager
            Connection connect = (Connection) DriverManager
                    .getConnection("jdbc:mysql://" + address + "/" + databaseName + "?"
                            + "user=" + username + "&password=" + password + "&useSSL=false");

            PreparedStatement preparedStatement3 = (PreparedStatement) connect
                    .prepareStatement("select * from data where playerName = ?");
            preparedStatement3.setString(1, this.playerName);
            // "myuser, webpage, datum, summery, COMMENTS from feedback.comments");
            // Parameters start with 1
            ResultSet resultSet = preparedStatement3.executeQuery();
            if (resultSet.next()) {
                playerData = new PlayerData(playerName, resultSet.getInt("playerCoins"), resultSet.getString("playerRank"), resultSet.getInt("isYoutuber"), resultSet.getInt("miniBattleVip"), resultSet.getInt("buildBattleVip"), resultSet.getInt("turfWarsVip"), resultSet.getInt("allVip"), resultSet.getString("playerKit"), resultSet.getInt("playerMicroKit"), resultSet.getInt("hasKitArcher"), resultSet.getInt("hasKitMiner"), resultSet.getInt("hasKitClimber"), resultSet.getInt("hasKitArrow"), resultSet.getInt("hasKitKnockback"), resultSet.getInt("hasKitMobility"), resultSet.getInt("kitpvpVip"), resultSet.getInt("domivip"), resultSet.getInt("hasKitTnt"), resultSet.getInt("hasKitAlchemist"), resultSet.getInt("hasKitEnderman"));

            } else {
                PreparedStatement preparedStatement2 = (PreparedStatement) connect
                        .prepareStatement("insert into data (playerName) VALUES (?)");
                preparedStatement2.setString(1, this.playerName);
                preparedStatement2.execute();

                preparedStatement2.close();

                playerData = new PlayerData(playerName, 0, null, 0, 0, 0, 0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

            }

            resultSet.beforeFirst();

            resultSet.close();
            preparedStatement3.close();
            //connect.close();


            Object[] result = new Object[2];
            result[0] = this.playerName;
            result[1] = playerData;

            this.setResult(result);


        } catch (Exception e) {
            try {
                throw e;
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
        }
    }

    @Override
    public void onCompletion(Server server) {
        Object[] result = (Object[]) this.getResult();
        String playerName = (String) result[0];
        cbPlayer player = (cbPlayer) server.getPlayer(playerName);
        Main plugin = (Main) server.getPluginManager().getPlugin("BuildBattles");

        PlayerData playerData = (PlayerData) result[1];

        player.isFetchingData = false;
        player.storedPlayerData = playerData;


        player.coins = playerData.coins;
        if (playerData.microBattlesVip == 1) {
            player.vip = 1;
        }
        if (playerData.isYoutuber == 1) {
            player.vip = 0;
        }
        if (playerData.ultraVip == 1) {
            player.vip = 3;
        }

        player.loginComplete();


    }
}
