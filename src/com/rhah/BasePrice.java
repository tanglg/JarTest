package com.rhah;

import javax.script.ScriptException;
import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

public class BasePrice {
    /*
    存储基准价
     */
    private BigDecimal basePrice;
    /**
     * 获取基准价
     * @return 当前标段的基准价
     */
    public BigDecimal getBasePrice(){
        return basePrice ;
    }
    /**
     * 初始化一个基准价计算对象
     * @param bidders 投标人列表，Key=投标人编码，value=投保人报价，该列表已经按照之前步骤投标人的得分升序排列
     * @param zbfFullPath 招标文件全路径（zbf文件的绝对路径）
     */
    public BasePrice(LinkedHashMap<String,BigDecimal> bidders,String zbfFullPath) throws ScriptException {
        if(bidders == null || bidders.size()==0) throw new RuntimeException("未设置投标人数据，或投标人数量为0");
        if(!new File(zbfFullPath).exists()) throw new RuntimeException("指定的招标文件不存在");

        String basePriceComputeType = OfferScore.getSingleValueFromSqlite(zbfFullPath,"SELECT ComputeType FROM BasePriceComputeMethod");

        if(basePriceComputeType.equals("Average")){
            RemoveBidderResult result = getRemvoeBidderCount(zbfFullPath,bidders.size());
            if(result.RemoveLowCount+result.RemoveHeightCount>=bidders.size()){
                throw new RuntimeException("去除最高和最高报价后，有效报价数量不足");
            }
            basePrice = computeAverageBasePrice(getOriginalSortedOffer(bidders),result);
            System.out.printf("基准价（平均值）=%s%n",basePrice);
        }else if(basePriceComputeType.equals("MinValue")){
            basePrice  = Collections.min(bidders.values());
            System.out.printf("基准价（最小值）=%s%n",basePrice);
        }else if(basePriceComputeType.equals("TopN")){
            basePrice  = computeTopNBasePrice(bidders,zbfFullPath);
            System.out.printf("基准价（TopN）=%s%n",basePrice);
        }
        else if (basePriceComputeType.equals("BaseAsLimitPrice")){
            basePrice = computeLimitBasePrice(zbfFullPath,getOriginalSortedOffer(bidders));
            System.out.printf("基准价（基于投标限制价）=s%n%",basePrice);
        }
        else{
            throw new RuntimeException("不支持根据 "+basePriceComputeType+" 方式计算基准价");
        }
    }
    /**
     * 获取经过升序排序的投标人报价数组
     * @param bidders 投标人报价数据，Key=投标人编码，value=投保人报价
     * @return 升序排序的投标人报价数组
     */
    private List<BigDecimal> getOriginalSortedOffer(LinkedHashMap<String,BigDecimal> bidders){
        ArrayList<BigDecimal> list = new ArrayList<>(bidders.size());
        for (BigDecimal offer : bidders.values()
                ) {
            list.add(offer);
        }
        Collections.sort(list);
        return list;
    }
    private BigDecimal computeLimitBasePrice(String zbfPath,List<BigDecimal> offerList)
    {
        BigDecimal limitPrice = new BigDecimal( OfferScore.getSingleValueFromSqlite(zbfPath,"select Backup1 from Overview"));
        BigDecimal lowMult = new BigDecimal( OfferScore.getSingleValueFromSqlite(zbfPath,"select TopMult from BasePriceAsTopLimit"));
        BigDecimal topMult = new BigDecimal( OfferScore.getSingleValueFromSqlite(zbfPath,"select LowMult from BasePriceAsTopLimit"));
        BigDecimal lowValue = limitPrice .multiply( lowMult);
        System.out.printf("最低报价限制=%s%n", lowValue);
        BigDecimal topValue = limitPrice .multiply( topMult);
        System.out.printf("最高报价限制=%s%n", topValue);
        BigDecimal sum = new BigDecimal(0);//记录符合范围的报价的综合
        int count = 0;//记录有几个报价符合范围

        for(Integer i=0;i<offerList.size();i++){
            if(offerList.get(i).compareTo(lowValue)==1 && offerList.get(i).compareTo(topValue)==-1) {
                sum.add(offerList.get(i));
                count = count + 1;
                System.out.printf("符合条件的报价=%s%n", offerList.get(i));
            }
        }
        if(count ==0){
            throw new RuntimeException("根据投标限制价计算基准价时没有找到符合条件的投标报价");
        }
        return sum.divide(new BigDecimal(count));
    }
    /***
     * 计算按平均值方式计算基准价
     * @param offerList 投标人数组，要求投标人按报价升序自动排序
     * @param method 平均值计算时的参数，主要包括去掉最高、最低以及浮动比例规则
     * @return 基准价，未设置小数位
     */
    private BigDecimal computeAverageBasePrice(List<BigDecimal> offerList,RemoveBidderResult method){
        BigDecimal price = BigDecimal.valueOf(0);
        for(Integer i=method.RemoveLowCount;i<offerList.size()-method.RemoveHeightCount;i++){
            price = price.add(offerList.get(i));
            System.out.printf("参与平均的值"+(i+1-method.RemoveLowCount)+"=%s%n",offerList.get(i));
        }
        price = price.divide(BigDecimal.valueOf(offerList.size()-method.RemoveHeightCount-method.RemoveLowCount),3, BigDecimal.ROUND_DOWN);
        return price.multiply(method.Factor).divide(BigDecimal.valueOf(100),3, BigDecimal.ROUND_DOWN);
    }
    private BigDecimal computeTopNBasePrice(LinkedHashMap<String,BigDecimal> bidders,String dbPath) {
        Integer topN = Integer.valueOf( OfferScore.getSingleValueFromSqlite(dbPath,"SELECT RemoveMaxValueCount FROM BasePriceComputeMethod"));
        System.out.printf("前N名=%s%n",topN);

        BigDecimal price = BigDecimal.valueOf(0);
        Integer count = 0;
        for (Iterator<String> it = bidders.keySet().iterator(); it.hasNext();) {
            BigDecimal offer = bidders.get(it.next());
            price = price.add(offer);
            System.out.printf("经过之前评审后第"+(count+1)+"名的报价=%s%n",offer);
            count++;
            if(count>=topN){
                break;
            }
        }
        price = price.divide(BigDecimal.valueOf(count),3, BigDecimal.ROUND_DOWN);
        System.out.printf("未计算浮动比例的基准价=%s%n",price);
        BigDecimal factor = new BigDecimal(OfferScore.getSingleValueFromSqlite(dbPath,"SELECT DownFactor FROM BasePriceComputeMethod"));
        if(factor.compareTo(BigDecimal.valueOf(2000))==1)
        {
            price = price.multiply((factor.subtract(BigDecimal.valueOf(2000)))).divide(BigDecimal.valueOf(100),3, BigDecimal.ROUND_DOWN);
            System.out.printf("基准价计算的浮动比例=%s%n",factor);
        }
        return price;
    }
    /**
     * 获取按平均值计算基准价时去除最高和最低报价的规则列表
     * @param dbPath 招标文件SQLite全路径
     * @return SQL语句执行结果
     */
    private List<RemoveBidderParam> getRemoveBidderParam(String dbPath)  {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbPath));
            Statement stat = conn.createStatement();
            ResultSet rs = stat.executeQuery("SELECT ThresholdValue,HeightCount,LowCount,DownFactor from AverageBasePriceMethod WHERE ThresholdValue>0 ORDER BY SortOrder");
            List<RemoveBidderParam> params  = new ArrayList<RemoveBidderParam>();
            while( rs.next()) {
                RemoveBidderParam param = new RemoveBidderParam();
                param.ThresholdValue = rs.getInt("ThresholdValue");
                param.HeightCount = rs.getInt("HeightCount");
                param.LowCount = rs.getInt("LowCount");
                param.Factor = new BigDecimal(rs.getString("DownFactor"));
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
     * 按照平均值计算基准价时，根据投标人数量计算实际需要去除的最高报价、最低报价数量以及浮动比例
     * @param dbpath 招标文件全路径（默认招标文件中只有一个报价得分节点，且如果未设置去除规则将返回去除0个最高和最低报价）
     * @param bidderCount 投标人数量
     * @return 去除最高报价和最低报价数量
     */
    private RemoveBidderResult getRemvoeBidderCount(String dbpath,int bidderCount)
    {
        List<RemoveBidderParam> params = getRemoveBidderParam(dbpath);
        int positon = -1;
        for(int i=0;i<params.size();i++){
            if(bidderCount >= params.get(i).ThresholdValue &&  params.get(i).ThresholdValue>0){
                positon = i;
            }
        }
        RemoveBidderResult param = new RemoveBidderResult();
        if(positon==-1){//没有设置去除最高值和最低值的情况
            param.RemoveHeightCount=0;
            param.RemoveLowCount =0;
            param.Factor=BigDecimal.valueOf(100);
            } else {
            param.RemoveHeightCount=params.get(positon).HeightCount;
            param.RemoveLowCount =params.get(positon).LowCount;
            param.Factor =params.get(positon).Factor;
        }
        BigDecimal factor = GetFactor(dbpath);
        if (factor.compareTo(BigDecimal.valueOf(1000))==1&&factor.compareTo(BigDecimal.valueOf(2000))==-1){
            //如果设置了整体的浮动比例，则覆盖分项的浮动比例
            param.Factor= factor.subtract(BigDecimal.valueOf(1000));
        }
        return param;
    }
    private BigDecimal GetFactor(String dbpath)
    {
        BigDecimal factor = new BigDecimal(OfferScore.getSingleValueFromSqlite(dbpath,"SELECT DownFactor FROM BasePriceComputeMethod"));
        return factor;
    }
}

