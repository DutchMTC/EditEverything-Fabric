package com.dutchmtc.ee.gui.components.grid;

import com.dutchmtc.ee.gui.components.GuiComponentContainer;
import com.dutchmtc.ee.gui.components.GuiComponentLocation;

import java.util.List;

@FunctionalInterface
public interface GridPolicy {
    /**
     * compute the x,y location of the {@link GuiComponentLocation} list
     *
     * @param container the component container
     * @param locations the component locations
     */
    void computeLocations(GuiComponentContainer container, List<GuiComponentLocation> locations);
}
