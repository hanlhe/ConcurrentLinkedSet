import java.time.Instant;
import java.util.Random;

public class Main {

  public static void main(String[] args) {
    final ConcurrentLinkedSet<Integer> linkedSet = new ConcurrentLinkedSet<>();

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        Random r = new Random(Instant.now().getNano());
        for (int i = 0; i < 100000000; i++) {
          int o = r.nextInt(5), n = r.nextInt(5);
          System.out.printf("%d: replace %d with %d. %s\n", i, o, n, linkedSet);
          System.out.println(linkedSet.replace(o, n));
          System.out.printf("%d: replace %s\n", i, linkedSet);
          assert linkedSet.isSorted();
        }
      }
    };

    Thread[] ts = new Thread[8];
    for (int i = 0; i < 8; i++)
      ts[i] = new Thread(runnable);
    for (int i = 0; i < 8; i++)
      ts[i].start();

//    System.out.println(linkedSet.add(3));
//    System.out.println(linkedSet);
//    System.out.println(linkedSet.add(0));
//    System.out.println(linkedSet);
//    System.out.println(linkedSet.replace(0, 4));
//    System.out.println(linkedSet);

    System.out.println(linkedSet.isSorted() ? "sorted" : "not sorted");
    System.out.println(linkedSet);
  }
}
