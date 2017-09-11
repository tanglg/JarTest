package com.rhah;

import javax.script.ScriptException;
import java.io.Console;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {

        testComputeBasePrice();
        //TestSQLite testSQLite = new TestSQLite();
        //testSQLite.getSingleValueFromSqlite();
        //RemoveBidderResult result = BasePrice .getRemvoeBidderCount("H:\\zhaobiao\\示例项目\\双信封招标文件示例\\明珠花苑小学项目工程勘察设计.zbf",3);
        //System.out.printf("去除最高数量=%s%n", result.RemoveHeightCount);
        //System.out.printf("去除最低数量=%s%n", result.RemoveLowCount);
    }

    public static void testComputeBasePrice() {

        Map<String, BigDecimal> bidderList = new HashMap<>();
        bidderList.put("张30", BigDecimal.valueOf( 30.0));
        bidderList.put("李150", BigDecimal.valueOf(150.0));
        bidderList.put("王20", BigDecimal.valueOf(20.0));
        bidderList.put("赵50", BigDecimal.valueOf(50.0));
        bidderList.put("孙5", BigDecimal.valueOf(5.0));
        bidderList.put("苏60", BigDecimal.valueOf(60.0));
        try {

             //该测试数据库中，基准价采用的是平均值，报价得分=100，高1%减2分，低1%减1分，最高100分，最低0分
            OfferScore os = new OfferScore(bidderList, "H:\\zhaobiao\\示例项目\\双信封招标文件示例\\明珠花苑小学项目工程勘察设计.zbf",BigDecimal.valueOf(1));
            System.out.printf("基准价=%s%n", os.getBasePrice());

            Map<String, BigDecimal> scores = os.getBidderOfferScore();
            for (String bidder : scores.keySet()
                    ) {
                System.out.printf("%s=%s%n", bidder, scores.get(bidder));
            }

        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }
}


