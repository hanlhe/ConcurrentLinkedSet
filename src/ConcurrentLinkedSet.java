import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A concurrent linked set (list) that supports four operations, namely
 * {@code contains}, {@code insert}, {@code delete} and {@code replace}, using
 * lazy synchronization approach.
 * <p>
 * The elements in the linked set are sorted by the natural order of the
 * element's hashcode.
 *
 * @param <E> the type of elements in this list
 */
public class ConcurrentLinkedSet<E> {

  /**
   * Internal node class, implementing {@code Comparable} interface to
   * determine order in the linked list.
   */
  private class Node implements Comparable<Node> {

    /**
     * Element store in the node with generic type E.
     */
    private E item;

    /**
     * The hashcode of the element, store for quick access.
     */
    private int key;

    /**
     * Flag used to logically delete/replace a node.
     */
    private volatile boolean marked;

    /**
     * Next node in linked list.
     */
    private volatile Node next;

    /**
     * Node to be replaced with current node.
     */
    private volatile Node replaceNode;

    /**
     * Lock for the node.
     */
    private ReentrantLock lock;

    /**
     * Constructor used only for {@code head} and {@code tail} node.
     */
    private Node() {
      this.lock = new ReentrantLock();
    }

    /**
     * Constructor used in {@code add} function.
     *
     * @param item New element in the node.
     */
    private Node(E item) {
      this(item, null);
    }

    /**
     * Full constructor with node to be replaced.
     *
     * @param item     New element in the node.
     * @param replaced Node to be replace by this node.
     */
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

    /**
     * Helper function to recursively determine if a linked list is sorted.
     *
     * @param pred Predecessor of current node.
     * @return True if linked list from current node is sorted and false
     * otherwise.
     */
    private boolean isSorted(Node pred) {
      if (this == tail)
        return true;
      return pred.compareTo(this) < 0 && this.next.isSorted(this);
    }
  }

  /**
   * Head and tail sentinel node of the linked list.
   */
  final private Node head;
  final private Node tail;

  /**
   * Constructor of the {@code ConcurrentLinkedSet}, creating an empty linked
   * set (list) with only {@code head} and {@code tail} sentinel nodes.
   */
  public ConcurrentLinkedSet() {
    this.head = new Node();
    this.tail = new Node();
    this.head.next = tail;
  }

  /**
   * Add a new element into the linked list set if not present.
   *
   * @param item New element to be added.
   * @return True if the linked list set is modified.
   */
  public boolean add(E item) {
    int key = item.hashCode();
    while (true) {
      // first locate the window.
      List<Node> windows = find(key);
      Node pred = windows.get(0);
      Node curr = windows.get(1);

      //acquire lock for both node in the window.
      pred.lock();
      try {
        curr.lock();
        try {
          // validate the window before modify the list.
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

  /**
   * Remove a new element into the linked list set if present.
   *
   * @param item Element to be removed.
   * @return True if the linked list set is modified.
   */
  public boolean delete(E item) {
    int key = item.hashCode();
    while (true) {
      // first locate the window.
      List<Node> windows = find(key);
      Node pred = windows.get(0);
      Node curr = windows.get(1);

      //acquire lock for both node in the window.
      pred.lock();
      try {
        curr.lock();
        try {
          // validate the window before modify the list.
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

  /**
   * Replace an old element with a a new element in the linked list set if
   * the old element present and the new element not present.
   *
   * @param o Element to replace with.
   * @param n Element to be replaced.
   * @return True if the linked list set is modified in anyway.
   */
  public boolean replace(E o, E n) {
    int keyOld = o.hashCode();
    int keyNew = n.hashCode();

    // if replace(x, x), semantically identical with add(x).
    if (keyOld == keyNew)
      return add(n);

    while (true) {

      // first locate two windows.
      List<Node> windowsOld = find(keyOld);
      Node predOld = windowsOld.get(0);
      Node currOld = windowsOld.get(1);

      List<Node> windowsNew = find(keyNew);
      Node predNew = windowsNew.get(0);
      Node currNew = windowsNew.get(1);

      // sort four nodes in their actual order in the linked set (list).
      List<Node> list = new ArrayList<>();
      list.addAll(windowsNew);
      list.addAll(windowsOld);
      Collections.sort(list);

      // lock four nodes from left to right.
      list.get(0).lock();
      try {
        list.get(1).lock();
        try {
          list.get(2).lock();
          try {
            list.get(3).lock();
            try {
              // validate the window before modify the list.
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

  /**
   * Return if the set contains a certain element.
   *
   * @param item Element to query.
   * @return True if the element exist in the set and false otherwise.
   */
  public boolean contains(E item) {
    int key = item.hashCode();
    Node curr = head;
    while (curr.key < key)
      curr = curr.next;
    return curr.key == key && !curr.marked &&
      (curr.replaceNode == null || curr.replaceNode.marked);
  }

  /**
   * Helper function to validate the window after locating it.
   *
   * @param pred Predecessor node in the window.
   * @param curr Current node in the window.
   * @return True if the window is valid and false otherwise.
   */
  private boolean validate(Node pred, Node curr) {
    return !pred.marked && !curr.marked && pred.next == curr;
//    &&
//      (curr.replaceNode == null || curr.replaceNode.marked) &&
//      (pred.replaceNode == null || pred.replaceNode.marked);
  }

  /**
   * Helper function to locate the windows for a given element key.
   *
   * @param key The given hashcode (key)
   * @return A list of two adjacent nodes representing the windows.
   */
  private List<Node> find(int key) {
    Node pred = head;
    Node curr = head.next;
    while (curr != tail && curr.key < key) {
      pred = curr;
      curr = curr.next;
    }
    return Arrays.asList(pred, curr);
  }

  /**
   * Implementation of {@code replace} API.
   *
   * @param oldElement Element to be replaced.
   * @param newElement Element to replace with.
   * @param predOld    Predecessor node in old element window.
   * @param currOld    Current node in old element window.
   * @param predNew    Predecessor node in new element window.
   * @param currNew    Current node in new element window.
   * @return True if the linked list set is modified in anyway.
   */
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
        // old element as deleted, and delete old element.
        Node node = new Node(newElement, currOld);
        // lock the new element to avoid potential inconsistency.
//        node.lock();
//        try {
          node.next = currNew;
          predNew.next = node;
          node.replaceNode.marked = true; // serialization point.
          // jump over the element to be removed.
          if (predOld.next == currOld)
            predOld.next = currOld.next;
          else
            node.next = currOld.next;
          // reset the replaceNode pointer.
          node.replaceNode = null;
          return true;
//        } finally {
//          node.unlock();
//        }
      } else {
        // if new element already exists, no need to add,
        // mark old element as deleted and delete old element.
        currOld.marked = true; // serialization point.
        predOld.next = currOld.next;
        return true;
      }
    }
  }

  /**
   * Check if the linked set (list) is sorted. This method will only check if
   * the linked list elements are in ascending order. It will not check
   * whether the node has been marked. {@code isSorted} is wait free.
   *
   * @return True if the set is sorted and false otherwise.
   */
  public boolean isSorted() {
    return head.next.isSorted(head);
  }

  @Override
  public String toString() {
    return head.toString();
  }
}
