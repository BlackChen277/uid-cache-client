package com.black.opensdk.uidclient;

import com.black.opensdk.uidclient.buffer.UidProvider;
import com.black.opensdk.uidclient.client.CachedUidGeneratorClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

/**
 * @Author: chen
 * @Description:
 * @Date: 2018/11/29 上午10:48
 */


public class ExampleByOneThread {


  public static final Logger log = LoggerFactory.getLogger(ExampleByOneThread.class);

  public static class IdProvider implements UidProvider {

    public static final Logger log = LoggerFactory.getLogger(IdProvider.class);

    private static final AtomicLong atomLong = new AtomicLong(0);

    @Override
    public List <Long> provide(int count) {

      log.info("Get batch id [" + count + "]");
      List <Long> list = new ArrayList <>(count);
      for (int i = 0; i < count; i++) {
        list.add(atomLong.incrementAndGet());
      }
      return list;
    }

    @Override
    public Long provide() {
      log.info("Get on id ");
      return atomLong.incrementAndGet();
    }
  }

  public static void main(String[] args) throws Exception {

    CachedUidGeneratorClient cachedUidGenerator = new CachedUidGeneratorClient();

    /**
     * init config of Generator
     */
    cachedUidGenerator.setInitBufferSize(1 << 4);
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

    Set <Long> set = new ConcurrentSkipListSet <>();
    int num = 10 * 1024;
    for (int i = 0; i < num; i++) {
      set.add(cachedUidGenerator.getUID());
    }

    log.info("END GET UID !");

    Assert.isTrue(num == set.size(), "数量出错!");
    log.info(set.toString());

    /**
     * when program is shutdown , generator must destroyed
     */
    cachedUidGenerator.destroy();
  }
}
