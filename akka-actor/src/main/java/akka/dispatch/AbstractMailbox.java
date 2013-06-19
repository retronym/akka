/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.dispatch;

import akka.util.Unsafe;

public final class AbstractMailbox {
    public final static long mailboxStatusOffset;
    public final static long systemMessageOffset;

    static {
        try {
          mailboxStatusOffset = Unsafe.instance.objectFieldOffset(Mailbox.class.getDeclaredField("_statusDoNotCallMeDirectly"));
          systemMessageOffset = Unsafe.instance.objectFieldOffset(Mailbox.class.getDeclaredField("_systemQueueDoNotCallMeDirectly"));
        } catch(Throwable t){
            throw new ExceptionInInitializerError(t);
        }
    }
}
