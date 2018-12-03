package com.chinaredstar.opensdk.uidclient;

import com.chinaredstar.opensdk.uidclient.client.CachedUidGeneratorClient;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

/**
 * @Author: chen
 * @Description:
 * @Date: 2018/11/29 上午10:48
 */


public class ExampleByThreadPool {


  public static final Logger log = LoggerFactory.getLogger(ExampleByThreadPool.class);


  private static final int THREADS = Runtime.getRuntime().availableProcessors() << 1;

  public static void main(String[] args) throws Exception {

    CachedUidGeneratorClient cachedUidGenerator = new CachedUidGeneratorClient();

    /**
     * init config of Generator
     */
    cachedUidGenerator.setInitBufferSize(1 << 13);
    cachedUidGenerator.setPaddingUnderFactor(30);
    cachedUidGenerator.setPaddingUpperFactor(80);

    /**
     *  offer a IdProvider
     */
    cachedUidGenerator.setUidProvider(new IdProvider());

    /**
     * must init
     */
    cachedUidGenerator.init();

    /**
     *  can get id
     */

    log.info("START GET UID...");

    ArrayBlockingQueue arrayBlockingQueue  = new ArrayBlockingQueue<Long>(5000000);
    int num = 10 * 1000;

    long startTime = System.currentTimeMillis();
    List <Thread> threadList = new ArrayList <>(100);
    for (int i = 0; i < 10; i++) {
      Thread thread = new Thread(() -> {
        {
          for (int J = 0; J < num; J++) {
            arrayBlockingQueue.add(cachedUidGenerator.getUID());
          }
        }
      });
      thread.setName("UID-generator-" + i);
      threadList.add(thread);
      thread.start();
    }

    // Wait for worker done
    for (Thread thread : threadList) {
      thread.join();
    }



    log.info("END GET UID !");
    log.info("The End Set Num is " + arrayBlockingQueue.size());
    Assert.isTrue(num * 10 == arrayBlockingQueue.size(), "数量出错!");
    log.info("The End Set Num is " + arrayBlockingQueue.toString());

    log.info(cachedUidGenerator.getAbstractBuffer().toString());

    System.out.println("Time ------------" + (startTime - System.currentTimeMillis()));

    /**
     * when program is shutdown , generator must destroyed
     */


    cachedUidGenerator.destroy();
  }
}
