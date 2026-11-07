package wtf.moonlight.ui.esp;

public enum Kind {
    PLAYER("Players", "Player", 0xF007, .49F, -.27F, .27F, 0, 1.91F, 2.3F),
    MOB("Mobs", "Mob", 0xF54C, .5F, -.65F, .25F, 0, 2, 2.45F),
    ITEM("Items", "Item", 0xF466, .25F, -.25F, .25F, .0625F, .7625F, .75F),
    TNT("TNT", "TNT", 0xF1E2, .5F, -.5F, .5F, 0, 1, 1.48F),
    CRYSTAL("Crystals", "Crystal", 0xF3A5, .87F, -.87F, .87F, -1, 2.27F, 2.5F),
    FIREBALL("Fireballs", "Fireball", 0xF06D, .5625F, -.05F, .05F, -.1875F, .9375F, 1.5F);

    private final String plural;
    private final String single;
    private final String icon;
    private final float half;
    private final float front;
    private final float back;
    private final float bottom;
    private final float top;
    private final float tag;

    Kind(String plural, String single, int icon, float half, float front, float back, float bottom, float top, float tag) {
        this.plural = plural;
        this.single = single;
        this.icon = Character.toString(icon);
        this.half = half;
        this.front = front;
        this.back = back;
        this.bottom = bottom;
        this.top = top;
        this.tag = tag;
    }

    public String plural() {
        return plural;
    }

    public String single() {
        return single;
    }

    public String icon() {
        return icon;
    }

    public float half() {
        return half;
    }

    public float front() {
        return front;
    }

    public float back() {
        return back;
    }

    public float bottom() {
        return bottom;
    }

    public float top() {
        return top;
    }

    public float tag() {
        return tag;
    }
}
