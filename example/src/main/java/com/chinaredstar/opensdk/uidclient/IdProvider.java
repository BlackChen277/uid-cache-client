package com.chinaredstar.opensdk.uidclient;

import com.chinaredstar.opensdk.uidclient.buffer.UidProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author: chen
 * @Description:
 * @Date: 2018/11/29 下午1:27
 */

public class IdProvider implements UidProvider {

  public static final Logger log = LoggerFactory.getLogger(IdProvider.class);

  private static final AtomicLong atomLong = new AtomicLong(0);

  private static final AtomicLong atomLong2 = new AtomicLong(100000000);

  @Override
  public List<Long> provide(int count) {

    log.info("Get batch count: [" + count + "]");
    List <Long> list = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      long r = atomLong.incrementAndGet();
      log.info("GET Batch :"+ r);
      list.add(r);
    }
    return list;
  }

  @Override
  public Long provide() {
    try {
      TimeUnit.MILLISECONDS.sleep(1);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    long res = atomLong2.incrementAndGet();
    log.info("Get One id  + "+ res);
    return res;
  }
}
