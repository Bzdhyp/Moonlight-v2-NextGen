package wtf.moonlight.module.impl.render;

import wtf.moonlight.module.Module;
import wtf.moonlight.module.Subcategory;
import wtf.moonlight.ui.clickgui.Colors;
import wtf.moonlight.value.impl.BoolValue;
import wtf.moonlight.value.impl.ColorValue;
import wtf.moonlight.value.impl.ListValue;
import wtf.moonlight.value.impl.ModeValue;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class Esp extends Module {
    public static final String PLAYERS = "Players";
    public static final String MOBS = "Mobs";
    public static final String ANIMALS = "Animals";
    public static final String ITEMS = "Items";
    public static final String TNT = "TNT";
    public static final String CRYSTALS = "Crystals";
    public static final String FIREBALLS = "Fireballs";
    public static final String NAME_TAG = "Name Tag";
    public static final String HEALTH_BAR = "Health Bar";
    public static final String ARMOR_BAR = "Armor Bar";
    public static final String BOX = "Box";
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int TOP = 2;
    public static final int BOTTOM = 3;

    private static final BooleanSupplier HIDDEN = () -> false;
    private static final int VANILLA = 2;
    private static final String[] SIDES = {"Left", "Right", "Top", "Bottom"};
    private static final int FIRST = Colors.rgb(71, 148, 253);
    private static final int SECOND = Colors.rgb(71, 253, 160);

    private final ListValue entries = add(new ListValue("Entries", List.of(PLAYERS, MOBS, ANIMALS, ITEMS, TNT, CRYSTALS, FIREBALLS), List.of(PLAYERS)));
    private final ListValue filter = add(new ListValue("Filter", List.of("Teams", "Invisibles"), List.of("Teams")));
    private final ListValue elements = add(new ListValue("Elements", List.of(NAME_TAG, HEALTH_BAR, ARMOR_BAR, BOX), List.of(NAME_TAG, HEALTH_BAR, BOX), HIDDEN));
    private final ModeValue boxMode = add(new ModeValue("Box Mode", 0, HIDDEN, "Normal", "Corners"));
    private final ColorValue boxColor = add(new ColorValue("Box Color", FIRST, HIDDEN));
    private final ColorValue boxSecond = add(new ColorValue("Box Second Color", SECOND, HIDDEN));
    private final BoolValue boxGlow = add(new BoolValue("Box Glow", false, HIDDEN));
    private final ModeValue healthMode = add(new ModeValue("Health Mode", 0, HIDDEN, "Health", "Standard", "Gradient"));
    private final ColorValue healthColor = add(new ColorValue("Health Color", FIRST, HIDDEN));
    private final ColorValue healthSecond = add(new ColorValue("Health Second Color", SECOND, HIDDEN));
    private final BoolValue healthGlow = add(new BoolValue("Health Glow", false, HIDDEN));
    private final ModeValue healthSide = add(new ModeValue("Health Side", LEFT, HIDDEN, SIDES));
    private final ColorValue armorColor = add(new ColorValue("Armor Color", FIRST, HIDDEN));
    private final ColorValue armorSecond = add(new ColorValue("Armor Second Color", SECOND, HIDDEN));
    private final BoolValue armorGlow = add(new BoolValue("Armor Glow", false, HIDDEN));
    private final ModeValue armorSide = add(new ModeValue("Armor Side", BOTTOM, HIDDEN, SIDES));
    private final ModeValue nameStyle = add(new ModeValue("Name Style", 0, HIDDEN, "Modern", "Classic", "Vanilla"));
    private final ColorValue nameColor = add(new ColorValue("Name Color", FIRST, HIDDEN));
    private final ColorValue nameBackground = add(new ColorValue("Name Background", Colors.rgba(0, 0, 0, 110), HIDDEN));
    private final BoolValue nameHealth = add(new BoolValue("Name Health", true, HIDDEN));
    private final BoolValue nameOverlays = add(new BoolValue("Name Overlays", true, HIDDEN));

    public Esp() {
        super("ESP", Subcategory.ENTITIES);
    }

    public boolean shows(String entry) {
        return entries.has(entry);
    }

    public boolean vanillaNames() {
        return nameStyle.get() == VANILLA;
    }

    public boolean hidesTeammates() {
        return filter.has("Teams");
    }

    public boolean showsInvisible() {
        return filter.has("Invisibles");
    }

    public ListValue elements() {
        return elements;
    }

    public ModeValue boxMode() {
        return boxMode;
    }

    public ColorValue boxColor() {
        return boxColor;
    }

    public ColorValue boxSecond() {
        return boxSecond;
    }

    public BoolValue boxGlow() {
        return boxGlow;
    }

    public ModeValue healthMode() {
        return healthMode;
    }

    public ColorValue healthColor() {
        return healthColor;
    }

    public ColorValue healthSecond() {
        return healthSecond;
    }

    public BoolValue healthGlow() {
        return healthGlow;
    }

    public ModeValue healthSide() {
        return healthSide;
    }

    public ColorValue armorColor() {
        return armorColor;
    }

    public ColorValue armorSecond() {
        return armorSecond;
    }

    public BoolValue armorGlow() {
        return armorGlow;
    }

    public ModeValue armorSide() {
        return armorSide;
    }

    public ModeValue nameStyle() {
        return nameStyle;
    }

    public ColorValue nameColor() {
        return nameColor;
    }

    public ColorValue nameBackground() {
        return nameBackground;
    }

    public BoolValue nameHealth() {
        return nameHealth;
    }

    public BoolValue nameOverlays() {
        return nameOverlays;
    }
}
