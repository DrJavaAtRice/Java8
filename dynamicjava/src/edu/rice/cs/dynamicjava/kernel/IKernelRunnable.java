package edu.rice.cs.dynamicjava.kernel;

public interface IKernelRunnable extends Runnable {
  public void start();
  public void shutdown();
}
