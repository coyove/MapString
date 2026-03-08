package org.example;


import sun.misc.Unsafe;

import java.lang.invoke.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntBiFunction;

public class Main {
    public static class FibMap<V> {
        static final boolean FIB_HASH = true;

        static class Slot<V> {
            byte[] key;
            V value;
            int hash;

            Slot() {
            }

            Slot(String key, V value) {
                this.key = (byte[])VALUE.get(key);
                this.value = value;
                this.hash = key.hashCode();
            }
        }

        static class Node<V> extends Slot<V> {
            long mark;
            Slot<V>[] overflow;

            Node() {
                super();
                mark = 0;
            }

            Node(String key, V value) {
                super(key, value);
                mark = 0;
            }

            public void add(String key, V value) {
                if (overflow == null) {
                    overflow = (Slot<V>[])new Slot<?>[1];
                } else {
                    overflow = Arrays.copyOf(overflow, overflow.length + 1);
                }
                overflow[overflow.length - 1] = new Slot<>(key, value);
                Arrays.sort(overflow, (a, b) -> Arrays.compare(a.key, b.key));
            }

            public int size() {
                return 1 + (overflow == null ? 0 : overflow.length);
            }
        }

        private final Node<V>[] entries;
        private final int shift;
        private final static Node<?> empty = new Node<>();

        @SuppressWarnings("unchecked")
        public FibMap(Map<String, V> map) {
            if (FIB_HASH) {
                shift = Long.numberOfLeadingZeros((long) map.size() - 1);
                entries = new Node[1 << (64 - shift)];
            } else {
                shift = Long.numberOfLeadingZeros((long) map.size() - 1) - 32;
                entries = new Node[1 << (32 - shift)];
            }
            Arrays.fill(entries, empty);
            for (var e : map.entrySet()) {
                put(e.getKey(), e.getValue());
            }

            // for (int i = 0; i < entries.length; i++) {
            //     var e = entries[i];
            //     int next = (i + 1) & (entries.length - 1);
            //     if (e != null && e.size() == 2 && entries[next] == null) {
            //         entries[next] = e.next;
            //         e.next = null;
            //     }
            // }

            Map<Integer, Integer> count = new HashMap<>();
            for (int i = 0; i < entries.length; i++) {
                var e = entries[i];
                int n = e == empty ? 0 : e.size();
                count.put(n, count.getOrDefault(n, 0) + 1);
            }
            for (var e : count.entrySet())
                System.out.println(shift + " " + e.getKey() + ": " + e.getValue());
        }

        public void put(String key, V value) {
            int i = fibHash(key.hashCode(), shift);
            if (entries[i] == empty) {
                entries[i] = new Node<>(key, value);
            } else {
                entries[i].add(key, value);
            }
            entries[i].mark |= 1L << (key.hashCode() & 0x3F);
        }

        public V get(String key) {
            int h = key.hashCode();
            int i = fibHash(h, shift);
            Node<V> e = entries[i];
            if ((e.mark & (1L << (h & 0x3F))) == 0) return null;
            byte[] k = (byte[])VALUE.get(key);
            if (e.hash == h && Arrays.equals(k, e.key))
                return e.value;
            if (e.overflow != null) {
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
            if (FIB_HASH) {
                long l = h * -7046029254386353131L;
                return (int) (l >>> n);
            }
            h = h ^ (h >>> 16);
            return h >>> (n);
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
        for (int i = 0; i < 10240; i++) {
            src.put(("" + i).repeat(100).substring(0, 50), i);
        }
        System.out.println("size: " + src.size() + "\n\n");

        var m = new ConcurrentHashMap<>(src);
        var sl = new FibMap<>(src);
        org.eclipse.collections.impl.map.mutable.ConcurrentHashMap<String, Integer> em = new org.eclipse.collections.impl.map.mutable.ConcurrentHashMap<>();
        em.putAll(src);

        for (var e : m.entrySet()) {
            if (!Objects.equals(sl.get(e.getKey()), e.getValue())) {
                throw new RuntimeException("mismatch");
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

                // start = System.nanoTime();
                // for (String k : keys) {
                //     // dumb += FibMap.hashString(k, 1);
                //     em.get(k);
                // }
                // emctr += System.nanoTime() - start;
                bestsl = Math.min(slctr / 100, bestsl);
                bestm = Math.min(mctr / 100, bestm);
                bestem = Math.min(emctr / 100, bestem);
                System.out.print("\r" + bestsl + " " + bestm + " " + emctr);
            }
        } catch (Throwable e) {
        }
    }

}