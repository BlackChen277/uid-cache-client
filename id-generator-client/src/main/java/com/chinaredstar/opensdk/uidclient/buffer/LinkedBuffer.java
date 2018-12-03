
package com.chinaredstar.opensdk.uidclient.buffer;

import com.chinaredstar.opensdk.uidclient.exception.UidGenerateException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

/**
 * 队列缓冲区,基于ConcurrentLinkedQueue 实现,频繁读写,内存碎片较多,容易造成GC.
 * @author  chen
 */
public class LinkedBuffer extends AbstractBuffer {

  private static final Logger LOGGER = LoggerFactory.getLogger(LinkedBuffer.class);

  private ConcurrentLinkedQueue <Long> concurrentLinkedQueue;

  private AtomicInteger currentSize;


  /**
   * init Buffer with bufferSize;
   * @param bufferSize 队列长度;
   */
  public LinkedBuffer(int bufferSize) {
    this(bufferSize, DEFAULT_UNDER_PADDING_PERCENT, DEFAULT_UPPER_PADDING_PERCENT);
  }

  /**
   *  init Buffer with bufferSize,paddingUnderFactor,paddingUpperFactor;
   *  paddingUnderFactor and paddingUpperFactor must be 0 - 100;
   *  paddingUnderFactor must less than  paddingUpperFactor;
   * @param bufferSize 队列长度
   * @param paddingUnderFactor 队列低水位
   * @param paddingUpperFactor 队列高水位
   */
  public LinkedBuffer(int bufferSize, int paddingUnderFactor, int paddingUpperFactor) {

    Assert.isTrue(bufferSize > 0L, "RingBuffer size must be positive");
    Assert.isTrue(paddingUpperFactor > paddingUnderFactor,
        "paddingUpperFactor  must more than paddingUnderFactor");
    Assert.isTrue(paddingUnderFactor > 0 && paddingUnderFactor < 100 && paddingUpperFactor > 0
        && paddingUpperFactor < 100, "factor must 0 - 100");

    this.bufferSize = bufferSize;
    currentSize = new AtomicInteger(0);
    concurrentLinkedQueue = new ConcurrentLinkedQueue <>();
    this.paddingUnderThreshold = bufferSize * paddingUnderFactor / 100;
    this.paddingUpperThreshold = bufferSize * paddingUpperFactor / 100;
  }


  @Override
  public  boolean put(long uid) {
    //数量超出,因为linked获取size是需要遍历链表,效率低下,故需要主动进行size记录.
    if (bufferSize < currentSize.get() + 1) {
      rejectedPutHandler.rejectPutBuffer(this, uid);
    }
    if (concurrentLinkedQueue.add(uid)) {
      currentSize.incrementAndGet();
      return true;
    } else {
      return false;
    }
  }

  @Override
  public long take() {
    if (paddingUpperThreshold < (currentSize.get() - 1)) {
      bufferPaddingExecutor.asyncPadding();
    }
    Long uid = concurrentLinkedQueue.poll();
    if (uid == null) {
      rejectedTakeHandler.rejectTakeBuffer(this);
    }
    currentSize.decrementAndGet();
    return uid;
  }

  @Override
  public int paddingNums() {
    return bufferSize - currentSize.get();
  }

  /**
   * 判断是否达到高水位上限.
   *
   * @return boolean
   */
  @Override
  public boolean isNeedPadding() {
    return currentSize.get() >= paddingUpperThreshold;
  }


  @Override
  public String toString() {
    return "LinkedBuffer{" + "{bufferSize=" + bufferSize + ", currentSize=" + currentSize
        +", paddingUnderThreshold=" + paddingUnderThreshold + ", paddingUpperThreshold="
        + paddingUpperThreshold + "}" + "Buffer:[" +concurrentLinkedQueue.toString() + "]";
  }
}
