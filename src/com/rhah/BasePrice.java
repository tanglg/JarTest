package com.rhah;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

public class BasePrice {
    /***
     * 获取招标文件中去除最高报价和最低报价数量
     * @param dbpath 招标文件全路径（默认招标文件中只有一个报价得分节点）
     * @param bidderCount 投标人数量
     * @return 去除最高报价和最低报价数量
     */
    public static RemoveBidderResult getRemvoeBidderCount(String dbpath,int bidderCount)
    {
        List<RemoveBidderParam> params = getRemoveBidderParam(dbpath);
        int positon = -1;
        for(int i=0;i<params.size();i++)
        {
            if(bidderCount >= params.get(i).ThresholdValue &&  params.get(i).ThresholdValue>0)
            {
                positon = i;
            }
        }
        RemoveBidderResult param = new RemoveBidderResult();
        if(positon==-1)
        {
            param.RemoveHeightCount=0;
            param.RemoveLowCount =0;
        }
        else
        {
            param.RemoveHeightCount=params.get(positon).HeightCount;
            param.RemoveLowCount =params.get(positon).LowCount;
        }
        return param;
    }
    /**
     * 从SQLite中读取单个数值
     * @param dbPath 招标文件SQLite全路径
     * @return SQL语句执行结果
     */
    private static List<RemoveBidderParam> getRemoveBidderParam(String dbPath)  {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbPath));
            Statement stat = conn.createStatement();
            ResultSet rs = stat.executeQuery("SELECT ThresholdValue,HeightCount,LowCount from AverageBasePriceMethod ORDER BY SortOrder");
            List<RemoveBidderParam> params  = new ArrayList<RemoveBidderParam>();
            while( rs.next()) {
                RemoveBidderParam param = new RemoveBidderParam();
                param.ThresholdValue = rs.getInt("ThresholdValue");
                param.HeightCount = rs.getInt("HeightCount");
                param.LowCount = rs.getInt("LowCount");
                params.add(param);
            }
            rs.close();
            conn.close();
            return params;
        }
        catch (Exception ex)  {
            throw new RuntimeException(ex.getMessage() ,ex );
        }
    }
}
class RemoveBidderResult{
    /***
     * 需要移除的最高报价数量
     */
    public int RemoveHeightCount;
    /***
     * 需要移除的最低报价数量
     */
    public int RemoveLowCount;
}

/***
 * 规则类，每一个对象表示一个阈值和阈值对应的去掉最高报价和最低报价个数
 */
class  RemoveBidderParam{
    /***
     * 阈值
     */
    public int ThresholdValue;
    /***
     * 投标人数量达到阈值时去掉的最高报价数量
     */
    public int HeightCount;
    /***
     * 投标人数量达到阈值时去掉的最低报价数量
     */
    public int LowCount;
}
