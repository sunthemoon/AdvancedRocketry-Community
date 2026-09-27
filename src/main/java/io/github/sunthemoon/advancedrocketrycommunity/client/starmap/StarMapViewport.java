package io.github.sunthemoon.advancedrocketrycommunity.client.starmap;

/** Bounded pan/zoom and hit region, usable without a client or graphics context. */
public final class StarMapViewport {
    public static final double MIN_ZOOM = 0.25;
    public static final double MAX_ZOOM = 2.0;
    private final StarMapLayout layout;
    private final int x, y, width, height;
    private double centerX, centerY;
    private double zoom = 1;

    public StarMapViewport(StarMapLayout layout, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) { throw new IllegalArgumentException("Empty viewport"); }
        this.layout = layout;
        this.x = x; this.y = y; this.width = width; this.height = height;
        centerX = layout.maxX() / 2; centerY = layout.maxY() / 2;
    }

    public boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    public double screenX(double world) { return x + width / 2.0 + (world - centerX) * zoom; }
    public double screenY(double world) { return y + height / 2.0 + (world - centerY) * zoom; }
    public double zoom() { return zoom; }
    public void focus(StarMapLayout.Node node) { centerX = node.x(); centerY = node.y(); clamp(); }

    public void drag(double dx, double dy) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) { return; }
        centerX -= dx / zoom; centerY -= dy / zoom; clamp();
    }

    public void zoom(double steps, double mouseX, double mouseY) {
        if (!Double.isFinite(steps) || !contains(mouseX, mouseY)) { return; }
        double old = zoom;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * Math.pow(1.2, Math.max(-32, Math.min(32, steps)))));
        centerX += (mouseX - x - width / 2.0) * (1 / old - 1 / zoom);
        centerY += (mouseY - y - height / 2.0) * (1 / old - 1 / zoom);
        clamp();
    }

    private void clamp() {
        centerX = Math.max(-40, Math.min(layout.maxX() + 40, centerX));
        centerY = Math.max(-40, Math.min(layout.maxY() + 40, centerY));
    }
}
