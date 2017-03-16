package com.company;
import java.util.*;
import java.sql.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * 报价得分计算类
 */
public class OfferScore{
    /*
    存储基准价
     */
    private Double basePrice;
    /*
    存储每个投标人的报价得分
     */
    private Map<String,Double> bidderOfferScore;
    /**
     * 获取基准价
     * @return 当前标段的基准价
     */
    public Double getBasePrice(){
        return basePrice ;
    }

    /**
     * 获取投标人报价得分
     * @return 投标人列表，Key=投标人编码，value=投保人报价得分
     */
    public Map<String,Double> getBidderOfferScore(){
        return  null;
    }

    /**
     * 初始化一个报价得分计算对象
     * @param bidders 投标人列表，Key=投标人编码，value=投保人报价
     * @param ZbfFullPath 招标文件全路径（zbf文件的绝对路径）
     */
    public OfferScore(Map<String,Double> bidders,String ZbfFullPath) throws ScriptException {
        basePrice = computeBasePrice(getOriginalSortedOffer(bidders),ZbfFullPath);
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

    /***
     * 计算基准价
     * @param offerList 升序排列的投标人报价数组
     * @param zbfFullPath 招标文件全路径
     * @return 基准价
     */
    private Double computeBasePrice(List<Double> offerList,String zbfFullPath) throws ScriptException {

        String formula = getBasePriceFormula(zbfFullPath);

        if(formula.contains("ZuiDiJia")) {
            //替换最低价
            formula = formula.replace("ZuiDiJia", offerList.get(0).toString());
        }
        if(formula.contains("SuoYouZongHe")) {
            //替换最低价
            formula = formula.replace("SuoYouZongHe", String.valueOf(getSummaryForList(offerList)));
        }
        if(formula.contains("SuoYouPingJunZhi")) {
            //替换所有报价平均值
            Double averageOffer = getSummaryForList(offerList) / offerList.size();
            formula = formula.replace("SuoYouPingJunZhi",averageOffer.toString());
        }
        if(formula.contains("YouXiaoBaoJiaShuLiang")) {
            //有效报价的家数
            formula = formula.replace("YouXiaoBaoJiaShuLiang",String.valueOf(offerList.size()));
        }
        if(formula.contains("MaxSummary")){
            //替换去除N个最大值
            //提取出去除最大值的数量(个数)
            Integer maxNumber = Integer.parseInt( getValueByRegex(formula,"MaxSummary\\((?<max>\\d+)\\)","max"));
            //计算要去除的最大值的合
            Double maxNumberSummary = getSummaryForList(offerList.subList(offerList.size()-maxNumber,offerList.size()));
            formula = formula.replaceAll("MaxSummary\\(\\d+\\)", String.valueOf(maxNumberSummary));
        }
        if(formula.contains("MinSummary")){
            //替换去除N个最小值
            //提取出去除最小值的数量(个数)
            Integer minNumber = Integer.parseInt( getValueByRegex(formula,"MinSummary\\((?<min>\\d+)\\)","min"));
            //计算要去除的最小值的合
            Double minNumberSummary = getSummaryForList(offerList.subList(0,minNumber));
            formula = formula.replaceAll("MinSummary\\(\\d+\\)", String.valueOf(minNumberSummary));
        }

        return evalExpression(formula);
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
    private Double evalExpression(String expression) throws ScriptException {
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByName("JavaScript");
        return Double.parseDouble(engine.eval(expression).toString());
    }
    /***
     * 利用正则表达式从指定的字符串中获取值（利用group）
     * @param content 指定字符串
     * @param regexString 正则表达式
     * @param groupName 正则表达式中的分组名称
     * @return 分组对应的值
     */
    private String getValueByRegex(String content,String regexString,String groupName ){
        Pattern regex = Pattern.compile(regexString);
        Matcher regexMatcher = regex.matcher(content);
        if (regexMatcher.find()) {
            return regexMatcher.group(groupName);
        }
        throw new RuntimeException("在指定的字符串中没有提取到对应的值");
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
