
package com.black.opensdk.uidclient.buffer;


/**
 * 获取拒绝策略
 * @author  chen
 */
@FunctionalInterface
public interface RejectedTakeBufferHandler {

    /**
     * Reject take buffer request,this must throw RuntimeException
     *
     * @param buffer
     * @exception  RuntimeException must reject program running.
     */
    void rejectTakeBuffer(Buffer buffer) throws RuntimeException;
}
