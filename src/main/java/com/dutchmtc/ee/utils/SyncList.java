package com.dutchmtc.ee.utils;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@SuppressWarnings("ALL")
public abstract class SyncList<E> implements List<E> {
    private class SyncLinkIterator implements Iterator<E> {
        protected final Iterator<E> it;

        private SyncLinkIterator(Iterator<E> it) {
            this.it = it;
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public E next() {
            return it.next();
        }

        @Override
        public void remove() {
            try {
                it.remove();
            } finally {
                noteUpdate();
            }
        }

        @Override
        public void forEachRemaining(Consumer<? super E> action) {
            it.forEachRemaining(action);
        }
    }

    private class SyncLinkListIterator extends SyncLinkIterator implements ListIterator<E> {
        private SyncLinkListIterator(ListIterator<E> iterator) {
            super(iterator);
        }

        @Override
        public boolean hasPrevious() {
            return ((ListIterator<E>) it).hasPrevious();
        }

        @Override
        public E previous() {
            return ((ListIterator<E>) it).previous();
        }

        @Override
        public int nextIndex() {
            return ((ListIterator<E>) it).nextIndex();
        }

        @Override
        public int previousIndex() {
            return ((ListIterator<E>) it).previousIndex();
        }

        @Override
        public void set(E e) {
            try {
                ((ListIterator<E>) it).set(e);
            } finally {
                noteUpdate();
            }
        }

        @Override
        public void add(E e) {
            try {
                ((ListIterator<E>) it).add(e);
            } finally {
                noteUpdate();
            }
        }
    }

    private final List<E> handle;
    public SyncList(List<E> handle) {
        this.handle = handle;
    }

    private void noteUpdate() {
        onUpdate(Collections.unmodifiableList(handle));
    }

    protected abstract void onUpdate(List<E> data);

    public void applyUpdate(Consumer<List<E>> consumer) {
        try {
            consumer.accept(handle);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public int size() {
        return handle.size();
    }

    @Override
    public boolean isEmpty() {
        return handle.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return handle.contains(o);
    }

    @NotNull
    @Override
    public Iterator<E> iterator() {
        return new SyncLinkIterator(handle.iterator());
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return handle.toArray();
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        return handle.toArray(a);
    }

    @Override
    public boolean add(E e) {
        try {
            return handle.add(e);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public boolean remove(Object o) {
        try {
            return handle.remove(o);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return handle.containsAll(c);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends E> c) {
        try {
            return handle.addAll(c);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends E> c) {
        try {
            return handle.addAll(index, c);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        try {
            return handle.removeAll(c);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        try {
            return handle.retainAll(c);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        try {
            handle.replaceAll(operator);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public void sort(Comparator<? super E> c) {
        try {
            handle.sort(c);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public void clear() {
        try {
            handle.clear();
        } finally {
            noteUpdate();
        }
    }

    @Override
    public boolean equals(Object o) {
        return handle.equals(o);
    }

    @Override
    public int hashCode() {
        return handle.hashCode();
    }

    @Override
    public E get(int index) {
        return handle.get(index);
    }

    @Override
    public E set(int index, E element) {
        try {
            return handle.set(index, element);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public void add(int index, E element) {
        try {
            handle.add(index, element);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public E remove(int index) {
        try {
            return handle.remove(index);
        } finally {
            noteUpdate();
        }
    }

    @Override
    public int indexOf(Object o) {
        return handle.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return handle.lastIndexOf(o);
    }

    @NotNull
    @Override
    public ListIterator<E> listIterator() {
        return new SyncLinkListIterator(handle.listIterator());
    }

    @NotNull
    @Override
    public ListIterator<E> listIterator(int index) {
        return new SyncLinkListIterator(handle.listIterator(index));
    }

    @NotNull
    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return handle.subList(fromIndex, toIndex);
    }

    @Override
    public Spliterator<E> spliterator() {
        return handle.spliterator();
    }

}
