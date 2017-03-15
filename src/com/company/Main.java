package com.company;
import com.cctv.*;
import org.sqlite.*;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import java.sql.*;

public class Main {

    public static void main(String[] args) {
        ArrayList<Integer> offers = new ArrayList<Integer>();
        offers.add(30);
        offers.add(75);
        offers.add(20);
        offers.add(55);
        offers.add(10);
        offers.add(60);
        computeBasePrice(offers);

    }
    public static void computeBasePrice(List<Integer> offers){
        //将报价升序排序
        Collections.sort(offers);
        for (Integer i: offers
             ) {
            System.out.println(i);
        }
    }
    public static void readSQLite(){
        try
        {
            //连接SQLite的JDBC
            Class.forName("org.sqlite.JDBC");
            //建立一个数据库连接
            Connection conn = DriverManager.getConnection("jdbc:sqlite:E:/Files/zhaobiao/中国石油天然气股份有限公司河北沧州销售分公司 第二十加油站原址改建项目.zbf");

            Statement stat = conn.createStatement();
            /*
            stat.executeUpdate( "create table table1(name varchar(64), age int);" );//创建一个表，两列
            stat.executeUpdate( "insert into table1 values('aa',12);" ); //插入数据
            stat.executeUpdate( "insert into table1 values('bb',13);" );
            stat.executeUpdate( "insert into table1 values('cc',20);" );
            */
            ResultSet rs = stat.executeQuery("select nodecaption from EvaluationFlow;"); //查询数据

            while (rs.next()) { //将查询到的数据打印出来

                System.out.printf("name = %s %n", rs.getString("nodecaption")); //列属性一

            }
            rs.close();
            conn.close(); //结束数据库的连接
        }
        catch( Exception e )
        {
            e.printStackTrace ( );
        }
    }
    public static void TestMap(){

    }
}


