package dev.craftlink.config;
public enum TouchLayout {
    TAP("tap"), MOBILE("mobile"), CLASSIC_TAP("classic_tap"), CLASSIC("classic");
    private final String id;
    TouchLayout(String id) { this.id = id; }
    public String id() { return id; }
}
