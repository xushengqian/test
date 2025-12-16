package com.example.taskqueue;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一命名线程，便于定位与观测。
 */
public final class NamedThreadFactory implements ThreadFactory {
  private final String prefix;
  private final boolean daemon;
  private final AtomicInteger idx = new AtomicInteger(1);

  public NamedThreadFactory(String prefix, boolean daemon) {
    this.prefix = prefix;
    this.daemon = daemon;
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread t = new Thread(r);
    t.setName(prefix + "-" + idx.getAndIncrement());
    t.setDaemon(daemon);
    return t;
  }
}
