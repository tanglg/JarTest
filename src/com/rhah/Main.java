package com.rhah;

import javax.script.ScriptException;
import java.io.Console;
import java.lang.reflect.GenericArrayType;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class Main {

    public static void main(String[] args) throws ScriptException {

        testComputeBasePrice();
        //TestSQLite testSQLite = new TestSQLite();
        //testSQLite.getSingleValueFromSqlite();
        //RemoveBidderResult result = BasePrice .getRemvoeBidderCount("H:\zhaobiao\示例项目\EPC\epc项目测试",3);
        //System.out.printf("去除最高数量=%s%n", result.RemoveHeightCount);
        //System.out.printf("去除最低数量=%s%n", result.RemoveLowCount);
    }
    private static   LinkedHashMap generateDemoBidderOfferData(){
        LinkedHashMap<String, BigDecimal> bidderList = new LinkedHashMap<>();
        bidderList.put("李32", BigDecimal.valueOf(41.5));
        bidderList.put("张31", BigDecimal.valueOf(39.6));
        bidderList.put("丁36", BigDecimal.valueOf(23));
        bidderList.put("王33", BigDecimal.valueOf(35.19));
        bidderList.put("赵34", BigDecimal.valueOf(31.1));
        bidderList.put("孙35", BigDecimal.valueOf(37.5));

        return bidderList;
    }

    private static   LinkedHashMap generateDemoBidderScoreData(){
        LinkedHashMap<String, BigDecimal> bidderList = new LinkedHashMap<>();
        bidderList.put("李32", BigDecimal.valueOf(90));
        bidderList.put("张31", BigDecimal.valueOf(351000.0));
        bidderList.put("丁36", BigDecimal.valueOf(68));
        bidderList.put("王33", BigDecimal.valueOf(75.621));
        bidderList.put("赵34", BigDecimal.valueOf(74.5));
        bidderList.put("孙35", BigDecimal.valueOf(65.6));

        return bidderList;
    }
    public static void testComputeBasePrice() throws ScriptException {
        String zbfPath = "H:\\zhaobiao\\示例项目\\EPC\\epc项目测试\\epc项目测试.zbf";
        /*BasePrice priceObject= new BasePrice(generateDemoBidderOfferData(),generateDemoBidderScoreData(),zbfPath,
                "I1300000001000117001001","636856699991660264");*/

        BasePrice priceObject= new BasePrice(zbfPath,"I1300000001000117001001","636761581219885840");
        System.out.printf("基准价采用的报价条目=%s%n",priceObject.getPriceItem());
        BigDecimal basePrice = priceObject.getBasePrice(generateDemoBidderOfferData(),generateDemoBidderScoreData(),3);

        System.out.printf("最终计算基准价=%s%n",basePrice );

        OfferScore score = new OfferScore(generateDemoBidderOfferData(),zbfPath,basePrice,"636761581405926978",2);
        System.out.printf("报价分采用的基准价节点：%s%n",score.GetRelateBasePrice() );
        LinkedHashMap offerScore = score.getBidderOfferScore();
        for (Object bidder : offerScore.keySet()
                ) {
            System.out.printf("%s=%s%n", bidder, offerScore.get(bidder));
        }

    }
    public static void testComputeOfferScore() {
        //该测试数据库中，基准价采用的是平均值，报价得分=100，高1%减2分，低1%减1分，最高100分，最低0分
        /*
        OfferScore os = new OfferScore(generateDemoBidderData(), "H:\\zhaobiao\\示例项目\\双信封招标文件示例\\明珠花苑小学项目工程勘察设计.zbf",BigDecimal.valueOf(1));
        System.out.printf("基准价=%s%n", os.getBasePrice());

        Map<String, BigDecimal> scores = os.getBidderOfferScore();
        for (String bidder : scores.keySet()
                ) {
            System.out.printf("%s=%s%n", bidder, scores.get(bidder));
        }
        */
    }
}


