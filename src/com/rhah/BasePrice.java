package com.rhah;

import javax.script.ScriptException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    /*
    招标文件（已解密的zbf）全路径
     */
    private String _zbfFullPath;
    /*
    当前基准价节点编码
     */
    private String _basePriceNodeKey;
    /*
    当前标段编码
     */
    private String _subItemCode;

    /**
     * 获取房建项目随机平均价的基准价(BindRules表中Code=BanFa，Value=RandomAverage时)
     * @param weight 现场抽取的随机数 （注意，应为除100后的数）
     * @param biddersPrice 投标人报价列表(注意区别提取哪个报价条目)，Key=投标人编码，value=投保人报价
     * @param scale 最终结果保留的小数位(四舍五入)
     * @return 评分基准价
     */
    public BigDecimal getRandomAverageBasePrice(LinkedHashMap<String,BigDecimal> biddersPrice,BigDecimal weight,int scale) {
        System.out.printf("随机平均价作为基准价");
        basePrice = computeRandomAveragePrice(getOriginalSortedOffer(biddersPrice));
        System.out.printf("基准价=",basePrice);
        return basePrice.multiply(weight).setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * 获取房建项目合理平均价的基准价(BindRules表中Code=BanFa，Value=ReasonableAverage时)
     * @param biddersPrice 投标人报价列表(注意区别提取哪个报价条目)，Key=投标人编码，value=投保人报价
     * @param c1 现场抽取的基准价系数（注意，应为除100后的数）
     * @param c2 现场抽取的最高限价系数（注意，应为除100后的数）
     * @param scale 最终结果保留的小数位(四舍五入)
     * @return 评分基准价
     */
    public BigDecimal getReasonableAverageBasePrice(LinkedHashMap<String,BigDecimal> biddersPrice,BigDecimal c1,BigDecimal c2,int scale) {
        System.out.printf("合理平均价作为基准价%n");
        BigDecimal offerScore = computeRandomAveragePrice(getOriginalSortedOffer(biddersPrice));
        System.out.printf("A=%s%n",offerScore);
        offerScore = offerScore.multiply(c1).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal q1 = new BigDecimal(OfferScore.getSingleValueFromSqlite(_zbfFullPath,"SELECT Backup3 FROM BasePriceComputeMethod WHERE RelationKey='"+_basePriceNodeKey+"'")).divide(new BigDecimal(100));
        System.out.printf("Q1=%s%n",q1);
        offerScore = offerScore.multiply(q1).setScale(scale, RoundingMode.HALF_UP);
        System.out.printf("A*Q1*C1=%s%n",offerScore);

        BigDecimal q2 = new BigDecimal(OfferScore.getSingleValueFromSqlite(_zbfFullPath,"SELECT Backup4 FROM BasePriceComputeMethod WHERE RelationKey='"+_basePriceNodeKey+"'")).divide(new BigDecimal(100));
        System.out.printf("Q2=%s%n",q2);
        BigDecimal limitPrice = new BigDecimal(OfferScore.getSingleValueFromSqlite(_zbfFullPath,"SELECT Backup1 FROM Overview WHERE Backup2='"+_subItemCode+"'"));
        System.out.printf("B=%s%n",limitPrice);
        BigDecimal limitScore;
        limitScore = q2.multiply(c2).multiply(limitPrice).setScale(scale,RoundingMode.HALF_UP);
        System.out.printf("B*Q2*C2=%s%n",limitScore);

        basePrice = offerScore.add(limitScore);
        System.out.printf("基准价=%s%n",basePrice);

        return basePrice;
    }
    /**
     * 获取基准价
     * @param biddersPrice 投标人报价列表(注意区别提取哪个报价条目)，Key=投标人编码，value=投保人报价
     * @param biddersScore 投标人累计得分列表，Key=投标人编码，value=投保人累计得分，无累计得分时传入null
     * @param scale 基准价计算结果保留的小数位数
     * @return 当前标段的基准价
     */
    public BigDecimal getBasePrice(LinkedHashMap<String,BigDecimal> biddersPrice,LinkedHashMap<String,BigDecimal> biddersScore,Integer scale){
        String basePriceComputeType= getComputeType(_zbfFullPath,_basePriceNodeKey);

        if(basePriceComputeType.equals("Average")){
            RemoveBidderResult result = getRemvoeBidderCount(_zbfFullPath,biddersPrice.size());
            if(result.RemoveLowCount+result.RemoveHeightCount>=biddersPrice.size()){
                throw new RuntimeException("去除最高和最高报价后，有效报价数量不足");
            }
            basePrice = computeAverageBasePrice(getOriginalSortedOffer(biddersPrice),result,scale);
            System.out.printf("基准价（平均值）=%s%n",basePrice);
        }else if(basePriceComputeType.equals("MinValue")){
            basePrice  = Collections.min(biddersPrice.values());
            System.out.printf("基准价（最小值）=%s%n",basePrice);
        }else if(basePriceComputeType.equals("TopN")){
            basePrice  = computeTopNBasePrice(getOriginalSortedOffer(biddersPrice),_zbfFullPath,scale);
            System.out.printf("基准价（基于报价从高到低前N名计算平均值）=%s%n",basePrice);
        }
        else if (basePriceComputeType.equals("BaseAsLimitPrice")){
            basePrice = computeLimitBasePrice(_zbfFullPath,getOriginalSortedOffer(biddersPrice),_subItemCode);
            System.out.printf("基准价（基于投标限制价）=%s%n",basePrice);
        }
        else if (basePriceComputeType.equals("TopScoreN")){
            basePrice = computeTopScoreBasePrice(_zbfFullPath,extractAscSortedOfferByScore(biddersPrice,biddersScore));
            System.out.printf("基准价（基于之前步骤累计得分前N名计算平均值）=%s%n",basePrice);
        }
        else if(basePriceComputeType.equals("SecondLower")){
            basePrice = computeSecondLowerPrice(getOriginalSortedOffer(biddersPrice));
            System.out.printf("基准价（次低价）=%s%n",basePrice);
        }
        else if(basePriceComputeType.equals("RandomAverage")){
            basePrice = computeRandomAveragePrice(getOriginalSortedOffer(biddersPrice));
        }
        else if(basePriceComputeType.equals("TopLimitAndAverage")){
            try {
                basePrice = computeTopLimitAndAverage(getOriginalSortedOffer(biddersPrice), scale);
                System.out.printf("基准价（限价和平均价）=%s%n", basePrice);
            }catch  (ScriptException ex) {
                System.out.printf("计算基准价时，出现异常=%s%n", ex.getStackTrace());
            }
        }
        else{
            throw new RuntimeException("不支持根据 "+basePriceComputeType+" 方式计算基准价");
        }
        return basePrice ;
    }

    /**
     * 基准价计算对象
     * @param zbfFullPath 招标文件全路径（解密后的zbf文件绝对路径）
     * @param subItemCode 当前标段唯一编码
     * @param basePriceNodeKey 需要进行计算的基准价节点的编码，SQLite库EvaluationFlow表NodeKey列的值
     */
    public BasePrice(String zbfFullPath,String subItemCode,String basePriceNodeKey) {
        _zbfFullPath = zbfFullPath;
        _basePriceNodeKey = basePriceNodeKey;
        _subItemCode = subItemCode;
    }
    private String getComputeType(String zbfFullPath,String basePriceNodeKey) {
        return OfferScore.getSingleValueFromSqlite(zbfFullPath,"SELECT ComputeType FROM BasePriceComputeMethod WHERE RelationKey="+basePriceNodeKey);
    }
    /**
     * 获取当前基准价计算使用的是开标一览表中的哪个报价条目
     * @return 报价条目的名称
     */
    public String getPriceItem() {
        String signupType = OfferScore.getSingleValueFromSqlite(_zbfFullPath, "SELECT count(*)  FROM Parameters where ParameterName='SignupType' AND ParameterValue='1'");
        if(signupType.equals("1")){
            return OfferScore.getSingleValueFromSqlite(_zbfFullPath, "SELECT NodeKey FROM CustomItem WHERE Backup2=='"+_subItemCode+"' AND Backup4=0");
        }else {
            return OfferScore.getSingleValueFromSqlite(_zbfFullPath, "SELECT Backup1 FROM BasePriceComputeMethod WHERE RelationKey=" + _basePriceNodeKey);
        }
    }

    /**
     * 获取经过升序排序的投标人数组，要求Key=投标人编码，value=投保人报价或得分，将根据value值进行升序排列
     * @param bidders 投标人报价数据，Key=投标人编码，value=用来排序的数值
     * @return 升序排序的数值数组
     */
    private List<BigDecimal> getOriginalSortedOffer(LinkedHashMap<String,BigDecimal> bidders){
        ArrayList<BigDecimal> list = new ArrayList<>(bidders.size());
        for (BigDecimal offer : bidders.values() ) {
            list.add(offer);
        }
        Collections.sort(list);
        return list;
    }

    /**
     * 提取经过升序排列的投标报价
     * @param offers 投标人报价
     * @param scores 投标人累计得分
     * @return 升序排列的投标报价
     */
    private List<BigDecimal> extractAscSortedOfferByScore(LinkedHashMap<String,BigDecimal> offers,LinkedHashMap<String,BigDecimal> scores){
        ArrayList<SimpleBidder> simpleBidders = new ArrayList<SimpleBidder>(offers.size());
        Iterator item  = offers.entrySet().iterator();
        while (item.hasNext()) {
            Map.Entry<String, BigDecimal> entry =  (Map.Entry<String, BigDecimal>)item.next();
            SimpleBidder bidder = new SimpleBidder();
            bidder.bidderKey = entry.getKey();
            bidder.offer = entry.getValue();
            bidder.score = scores.get(entry.getKey());
            simpleBidders.add(bidder);
        }
        simpleBidders.sort(new SortByScore());
        ArrayList<BigDecimal> list = new ArrayList<>(offers.size());
        System.out.printf("根据累计得分升序排序后：%n");
        for (SimpleBidder sbidder : simpleBidders ) {
            list.add(sbidder.offer);
            System.out.printf("投标人=%s 分数=%s  报价=%s%n",sbidder.bidderKey,sbidder.score.toString(),sbidder.offer.toString());
        }
        return list;
    }
    private BigDecimal computeTopLimitAndAverage(List<BigDecimal> prices,Integer scale) throws ScriptException {
        RemoveBidderResult removeBidderResult = getRemvoeBidderCount(_zbfFullPath,prices.size());
        BigDecimal average = computeAverageBasePrice(prices,removeBidderResult,scale);
        System.out.printf("投标人数=%s 去最高数=%s  去最低数=%s   平均值=%s%n",prices.size(),removeBidderResult.RemoveHeightCount,removeBidderResult.RemoveLowCount,average);
        BigDecimal limitPrice;
        try{
            limitPrice = new BigDecimal(OfferScore.getSingleValueFromSqlite(_zbfFullPath,"select Backup1 from Overview WHERE Backup2='"+_subItemCode+"'"));
        }catch (Exception ex)  {
            throw new RuntimeException("根据标段（"+_subItemCode+"）获取限价时异常，请确认标段编码是否正确\r\n"+ex.getMessage() ,ex );
        }
        System.out.printf("最高限价=%s%n",limitPrice);
        String fomular = OfferScore.getSingleValueFromSqlite(_zbfFullPath,"SELECT Formula FROM BasePriceComputeMethod WHERE RelationKey=='"+_basePriceNodeKey+"'");
        System.out.printf("计算公式=%s%n",fomular);
        fomular = fomular.replace("最高限价",limitPrice.toString());
        fomular = fomular.replace("报价平均值",average.toString());
        System.out.printf("计算公式=%s%n",fomular);
        BigDecimal result = OfferScore.evalExpression(fomular).setScale(scale, RoundingMode.HALF_UP);

        return result;
    }

    /**
     * 根据投标单位之前步骤累计得分的前N名计算基准价
     * @param zbfPath 招标文件全路径
     * @param scoreList 经过升序排序的投标人累计得分的分值
     * @return
     */
    private BigDecimal computeTopScoreBasePrice(String zbfPath,List<BigDecimal> scoreList){
        Integer topN = Integer.valueOf( OfferScore.getSingleValueFromSqlite(zbfPath,"SELECT RemoveMaxValueCount FROM BasePriceComputeMethod"));
        System.out.printf("前N名=%s%n",topN);
        BigDecimal price = BigDecimal.valueOf(0);
        Integer count = 0;
        for(Integer i=scoreList.size()-1;i>-1;i--){
            price = price.add(scoreList.get(i));
            System.out.printf("累计得分第"+(count+1)+"名的报价=%s%n",scoreList.get(i));
            count++;
            if(count>=topN){
                break;
            }
        }
        price = price.divide(BigDecimal.valueOf(count),2, RoundingMode.HALF_UP);
        System.out.printf("基准价=%s%n",price);
        return price;
    }
    private BigDecimal computeSecondLowerPrice(List<BigDecimal> offerList)
    {
        if(offerList.isEmpty())throw new RuntimeException("报价数据为空，无法计算次低价基准价");
        if(offerList.size()==1) return offerList.get(0);
        return offerList.get(1);
    }
    private BigDecimal computeRandomAveragePrice(List<BigDecimal> offerList)
    {
        if(offerList.isEmpty())throw new RuntimeException("报价数据为空，无法计算基准价");
        System.out.printf("%s个报价参与计算%n", offerList.size());
        BigDecimal price = BigDecimal.valueOf(0);
        if(offerList.size()<20) {
            for(int i=0;i<offerList.size();i++){
                price = price.add(offerList.get(i));
            }
            price = price.divide(BigDecimal.valueOf(offerList.size()),2, RoundingMode.HALF_UP);
            System.out.printf("直接取平均值，基准价=%s%n", price);
        } else{
            int startPos = (int) Math.floor(offerList.size() * 0.2);
            int endPos = (int) Math.floor(offerList.size() * 0.2);

            for(int i=startPos;i<offerList.size()-endPos;i++){
                price = price.add(offerList.get(i));
            }
            price = price.divide(BigDecimal.valueOf(offerList.size()-startPos-endPos),2, RoundingMode.HALF_UP);
            System.out.printf("去掉%s个最高  %s个最低后，基准价=%s%n", startPos,endPos,price);
        }
        return price;
    }
    private BigDecimal computeLimitBasePrice(String zbfPath,List<BigDecimal> offerList,String subitemCode)
    {
        BigDecimal limitPrice;
        try{
            limitPrice = new BigDecimal(OfferScore.getSingleValueFromSqlite(zbfPath,"select Backup1 from Overview WHERE Backup2='"+subitemCode+"'"));
        }catch (Exception ex)  {
            throw new RuntimeException("根据标段（"+subitemCode+"）获取限价时异常，请确认标段编码是否正确\r\n"+ex.getMessage() ,ex );
        }
        BigDecimal lowMult = new BigDecimal( OfferScore.getSingleValueFromSqlite(zbfPath,"select LowMult from BasePriceAsTopLimit"));
        BigDecimal topMult = new BigDecimal( OfferScore.getSingleValueFromSqlite(zbfPath,"select TopMult from BasePriceAsTopLimit"));
        BigDecimal lowValue = limitPrice .multiply( lowMult);
        System.out.printf("最低报价限制=%s%n", lowValue);
        BigDecimal topValue = limitPrice .multiply( topMult);
        System.out.printf("最高报价限制=%s%n", topValue);
        BigDecimal sum = new BigDecimal(0);//记录符合范围的报价的综合
        int count = 0;//记录有几个报价符合范围

        for(Integer i=0;i<offerList.size();i++){
            if((offerList.get(i).compareTo(lowValue)==1 && offerList.get(i).compareTo(topValue)==-1)||offerList.get(i).compareTo(lowValue)==0 ||
                    offerList.get(i).compareTo(topValue)==0) {
                sum=sum.add(offerList.get(i));
                count = count + 1;
                System.out.printf("符合条件的报价=%s%n", offerList.get(i));
            }
        }
        if(count ==0){
            BigDecimal defaultMult = new BigDecimal( OfferScore.getSingleValueFromSqlite(zbfPath,"select Backup1 from BasePriceAsTopLimit"));
            System.out.print("没有找到符合条件("+lowValue+"-"+topValue+")的投标报价，将采用限价的"+defaultMult+"倍作为基准价");
            return limitPrice.multiply(defaultMult).setScale(2, RoundingMode.HALF_UP);
        }
        //获取浮动比例  最大值为100,0表示未设置比例
        BigDecimal factor = new BigDecimal( OfferScore.getSingleValueFromSqlite(zbfPath,"select Backup4 from BasePriceAsTopLimit"));
        System.out.printf("浮动比例（最大值为100,0表示未设置比例）为=%s%n", factor);
        BigDecimal result =  sum.divide(new BigDecimal(count),2,RoundingMode.HALF_UP);
        if(factor.compareTo(BigDecimal.valueOf(0.0))==0){
            return result;
        }
        return result.multiply(factor).divide(BigDecimal.valueOf(100));
    }
    /**
     * 计算按平均值方式计算基准价
     * @param offerList 投标人数组，要求投标人按报价升序自动排序
     * @param method 平均值计算时的参数，主要包括去掉最高、最低以及浮动比例规则
     * @return 基准价，未设置小数位
     */
    private BigDecimal computeAverageBasePrice(List<BigDecimal> offerList,RemoveBidderResult method,Integer scale){
        BigDecimal price = BigDecimal.valueOf(0);
        for(Integer i=method.RemoveLowCount;i<offerList.size()-method.RemoveHeightCount;i++){
            price = price.add(offerList.get(i));
            System.out.printf("参与平均的值"+(i+1-method.RemoveLowCount)+"=%s%n",offerList.get(i));
        }
        price = price.divide(BigDecimal.valueOf(offerList.size()-method.RemoveHeightCount-method.RemoveLowCount),scale, RoundingMode.HALF_UP);
        if(!method.Factor.equals(new BigDecimal(100))){
            System.out.printf("基准价将在 %s的基础上下浮%s%%%n",price,new BigDecimal(100).subtract(method.Factor));
        }
        return price.multiply(method.Factor).divide(BigDecimal.valueOf(100),scale, RoundingMode.HALF_UP);
    }
    private BigDecimal computeTopNBasePrice(List<BigDecimal> bidders,String dbPath,Integer scale) {
        Integer topN = Integer.valueOf( OfferScore.getSingleValueFromSqlite(dbPath,"SELECT RemoveMaxValueCount FROM BasePriceComputeMethod"));
        System.out.printf("前N名=%s%n",topN);
        BigDecimal price = BigDecimal.valueOf(0);
        Integer count = 0;
        for(Integer i=bidders.size()-1;i>-1;i--){
            price = price.add(bidders.get(i));
            System.out.printf("报价第"+(count+1)+"名的报价=%s%n",bidders.get(i));
            count++;
            if(count>=topN){
                break;
            }
        }
        price = price.divide(BigDecimal.valueOf(count),scale, RoundingMode.HALF_UP);
        System.out.printf("未计算浮动比例的基准价=%s%n",price);
        BigDecimal factor = new BigDecimal(OfferScore.getSingleValueFromSqlite(dbPath,"SELECT DownFactor FROM BasePriceComputeMethod"));
        if(factor.compareTo(BigDecimal.valueOf(2000))==1)
        {
            price = price.multiply((factor.subtract(BigDecimal.valueOf(2000)))).divide(BigDecimal.valueOf(100),scale, RoundingMode.HALF_UP);
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
            ResultSet rs = stat.executeQuery("SELECT ThresholdValue,HeightCount,LowCount,DownFactor,Backup1 from AverageBasePriceMethod WHERE ThresholdValue>0 ORDER BY SortOrder");
            List<RemoveBidderParam> params  = new ArrayList<RemoveBidderParam>();
            while( rs.next()) {
                RemoveBidderParam param = new RemoveBidderParam();
                param.ThresholdValue = rs.getInt("ThresholdValue");
                param.HeightCount = rs.getInt("HeightCount");
                param.LowCount = rs.getInt("LowCount");
                param.Factor = new BigDecimal(rs.getString("DownFactor"));
                param.RemoveType = rs.getString("Backup1");
                if (param.RemoveType==null){
                    param.RemoveType = "数值";
                }
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
        System.out.printf("参与计算基准价总数：%s%n",bidderCount);
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
            if(params.get(positon).RemoveType.equals("数值")){
                param.RemoveHeightCount=params.get(positon).HeightCount;
                param.RemoveLowCount =params.get(positon).LowCount;
                System.out.printf("计算基准价去掉最高个数：%s%n",param.RemoveHeightCount);
                System.out.printf("计算基准价去掉最低个数：%s%n",param.RemoveLowCount);
            } else if(params.get(positon).RemoveType.equals("百分比四舍五入")){
                param.RemoveHeightCount= Math.round( params.get(positon).HeightCount * bidderCount / 100f);
                param.RemoveLowCount = Math.round( params.get(positon).LowCount * bidderCount / 100f);
                System.out.printf("去掉最高个数：%s%%，%s = %s%n",params.get(positon).HeightCount,params.get(positon).RemoveType,param.RemoveHeightCount);
                System.out.printf("去掉最低个数：%s%%，%s = %s%n",params.get(positon).LowCount,params.get(positon).RemoveType,param.RemoveLowCount);
            } else{
                param.RemoveHeightCount=(int)Math.floor(params.get(positon).HeightCount* bidderCount / 100f);
                param.RemoveLowCount =(int)Math.floor(params.get(positon).LowCount* bidderCount / 100f);
                System.out.printf("去掉最高个数：%s%%，%s = %s%n",params.get(positon).HeightCount,params.get(positon).RemoveType,param.RemoveHeightCount);
                System.out.printf("去掉最低个数：%s%%，%s = %s%n",params.get(positon).LowCount,params.get(positon).RemoveType,param.RemoveLowCount);
            }
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
    private class  SimpleBidder
    {
        /**
         * 投标人编码
         */
        public String bidderKey;
        /**
         * 投标人报价
         */
        public BigDecimal offer;
        /**
         * 投标人累计得分
         */
        public BigDecimal score;
    }
    private class SortByScore implements Comparator {
        public int compare(Object o1, Object o2) {
            SimpleBidder s1 = (SimpleBidder) o1;
            SimpleBidder s2 = (SimpleBidder) o2;
            return s1.score.compareTo(s2.score);
        }
    }
}

