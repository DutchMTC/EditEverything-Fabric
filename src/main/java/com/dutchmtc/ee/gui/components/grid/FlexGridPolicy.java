package com.dutchmtc.ee.gui.components.grid;

import com.dutchmtc.ee.gui.components.GuiComponent;
import com.dutchmtc.ee.gui.components.GuiComponentContainer;
import com.dutchmtc.ee.gui.components.GuiComponentLocation;
import com.dutchmtc.ee.utils.EEUtils;

import java.util.List;

public class FlexGridPolicy implements GridPolicy {
    public enum FlexGridType {
        HORIZONTAL, VERTICAL
    }

    private final int maxElement;
    private final FlexGridType type;
    private final GridAlignmentX.AlignFunction alignmentX;
    private final GridAlignmentY.AlignFunction alignmentY;

    public FlexGridPolicy(int maxElement, FlexGridType type, GridAlignmentX alignmentX, GridAlignmentY alignmentY) {
        this.type = type;
        this.maxElement = EEUtils.positiveStrict(maxElement, "maxElement");
        this.alignmentX = alignmentX.alignFunction;
        this.alignmentY = alignmentY.alignFunction;
    }

    @Override
    public void computeLocations(GuiComponentContainer container, List<GuiComponentLocation> components) {
        var tileWidth = components.stream().map(GuiComponentLocation::getComponent).mapToInt(GuiComponent::getWidth)
                .max().orElse(0);
        var tileHeight = components.stream().map(GuiComponentLocation::getComponent).mapToInt(GuiComponent::getHeight)
                .max().orElse(0);

        switch (type) {
            case VERTICAL:
                for (var it = components.listIterator(); it.hasNext(); ) {
                    var i = it.nextIndex();
                    var c = it.next();

                    c.x = (i % maxElement) * tileWidth + alignmentX.apply(tileWidth, c.getComponent().getWidth());
                    c.y = (i / maxElement) * tileHeight + alignmentY.apply(tileHeight, c.getComponent().getHeight());
                }
                break;
            case HORIZONTAL:
                for (var it = components.listIterator(); it.hasNext(); ) {
                    var i = it.nextIndex();
                    var c = it.next();

                    c.x = (i / maxElement) * tileWidth + alignmentX.apply(tileWidth, c.getComponent().getWidth());
                    c.y = (i % maxElement) * tileHeight + alignmentY.apply(tileHeight, c.getComponent().getHeight());
                }
                break;
        }
    }
}
