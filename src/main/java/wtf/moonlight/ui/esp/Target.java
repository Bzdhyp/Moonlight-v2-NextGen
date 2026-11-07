package wtf.moonlight.ui.esp;

public record Target(float minX, float minY, float maxX, float maxY, String name, boolean living, float health, float maxHealth, int armor,
                     boolean strength) {
}
