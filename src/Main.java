import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

  private static volatile boolean failed = false;

  public static void main(String[] args) {

    int itr = 100000;
    if (args.length > 0)
      itr = Integer.parseInt(args[0]);
    int threads = 8;
    if (args.length > 1)
      threads = Integer.parseInt(args[1]);

    final int iteration = itr;

    final ConcurrentLinkedSet<Integer> linkedSet = new ConcurrentLinkedSet<>();

    final AtomicInteger id = new AtomicInteger(0);
    ThreadLocal<Integer> THREAD_ID = ThreadLocal.withInitial(id::getAndIncrement);

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        Timer timer = new Timer();
        final int thread_id = THREAD_ID.get();
        int i = 0;
        try {
          Random r = new Random(Instant.now().getNano());
          for (i = 0; i < iteration && !failed; i++) {
            int o = r.nextInt(10), n = r.nextInt(10);
            int p = r.nextInt(300);
            if (p < 100) {
              linkedSet.replace(o, n);
            } else if (p < 200) {
              linkedSet.add(n);
            } else {
              linkedSet.delete(o);
            }
            if (!linkedSet.isSorted()) {
              failed = true;
              return;
            }
          }
        } finally {
          System.out.printf("Thread %d stopped at iteration %d, result: %s\n",
            thread_id, i, (failed ? "failed" : "success"));
          System.out.println(linkedSet.isSorted() ? "sorted" : "not sorted");
          System.out.println(linkedSet);
          System.out.println(timer.end().toString());
        }
      }
    };

    Thread[] ts = new Thread[threads];
    for (int i = 0; i < threads; i++)
      ts[i] = new Thread(runnable);
    for (int i = 0; i < threads; i++)
      ts[i].start();
  }
}
