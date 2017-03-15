package com.company;
import java.util.*;
import java.sql.*;

/**
 * 报价得分计算类
 */
public class OfferScore{
    /*
    存储基准价
     */
    private Float basePrice;
    /*
    存储每个投标人的报价得分
     */
    private Map<String,Integer> bidderOfferScore;
    /**
     * 获取基准价
     * @return 当前标段的基准价
     */
    public Float getBasePrice(){
        return basePrice ;
    }

    /**
     * 获取投标人报价得分
     * @return 投标人列表，Key=投标人编码，value=投保人报价得分
     */
    public Map<String,Integer> getBidderOfferScore(){
        return  null;
    }

    /**
     * 初始化一个报价得分计算对象
     * @param bidders 投标人列表，Key=投标人编码，value=投保人报价
     * @param ZbfFullPath 招标文件全路径（zbf文件的绝对路径）
     */
    public OfferScore(Map<String,Integer> bidders,String ZbfFullPath){

    }

    /**
     * 获取经过升序排序的投标人报价数组
     * @param bidders 投标人报价数据，Key=投标人编码，value=投保人报价
     * @return 升序排序的投标人报价数组
     */
    private List<Double> getOriginalSortedOffer(Map<String,Double> bidders){
        ArrayList<Double> list = new ArrayList<Double>(bidders.size());
        for (Double offer : bidders.values()
                ) {
            list.add(offer);
            Collections.sort(list);
        }
        return list;
    }
    /**
     * 计算基准价
     * @return
     */
    private Double computeBasePrice(List<Double> offerList,String zbfFullPath){
        String formula = getBasePriceFormula(zbfFullPath);
        //替换最低价
        formula  = formula.replace("ZuiDiJia",offerList.get(0).toString());
        //替换所有报价平均值
        Double everageOffer = getSummaryForList(offerList)/offerList.size();
        formula = formula.replace("SuoYouPingJunZhi",everageOffer.toString());
        //替换部分报价的平均值
    }

    /***
     * 计算一个Double类型数组所有元素和和
     * @param list Double类型的数组
     * @return 所有数组元素的和
     */
    private Double getSummaryForList(List<Double> list){
        Double result=0.0;
        for (Double item :list
                ) {
            result = result+item ;
        }
        return result;
    }

    /**
     * 获取基准价计算公式
     * @param zbfFullPath 招标文件全路径（zbf文件的绝对路径）
     * @return 基准价计算公式
     */
    private String getBasePriceFormula(String zbfFullPath){
        return getSingleValueFromSqlite(zbfFullPath,"select Formula from BasePriceComputeMethod");
    }

    /**
     * 获取报价得分的计算公式
     * @param zbfFullPath 招标文件全路径（zbf文件的绝对路径）
     * @return 报价得分计算公式
     */
    private String getOfferScoreFormula(String zbfFullPath){
        return getSingleValueFromSqlite(zbfFullPath,"select Formula from OfferScoreComputeMethod");
    }

    /**
     * 从SQLite中读取单个数值
     * @param dbFullPath SQLite全路径
     * @param sql SQL语句
     * @return SQL语句执行结果
     */
    private String getSingleValueFromSqlite(String dbFullPath,String sql)  {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbFullPath));
            Statement stat = conn.createStatement();
            ResultSet rs = stat.executeQuery(sql);
            rs.next();
            String value = rs.getString(0);
            rs.close();
            conn.close();

            return value;
        }
        catch (Exception ex)  {
            throw new RuntimeException(ex.getMessage() ,ex );
        }
    }
}
