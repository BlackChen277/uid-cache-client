
package com.chinaredstar.opensdk.uidclient.buffer;

import java.util.List;

/**
 * uid provider
 */

public interface UidProvider {

    /**
     * Provides UID batch
     *
     * 正常情况下,通过批量获取uid接口缓存uid, 其他线程获取已经缓存好的uid.
     * @param count 批量获取的数量
     * @return
     */
    List<Long> provide(int count);

    /**
     * Provides UID one by one
     * 非正常情况下(uid 补充速度跟不上获取速度) ,需直接调用provide()获取.
     * @return
     */
    Long provide();
}
