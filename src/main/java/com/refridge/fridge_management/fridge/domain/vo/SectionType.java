package com.refridge.fridge_management.fridge.domain.vo;

public enum SectionType {

    ROOM_TEMPERATURE(0, "상온"),
    REFRIGERATED(1, "냉장"),
    FREEZER(2, "냉동");

    private final int temperatureOrder;  // 높을수록 낮은 온도 (냉동=2 > 냉장=1 > 상온=0)
    private final String displayName;

    SectionType(int temperatureOrder, String displayName) {
        this.temperatureOrder = temperatureOrder;
        this.displayName = displayName;
    }

    /**
     * 상행성(온도 상승) 이동 여부.
     * 냉동(2)→냉장(1), 냉동(2)→상온(0), 냉장(1)→상온(0) 이 해당.
     */
    public boolean isUpwardMoveTo(SectionType to) {
        return this.temperatureOrder > to.temperatureOrder;
    }

    public String getDisplayName() { return displayName; }
}