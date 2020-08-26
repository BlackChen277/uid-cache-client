
package com.black.opensdk.uidclient.buffer;

import com.black.opensdk.uidclient.util.NamingThreadFactory;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;


/**
 * 添加UID 线程池封装. 包括schedule线程池和普通线程池.
 *
 * @author chen
 */
public class BufferPaddingExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RingBuffer.class);

  private static final String WORKER_NAME = "RingBuffer-Worker";
  private static final String SCHEDULE_NAME = "RingBuffer-Schedule";

  /**
   * 默认 3 分种添加一次.
   */
  public static final long DEFAULT_SCHEDULE_INTERVAL = 3 * 60L;

  /**
   * 标志位 Whether buffer padding is running
   */
  private final AtomicBoolean running;

  /**
   * RingBuffer & BufferUidProvider
   */
  private final AbstractBuffer abstractBuffer;
  private final UidProvider uidProvider;

  /**
   * Padding immediately by the thread pool
   */
  private final ExecutorService bufferPadExecutors;
  /**
   * Padding schedule thread
   */
  private final ScheduledExecutorService bufferPadSchedule;

  /**
   * Schedule interval Unit as seconds
   */
  private long scheduleInterval = DEFAULT_SCHEDULE_INTERVAL;


  /**
   * Constructor with {@link AbstractBuffer} and {@link UidProvider}, default use schedule
   *
   * @param abstractBuffer {@link AbstractBuffer}
   * @param uidProvider {@link UidProvider}
   */
  public BufferPaddingExecutor(AbstractBuffer abstractBuffer, UidProvider uidProvider) {
    this(abstractBuffer, uidProvider, true);
  }

  /**
   * Constructor with {@link RingBuffer}, {@link UidProvider}, and whether use schedule
   * padding
   *
   * @param abstractBuffer {@link AbstractBuffer}
   * @param uidProvider {@link UidProvider}
   */
  public BufferPaddingExecutor(AbstractBuffer abstractBuffer, UidProvider uidProvider,
      boolean usingSchedule) {
    this.running = new AtomicBoolean(false);
    this.abstractBuffer = abstractBuffer;
    this.uidProvider = uidProvider;

    // initialize thread pool
    bufferPadExecutors = new ThreadPoolExecutor(1, 1, 100, TimeUnit.SECONDS,
        new ArrayBlockingQueue <Runnable>(1), new NamingThreadFactory(WORKER_NAME),
        new ThreadPoolExecutor.DiscardPolicy());

    // initialize schedule thread
    if (usingSchedule) {
      bufferPadSchedule = Executors
          .newSingleThreadScheduledExecutor(new NamingThreadFactory(SCHEDULE_NAME));
    } else {
      bufferPadSchedule = null;
    }
  }

  /**
   * Start executors such as schedule
   */
  public void start() {
    if (bufferPadSchedule != null) {
      bufferPadSchedule
          .scheduleWithFixedDelay(this::paddingBuffer, scheduleInterval, scheduleInterval,
              TimeUnit.SECONDS);
    }
  }

  /**
   * Shutdown executors
   */
  public void shutdown() {
    if (!bufferPadExecutors.isShutdown()) {
      bufferPadExecutors.shutdownNow();
    }

    if (bufferPadSchedule != null && !bufferPadSchedule.isShutdown()) {
      bufferPadSchedule.shutdownNow();
    }
  }

  /**
   * Whether is padding
   */
  public boolean isRunning() {
    return running.get();
  }

  /**
   * Padding buffer in the thread pool
   */
  public void asyncPadding() {
    bufferPadExecutors.submit(this::paddingBuffer);
  }

  /**
   * Padding buffer fill the slots until to catch the cursor
   */
  public synchronized void  paddingBuffer() {

    try {

      LOGGER.info("Ready to padding buffer  {}", abstractBuffer);
      boolean isFullRingBuffer = false;

      while (!isFullRingBuffer) {
        int count = abstractBuffer.paddingNums();
        if (LOGGER.isDebugEnabled()) {
          LOGGER.debug("Padding Count is {}", count);
        }
        List <Long> uidList = uidProvider.provide(count);
        for (Long uid : uidList) {
          isFullRingBuffer = !abstractBuffer.put(uid);
          if (isFullRingBuffer) {
            break;
          }
        }
        if (abstractBuffer.isNeedPadding()) {
          break;
        }
      }

      LOGGER.info("End to padding buffer {}", abstractBuffer);
    } catch (Exception e) {
      LOGGER.error("Padding Buffer Error!");
    }
  }

  /**
   * Setters
   */
  public void setScheduleInterval(long scheduleInterval) {
    Assert.isTrue(scheduleInterval > 0, "Schedule interval must positive!");
    this.scheduleInterval = scheduleInterval;
  }

}
