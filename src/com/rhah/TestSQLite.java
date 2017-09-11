package com.rhah;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Created by tanglg on 2017/7/10.
 */
public class TestSQLite {
    public String getSingleValueFromSqlite()  {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = DriverManager.getConnection(String.format("jdbc:sqlite:%s", "C:\\Users\\tanglg\\Desktop\\999\\999.tbf"));
            Statement stat = conn.createStatement();
            ResultSet rs = stat.executeQuery("SELECT * FROM Parameters");
            rs.next();
            String value = rs.getString(1);
            rs.close();
            conn.close();
            return value;
        }
        catch (Exception ex)  {
            throw new RuntimeException(ex.getMessage() ,ex );
        }
    }
}
