
package com.chinaredstar.opensdk.uidclient.buffer;

import com.chinaredstar.opensdk.uidclient.util.PaddedAtomicLong;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

/**
 * 环形队列缓冲区
 * @author chen
 */
public class RingBuffer extends AbstractBuffer {

  private static final Logger LOGGER = LoggerFactory.getLogger(RingBuffer.class);

  private Lock lock = new ReentrantLock();
  /**
   * 常量
   */
  private static final int START_POINT = -1;
  private static final long CAN_PUT_FLAG = 0L;
  private static final long CAN_TAKE_FLAG = 1L;


  /**
   * 环形队列长度, 计算位置掩码,uid槽,标志位槽
   */
  private final long indexMask;
  private final long[] slots;
  private final PaddedAtomicLong[] flags;
  private final AtomicLong useFlag = new PaddedAtomicLong(START_POINT);

  /**
   * 末尾标志
   */
  private final AtomicLong tail = new PaddedAtomicLong(START_POINT);

  /**
   * 读取位标志
   */
  private final AtomicLong cursor = new PaddedAtomicLong(START_POINT);

  /**
   * @param bufferSize bufferSize 必须是2的倍数
   */
  public RingBuffer(int bufferSize) {
    this(bufferSize, DEFAULT_UNDER_PADDING_PERCENT, DEFAULT_UPPER_PADDING_PERCENT);
  }

  /**
   * @param bufferSize must be positive & a power of 2
   * @param paddingUnderFactor percent in (0 - 100). When the count of rest available UIDs reach the
   * threshold, it will trigger padding buffer<br> Sample: paddingFactor=20, bufferSize=1000 ->
   * threshold=1000 * 20 /100, padding buffer will be triggered when tail-cursor<threshold
   * @param paddingUpperFactor percent in (0 - 100) and paddingUpperFactor > paddingUnderFactor
   */
  public RingBuffer(int bufferSize, int paddingUnderFactor, int paddingUpperFactor) {

    Assert.isTrue(bufferSize > 0L, "RingBuffer size must be positive");
    Assert.isTrue(paddingUpperFactor > paddingUnderFactor,
        "paddingUpperFactor  must more than paddingUnderFactor");
    Assert.isTrue(Integer.bitCount(bufferSize) == 1, "RingBuffer size must be a power of 2");
    Assert.isTrue(paddingUnderFactor > 0 && paddingUnderFactor < 100 && paddingUpperFactor > 0
        && paddingUpperFactor < 100, "factor must 0 - 100");

    this.bufferSize = bufferSize;
    this.indexMask = bufferSize - 1;
    this.slots = new long[bufferSize];
    this.flags = initFlags(bufferSize);

    this.paddingUnderThreshold = bufferSize * paddingUnderFactor / 100;
    this.paddingUpperThreshold = bufferSize * paddingUpperFactor / 100;
  }

  /**
   * 添加uid,并移动末尾标志.
   *
   * @return false means that the buffer is full, apply {@link RejectedPutBufferHandler}
   */
  @Override
  public synchronized boolean put(long uid) {
    long currentTail = tail.get();
    long currentCursor = cursor.get();

    // tail catches the cursor, means that you can't put any cause of RingBuffer is full
    long distance = currentTail - (currentCursor == START_POINT ? 0 : currentCursor);
    if (distance == bufferSize - 1) {
      if(LOGGER.isDebugEnabled()){
        LOGGER.debug("Buffer is full!!!");
      }
      rejectedPutHandler.rejectPutBuffer(this, uid);
      return false;
    }

    // 1. pre-check whether the flag is CAN_PUT_FLAG
    int nextTailIndex = calSlotIndex(currentTail + 1);
    if (flags[nextTailIndex].get() != CAN_PUT_FLAG) {
      if(LOGGER.isDebugEnabled()){
        LOGGER.debug("CAN NOT PUT, BECAUSE FLAG IS 1 . NextTailIndex :" + nextTailIndex
            + " Current Usage Flag: " + calSlotIndex(useFlag.get()) + " Current Cursor :"
            + calSlotIndex(cursor.get()) + "flag : " + flags[nextTailIndex].get());
      }
      rejectedPutHandler.rejectPutBuffer(this, uid);
      return false;
    }

    // 2. put UID in the next slot
    // 3. update next slot' flag to CAN_TAKE_FLAG
    // 4. publish tail with sequence increase by one
    slots[nextTailIndex] = uid;
    flags[nextTailIndex].set(CAN_TAKE_FLAG);
    tail.incrementAndGet();

    return true;
  }

  /**
   * 获取uid,并移动cursor, 通过AtomicLong.updataAndGet()保证线程安全.
   *
   * Before getting the UID, we also check whether reach the padding threshold, the padding buffer
   * operation will be triggered in another thread<br> If there is no more available UID to be
   * taken, the specified {@link RejectedTakeBufferHandler} will be applied<br>
   *
   * @return UID
   * @throws IllegalStateException if the cursor moved back
   *
   * TODO: 需要优化.
   */
  @Override
  public long take() {
    long currentCursor;
    long nextCursor;

    lock.lock();
    try {
      //保证原子性 CAS
      currentCursor = cursor.get();
      nextCursor = cursor.updateAndGet(old -> old == tail.get() ? old : old + 1);
    }finally {
      lock.unlock();
    }

    // 判断是否达到低水位
    long currentTail = tail.get();
    if (currentTail - nextCursor < paddingUnderThreshold) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            "Need Padding : currentTail - nextCursor = [{} - {} = {}] paddingUnderThreshold = {}",
            currentTail, nextCursor, currentTail - currentCursor, paddingUnderThreshold);
      }
      bufferPaddingExecutor.asyncPadding();
    }

    // 如果是末尾,说明已经没有数据可取
    if (nextCursor == currentCursor) {
      rejectedTakeHandler.rejectTakeBuffer(this);
    }

    int nextCursorIndex = calSlotIndex(nextCursor);
    Assert.isTrue(flags[nextCursorIndex].get() == CAN_TAKE_FLAG, "Cursor not in can take status");

    // 位置不能交换,必须先取id,然后再设置标记位
    long uid = slots[nextCursorIndex];
    flags[nextCursorIndex].set(CAN_PUT_FLAG);


    useFlag.incrementAndGet();
    return uid;
  }

  /**
   * 获取需要添加的UID数量.
   *
   * @return count
   */
  @Override
  public int paddingNums() {
    long currentTail = tail.get();
    long currentUseFlag = useFlag.get();
    long distance = currentTail - (currentUseFlag == START_POINT ? 0 : currentUseFlag);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Current UseFlag: " + currentUseFlag + " Current Cursor: " + cursor.get()
          + "  Current Tail: " + currentTail + " Distance :" + distance);
    }
    return (int) (bufferSize - distance - 1);
  }

  /**
   * 判断是否达到高水位上限.
   *
   * @return boolean
   */
  @Override
  public boolean isNeedPadding() {
    long currentCursor = cursor.get();
    long currentTail = tail.get();
    boolean result = currentTail - currentCursor >= paddingUpperThreshold;
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Current Cursor: " + currentCursor + "Current Tail: " + currentTail
          + "Padding Upper Threshold:  " + paddingUpperThreshold);
    }
    return result;
  }


  /**
   * Calculate slot index with the slot sequence (sequence % bufferSize)
   */
  protected int calSlotIndex(long sequence) {
    return (int) (sequence & indexMask);
  }


  /**
   * Initialize flags as CAN_PUT_FLAG
   */
  private PaddedAtomicLong[] initFlags(int bufferSize) {
    PaddedAtomicLong[] flags = new PaddedAtomicLong[bufferSize];
    for (int i = 0; i < bufferSize; i++) {
      flags[i] = new PaddedAtomicLong(CAN_PUT_FLAG);
    }
    return flags;
  }

  /**
   * Getters
   */
  public long getTail() {
    return tail.get();
  }

  public long getCursor() {
    return cursor.get();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("RingBuffer [bufferSize=").append(bufferSize).append(", tail=").append(tail)
        .append(", cursor=").append(cursor).append(", useFlag=").append(useFlag)
        .append(", paddingUnderThreshold=").append(paddingUnderThreshold)
        .append(", paddingUpperThreshold=").append(paddingUpperThreshold).append("]");

    return builder.toString();
  }
}
