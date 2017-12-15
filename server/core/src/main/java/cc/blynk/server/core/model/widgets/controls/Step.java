package cc.blynk.server.core.model.widgets.controls;

import cc.blynk.server.core.model.enums.PinMode;
import cc.blynk.server.core.model.widgets.OnePinWidget;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 01.04.15.
 */
public class Step extends OnePinWidget {

    public float step;

    public boolean isArrowsOn;

    public boolean isLoopOn;

    public boolean isSendStep;

    public int frequency;

    @Override
    public PinMode getModeType() {
        return PinMode.out;
    }

    @Override
    public int getPrice() {
        return 500;
    }
}
