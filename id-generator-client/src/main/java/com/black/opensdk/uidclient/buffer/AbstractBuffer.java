package com.black.opensdk.uidclient.buffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author: chen
 * @Description:
 * @Date: 2018/11/30 下午12:06
 */
public abstract class AbstractBuffer implements Buffer {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractBuffer.class);


  protected int bufferSize;
  /**
   * 默认添加最低水位 和 最高水位
   */
  public static final int DEFAULT_UNDER_PADDING_PERCENT = 40;
  public static final int DEFAULT_UPPER_PADDING_PERCENT = 80;


  protected BufferPaddingExecutor bufferPaddingExecutor;

  /**
   * Threshold for trigger padding buffer
   */
  protected int paddingUnderThreshold;

  /**
   * Threshold for off padding buffer
   */
  protected int paddingUpperThreshold;


  /**
   * 默认添加(put)拒绝策略 Discard policy for {@link RejectedPutBufferHandler}, we just do logging
   */
  protected void discardPutBuffer(Buffer uidBuffer, long uid) {
    LOGGER.warn("Rejected putting buffer for uid:{}. {}", uid, uidBuffer);
  }

  /**
   * 默认获取(take)拒绝策略 Policy for {@link RejectedTakeBufferHandler}, throws {@link RuntimeException}
   * after logging
   */
  protected void exceptionRejectedTakeBuffer(Buffer uidBuffer) {
    LOGGER.warn("Rejected take buffer. {}", uidBuffer);
    throw new RuntimeException("Rejected take buffer. " + uidBuffer);
  }

  /**
   * 拒绝策略: 添加拒绝策略,获取拒绝策略.
   */
  protected RejectedPutBufferHandler rejectedPutHandler = this::discardPutBuffer;
  protected RejectedTakeBufferHandler rejectedTakeHandler = this::exceptionRejectedTakeBuffer;

  /**
   * Setters
   */
  public void setBufferPaddingExecutor(BufferPaddingExecutor bufferPaddingExecutor) {
    this.bufferPaddingExecutor = bufferPaddingExecutor;
  }

  public void setRejectedPutHandler(RejectedPutBufferHandler rejectedPutHandler) {
    this.rejectedPutHandler = rejectedPutHandler;
  }

  public void setRejectedTakeHandler(RejectedTakeBufferHandler rejectedTakeHandler) {
    this.rejectedTakeHandler = rejectedTakeHandler;
  }

}
