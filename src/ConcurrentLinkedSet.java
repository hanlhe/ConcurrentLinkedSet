import java.util.Iterator;
import java.util.TreeSet;
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
    private boolean marked;
    private Node next;
    private Node replaced;
    private Lock lock;

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
      this.replaced = replaced;
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

//    @Override
//    public String toString() {
//      return "Node{" +
//        "item=" + item +
//        ", marked=" + marked +
//        ", next=" + next +
//        ", replaced=" + replaced +
//        '}';
//    }
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
      Node pred = find(key);
      Node curr = pred.next;
      pred.lock();
      try {
        curr.lock();
        try {
          if (validate(pred, curr)) {
            if (curr.key == key) {
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
      Node pred = find(key);
      Node curr = pred.next;
      pred.lock();
      try {
        curr.lock();
        try {
          if (validate(pred, curr)) {
            if (curr.key != key) {
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
      Node predNew = find(keyNew);
      Node currNew = predNew.next;
      Node predOld = find(keyOld);
      Node currOld = predOld.next;

      if (!validate(predNew, currNew) || !validate(predOld, currOld))
        continue;

      if (predOld.compareTo(predNew) < 0) {
        // if old element's window is before new element's window
        // predOld -> predNew
        predOld.lock();
        try {
          currOld.lock();
          try {
            predNew.lock();
            try {
              currNew.lock();
              try {
                return replace(o, n, predOld, currOld, predNew, currNew);
              } finally {
                currNew.unlock();
              }
            } finally {
              predNew.unlock();
            }
          } finally {
            currOld.unlock();
          }
        } finally {
          predOld.unlock();
        }
      } else if (predOld.compareTo(predNew) > 0) {
        predNew.lock();
        try {
          currNew.lock();
          try {
            predOld.lock();
            try {
              currOld.lock();
              try {
                return replace(o, n, predOld, currOld, predNew, currNew);
              } finally {
                currOld.unlock();
              }
            } finally {
              predOld.unlock();
            }
          } finally {
            currNew.unlock();
          }
        } finally {
          predNew.unlock();
        }
      } else {
        // two windows are the same.
        // predNew (predOld) -> currNew (currOld)
        predNew.lock();
        try {
          currNew.lock();
          try {
            return replace(o, n, predOld, currOld);
          } finally {
            currNew.unlock();
          }
        } finally {
          predNew.unlock();
        }
      }
    }
  }

  public boolean contains(E item) {
    int key = item.hashCode();
    Node curr = head;
    while (curr.key < key)
      curr = curr.next;
    return curr.key == key && !curr.marked && (curr.replaced == null || curr.replaced.marked);
  }

  private boolean validate(Node pred, Node curr) {
    return !pred.marked && !curr.marked && pred.next == curr;
  }

  private Node find(int key) {
    Node pred = head;
    Node curr = head.next;
    while (curr != tail && curr.key < key) {
      pred = curr;
      curr = curr.next;
    }
    return pred;
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
        node.next = currNew;
        predNew.next = node;
        currOld.marked = true; // serialization point.
        predOld.next = currOld.next;
        node.replaced = null;
        return true;
      } else {
        // if new element already exists, no need to add,
        // mark old element as deleted and remove old element.
        currOld.marked = true; // serialization point.
        predOld.next = currOld.next;
        return true;
      }
    }
  }

  private boolean replace(E oldElement, E newElement, Node pred, Node curr) {
    if (curr == tail || curr.key != oldElement.hashCode()) {
      // old element does not exist
      if (curr == tail || curr.key != newElement.hashCode()) {
        // if new element does not exist, add new element.
        Node node = new Node(newElement);
        node.next = curr;
        pred.next = node;
        return true;
      } else {
        // if new element already exists, no change needed.
        return false;
      }
    } else {
      // old element does exist
      if (curr.key != newElement.hashCode()) {
        // if new element does not exist, add new element, mark
        // old element as deleted, and remove old element.
        Node node = new Node(newElement, curr);
        node.next = curr;
        pred.next = node;
        curr.marked = true; // serialization point.
        node.next = curr.next;
        node.replaced = null;
        return true;
      } else {
        // if new element already exists, no need to add,
        // mark old element as deleted and remove old element.
        curr.marked = true; // serialization point.
        pred.next = curr.next;
        return true;
      }
    }
  }

  public boolean isSorted() {
    if (head.next == tail)
      return true;
    Node itr = head.next;
    while (itr.next != tail) {
      if (itr.compareTo(itr.next) >= 0)
        return false;
      itr = itr.next;
    }
    return true;
  }

  @Override
  public String toString() {
    if (head.next == tail)
      return "[]";
    StringBuilder sb = new StringBuilder("[");
    Node itr = head.next;
    while (itr != tail) {
      sb.append(itr.item);
      itr = itr.next;
      if (itr != tail)
        sb.append(", ");
    }
    sb.append("]");
    return sb.toString();
//    return head.toString();
  }
}
