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
/**
 * 报价得分计算类
 */
public class OfferScore{

    private String _zbfFullPath;
    private String _subItemCode;
    private String _offerScoreNodeKey;
    LinkedHashMap<String,BigDecimal> _bidders;
    Integer _scale;
    /*
    存储基准价
     */
    private BigDecimal basePrice;
    /*
    存储每个投标人的报价得分
     */
    private LinkedHashMap<String,BigDecimal> bidderOfferScore;

    /**
     * 获取投标人报价得分
     * @param bidders 投标人列表，Key=投标人编码，value=投保人报价，投标人列表无须进行任何排序
     * @param price 基准价
     * @return 投标人列表，Key=投标人编码，value=投标人报价得分
     */
    public LinkedHashMap<String,BigDecimal> getBidderOfferScore(LinkedHashMap<String,BigDecimal> bidders,BigDecimal price) throws ScriptException {
        if(bidders == null || bidders.size()==0) throw new RuntimeException("未设置投标人数据，或投标人数量为0");
        _bidders = bidders;
        basePrice = price;
        bidderOfferScore = computeOfferScore(_bidders,_zbfFullPath,_offerScoreNodeKey,_scale);
        return  bidderOfferScore;
    }


    /**
     * 初始化一个报价得分计算对象
     * @param zbfFullPath 招标文件全路径（zbf文件的绝对路径）
     * @param offerScoreNodeKey 当前要计算的报价得分节点的编码，SQLite库EvaluationFlow表NodeKey列的值
     * @param scale 报价分计算结果保留小数位
     */
    public OfferScore(String zbfFullPath,String offerScoreNodeKey,Integer scale,String subItemCode) throws ScriptException {
        if(!new File(zbfFullPath).exists()) throw new RuntimeException("指定的招标文件不存在");
        _zbfFullPath = zbfFullPath;
        _offerScoreNodeKey = offerScoreNodeKey;
        _scale = scale;
        _subItemCode = subItemCode;

    }

    /**
     * 获取当前报价分阶段对应的基准价节点
     * @return 基准价节点的NodeKey
     */
    public String GetRelateBasePrice(){
        return OfferScore.getSingleValueFromSqlite(_zbfFullPath, "SELECT Backup1 FROM OfferScoreComputeMethod WHERE RelationKey=" + _offerScoreNodeKey);
    }

    /**
     * 获取当前报价分节点使用的是哪个开标一览表条目
     * @return 返回开标一览表项的唯一编码（SQLite中CustomItem的NodeKey列的值)
     */
    public String GetCustomItem()
    {
        String signupType = OfferScore.getSingleValueFromSqlite(_zbfFullPath, "SELECT count(*)  FROM Parameters where ParameterName='SignupType' AND ParameterValue='1'");
        if(signupType.equals("1")){
            return OfferScore.getSingleValueFromSqlite(_zbfFullPath, "SELECT NodeKey FROM CustomItem WHERE Backup2=='"+_subItemCode+"' AND Backup4=0");
        }else {
            return OfferScore.getSingleValueFromSqlite(_zbfFullPath, "SELECT Backup1 FROM BasePriceComputeMethod WHERE RelationKey=(SELECT Backup1 FROM OfferScoreComputeMethod WHERE RelationKey="+_offerScoreNodeKey+")" );
        }

    }
    /***
     * 计算报价得分
     * @param bidders 投标人及报价列表
     * @param zbfFullPath 招标文件全路径
     * @param offerScoreNodeKey 当前要计算的报价得分节点的编码，SQLite库EvaluationFlow表NodeKey列的值
     * @return 投标人及报价得分
     * @throws ScriptException
     */
    private LinkedHashMap<String,BigDecimal>  computeOfferScore(LinkedHashMap<String,BigDecimal> bidders,String zbfFullPath,String offerScoreNodeKey,Integer scale) throws ScriptException {
        String[] parameter = getOfferScoreFormula(zbfFullPath,offerScoreNodeKey).split("@");//注意返回值格式
        String formula= parameter[0];
        System.out.printf("报价得分计算公式=%s%n",formula);
        BigDecimal minScore = new BigDecimal( parameter[1]);
        BigDecimal maxScore = new BigDecimal( parameter[2]);
        //-99.99指不设上限或下限
        if(maxScore .compareTo(new BigDecimal(-99.99)) == 0){maxScore = BigDecimal.valueOf(Double.MAX_VALUE);}
        if(minScore .compareTo(new BigDecimal(-99.99)) == 0){minScore = BigDecimal.valueOf(Double.MIN_VALUE);}

        LinkedHashMap<String,BigDecimal> scoreMap = new LinkedHashMap<>();

        for (String bidderID:bidders.keySet()
             ) {
            scoreMap.put(bidderID,computeBidderScore(formula,bidders.get(bidderID),maxScore,minScore).setScale(scale, RoundingMode.HALF_UP));

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
        formula = formula.replace("BaoJia".toUpperCase(),offer.toString()) ;
        formula = formula.replace("JiZhunJia".toUpperCase(),basePrice.toString()) ;
        //注意：严格来说，标准分采用maxScore来替换不合适，因为最高分有可能高于标准分，但招标工具端设置了标准分=最高分，所以
        //采用这个替换没有问题，如果工具端调整，此处应同步调整
        formula = formula.replace("biaozhunfen".toUpperCase(),maxScore.toString()) ;
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


    /**
     * 获取报价得分的计算公式
     * @param zbfFullPath 招标文件全路径（zbf文件的绝对路径）
     * @return 报价得分计算公式，注意：返回了除公式外额外数据，格式=公式@最低分@最高分。
     */
    private String getOfferScoreFormula(String zbfFullPath,String offerScoreNodeKey){
        return getSingleValueFromSqlite(zbfFullPath,"select Formula ||'@'||LowestScoreWhenIncrease||'@'||LowestScoreWhenReduce AS Parameter"+
                                                        " from OfferScoreComputeMethod where RelationKey="+offerScoreNodeKey);
    }
    /***
     * 计算数学表达式的值
     * @param expression 数学表达式
     * @return 计算后的数值
     * @throws ScriptException
     */
    public static BigDecimal evalExpression(String expression ) throws ScriptException {
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByName("JavaScript");
        return new BigDecimal(engine.eval(expression).toString());
    }

    /**
     * 从SQLite中读取单个数值
     * @param dbFullPath SQLite全路径
     * @param sql SQL语句
     * @return SQL语句执行结果
     */
    public static String getSingleValueFromSqlite(String dbFullPath,String sql)  {
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
class RemoveBidderResult{
    /***
     * 需要移除的最高报价数量
     */
    public int RemoveHeightCount;
    /***
     * 需要移除的最低报价数量
     */
    public int RemoveLowCount;
    /**
     * 投标人数量达到阈值时的浮动比例，例如：值=98，则实际计算时应对基准价*0.98
     */
    public BigDecimal Factor;
}

/***
 * 基准价计算规则类，每一个对象表示一个阈值和阈值对应的去掉最高报价和最低报价个数
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
    /**
     * 投标人数量达到阈值时的浮动比例，例如：值=98，则实际计算时应对基准价*0.98
     */
    public BigDecimal Factor;
}
