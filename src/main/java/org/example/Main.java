package org.example;


import sun.misc.Unsafe;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    public static class HashMapString<V> implements Map<String, V> {
        static class Slot<V> {
            byte[] key;
            V value;
            int hash;

            Slot(String key, V value) {
                this.key = (byte[])VALUE.get(key);
                this.value = value;
                this.hash = key.hashCode();
            }
        }

        static class Node<V> extends Slot<V> {
            long mark;
            Slot<V>[] overflow;

            Node(String key, V value) {
                super(key, value);
                mark = 0;
            }

            @SuppressWarnings("unchecked")
            public boolean add(String key, V value) {
                byte[] b = (byte[])VALUE.get(key);
                if (Arrays.equals(this.key, b)) {
                    this.value = value;
                    return false;
                }
                if (overflow == null) {
                    overflow = (Slot<V>[])new Slot<?>[1];
                } else {
                    for (Slot<V> o : overflow) {
                        if (Arrays.equals(o.key, b)) {
                            o.value = value;
                            return false;
                        }
                    }
                    overflow = Arrays.copyOf(overflow, overflow.length + 1);
                }
                overflow[overflow.length - 1] = new Slot<>(key, value);
                return true;
            }

            public int size() {
                return 1 + (overflow == null ? 0 : overflow.length);
            }
        }

        private final Node<V>[] entries;
        private final int shift;
        private int count = 0;

        @SuppressWarnings("unchecked")
        public HashMapString(int capacity) {
            int s= Long.numberOfLeadingZeros((long) capacity - 1);
            if (1 << (64 - s) < capacity * 3 / 2)
                s--;
            entries = new Node[1 << (64 - (shift = s))];
            count = 0;
        }

        public HashMapString(Map<String, V> map) {
            this(map.size());
            putAll(map);

            // for (int i = 0; i < entries.length; i++) {
            //     var e = entries[i];
            //     int next = (i + 1) & (entries.length - 1);
            //     if (e != null && e.size() == 2 && entries[next] == null) {
            //         entries[next] = e.next;
            //         e.next = null;
            //     }
            // }

            Map<Integer, Integer> count = new HashMap<>();
            for (Node<V> e : entries) {
                int n = e == null ? 0 : e.size();
                count.put(n, count.getOrDefault(n, 0) + 1);
            }
            for (var e : count.entrySet())
                System.out.println(shift + " " + e.getKey() + ": " + e.getValue());
        }

        @Override
        public int size() {
            return count;
        }

        @Override
        public boolean isEmpty() {
            return count == 0;
        }

        @Override
        public boolean containsKey(Object key) {
            return get(key) != null;
        }

        @Override
        public boolean containsValue(Object value) {
            throw new RuntimeException("not implemented");
        }

        public V put(String key, V value) {
            V old = get(key);
            put0(key, value);
            return old;
        }

        public void put0(String key, V value) {
            if (key == null) // || key.isEmpty())
                throw new IllegalArgumentException("key");
            if (value == null)
                throw new IllegalArgumentException("value");
            int i = fibHash(key.hashCode(), shift);
            if (entries[i] == null) {
                entries[i] = new Node<>(key, value);
                count++;
            } else {
                if (entries[i].add(key, value))
                    count++;
                entries[i].mark |= 1L << (key.hashCode() & 0x3F);
            }
        }

        @Override
        public V remove(Object key) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public void putAll(Map<? extends String, ? extends V> m) {
            for (var e : m.entrySet()) {
                put0(e.getKey(), e.getValue());
            }
        }

        @Override
        public void clear() {
            count = 0;
        }

        @Override
        public Set<String> keySet() {
            return Set.of();
        }

        @Override
        public Collection<V> values() {
            return List.of();
        }

        @Override
        public Set<Entry<String, V>> entrySet() {
            return Set.of();
        }

        @Override
        public V get(Object key) {
            if (key == null) // || ((String)key).isEmpty())
                throw new IllegalArgumentException("key");
            int h = key.hashCode();
            int i = fibHash(h, shift);
            Node<V> e = entries[i];
            if (e == null)
                return null;
            byte[] k = (byte[])VALUE.get(key);
            if (e.hash == h && Arrays.equals(k, e.key))
                return e.value;
            if ((e.mark & (1L << (h & 0x3F))) != 0) {
                for (Slot<V> o : e.overflow) {
                    if (o.hash == h && Arrays.equals(k, o.key))
                        return o.value;
                }
            }
            return null;
        }

        private static Unsafe U;
        private static int ABASE;
        private static int ASHIFT;

        static {
            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                Unsafe unsafe = (Unsafe) f.get(null);
                ABASE = unsafe.arrayBaseOffset(Node[].class);
                int scale = unsafe.arrayIndexScale(Node[].class);
                ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
                U = unsafe;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        static <V> Node<V> entryAt(Node<V>[] entries, int i) {
            return (Node<V>)U.getObject(entries, ((long)i << ASHIFT) + ABASE);
        }

        public static int fibHash(int h, int n) {
            long l = h * -7046029254386353131L;
            return (int) (l >>> n);
        }

        private static final VarHandle VALUE;
        // private static final MethodHandle MISMATCH;
        // private static final ToIntBiFunction<byte[], byte[]> mismatch;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
                VALUE = lookup.findVarHandle(String.class, "value", byte[].class);

                // Class<?> latin1 = Class.forName("jdk.internal.util.ArraysSupport");
                // lookup = MethodHandles.privateLookupIn(latin1, MethodHandles.lookup());

                // MISMATCH = lookup.findStatic(
                //         latin1,
                //         "mismatch",
                //         MethodType.methodType(int.class, byte[].class, byte[].class, int.class)
                // );

                // Field f = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                // f.setAccessible(true);
                // MethodHandles.Lookup trustedLookup = (MethodHandles.Lookup) f.get(null);

                // CallSite site = LambdaMetafactory.metafactory(
                //         trustedLookup,
                //         "applyAsInt",
                //         MethodType.methodType(ToIntBiFunction.class),
                //         MethodType.methodType(int.class, Object.class, Object.class), // erased
                //         MISMATCH, //.asType(MethodType.methodType(Integer.class, byte[].class, Integer.class, byte[].class, Integer.class, Integer.class)),
                //         MethodType.methodType(int.class, byte[].class, byte[].class) // exact
                // );

                // mismatch = (<byte[], byte[]>) site.getTarget().invokeExact();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        // public static int hashString(String s) throws Throwable {
        //     byte[] b = (byte[])VALUE.get(s);
        //     return (int) HASHCODE.invoke(b, 0, b.length, 0, 4);
        // }

        // public static int hashString(String s, int offset) throws Throwable {
        //     byte[] b = (byte[])VALUE.get(s);
        //     return (int) HASHCODE.invoke(b, 0, b.length, offset, 4);
        // }
    }

    public static void main(String[] args) throws Throwable {
        var src = new HashMap<String, Integer>();
        var rnd = new Random();
        for (int i = 0; i < 4096; i++) {
            src.put(("" + i).repeat(100).substring(0, 50), i);
        }
        src.put("", -1);
        System.out.println("size: " + src.size() + "\n\n");

        var m = new ConcurrentHashMap<>(src);
        var sl = new HashMapString<>(src);
        org.eclipse.collections.impl.map.mutable.ConcurrentHashMap<String, Integer> em = new org.eclipse.collections.impl.map.mutable.ConcurrentHashMap<>();
        em.putAll(src);

        for (var e : m.entrySet()) {
            var a = sl.get(e.getKey());
            var b = e.getValue();
            if (!Objects.equals(a, b)) {
                throw new RuntimeException("mismatch " + a + " " + b);
            }
        }

        var kkk = new ArrayList<>(m.keySet());

        try {
            long bestm = 1000000000;
            long bestsl = 1000000000;
            long bestem = 1000000000;
            for (int zzz = 0; ; zzz++) {
                long mctr = 0, slctr = 0, emctr = 0;

                int times = 10000;
                String[] keys = new String[times];
                for (int i = 0; i < times; i++) {
                    keys[i] = "" + (i % src.size());
                    if (rnd.nextInt(1) == 0) {
                        keys[i] = kkk.get(rnd.nextInt(kkk.size()));
                    }
                }

                long start = System.nanoTime();
                for (String k : keys) {
                    // dumb += FibMap.hashString(k);
                    for (int j = 0; j < 100; j++) {
                        m.get(k);
                    }
                }
                mctr += System.nanoTime() - start;

                start = System.nanoTime();
                for (String k : keys) {
                    // dumb += FibMap.hashString(k, 1);
                    for (int j = 0; j < 100; j++) {
                        sl.get(k);
                    }
                }
                slctr += System.nanoTime() - start;

                start = System.nanoTime();
                for (String k : keys) {
                    // dumb += FibMap.hashString(k, 1);
                    for (int j = 0; j < 100; j++) {
                        em.get(k);
                    }
                }
                emctr += System.nanoTime() - start;
                bestsl = Math.min(slctr / 100, bestsl);
                bestm = Math.min(mctr / 100, bestm);
                bestem = Math.min(emctr / 100, bestem);
                System.out.print("\r" + bestsl + " " + bestm + " " + bestem);
            }
        } catch (Throwable e) {
            System.out.println(e);
        }
    }

}