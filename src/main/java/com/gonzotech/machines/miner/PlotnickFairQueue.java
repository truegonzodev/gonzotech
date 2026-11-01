package com.gonzotech.machines.miner;

import java.util.IdentityHashMap;
import java.util.function.Predicate;

/** Server-thread-only identity FIFO. Duplicate admission cannot replace work or jump the queue. */
public final class PlotnickFairQueue<K, V> {
    private final IdentityHashMap<K, Node> entries = new IdentityHashMap<>();
    private Node first, last;
    private final class Node {
        final K key;
        final V value;
        Node prev, next;
        Node(K key, V value) { this.key = key; this.value = value; }
    }
    public void offer(K key, V value) {
        if (entries.containsKey(key)) return;
        Node node = new Node(key, value);
        entries.put(key, node);
        node.prev = last;
        if (last == null) first = node; else last.next = node;
        last = node;
    }
    public void cancel(K key) {
        Node node = entries.remove(key);
        if (node == null) return;
        if (node.prev == null) first = node.next; else node.prev.next = node.next;
        if (node.next == null) last = node.prev; else node.next.prev = node.prev;
    }
    public V poll() {
        if (first == null) return null;
        V value = first.value;
        cancel(first.key);
        return value;
    }
    public void cancelIf(Predicate<V> predicate) {
        for (Node node = first, next; node != null; node = next) {
            next = node.next;
            if (predicate.test(node.value)) cancel(node.key);
        }
    }
    public boolean contains(K key) { return entries.containsKey(key); }
    public int size() { return entries.size(); }
}
