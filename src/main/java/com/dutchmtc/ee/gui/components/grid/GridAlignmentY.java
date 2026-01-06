package com.dutchmtc.ee.gui.components.grid;

public enum GridAlignmentY {
    TOP((containerHeight, height) -> 0),

    MIDDLE((containerHeight, height) -> (containerHeight - height) / 2),

    BOTTOM((containerHeight, height) -> (containerHeight - height));

    @FunctionalInterface
    public interface AlignFunction {
        /**
         * process the alignement of the object
         *
         * @param containerHeight the height of the container
         * @param height          the height of the object
         * @return the Y location of the object
         */
        int apply(int containerHeight, int height);
    }

    public final AlignFunction alignFunction;

    GridAlignmentY(AlignFunction alignFunction) {
        this.alignFunction = alignFunction;
    }
}
