# ConcurrentLinkedSet

To compile, run `javac Main.java` in `src` folder.

To execute, run `java Main <iteration> <thread_num>` in `src` folder.

An example would be:

```
> java Main 1000000 4
Thread 0 stopped at iteration 1000000, result: success
sorted
[0, 2, 5, 7, ]
Time: 1708 msc.

Thread 1 stopped at iteration 1000000, result: success
sorted
[2, 3, 4, 5, 6, 8, ]
Time: 1720 msc.

Thread 3 stopped at iteration 1000000, result: success
sorted
[2, 3, 5, 6, 7, 8, ]
Time: 1724 msc.

Thread 2 stopped at iteration 1000000, result: success
sorted
[0, 1, 3, 4, 8, ]
Time: 1725 msc.
```