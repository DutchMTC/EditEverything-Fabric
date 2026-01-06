package com.dutchmtc.ee.gui.components.grid;

import com.dutchmtc.ee.gui.components.GuiComponent;
import com.dutchmtc.ee.gui.components.GuiComponentContainer;
import com.dutchmtc.ee.gui.components.GuiComponentLocation;

import java.util.List;

public class HorizontalGridPolicy implements GridPolicy {
    private final GridAlignmentY.AlignFunction alignFunction;

    public HorizontalGridPolicy(GridAlignmentY alignment) {
        this.alignFunction = alignment.alignFunction;
    }

    @Override
    public void computeLocations(GuiComponentContainer container, List<GuiComponentLocation> components) {
        var globalHeight = components.stream().map(GuiComponentLocation::getComponent).mapToInt(GuiComponent::getHeight)
                .max().orElse(0);
        var x = 0;
        for (var l : components) {
            var c = l.getComponent();
            l.x = x;
            l.y = alignFunction.apply(globalHeight, c.getHeight());
            x += c.getWidth();
        }
    }

}
