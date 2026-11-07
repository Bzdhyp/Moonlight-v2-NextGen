package wtf.moonlight.ui.esp;

public final class View {
    private final float yaw;
    private final float pitch;
    private final float pivotY;
    private final float pivotZ;
    private final float cosYaw;
    private final float sinYaw;
    private final float cosPitch;
    private final float sinPitch;

    public View(Kind kind, float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.pivotY = (kind.bottom() + kind.top()) / 2;
        this.pivotZ = (kind.front() + kind.back()) / 2;
        this.cosYaw = (float) Math.cos(yaw);
        this.sinYaw = (float) Math.sin(yaw);
        this.cosPitch = (float) Math.cos(pitch);
        this.sinPitch = (float) Math.sin(pitch);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float pivotY() {
        return pivotY;
    }

    public float pivotZ() {
        return pivotZ;
    }

    public float x(float x, float z) {
        return -(x * cosYaw + (z - pivotZ) * sinYaw);
    }

    public float y(float x, float y, float z) {
        return -((y - pivotY) * cosPitch - turned(x, z) * sinPitch);
    }

    public float depth(float x, float y, float z) {
        return (y - pivotY) * sinPitch + turned(x, z) * cosPitch;
    }

    private float turned(float x, float z) {
        return -x * sinYaw + (z - pivotZ) * cosYaw;
    }
}
