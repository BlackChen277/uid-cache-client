package com.chinaredstar.opensdk.uidclient.buffer;

/**
 * @Author: chen
 * @Description:
 * @Date: 2018/11/30 上午10:45
 */

public interface Buffer {

  /**
   * 添加
   */
  boolean put(long uid);

  /**
   * 获取
   */
  long take();

  /**
   * 需要添加的个数
   * @return 返回需添加的个数
   */
  int paddingNums();

  /**
   * 是否需要添加
   * @return 返回是否还需再进行添加
   */
  boolean isNeedPadding();

}
