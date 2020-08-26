
package com.black.opensdk.uidclient.buffer;


/**
 * 拒绝添加策略
 */
@FunctionalInterface
public interface RejectedPutBufferHandler {

    /**
     * Reject put buffer request
     * 
     * @param buffer
     * @param uid
     */
    void rejectPutBuffer(Buffer buffer, long uid);
}
