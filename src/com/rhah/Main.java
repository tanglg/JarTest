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
        bidderList.put("1", BigDecimal.valueOf(1));
        bidderList.put("2", BigDecimal.valueOf(2));
        bidderList.put("3", BigDecimal.valueOf(3));
        bidderList.put("4", BigDecimal.valueOf(4));
        bidderList.put("5", BigDecimal.valueOf(5));
        bidderList.put("6", BigDecimal.valueOf(6));

        bidderList.put("7", BigDecimal.valueOf(7));
        bidderList.put("8", BigDecimal.valueOf(8));
        bidderList.put("9", BigDecimal.valueOf(9));
        bidderList.put("10", BigDecimal.valueOf(10));
        bidderList.put("11", BigDecimal.valueOf(11));
        bidderList.put("12", BigDecimal.valueOf(12));

        bidderList.put("13", BigDecimal.valueOf(13));
        bidderList.put("14", BigDecimal.valueOf(14));
        bidderList.put("15", BigDecimal.valueOf(15));
        bidderList.put("16", BigDecimal.valueOf(16));
        bidderList.put("17", BigDecimal.valueOf(17));
        bidderList.put("18", BigDecimal.valueOf(18));

        bidderList.put("19", BigDecimal.valueOf(19));
        bidderList.put("20", BigDecimal.valueOf(20));
        bidderList.put("21", BigDecimal.valueOf(21));
        bidderList.put("22", BigDecimal.valueOf(22));
        bidderList.put("23", BigDecimal.valueOf(23));
        bidderList.put("24", BigDecimal.valueOf(24));

        bidderList.put("25", BigDecimal.valueOf(25));
        bidderList.put("26", BigDecimal.valueOf(26));
        bidderList.put("27", BigDecimal.valueOf(27));
        bidderList.put("28", BigDecimal.valueOf(28));
        return bidderList;
    }

    private static   LinkedHashMap generateDemoBidderScoreData(){
        LinkedHashMap<String, BigDecimal> bidderList = new LinkedHashMap<>();
        bidderList.put("1", BigDecimal.valueOf(90));
        bidderList.put("2", BigDecimal.valueOf(351000.0));
        bidderList.put("3", BigDecimal.valueOf(68));
        bidderList.put("4", BigDecimal.valueOf(75.621));
        bidderList.put("5", BigDecimal.valueOf(74.5));
        bidderList.put("6", BigDecimal.valueOf(65.6));

        return bidderList;
    }
    public static void testComputeBasePrice() throws ScriptException {
        String zbfPath = "H:\\zhaobiao\\示例项目\\EPC\\epc项目测试\\epc项目测试.zbf";
        /*BasePrice priceObject= new BasePrice(generateDemoBidderOfferData(),generateDemoBidderScoreData(),zbfPath,
                "I1300000001000117001001","636856699991660264");*/

        BasePrice priceObject= new BasePrice(zbfPath,"I1300000001000117001001","636903324990146947");
        System.out.printf("基准价采用的报价条目=%s%n",priceObject.getPriceItem());
        BigDecimal basePrice = priceObject.getBasePrice(generateDemoBidderOfferData(),generateDemoBidderScoreData(),3);

        System.out.printf("最终计算基准价=%s%n",basePrice );

        /*
        OfferScore score = new OfferScore(zbfPath,"636903325136710124",2);
        System.out.printf("报价分采用的基准价节点：%s%n",score.GetRelateBasePrice());
        System.out.printf("报价分采用的开标一览表项是：%s%n",score.GetCustomItem());
        LinkedHashMap offerScore = score.getBidderOfferScore(generateDemoBidderOfferData(),basePrice);
        for (Object bidder : offerScore.keySet()
                ) {
            System.out.printf("%s=%s%n", bidder, offerScore.get(bidder));
        }
        */
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


