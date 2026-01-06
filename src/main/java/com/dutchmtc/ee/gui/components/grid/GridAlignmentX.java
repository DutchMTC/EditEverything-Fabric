package com.dutchmtc.ee.gui.components.grid;

public enum GridAlignmentX {
    LEFT((containerWidth, width) -> 0),

    CENTER((containerWidth, width) -> (containerWidth - width) / 2),

    RIGHT((containerWidth, width) -> (containerWidth - width));

    @FunctionalInterface
    public interface AlignFunction {
        /**
         * process the alignement of the object
         *
         * @param containerWidth the width of the container
         * @param width          the width of the object
         * @return the X location of the object
         */
        int apply(int containerWidth, int width);
    }

    public final AlignFunction alignFunction;

    GridAlignmentX(AlignFunction alignFunction) {
        this.alignFunction = alignFunction;
    }
}
