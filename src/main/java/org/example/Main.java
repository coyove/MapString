package org.example;


import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import sun.misc.Unsafe;

import java.lang.invoke.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static org.objectweb.asm.Opcodes.*;

public class Main {
    public static class HashMapString<V> implements Map<String, V> {
        static class Node<V> {
            @SuppressWarnings("rawtypes")
            static Class<Node1>[] NODE_CLASSES = new Class[] { Node1.class, Node2.class };

            @SuppressWarnings("unchecked,rawtypes")
            static synchronized void createNodeNext() throws Exception {
                int n = NODE_CLASSES.length + 1;
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                String superName = NODE_CLASSES[NODE_CLASSES.length - 1].getName().replaceAll("\\.", "/");
                String baseName = Node.class.getName().replaceAll("\\.", "/");
                String className = baseName + n;
                cw.visit(V17, ACC_PUBLIC, className, null, superName, null);
                cw.visitField(ACC_PUBLIC, "key" + n, "[B", null, null).visitEnd();
                cw.visitField(ACC_PUBLIC, "value" + n, "Ljava/lang/Object;", null, null).visitEnd();
                cw.visitField(ACC_PUBLIC, "hash" + n, "I", null, null).visitEnd();

                // Constructor
                MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                // Object findKey(int hash, byte[] key)
                mv = cw.visitMethod(ACC_PUBLIC, "findKey", "(I[B)Ljava/lang/Object;", null, null);
                mv.visitCode();
                for (int i = 2; i <= n; i++) {
                    Label nextKeyLabel = new Label();

                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "hash" + i, "I");
                    mv.visitVarInsn(ILOAD, 1);
                    mv.visitJumpInsn(IF_ICMPNE, nextKeyLabel); // if (hash != this.hashX)

                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "key" + i, "[B"); // load this.keyX
                    mv.visitVarInsn(ALOAD, 2); // load key
                    mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "equals", "([B[B)Z", false);
                    mv.visitJumpInsn(IFEQ, nextKeyLabel);

                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "value" + i, "Ljava/lang/Object;"); // load this.valueX
                    mv.visitInsn(ARETURN);

                    mv.visitLabel(nextKeyLabel);
                }
                mv.visitInsn(ACONST_NULL);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                // Object setKeyValue(byte[] key, Object value)
                mv = cw.visitMethod(ACC_PUBLIC, "setKeyValue", "([BLjava/lang/Object;)Ljava/lang/Object;", null, null);
                mv.visitCode();
                for (int i = 1; i <= n; i++) {
                    Label nextKeyLabel = new Label();

                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "key" + i, "[B"); // load this.keyX
                    mv.visitVarInsn(ALOAD, 1); // load key
                    mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "equals", "([B[B)Z", false);
                    mv.visitJumpInsn(IFEQ, nextKeyLabel);

                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "value" + i, "Ljava/lang/Object;"); // load this.valueX
                    mv.visitVarInsn(ASTORE, 4);

                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitFieldInsn(PUTFIELD, className, "value" + i, "Ljava/lang/Object;"); // store this.valueX

                    mv.visitVarInsn(ALOAD, 4);
                    mv.visitInsn(ARETURN);

                    mv.visitLabel(nextKeyLabel);
                }
                mv.visitInsn(ACONST_NULL);
                mv.visitInsn(ARETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                // void copySuper(Node s, byte[] key, int hash, Object value)
                mv = cw.visitMethod(ACC_PUBLIC, "copySuper", "(L" + baseName + ";[BILjava/lang/Object;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, superName);
                mv.visitVarInsn(ASTORE, 6);
                for (int i = 1; i <= n - 1; i++) {
                    mv.visitVarInsn(ALOAD, 6);
                    mv.visitFieldInsn(GETFIELD, superName, "key" + i, "[B");
                    mv.visitVarInsn(ASTORE, 5);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitFieldInsn(PUTFIELD, className, "key" + i, "[B");

                    mv.visitVarInsn(ALOAD, 6);
                    mv.visitFieldInsn(GETFIELD, superName, "hash" + i, "I");
                    mv.visitVarInsn(ISTORE, 5);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ILOAD, 5);
                    mv.visitFieldInsn(PUTFIELD, className, "hash" + i, "I");

                    mv.visitVarInsn(ALOAD, 6);
                    mv.visitFieldInsn(GETFIELD, superName, "value" + i, "Ljava/lang/Object;");
                    mv.visitVarInsn(ASTORE, 5);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitFieldInsn(PUTFIELD, className, "value" + i, "Ljava/lang/Object;");
                }
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 2);
                mv.visitFieldInsn(PUTFIELD, className, "key" + n, "[B");
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ILOAD, 3);
                mv.visitFieldInsn(PUTFIELD, className, "hash" + n, "I");
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 4);
                mv.visitFieldInsn(PUTFIELD, className, "value" + n, "Ljava/lang/Object;");
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                // int size()
                mv = cw.visitMethod(ACC_PUBLIC, "size", "()I", null, null);
                mv.visitCode();
                mv.visitLdcInsn(n);
                mv.visitInsn(IRETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                cw.visitEnd();
                Class<Node1> c = (Class<Node1>)MethodHandles.lookup().defineClass(cw.toByteArray());
                NODE_CLASSES = Arrays.copyOf(NODE_CLASSES, n);
                NODE_CLASSES[n - 1] = c;
            }

            static Node1 createNodeN(int n) {
                try {
                    while (NODE_CLASSES.length < n)
                        createNodeNext();
                    return NODE_CLASSES[n - 1].getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            long mark;
        }

        static class Node1<V> extends Node<V> {
            public byte[] key1;
            public int hash1;
            public V value1;

            Node1() {
            }

            Node1(String key, V value) {
                key1 = (byte[])VALUE.get(key);
                hash1 = key.hashCode();
                value1 = value;
            }

            int size() {
                return 1;
            }

            void copySuper(Node<V> s, byte[] key, int hash, V value) {
                throw new RuntimeException("Node1");
            }

            V findKey(int h, byte[] key) {
                throw new RuntimeException("Node1");
            }

            V setKeyValue(byte[] key, V value) {
                if (Arrays.equals(key, key1)) {
                    V old = value1;
                    value1 = value;
                    return old;
                }
                return null;
            }
        }

        static class Node2<V> extends Node1<V> {
            public byte[] key2;
            public int hash2;
            public V value2;

            @Override
            V findKey(int h, byte[] key) {
                if (hash2 == h && Arrays.equals(key2, key)) return value2;
                return null;
            }

            @Override
            int size() {
                return 2;
            }

            @Override
            V setKeyValue(byte[] key, V value) {
                if (Arrays.equals(key, key2)) {
                    V old = value2;
                    value2 = value;
                    return old;
                }
                if (Arrays.equals(key, key1)) {
                    V old = value1;
                    value1 = value;
                    return old;
                }
                return null;
            }

            @Override
            void copySuper(Node<V> s, byte[] key, int hash, V value) {
                key1 = ((Node1<V>)s).key1;
                hash1 = ((Node1<V>)s).hash1;
                value1 = ((Node1<V>)s).value1;
                key2 = key;
                hash2 = hash;
                value2 = value;
            }
        }

        private transient final Node1<V>[] entries;
        private final int shift;
        private AtomicInteger count = new AtomicInteger(0);

        @SuppressWarnings("unchecked")
        public HashMapString(int capacity) {
            int s= Long.numberOfLeadingZeros((long) capacity - 1);
            if (1 << (64 - s) < capacity * 3 / 2)
                s--;
            entries = new Node1[1 << (64 - (shift = s))];
        }

        public HashMapString(Map<String, V> map) {
            this(map.size());
            putAll(map);
        }

        public void debugDistribution() {
            Map<Integer, Integer> count = new HashMap<>();
            for (Node1<V> e : entries) {
                int n = e == null ? 0 : e.size();
                count.put(n, count.getOrDefault(n, 0) + 1);
            }
            for (var e : count.entrySet())
                System.out.println(shift + " " + e.getKey() + ": " + e.getValue());
        }

        @Override
        public int size() {
            return count.get();
        }

        @Override
        public boolean isEmpty() {
            return count.get() == 0;
        }

        @Override
        public boolean containsKey(Object key) {
            return get(key) != null;
        }

        @Override
        public boolean containsValue(Object value) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public V put(String key, V value) {
            if (key == null)
                throw new IllegalArgumentException("key");
            if (value == null)
                throw new IllegalArgumentException("value");
            int i = fibHash(key.hashCode(), shift);
            byte[] b = (byte[])VALUE.get(key);
            while (true) {
                Node1<V> e = entries[i];
                if (e == null) {
                    if (nodeCAS(entries, i, e, new Node1<>(key, value))) {
                        count.incrementAndGet();
                        return null;
                    }
                    continue;
                }
                V old = e.setKeyValue(b, value);
                if (old != null)
                    return old;
                Node1<V> e2 = Node.createNodeN(e.size() + 1);
                e2.copySuper(e, b, key.hashCode(), value);
                e2.mark = e.mark | (1L << (key.hashCode() & 0x3E));
                if (e2.size() == 2)
                    e2.mark |= 0xAAAAAAAAAAAAAAAAL;
                else
                    e2.mark &= 0x5555555555555555L;

                if (nodeCAS(entries, i, e, e2))
                    break;
            }
            count.incrementAndGet();
            return null;
        }

        @Override
        public V remove(Object key) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public void putAll(Map<? extends String, ? extends V> m) {
            for (var e : m.entrySet()) {
                put(e.getKey(), e.getValue());
            }
        }

        @Override
        public void clear() {
            for (int i = 0; i < entries.length; i++) {
                var old = nodeSwap(entries, i, null);
                if (old != null)
                    count.addAndGet(-old.size());
            }
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
            Node1<V> e = nodeAt(entries, i);
            if (e == null)
                return null;
            byte[] k = (byte[])VALUE.get(key);
            if (e.hash1 == h && Arrays.equals(k, e.key1))
                return e.value1;
            return switch ((int)((e.mark >>> (h & 0x3E)) & 0x3)) {
                case 2, 3 -> {
                    var e2 = ((Node2<V>)e);
                    yield e2.hash2 == h && Arrays.equals(e2.key2, k) ? e2.value2 : null;
                }
                case 1 -> e.findKey(h, k);
                default -> null;
            };
        }

        private static final Unsafe U;
        private static final int ABASE;
        private static final int ASHIFT;
        private static final VarHandle VALUE;

        static {
            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                U = (Unsafe) f.get(null);
                ABASE = U.arrayBaseOffset(Node1[].class);
                ASHIFT = 31 - Integer.numberOfLeadingZeros(U.arrayIndexScale(Node1[].class));
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
                VALUE = lookup.findVarHandle(String.class, "value", byte[].class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        static <V> boolean nodeCAS(Node1<V>[] entries, int i, Node1<V> expected, Node1<V> x) {
            return U.compareAndSwapObject(entries, ((long)i << ASHIFT) + ABASE, expected, x);
        }

        static <V> Node1<V> nodeSwap(Node1<V>[] entries, int i, Node1<V> x) {
            return (Node1<V>) U.getAndSetObject(entries, ((long)i << ASHIFT) + ABASE, x);
        }

        static <V> Node1<V> nodeAt(Node1<V>[] entries, int i) {
            return (Node1<V>) U.getObject(entries, ((long)i << ASHIFT) + ABASE);
        }

        public static int fibHash(int h, int n) {
            long l = h * -7046029254386353131L;
            return (int) (l >>> n);
        }
    }

    public static void main(String[] args) throws Throwable {
        var src = new HashMap<String, Integer>();
        var rnd = new Random();
        long bestm = 1000000000;
        long bestsl = 1000000000;
        long bestem = 1000000000;
        src.clear();
        for (int i = 0; i < 4096; i++) {
            src.put(("" + i).repeat(100).substring(0, 50), i);
        }
        src.put("", -1);
        src.put("Ea", -2);
        src.put("FB", -3);

        var m = new ConcurrentHashMap<>(src);
        var sl = new HashMapString<>(src);
        var em = new HashMap<>(src);
        sl.debugDistribution();

        for (var e : m.entrySet()) {
            var a = sl.get(e.getKey());
            var b = e.getValue();
            if (!Objects.equals(a, b)) {
                throw new RuntimeException("mismatch " + a + " " + b);
            }
        }
        var kkk = new ArrayList<>(m.keySet());

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
    }
}