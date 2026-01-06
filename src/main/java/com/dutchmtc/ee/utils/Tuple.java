package com.dutchmtc.ee.utils;

/**
 * Tuple to store data
 *
 * @param <A> First type
 * @param <B> Second type
 * @author ATE47
 * @see QuadTuple
 * @since 2.0
 */
public class Tuple<A, B> {
    /**
     * Quad Tuple to store data
     *
     * @param <A> First type
     * @param <B> Second type
     * @param <C> Third type
     * @param <D> Fourth type
     * @author ATE47
     * @see Tuple
     * @since 2.0
     */
    public static class QuadTuple<A, B, C, D> extends Tuple<A, B> {
        public C c;
        public D d;

        /**
         * Create a quad tuple with four data
         *
         * @param a a value
         * @param b b value
         * @param c c value
         * @param d d value
         * @since 2.0
         */
        public QuadTuple(A a, B b, C c, D d) {
            super(a, b);
            this.c = c;
            this.d = d;
        }

        /**
         * Create a new quad tuple with same data
         *
         * @param quad the quad to clone
         * @since 2.1
         */
        public QuadTuple(QuadTuple<A, B, C, D> quad) {
            this(quad.a, quad.b, quad.c, quad.d);
        }
    }

    public A a;

    public B b;

    /**
     * Create a simple tuple with two data
     *
     * @param a a value
     * @param b b value
     * @since 2.0
     */
    public Tuple(A a, B b) {
        this.a = a;
        this.b = b;
    }

    /**
     * Create a new tuple with same data
     *
     * @param tuple the tuple to clone
     * @since 2.0
     */
    public Tuple(Tuple<A, B> tuple) {
        this(tuple.a, tuple.b);
    }
}
