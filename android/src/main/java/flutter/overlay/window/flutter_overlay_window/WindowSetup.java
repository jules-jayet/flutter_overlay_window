package flutter.overlay.window.flutter_overlay_window;

import android.view.Gravity;
import android.view.WindowManager;

import io.flutter.plugin.common.BasicMessageChannel;

public abstract class WindowSetup {

    static int height = WindowManager.LayoutParams.MATCH_PARENT;
    static int width = WindowManager.LayoutParams.MATCH_PARENT;
    static int flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    static int gravity = Gravity.CENTER;
    static BasicMessageChannel<Object> messenger = null;
    static String positionGravity = "none";
    static boolean enableDrag = false;

    static void setFlag(String name) {
        if (name.equalsIgnoreCase("flagNotFocusable") || name.equalsIgnoreCase("defaultFlag")) {
            flag = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else if (name.equalsIgnoreCase("flagNotTouchable") || name.equalsIgnoreCase("clickThrough")) {
            flag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        } else if (name.equalsIgnoreCase("flagNotTouchModal") || name.equalsIgnoreCase("focusPointer")) {
            flag = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }
    }

    static void setGravityFromAlignment(String alignment) {
        switch (alignment) {
            case "topLeft":
                gravity = Gravity.TOP | Gravity.LEFT;
                break;
            case "topCenter":
                gravity = Gravity.TOP;
                break;
            case "topRight":
                gravity = Gravity.TOP | Gravity.RIGHT;
                break;
            case "centerLeft":
                gravity = Gravity.CENTER | Gravity.LEFT;
                break;
            case "center":
                gravity = Gravity.CENTER;
                break;
            case "centerRight":
                gravity = Gravity.CENTER | Gravity.RIGHT;
                break;
            case "bottomLeft":
                gravity = Gravity.BOTTOM | Gravity.LEFT;
                break;
            case "bottomCenter":
                gravity = Gravity.BOTTOM;
                break;
            case "bottomRight":
                gravity = Gravity.BOTTOM | Gravity.RIGHT;
                break;
        }
    }
}
