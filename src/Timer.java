public class Timer {
  long startTime, endTime, elapsedTime, memAvailable, memUsed;

  public Timer() {
    startTime = System.currentTimeMillis();
  }

  public void start() {
    startTime = System.currentTimeMillis();
  }

  public Timer end() {
    endTime = System.currentTimeMillis();
    elapsedTime = endTime - startTime;
    memAvailable = Runtime.getRuntime().totalMemory();
    memUsed = memAvailable - Runtime.getRuntime().freeMemory();
    return this;
  }

  public long getElapsedTime() {
    return elapsedTime;
  }

  public long getMemAvailable() {
    return memAvailable;
  }

  public long getMemUsed() {
    return memUsed;
  }

  public String toString() {
    return "Time: " + elapsedTime + " msc.\n";
  }
}
