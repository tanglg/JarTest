package com.rhah;

import javax.script.ScriptException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {

        testComputeBasePrice();

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
            OfferScore os = new OfferScore(bidderList, "H:\\zhaobiao\\示例项目\\报价得分测试专用.zbf",BigDecimal.valueOf(0.3));
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


