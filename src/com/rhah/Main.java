package com.rhah;

import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {

        testComputeBasePrice();

    }

    public static void testComputeBasePrice() {

        Map<String, Double> bidderList = new HashMap<>();
        bidderList.put("张30", 30.0);
        bidderList.put("李150", 150.0);
        bidderList.put("王20", 20.0);
        bidderList.put("赵50", 50.0);
        bidderList.put("孙5", 5.0);
        bidderList.put("苏60", 60.0);
        try {
            OfferScore os = new OfferScore(bidderList, "E:/Files/zhaobiao/中国石油天然气股份有限公司河北沧州销售分公司 第二十加油站原址改建项目.zbf");
            System.out.printf("基准价=%s%n", os.getBasePrice());

            Map<String, Double> scores = os.getBidderOfferScore();
            for (String bidder : scores.keySet()
                    ) {
                System.out.printf("%s=%s%n", bidder, scores.get(bidder));
            }

        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }
}


