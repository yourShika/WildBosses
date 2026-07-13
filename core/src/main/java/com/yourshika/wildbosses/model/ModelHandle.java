package com.yourshika.wildbosses.model;

/**
 * A handle to a model attached to a boss entity. For the vanilla adapter this is a no-op;
 * for BetterModel it drives the underlying tracker (animations, tint, removal).
 */
public interface ModelHandle {

    void playAnimation(String name, boolean loop);

    void stopAnimation(String name);

    void setTint(int rgb);

    void remove();

    /** A handle that does nothing (used when there is no custom model). */
    ModelHandle NOOP = new ModelHandle() {
        @Override
        public void playAnimation(String name, boolean loop) {
        }

        @Override
        public void stopAnimation(String name) {
        }

        @Override
        public void setTint(int rgb) {
        }

        @Override
        public void remove() {
        }
    };
}
