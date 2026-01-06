package com.dutchmtc.ee.gui.components.grid;

import com.dutchmtc.ee.gui.components.GuiComponent;
import com.dutchmtc.ee.gui.components.grid.FlexGridPolicy.FlexGridType;

public class GridPolicies {

    private static final GridPolicy ABSOLUTE = (cc, components) -> components.forEach(l -> {
        var c = l.getComponent();
        l.x = c.getX();
        l.y = c.getY();
    });

    /**
     * @return a grid using the {@link GuiComponent#getX()} and
     * {@link GuiComponent#getY()} functions to get the location
     */
    public static GridPolicy absoluteGrid() {
        return ABSOLUTE;
    }

    /**
     * @return a grid putting all the components vertically
     */
    public static GridPolicy verticalGrid(GridAlignmentX alignment) {
        return new VerticalGridPolicy(alignment);
    }

    /**
     * @return a grid putting all the components horizontally
     */
    public static GridPolicy horizontalGrid(GridAlignmentY alignment) {
        return new HorizontalGridPolicy(alignment);
    }

    /**
     * @param maxElementY max element vertically
     * @return FlexGridPolicy
     */
    public static GridPolicy horizontalFlexGrid(int maxElementY) {
        return new FlexGridPolicy(maxElementY, FlexGridType.HORIZONTAL, GridAlignmentX.CENTER, GridAlignmentY.MIDDLE);
    }

    /**
     * @param maxElementX max element horizontally
     * @param alignmentX align x
     * @param alignmentY align y
     * @return FlexGridPolicy
     */
    public static GridPolicy verticalFlexGrid(int maxElementX, GridAlignmentX alignmentX, GridAlignmentY alignmentY) {
        return new FlexGridPolicy(maxElementX, FlexGridType.VERTICAL, alignmentX, alignmentY);
    }

    /**
     * @param maxElementX max element horizontally
     * @return FlexGridPolicy
     */
    public static GridPolicy verticalFlexGrid(int maxElementX) {
        return new FlexGridPolicy(maxElementX, FlexGridType.VERTICAL, GridAlignmentX.CENTER, GridAlignmentY.MIDDLE);
    }

    /**
     * @param maxElementY max element vertically
     * @param alignmentX align x
     * @param alignmentY align y
     * @return FlexGridPolicy
     */
    public static GridPolicy horizontalFlexGrid(int maxElementY, GridAlignmentX alignmentX, GridAlignmentY alignmentY) {
        return new FlexGridPolicy(maxElementY, FlexGridType.HORIZONTAL, alignmentX, alignmentY);
    }
}
