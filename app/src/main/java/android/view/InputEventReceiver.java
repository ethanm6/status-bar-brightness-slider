package android.view;

import android.os.Looper;

// Compile-time stub. At runtime the BootClassLoader provides the real class,
// so our subclass correctly extends the live framework version.
public abstract class InputEventReceiver {
    public InputEventReceiver(InputChannel inputChannel, Looper looper) {
        throw new RuntimeException("Stub!");
    }
    public void onInputEvent(InputEvent event) {}
    public final void finishInputEvent(InputEvent event, boolean handled) {
        throw new RuntimeException("Stub!");
    }
    public void dispose() {
        throw new RuntimeException("Stub!");
    }
}
