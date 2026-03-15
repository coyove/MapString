package org.example;


import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.lang.invoke.*;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static org.objectweb.asm.Opcodes.*;

public class Main {
    public static class HashMapString<V> implements Map<String, V> {
        static abstract class Node<V> {
            @SuppressWarnings("rawtypes")
            static class Clazz {
                final static MethodHandles.Lookup lookup = MethodHandles.lookup();

                Class<? extends Node1> cls;
                Constructor<? extends Node1> ctor;
                VarHandle key, hash, value;

                Clazz(Class<? extends Node1> c, int n) throws Exception {
                    cls = c;
                    ctor = c.getDeclaredConstructor();
                    key = lookup.findVarHandle(c, "key" + n, byte[].class);
                    hash = lookup.findVarHandle(c, "hash" + n, int.class);
                    value = lookup.findVarHandle(c, "value" + n, Object.class);
                }
            }

            interface Iterator<V> {
                boolean test(byte[] key, int hash, V value);
            }

            static Clazz[] NODE_CLASSES;
            static {
                try {
                    NODE_CLASSES = new Clazz[]{ new Clazz(Node1.class, 1), new Clazz(Node2.class, 2) };
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @SuppressWarnings("unchecked,rawtypes")
            static synchronized void createNextNode(int expected) throws Exception {
                int n = NODE_CLASSES.length + 1;
                if (n > expected)
                    return;
                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                final String superName = NODE_CLASSES[NODE_CLASSES.length - 1].cls.getName().replaceAll("\\.", "/");
                final String baseName = Node.class.getName().replaceAll("\\.", "/");
                final String className = baseName + n;
                cw.visit(V17, ACC_PUBLIC, className, null, superName, null);
                cw.visitField(ACC_VOLATILE, "key" + n, "[B", null, null).visitEnd();
                cw.visitField(ACC_VOLATILE, "value" + n, "Ljava/lang/Object;", null, null).visitEnd();
                cw.visitField(ACC_VOLATILE, "hash" + n, "I", null, null).visitEnd();

                // Constructor
                MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKESPECIAL, superName, "<init>", "()V", false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                // Object findKey2(int hash, byte[] key)
                mv = cw.visitMethod(ACC_PUBLIC, "findKey2", "(I[B)Ljava/lang/Object;", null, null);
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

                // void copy(Node s, int count, byte[] key, Object value)
                mv = cw.visitMethod(ACC_PUBLIC, "copy", "(L" + baseName + ";I[BLjava/lang/Object;)V", null, null);
                mv.visitCode();
                for (int i = 1; i <= n; i++) {
                    // ASM: if (i > count) return;
                    Label ok = new Label();
                    mv.visitLdcInsn(i);
                    mv.visitVarInsn(ILOAD, 2);
                    mv.visitJumpInsn(IF_ICMPLE, ok);
                    mv.visitInsn(RETURN);
                    mv.visitLabel(ok);

                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(CHECKCAST, baseName + i);
                    mv.visitVarInsn(ASTORE, 5);

                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitFieldInsn(GETFIELD, baseName + i, "key" + i, "[B");
                    mv.visitFieldInsn(PUTFIELD, className, "key" + i, "[B");

                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitFieldInsn(GETFIELD, baseName + i, "hash" + i, "I");
                    mv.visitFieldInsn(PUTFIELD, className, "hash" + i, "I");
                    // this.markKey
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitFieldInsn(GETFIELD, baseName + i, "hash" + i, "I");
                    mv.visitMethodInsn(INVOKEVIRTUAL, baseName, "markKey", "(I)V", false);

                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitFieldInsn(GETFIELD, baseName + i, "value" + i, "Ljava/lang/Object;");
                    mv.visitVarInsn(ASTORE, 6); // store tmp6

                    Label toUpdateLabel = new Label();
                    mv.visitVarInsn(ALOAD, 5);
                    mv.visitFieldInsn(GETFIELD, baseName + i, "key" + i, "[B"); // load this.keyX
                    mv.visitVarInsn(ALOAD, 3); // load key
                    mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "equals", "([B[B)Z", false);
                    mv.visitJumpInsn(IFEQ, toUpdateLabel);
                    mv.visitVarInsn(ALOAD, 4); // load value
                    mv.visitVarInsn(ASTORE, 6); // overwrite tmp6
                    mv.visitLabel(toUpdateLabel);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitVarInsn(ALOAD, 6);
                    mv.visitFieldInsn(PUTFIELD, className, "value" + i, "Ljava/lang/Object;");
                }
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                // void move1(Node1 s, byte[] key)
                mv = cw.visitMethod(ACC_PUBLIC, "move1", "(L" + baseName + "1;[B)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, superName);
                mv.visitVarInsn(ASTORE, 3);
                for (int i = n; i >= 2; i--) {
                    Label toUpdateLabel = new Label();
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "key" + i, "[B");
                    mv.visitVarInsn(ALOAD, 2); // load key
                    mv.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "equals", "([B[B)Z", false);
                    mv.visitJumpInsn(IFEQ, toUpdateLabel);
                    mv.visitInsn(RETURN);
                    mv.visitLabel(toUpdateLabel);

                    mv.visitVarInsn(ALOAD, 3);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "key" + i, "[B");
                    mv.visitFieldInsn(PUTFIELD, superName, "key" + (i - 1), "[B");

                    mv.visitVarInsn(ALOAD, 3);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "hash" + i, "I");
                    mv.visitFieldInsn(PUTFIELD, superName, "hash" + (i - 1), "I");
                    // s.markKey
                    mv.visitVarInsn(ALOAD, 3);
                    mv.visitVarInsn(ALOAD, 3);
                    mv.visitFieldInsn(GETFIELD, superName, "hash" + (i - 1), "I");
                    mv.visitMethodInsn(INVOKEVIRTUAL, baseName, "markKey", "(I)V", false);

                    mv.visitVarInsn(ALOAD, 3);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "value" + i, "Ljava/lang/Object;");
                    mv.visitFieldInsn(PUTFIELD, superName, "value" + (i - 1), "Ljava/lang/Object;");

                }
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                // void setLastKeyValue(byte[] key, int hash, Object value)
                mv = cw.visitMethod(ACC_PUBLIC, "setLastKeyValue", "([BILjava/lang/Object;)V", null, null);
                mv.visitCode();
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitFieldInsn(PUTFIELD, className, "key" + n, "[B");

                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ILOAD, 2);
                mv.visitFieldInsn(PUTFIELD, className, "hash" + n, "I");
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ILOAD, 2);
                mv.visitMethodInsn(INVOKEVIRTUAL, baseName, "markKey", "(I)V", false);

                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 3);
                mv.visitFieldInsn(PUTFIELD, className, "value" + n, "Ljava/lang/Object;");
                mv.visitInsn(RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();

                // boolean forEach(Node.Iterator f)
                mv = cw.visitMethod(ACC_PUBLIC, "forEach", "(L" + baseName + "$Iterator;)Z", null, null);
                mv.visitCode();
                for (int i = 1; i <= n; i++) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "key" + i, "[B");
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "hash" + i, "I");
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitFieldInsn(GETFIELD, className, "value" + i, "Ljava/lang/Object;");
                    mv.visitMethodInsn(
                            INVOKEINTERFACE,
                            baseName + "$Iterator",
                            "test",
                            "([BILjava/lang/Object;)Z",
                            true
                    );
                    Label nextLabel = new Label();
                    mv.visitJumpInsn(IFNE, nextLabel);
                    mv.visitLdcInsn(false);
                    mv.visitInsn(IRETURN);
                    mv.visitLabel(nextLabel);
                }
                mv.visitLdcInsn(true);
                mv.visitInsn(IRETURN);
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
                NODE_CLASSES[n - 1] = new Clazz(c, n);
            }

            @SuppressWarnings("rawtypes")
            static Node1 createNodeN(int n) {
                try {
                    while (NODE_CLASSES.length < n)
                        createNextNode(n);
                    return NODE_CLASSES[n - 1].ctor.newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            long filter;

            abstract int size();

            void markKey(int hashCode) {
                if (size() == 1)
                    return;
                filter |= (size() == 2 ? 3L : 1L) << (hashCode & 0x3E);
            }
        }

        static class Node1<V> extends Node<V> implements Map.Entry<String, V> {
            byte[] key1;
            int hash1;
            V value1;

            Node1() {
            }

            Node1(byte[] key, int hash, V value) {
                key1 = key;
                hash1 = hash;
                value1 = value;
            }

            @Override
            int size() {
                return 1;
            }

            V findKey1(int h, byte[] key) {
                if (h == hash1 && Arrays.equals(key1, key)) return value1;
                return findKey2(h, key);
            }

            V findKey2(int h, byte[] key) {
                return null;
            }

            void copy(Node<V> s, int count, byte[] key, V value) {
                if (Math.min(count, Math.min(size(), s.size())) == 1) {
                    Node1<V> n = (Node1<V>) s;
                    key1 = n.key1;
                    hash1 = n.hash1;
                    value1 = Arrays.equals(n.key1, key) ? value : n.value1;
                }
            }

            void move1(Node1<V> s, byte[] key) {
                throw new RuntimeException("BUG: unreachable");
            }

            void setLastKeyValue(byte[] key, int hash, V value) {
                key1 = key;
                hash1 = hash;
                value1 = value;
            }

            boolean forEach(Node.Iterator<V> f) {
                return f.test(key1, hash1, value1);
            }

            @SuppressWarnings("unchecked")
            Node1<V> asNode1(int i) {
                Clazz c = NODE_CLASSES[i - 1];
                return new Node1<>((byte[])c.key.get(this), (int)c.hash.get(this), (V)c.value.get(this));
            }

            @Override
            public String getKey() {
                return new String(key1);
            }

            @Override
            public V getValue() {
                return value1;
            }

            @Override
            public V setValue(V value) {
                throw new UnsupportedOperationException();
            }
        }

        static class Node2<V> extends Node1<V> {
            byte[] key2;
            int hash2;
            V value2;

            @Override
            V findKey2(int h, byte[] key) {
                if (hash2 == h && Arrays.equals(key2, key)) return value2;
                return null;
            }

            @Override
            void copy(Node<V> s, int count, byte[] key, V value) {
                switch (Math.min(count, Math.min(size(), s.size()))) {
                    case 2:
                        Node2<V> n2 = (Node2<V>) s;
                        key2 = n2.key2;
                        hash2 = n2.hash2;
                        value2 = Arrays.equals(n2.key2, key) ? value : n2.value2;
                        markKey(hash2);
                        // fallthrough
                    case 1:
                        Node1<V> n1 = (Node1<V>) s;
                        key1 = n1.key1;
                        hash1 = n1.hash1;
                        value1 = Arrays.equals(n1.key1, key) ? value : n1.value1;
                        markKey(hash1);
                }
            }

            @Override
            void move1(Node1<V> s, byte[] key) {
                if (Arrays.equals(key2, key)) return;
                s.key1 = key2;
                s.hash1 = hash2;
                s.value1 = value2;
                s.markKey(hash2);
            }

            @Override
            void setLastKeyValue(byte[] key, int hash, V value) {
                key2 = key;
                hash2 = hash;
                value2 = value;
                markKey(hash2);
            }

            @Override
            boolean forEach(Node.Iterator<V> f) {
                if (!f.test(key1, hash1, value1)) return false;
                return f.test(key2, hash2, value2);
            }

            @Override
            int size() {
                return 2;
            }
        }

        private volatile Node1<V>[] entries;
        private volatile int shift;
        private int maxDepth;
        private final AtomicInteger count = new AtomicInteger(0);
        private final AtomicLong altering = new AtomicLong(0);

        public HashMapString() {
            this(4);
        }

        private HashMapString(boolean ignored) {
        }

        @SuppressWarnings("unchecked")
        public HashMapString(int capacity) {
            int s = Long.numberOfLeadingZeros((long) capacity - 1);
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

        private void startAltering() {
            while (altering.incrementAndGet() <= 0)
                Thread.yield();
        }

        private void finishAltering() {
            altering.decrementAndGet();
        }

        @SuppressWarnings("unchecked")
        private void grow() {
            int s = shift - 1;
            while (altering.compareAndSet(0, Long.MIN_VALUE))
                Thread.yield();
            HashMapString<V> grown = new HashMapString<>(true);
            grown.shift = s;
            grown.entries = new Node1[1 << (64 - s)];
            // System.out.println("grow to " + grown.entries.length + " " + size());
            for (Node1<V> old : entries) {
                if (old != null)
                    old.forEach((k, h, v) -> {
                        put0(grown, k, h, v);
                        return true;
                    });
            }
            maxDepth = 0;
            entries = grown.entries;
            shift = grown.shift;
            altering.set(0);
        }

        @Override
        public V put(String key, V value) {
            startAltering();
            try {
                if (key == null || value == null)
                    throw new NullPointerException();
                return put0(this, (byte[]) VALUE.get(key), key.hashCode(), value);
            } finally {
                finishAltering();
                if (maxDepth >= 8)
                    grow();
            }
        }

        @SuppressWarnings("unchecked")
        private static <V> V put0(HashMapString<V> m, byte[] key, int hash, V value) {
            int i = fibHash(hash, m.shift);
            V old;
            while (true) {
                Node1<V> n = nodeAt(m.entries, i);
                if (n == null) {
                    if (nodeCAS(m.entries, i, n, new Node1<>(key, hash, value))) {
                        m.count.incrementAndGet();
                        return null;
                    }
                    continue;
                }
                Node1<V> n2;
                old = n.findKey1(hash, key);
                if (old == null) {
                    n2 = Node.createNodeN(n.size() + 1);
                    n2.copy(n, n.size(), null, null);
                    n2.setLastKeyValue(key, hash, value);
                } else {
                    n2 = Node.createNodeN(n.size());
                    n2.copy(n, n.size(), key, value);
                }
                m.maxDepth = Math.max(m.maxDepth, n2.size());
                if (nodeCAS(m.entries, i, n, n2))
                    break;
            }
            if (old == null)
                m.count.incrementAndGet();
            return old;
        }

        @Override
        @SuppressWarnings("unchecked")
        public V remove(Object key) {
            startAltering();
            try {
                if (key == null)
                    throw new NullPointerException();
                int i = fibHash(key.hashCode(), shift);
                byte[] b = (byte[]) VALUE.get(key);
                V old;
                while (true) {
                    Node1<V> n = nodeAt(entries, i);
                    if (n == null)
                        return null;
                    old = n.findKey1(key.hashCode(), b);
                    if (old == null)
                        return null;
                    Node1<V> n2 = null;
                    if (n.size() > 1) {
                        n2 = Node.createNodeN(n.size() - 1);
                        n2.copy(n, n.size(), null, null);
                        n.move1(n2, b);
                    }
                    if (nodeCAS(entries, i, n, n2))
                        break;
                }
                count.decrementAndGet();
                return old;
            } finally {
                finishAltering();
            }
        }

        @Override
        public void putAll(Map<? extends String, ? extends V> m) {
            for (var e : m.entrySet()) {
                put(e.getKey(), e.getValue());
            }
        }

        @Override
        public void clear() {
            startAltering();
            try {
                for (int i = 0; i < entries.length; i++) {
                    var old = nodeSwap(entries, i, null);
                    if (old != null)
                        count.addAndGet(-old.size());
                }
            } finally {
                finishAltering();
            }
        }

        public void forEach(BiPredicate<String, V> f) {
            Node1<V>[] nodes = entries;
            for (Node1<V> n : nodes) {
                if (!n.forEach((k, h, v) -> f.test(new String(k), v)))
                    break;
            }
        }

        @Override
        public Set<String> keySet() {
            return new SetAndIter<>(this, SetAndIter.Type.Key);
        }

        @Override
        public Collection<V> values() {
            return new SetAndIter<>(this, SetAndIter.Type.Value);
        }

        @Override
        public Set<Entry<String, V>> entrySet() {
            return new SetAndIter<>(this, SetAndIter.Type.Entry);
        }

        static class SetAndIter<V, S> extends HashSet<S> implements java.util.Iterator<S> {
            enum Type { Key, Value, Entry }

            final HashMapString<V> map;
            final Type type;
            int index = 0;
            int nodeIndex = 1;
            Node1<V> peeked = null;

            SetAndIter(HashMapString<V> m, Type t) {
                super();
                this.type = t;
                this.map = m;
            }

            @Override
            public boolean hasNext() {
                while (peeked == null) {
                    if (index >= map.entries.length)
                        return false;
                    Node1<V> e = nodeAt(map.entries, index);
                    if (e != null && nodeIndex <= e.size()) {
                        peeked = e.asNode1(nodeIndex);
                        return true;
                    }
                    index++;
                    nodeIndex = 1;
                }
                return true;
            }

            @Override
            @SuppressWarnings("unchecked")
            public S next() {
                if (peeked == null)
                    throw new IllegalStateException();
                Node1<V> e = peeked;
                peeked = null;
                nodeIndex++;
                return (S) switch (this.type) {
                    case Key -> new String(e.key1);
                    case Value -> e.value1;
                    case Entry -> e;
                };
            }

            @Override
            public int size() {
                return map.size();
            }

            @Override
            public boolean isEmpty() {
                return map.isEmpty();
            }

            @Override
            public Iterator<S> iterator() {
                return new SetAndIter<>(map, type);
            }
        }

        @Override
        public V get(Object key) {
            if (key == null)
                throw new NullPointerException();
            int h = key.hashCode();
            int i = fibHash(h, shift);
            Node1<V> e = nodeAt(entries, i);
            if (e == null)
                return null;
            byte[] k = (byte[])VALUE.get(key);
            if (e.hash1 == h && Arrays.equals(k, e.key1))
                return e.value1;
            return switch ((int)((e.filter >>> (h & 0x3E)) & 0x3)) {
                case 2, 3 -> {
                    var e2 = ((Node2<V>)e);
                    yield e2.hash2 == h && Arrays.equals(e2.key2, k) ? e2.value2 : null;
                }
                case 1 -> e.findKey2(h, k);
                default -> null;
            };
        }

        private static final VarHandle VALUE;
        private static final VarHandle ARRAY;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
                VALUE = lookup.findVarHandle(String.class, "value", byte[].class);
                ARRAY = MethodHandles.arrayElementVarHandle(Node1[].class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        static <V> boolean nodeCAS(Node1<V>[] entries, int i, Node1<V> expected, Node1<V> x) {
            return ARRAY.compareAndSet(entries, i, expected, x);
        }

        static <V> Node1<V> nodeSwap(Node1<V>[] entries, int i, Node1<V> x) {
            return (Node1<V>) ARRAY.getAndSet(entries, i, x);
        }

        static <V> Node1<V> nodeAt(Node1<V>[] entries, int i) {
            return (Node1<V>) ARRAY.get(entries, i);
        }

        public static int fibHash(int h, int n) {
            long l = h * -7046029254386353131L;
            return (int) (l >>> n);
        }
    }

    static <V> V measure(String title, Supplier<V> f) {
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++)
            f.get();
        V res = f.get();
        System.out.println(title + " " + (System.nanoTime() - start) / 100 / 1000000 + "ms");
        return res;
    }

    public static void main(String[] args) throws Throwable {
        var src = new HashMap<String, Integer>();
        var src2 = new HashMap<String, Integer>();
        var rnd = new Random();
        long bestm = 1000000000;
        long bestsl = 1000000000;
        long bestem = 1000000000;
        for (int i = 0; i < 40960; i++) {
            String k = ("" + i).repeat(100);
            src.put(k.substring(0, 50), i);
            src2.put(k.substring(50, 100), i);
        }
        src.put("", -1);
        src.put("Ea", -2);
        src.put("FB", -3);

        var m = measure("CHM", () -> new ConcurrentHashMap<>(src));
        var sl = measure("MapString", () -> new HashMapString<>(src));
        var em = measure("HM", () -> new HashMap<>(src));
        var kkk = new ArrayList<>(src.keySet());
        sl = new HashMapString<>();
        sl.putAll(src);

        sl.debugDistribution();
        for (int i = 0; i < src.size() / 2; i++) {
            String k = kkk.get(rnd.nextInt(kkk.size()));
            m.remove(k);
            sl.remove(k);
            em.remove(k);
        }
        m.putAll(src2);
        sl.putAll(src2);
        em.putAll(src2);
        kkk.addAll(src2.keySet());

        for (var e : m.entrySet()) {
            var a = sl.get(e.getKey());
            var b = e.getValue();
            if (!Objects.equals(a, b))
                throw new RuntimeException("mismatch " + a + " " + b);
        }

        int actual = 0;
        for (var e : sl.entrySet()) {
            var a = m.get(e.getKey());
            var b = e.getValue();
            if (!Objects.equals(a, b))
                throw new RuntimeException("mismatch2 " + a + " " + b);
            actual++;
        }

        if (sl.size() != m.size() || actual != m.size())
            throw new RuntimeException("mismatch len " + sl.size() + " " + m.size() + " " + actual);

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