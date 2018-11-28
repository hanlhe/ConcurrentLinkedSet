import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

  private static volatile boolean failed = false;

  public static void main(String[] args) {
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
          for (i = 0; i < 100000000 && !failed; i++) {
            int o = r.nextInt(10), n = r.nextInt(10);
            int p = r.nextInt(300);
            if (p < 100) {
//              System.out.printf("%d-%d: %b replace %d with %d. %s\n", thread_id, i,
//                linkedSet.replace(o, n), o, n, linkedSet);
              linkedSet.replace(o, n);
            } else if (p < 200) {
//              System.out.printf("%d-%d: %b add %d. %s\n", thread_id, i,
//                linkedSet.add(n), n, linkedSet);
              linkedSet.add(n);
            } else {
//              System.out.printf("%d-%d: %b remove %d. %s\n", thread_id, i,
//                linkedSet.remove(o), o, linkedSet);
              linkedSet.remove(o);
            }
            if (!linkedSet.isSorted()) {
              failed = true;
              return;
            }
//            System.out.println(i);
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

    Thread[] ts = new Thread[32];
    for (int i = 0; i < 32; i++)
      ts[i] = new Thread(runnable);
    for (int i = 0; i < 7; i++)
      ts[i].start();

//    System.out.println(linkedSet.add(3));
//    System.out.println(linkedSet);
//    System.out.println(linkedSet.add(0));
//    System.out.println(linkedSet);
//    System.out.println(linkedSet.replace(0, 4));
//    System.out.println(linkedSet);

  }
}
