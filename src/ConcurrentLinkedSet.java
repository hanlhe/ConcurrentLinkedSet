import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A concurrent linked list that supports four operations, namely
 * {@code contains}, {@code insert}, {@code delete} and {@code replace}, using
 * lazy synchronization approach.
 *
 * @param <E> the type of elements in this list
 */
public class ConcurrentLinkedSet<E> {
  private class Node implements Comparable<Node> {
    private E item;
    private int key;
    private volatile boolean marked;
    private volatile Node next;
    private volatile Node replaceNode;
    private ReentrantLock lock;

    private Node() {
      this.lock = new ReentrantLock();
    }

    private Node(E item) {
      this(item, null);
    }

    private Node(E item, Node replaced) {
      this.item = item;
      this.key = item.hashCode();
      this.marked = false;
      this.next = null;
      this.replaceNode = replaced;
      this.lock = new ReentrantLock();
    }

    private void lock() {
      this.lock.lock();
    }

    private void unlock() {
      this.lock.unlock();
    }

    @Override
    public int compareTo(Node o) {
      if (this == o)
        return 0;
      if (this == head || o == tail)
        return -1;
      if (o == head || this == tail)
        return 1;
      return Integer.compare(this.key, o.key);
    }

    @Override
    public String toString() {
      if (this == head)
        return "[" + this.next;
      if (this == tail)
        return "]";

      return this.item + ", " + this.next;
    }

    public boolean isSorted(Node pred) {
      if (this == tail)
        return true;
      return pred.compareTo(this) < 0 && this.next.isSorted(this);
    }
  }

  final private Node head;
  final private Node tail;

  public ConcurrentLinkedSet() {
    this.head = new Node();
    this.tail = new Node();
    this.head.next = tail;
  }

  public boolean add(E item) {
    int key = item.hashCode();
    while (true) {
      List<Node> windows = find(key);
      Node pred = windows.get(0);
      Node curr = windows.get(1);
      pred.lock();
      try {
        curr.lock();
        try {
          if (validate(pred, curr)) {
            if (curr != tail && curr.key == key) {
              return false;
            } else {
              Node node = new Node(item);
              node.next = curr;
              pred.next = node;
              return true;
            }
          }
        } finally {
          curr.unlock();
        }
      } finally {
        pred.unlock();
      }
    }
  }

  public boolean remove(E item) {
    int key = item.hashCode();
    while (true) {
      List<Node> windows = find(key);
      Node pred = windows.get(0);
      Node curr = windows.get(1);
      pred.lock();
      try {
        curr.lock();
        try {
          if (validate(pred, curr)) {
            if (curr == tail || curr.key != key) {
              return false;
            } else {
              curr.marked = true;
              pred.next = curr.next;
              return true;
            }
          }
        } finally {
          curr.unlock();
        }
      } finally {
        pred.unlock();
      }
    }
  }

  public boolean replace(E o, E n) {
    int keyOld = o.hashCode();
    int keyNew = n.hashCode();
    if (keyOld == keyNew) {
      return add(n);
    }
    while (true) {
      List<Node> windowsOld = find(keyOld);
      Node predOld = windowsOld.get(0);
      Node currOld = windowsOld.get(1);

      List<Node> windowsNew = find(keyNew);
      Node predNew = windowsNew.get(0);
      Node currNew = windowsNew.get(1);

      List<Node> list = new ArrayList<>();
      list.addAll(windowsNew);
      list.addAll(windowsOld);
      Collections.sort(list);

      list.get(0).lock();
      try {
        list.get(1).lock();
        try {
          list.get(2).lock();
          try {
            list.get(3).lock();
            try {
              if (!validate(predNew, currNew) || !validate(predOld, currOld))
                continue;
              return replace(o, n, predOld, currOld, predNew, currNew);
            } finally {
              list.get(3).unlock();
            }
          } finally {
            list.get(2).unlock();
          }
        } finally {
          list.get(1).unlock();
        }
      } finally {
        list.get(0).unlock();
      }
    }
  }

  public boolean contains(E item) {
    int key = item.hashCode();
    Node curr = head;
    while (curr.key < key)
      curr = curr.next;
    return curr.key == key && !curr.marked && (curr.replaceNode == null || curr.replaceNode.marked);
  }

  private boolean validate(Node pred, Node curr) {
    return !pred.marked && !curr.marked && pred.next == curr &&
      (curr.replaceNode == null || curr.replaceNode.marked) &&
      (pred.replaceNode == null || pred.replaceNode.marked);
  }

  private List<Node> find(int key) {
    Node pred = head;
    Node curr = head.next;
    while (curr != tail && curr.key < key) {
      pred = curr;
      curr = curr.next;
    }
    return Arrays.asList(pred, curr);
  }

  private boolean replace(E oldElement, E newElement,
                          Node predOld, Node currOld,
                          Node predNew, Node currNew) {
    if (currOld == tail || currOld.key != oldElement.hashCode()) {
      // old element does not exist
      if (currNew == tail || currNew.key != newElement.hashCode()) {
        // if new element does not exist, add new element.
        Node node = new Node(newElement);
        node.next = currNew;
        predNew.next = node;
        return true;
      } else {
        // if new element already exists, no change needed.
        return false;
      }
    } else {
      // old element does exist
      if (currNew == tail || currNew.key != newElement.hashCode()) {
        // if new element does not exist, add new element, mark
        // old element as deleted, and remove old element.
        Node node = new Node(newElement, currOld);
        node.lock();
        try {
          node.next = currNew;
          predNew.next = node;
          node.replaceNode.marked = true; // serialization point.
          if (predOld.next == currOld)
            predOld.next = currOld.next;
          else
            node.next = currOld.next;
          node.replaceNode = null;
          return true;
        } finally {
          node.unlock();
        }
      } else {
        // if new element already exists, no need to add,
        // mark old element as deleted and remove old element.
        currOld.marked = true; // serialization point.
        predOld.next = currOld.next;
        return true;
      }
    }
  }

  public boolean isSorted() {
    return head.next.isSorted(head);
  }

  @Override
  public String toString() {
    return head.toString();
  }
}
