
package com.black.opensdk.uidclient.client;


import com.black.opensdk.uidclient.exception.UidGenerateException;
import com.black.opensdk.uidclient.buffer.AbstractBuffer;
import com.black.opensdk.uidclient.buffer.BufferPaddingExecutor;
import com.black.opensdk.uidclient.buffer.LinkedBuffer;
import com.black.opensdk.uidclient.buffer.RejectedPutBufferHandler;
import com.black.opensdk.uidclient.buffer.RejectedTakeBufferHandler;
import com.black.opensdk.uidclient.buffer.UidProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

/**
 * uid generator client.
 * @author chen
 */
public class CachedUidGeneratorClient implements UidGenerator {

  public static final Logger LOGGER = LoggerFactory.getLogger(CachedUidGeneratorClient.class);

  /**
   * 默认 4096,根据业务需求调整,但是必须是2的倍数.
   */
  private int initBufferSize = 1 << 12;

  private int paddingUnderFactor = AbstractBuffer.DEFAULT_UPPER_PADDING_PERCENT;

  private int paddingUpperFactor = AbstractBuffer.DEFAULT_UPPER_PADDING_PERCENT;

  private Long scheduleInterval;


  private RejectedPutBufferHandler rejectedPutBufferHandler;

  private RejectedTakeBufferHandler rejectedTakeBufferHandler;

  private UidProvider uidProvider;

  /**
   * 缓冲区,默认实现为LinkedBuffer.
   */
  private AbstractBuffer abstractBuffer;

  private BufferPaddingExecutor bufferPaddingExecutor;

  public void init() throws Exception {
    this.initBuffer();
    LOGGER.info("Initialized RingBuffer successfully.");
  }

  /**
   * 当第三方id提供者一直出现异常时,会抛出 {@link UidGenerateException}
   */
  @Override
  public long getUID() {
    try {
      return abstractBuffer.take();
    } catch (Exception e) {
      LOGGER.warn("Get uid from buffer warning !!,Start Get Uid Direct By Provider", e);
      try {
        return uidProvider.provide();
      } catch (Exception e1) {
        LOGGER.error("Get uid from buffer Error", e);
        throw new UidGenerateException("Get Uid Error!!");
      }
    }
  }

  public void destroy() throws Exception {
    bufferPaddingExecutor.shutdown();
  }

  /**
   * Initialize RingBuffer & RingBufferPaddingExecutor
   */
  private void initBuffer() {

    int bufferSize = initBufferSize;

    if (this.abstractBuffer == null) {
      abstractBuffer = new LinkedBuffer(initBufferSize, paddingUnderFactor, paddingUpperFactor);
    }
    LOGGER.info("Initialized ring buffer size:{}, paddingUnderFactor:{},paddingUpperFactor:{}",
        bufferSize, paddingUnderFactor, paddingUpperFactor);

    Assert.notNull(uidProvider, "Provider Can Not Be Null!");
    Assert.notNull(abstractBuffer, "Buffer Can Not Be Null!");

    boolean usingSchedule = (scheduleInterval != null);

    this.bufferPaddingExecutor = new BufferPaddingExecutor(abstractBuffer, uidProvider,
        usingSchedule);

    if (usingSchedule) {
      bufferPaddingExecutor.setScheduleInterval(scheduleInterval);
    }

    LOGGER.info("Initialized BufferPaddingExecutor. Using schedule:{}, interval:{}", usingSchedule,
        scheduleInterval);

    this.abstractBuffer.setBufferPaddingExecutor(bufferPaddingExecutor);

    if (rejectedPutBufferHandler != null) {
      this.abstractBuffer.setRejectedPutHandler(rejectedPutBufferHandler);
    }
    if (rejectedTakeBufferHandler != null) {
      this.abstractBuffer.setRejectedTakeHandler(rejectedTakeBufferHandler);
    }
    bufferPaddingExecutor.paddingBuffer();
    bufferPaddingExecutor.start();
  }


  /**
   * getter and setter
   */
  public int getInitBufferSize() {
    return initBufferSize;
  }

  public int getPaddingUnderFactor() {
    return paddingUnderFactor;
  }

  public int getPaddingUpperFactor() {
    return paddingUpperFactor;
  }

  public Long getScheduleInterval() {
    return scheduleInterval;
  }


  public UidProvider getUidProvider() {
    return uidProvider;
  }

  public AbstractBuffer getAbstractBuffer() {
    return abstractBuffer;
  }

  public void setAbstractBuffer(AbstractBuffer abstractBuffer) {
    this.abstractBuffer = abstractBuffer;
  }

  public void setInitBufferSize(int initBufferSize) {
    this.initBufferSize = initBufferSize;
  }

  public void setPaddingUnderFactor(int paddingUnderFactor) {
    this.paddingUnderFactor = paddingUnderFactor;
  }

  public void setPaddingUpperFactor(int paddingUpperFactor) {
    this.paddingUpperFactor = paddingUpperFactor;
  }

  public void setScheduleInterval(Long scheduleInterval) {
    this.scheduleInterval = scheduleInterval;
  }

  public void setRejectedPutBufferHandler(RejectedPutBufferHandler rejectedPutBufferHandler) {
    this.rejectedPutBufferHandler = rejectedPutBufferHandler;
  }

  public void setRejectedTakeBufferHandler(RejectedTakeBufferHandler rejectedTakeBufferHandler) {
    this.rejectedTakeBufferHandler = rejectedTakeBufferHandler;
  }

  public void setUidProvider(UidProvider uidProvider) {
    this.uidProvider = uidProvider;
  }
}
