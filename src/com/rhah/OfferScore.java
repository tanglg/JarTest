package com.rhah;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 报价得分计算类
 */
public class OfferScore{
    /*
    计算基准价时要去除的最高报价个数
     */
    private Integer delMaxCount;
    /*
    计算基准价时要去除的最低报价个数
     */
    private Integer delMinCount;
    /*
    存储基准价下浮系数
     */
    private BigDecimal floatCoefficient;
    /*
    存储基准价
     */
    private BigDecimal basePrice;
    /*
    存储每个投标人的报价得分
     */
    private Map<String,BigDecimal> bidderOfferScore;
    /**
     * 获取基准价
     * @return 当前标段的基准价
     */
    public BigDecimal getBasePrice(){
        return basePrice ;
    }
    /**
     * 获取投标人报价得分
     * @return 投标人列表，Key=投标人编码，value=投标人报价得分
     */
    public Map<String,BigDecimal> getBidderOfferScore(){
        return  bidderOfferScore;
    }


    /**
     * 初始化一个报价得分计算对象(计算基准价时不需要去除最高报价和最低报价)
     * @param bidders 投标人列表，Key=投标人编码，value=投保人报价
     * @param zbfFullPath 招标文件全路径（zbf文件的绝对路径）
     * @param coefficient 基准价系数（例：下浮5%，则应传入0.95）
     */
    public OfferScore(Map<String,BigDecimal> bidders,String zbfFullPath,BigDecimal coefficient) throws ScriptException {
        if(bidders == null || bidders.size()==0) throw new RuntimeException("没有发现投标人数据，或投标人数量为0");
        if(!new File(zbfFullPath).exists()) throw new RuntimeException("指定的招标文件不存在");
        floatCoefficient = coefficient;
        delMaxCount = 0;
        delMinCount = 0;
        String basePriceComputeType = getSingleValueFromSqlite(zbfFullPath,"SELECT ComputeType FROM BasePriceComputeMethod");
        if(basePriceComputeType.equals("Average")){
            RemoveBidderResult resule = getRemvoeBidderCount(zbfFullPath,bidders.size());
            delMaxCount = resule.RemoveHeightCount;
            delMinCount = resule.RemoveLowCount;
            basePrice = computeBasePrice(getOriginalSortedOffer(bidders),zbfFullPath);
        }else{
            basePrice  = Collections.min(bidders.values());
            System.out.printf("基准价（最小值）=%s%n",basePrice);
        }
        bidderOfferScore = computeOfferScore(bidders,zbfFullPath);
    }
    /**
     * 获取经过升序排序的投标人报价数组
     * @param bidders 投标人报价数据，Key=投标人编码，value=投保人报价
     * @return 升序排序的投标人报价数组
     */
    private List<BigDecimal> getOriginalSortedOffer(Map<String,BigDecimal> bidders){
        ArrayList<BigDecimal> list = new ArrayList<>(bidders.size());
        for (BigDecimal offer : bidders.values()
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
    private BigDecimal computeBasePrice(List<BigDecimal> offerList,String zbfFullPath) throws ScriptException {

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
            BigDecimal averageOffer = getSummaryForList(offerList) .divide(new BigDecimal(String.valueOf(offerList.size())),2) ;
            formula = formula.replace("SuoYouPingJunZhi",averageOffer.toString());
        }
        if(formula.contains("YouXiaoBaoJiaShuLiang")) {
            //有效报价的家数
            formula = formula.replace("YouXiaoBaoJiaShuLiang",String.valueOf(offerList.size()));
        }
        Integer removeBidderCount = 0;//保存需要去除的投标人数量，用于判断是否超出范围
        if(formula.contains("MaxSummary")){
            //替换去除N个最大值
            //提取出去除最大值的数量(个数)
            //去除数量由招标文件公式提取到开标时决定
            //Integer maxNumber = Integer.parseInt( getValueByRegex(formula,"MaxSummary\\((?<max>\\d+)\\)","max"));
            if(delMaxCount>=offerList.size()) {
                throw new RuntimeException(String.format("计算基准价时，去除的最大值个数(%d)超过了投标人数量(%d)", delMaxCount, offerList.size()));
            }
            //计算要去除的最大值的合
            BigDecimal maxNumberSummary = getSummaryForList(offerList.subList(offerList.size()-delMaxCount,offerList.size()));
            formula = formula.replaceAll("MaxSummary\\(\\d+\\)", String.valueOf(maxNumberSummary));
            removeBidderCount += delMaxCount;
        }
        if(formula.contains("MinSummary")){
            //替换去除N个最小值
            //提取出去除最小值的数量(个数)
            //Integer minNumber = Integer.parseInt( getValueByRegex(formula,"MinSummary\\((?<min>\\d+)\\)","min"));
            if(delMinCount>=offerList.size()) {
                throw new RuntimeException(String.format("计算基准价时，去除的最小值个数(%d)超过了投标人数量(%d)", delMinCount, offerList.size()));
            }
            //计算要去除的最小值的合
            BigDecimal minNumberSummary = getSummaryForList(offerList.subList(0,delMinCount));
            formula = formula.replaceAll("MinSummary\\(\\d+\\)", String.valueOf(minNumberSummary));
            removeBidderCount+=delMinCount;
        }
        if(removeBidderCount>=offerList.size()){
            throw new RuntimeException(String.format("计算基准价时，去除的最大值和最小值个数之和(%d)超过了投标人数量(%d)", removeBidderCount, offerList.size()));
        }
        formula = ReplaceRemoveMaxCount(formula);
        formula = ReplaceRemoveMinCount(formula);
        System.out.printf(formula);
        //计算出实际的基准价
        BigDecimal actualBasePrice = evalExpression(formula);
        //返回经过下浮系数计算后的基准价
        return actualBasePrice.multiply(floatCoefficient).setScale(2, RoundingMode.HALF_UP);
    }

    /***
     * 替换基准价公式中的移除最高价数量
     * @param formula 基准价公式
     * @return 替换后的公式
     */
    private String ReplaceRemoveMaxCount(String formula)
    {
        if(formula.contains("MaxCount")) {
            return formula.replaceAll("MaxCount\\(\\d+\\)", String.valueOf(delMaxCount));
        }else{
            return formula;
        }
    }

    /***
     * 换基准价公式中的移除最低价数量
     * @param formula 基准价计算公式
     * @return 替换后的公式
     */
    private String ReplaceRemoveMinCount(String formula)
    {
        if(formula.contains("MinCount")) {
            return formula.replaceAll("MinCount\\(\\d+\\)", String.valueOf(delMinCount));
        }else{
            return formula;
        }
    }
    /***
     * 计算报价得分
     * @param bidders 投标人及报价列表
     * @param zbfFullPath 招标文件全路径
     * @return 投标人及报价得分
     * @throws ScriptException
     */
    private Map<String,BigDecimal>  computeOfferScore(Map<String,BigDecimal> bidders,String zbfFullPath) throws ScriptException {
        String[] parameter = getOfferScoreFormula(zbfFullPath).split("@");//注意返回值格式
        String formula= parameter[0];
        BigDecimal minScore = new BigDecimal( parameter[1]);
        BigDecimal maxScore = new BigDecimal( parameter[2]);
        //-99.99指不设上限或下限
        if(maxScore .compareTo(new BigDecimal(-99.99)) == 0){maxScore = BigDecimal.valueOf(Double.MAX_VALUE);}
        if(minScore .compareTo(new BigDecimal(-99.99)) == 0){minScore = BigDecimal.valueOf(Double.MIN_VALUE);}

        Map<String,BigDecimal> scoreMap = new HashMap<>();

        for (String bidderID:bidders.keySet()
             ) {
            scoreMap.put(bidderID,computeBidderScore(formula,bidders.get(bidderID),maxScore,minScore).setScale(3, RoundingMode.HALF_UP));

        }

        return scoreMap;
    }

    /***
     * 计算单个投标人报价得分
     * @param formula 报价得分计算公式
     * @param offer 投标人报价
     * @param maxScore 报价得分上限
     * @param minScore 报价得分下限
     * @return 报价得分
     * @throws ScriptException
     */
    private BigDecimal computeBidderScore(String formula, BigDecimal offer,BigDecimal maxScore,BigDecimal minScore) throws ScriptException {
        formula = formula.replace("BaoJia",offer.toString()) ;
        formula = formula.replace("JiZhunJia",basePrice.toString()) ;
        //计算实际得分
        BigDecimal actualScore = evalExpression(formula);
        //判断是否超上、下限
        if(actualScore.compareTo(maxScore)==1) {
            return maxScore;
        }
        else if(actualScore.compareTo(minScore)==-1) {
            return minScore;
        }
        else {
            return actualScore;
        }
    }
    /***
     * 计算一个BigDecimal类型数组所有元素和和
     * @param list BigDecimal类型的数组
     * @return 所有数组元素的和
     */
    private BigDecimal getSummaryForList(List<BigDecimal> list){
        BigDecimal result= new BigDecimal("0.000");
        for (BigDecimal item :list
                ) {
            result = result.add(item) ;
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
     * @return 报价得分计算公式，注意：返回了除公式外额外数据，格式=公式@最低分@最高分。
     */
    private String getOfferScoreFormula(String zbfFullPath){
        return getSingleValueFromSqlite(zbfFullPath,"select Formula ||'@'||LowestScoreWhenIncrease||'@'||LowestScoreWhenReduce AS Parameter from OfferScoreComputeMethod");
    }
    /***
     * 计算数学表达式的值
     * @param expression 数学表达式
     * @return 计算后的数值
     * @throws ScriptException
     */
    private BigDecimal evalExpression(String expression ) throws ScriptException {
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByName("JavaScript");
        return new BigDecimal(engine.eval(expression).toString());
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
    /**
     * 从SQLite中读取单个数值
     * @param dbPath 招标文件SQLite全路径
     * @return SQL语句执行结果
     */
    private List<RemoveBidderParam> getRemoveBidderParam(String dbPath)  {
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
    /***
     * 获取招标文件中去除最高报价和最低报价数量
     * @param dbpath 招标文件全路径（默认招标文件中只有一个报价得分节点，且如果未设置去除规则将返回去除0个最高和最低报价）
     * @param bidderCount 投标人数量
     * @return 去除最高报价和最低报价数量
     */
    private RemoveBidderResult getRemvoeBidderCount(String dbpath,int bidderCount)
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
