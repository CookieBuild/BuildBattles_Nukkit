package BuildBattles.Data;

import cn.nukkit.scheduler.AsyncTask;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Created by Guillaume Claverie on 14/01/2017.
 */
public class dataBaseQuery extends AsyncTask {

    String query;
    String address;
    String databaseName;
    String username;
    String password;

    public dataBaseQuery(String query, String address, String databaseName, String username, String password) {
        this.query = query;
        this.address = address;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
    }


    @Override
    public void onRun() {
        // Setup the connection with the DB
        try {
            Connection connect = (Connection) DriverManager
                    .getConnection("jdbc:mysql://" + address + "/" + databaseName + "?"
                            + "user=" + username + "&password=" + password + "&useSSL=false");


            PreparedStatement preparedStatement = (PreparedStatement) connect.prepareStatement(this.query);
            preparedStatement.executeUpdate();
            preparedStatement.close();
            connect.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
