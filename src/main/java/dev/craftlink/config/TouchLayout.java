package dev.craftlink.config;
public enum TouchLayout {
    MOBILE("mobile"), CLASSIC("classic");
    private final String id;
    TouchLayout(String id) { this.id = id; }
    public String id() { return id; }
}
