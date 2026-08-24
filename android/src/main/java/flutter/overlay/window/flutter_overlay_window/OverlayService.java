package flutter.overlay.window.flutter_overlay_window;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.graphics.Rect;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import io.flutter.embedding.android.FlutterTextureView;
import io.flutter.embedding.android.FlutterView;
import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.embedding.engine.FlutterEngineGroup;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.JSONMessageCodec;
import io.flutter.plugin.common.MethodChannel;

@SuppressWarnings({"deprecation"})
public class OverlayService extends Service implements View.OnTouchListener {
    private final int DEFAULT_NAV_BAR_HEIGHT_DP = 48;
    private final int DEFAULT_STATUS_BAR_HEIGHT_DP = 25;

    private Integer mStatusBarHeight = -1;
    private Integer mNavigationBarHeight = -1;
    private Resources mResources;

    public static final String INTENT_EXTRA_IS_CLOSE_WINDOW = "IsCloseWindow";

    private static OverlayService instance;
    public static boolean isRunning = false;
    private WindowManager windowManager = null;
    private FlutterView flutterView;
    private MethodChannel flutterChannel;
    private BasicMessageChannel<Object> overlayMessageChannel;
    private int clickableFlag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    private int currentOrientation = Configuration.ORIENTATION_UNDEFINED;

    private Handler mAnimationHandler = new Handler(Looper.getMainLooper());
    private float lastX, lastY;
    private int lastYPosition;
    private boolean dragging;
    private static final float MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER = 0.8f;
    private Point szWindow = new Point();
    private Timer mTrayAnimationTimer;
    private TrayAnimationTimerTask mTrayTimerTask;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d("OverlayService", "Destroying the overlay window service");
        if (mTrayAnimationTimer != null) {
            mTrayAnimationTimer.cancel();
            mTrayAnimationTimer = null;
        }
        detachFlutterView();
        isRunning = false;
        instance = null;
    }

    // The window and its view always come down together; tearing them down
    // separately is what used to leave a detached view registered with the
    // WindowManager on restart.
    private void detachFlutterView() {
        if (windowManager != null && flutterView != null) {
            try { windowManager.removeView(flutterView); } catch (Throwable ignored) {}
        }
        windowManager = null;
        if (flutterView != null) {
            try { flutterView.detachFromFlutterEngine(); } catch (Throwable ignored) {}
            flutterView = null;
        }
    }

    // Single owner of the cached overlay engine: it was created in three
    // different places (plugin attach, onCreate, onStartCommand), each with
    // its own copy of the same block.
    static FlutterEngine ensureEngine(Context context) {
        FlutterEngine engine = FlutterEngineCache.getInstance().get(OverlayConstants.CACHED_TAG);
        if (engine == null) {
            FlutterEngineGroup engineGroup = new FlutterEngineGroup(context);
            DartExecutor.DartEntrypoint entryPoint = new DartExecutor.DartEntrypoint(
                    FlutterInjector.instance().flutterLoader().findAppBundlePath(),
                    "overlayMain");
            engine = engineGroup.createAndRunEngine(context, entryPoint);
            FlutterEngineCache.getInstance().put(OverlayConstants.CACHED_TAG, engine);
        }
        return engine;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    
        if (intent == null) {
            Log.w("OverlayService", "onStartCommand with null intent. NOT_STICKY -> stopping self");
            stopSelf();
            isRunning = false;
            return START_NOT_STICKY;
        }
    
        mResources = getApplicationContext().getResources();
    
        final int startX = intent.getIntExtra("startX", OverlayConstants.DEFAULT_XY);
        final int startY = intent.getIntExtra("startY", OverlayConstants.DEFAULT_XY);
        final boolean isCloseWindow = intent.getBooleanExtra(INTENT_EXTRA_IS_CLOSE_WINDOW, false);
    
        if (isCloseWindow) {
            detachFlutterView();
            stopSelf();
            isRunning = false;
            return START_NOT_STICKY;
        }

        detachFlutterView();

        isRunning = true;
        Log.d("OverlayService", "Service started (BG-driven)");

        FlutterEngine engine = FlutterEngineCache.getInstance().get(OverlayConstants.CACHED_TAG);
        if (engine == null) {
            Log.e("OverlayService", "Flutter engine not found in cache, creating a new one");
            engine = ensureEngine(this);
        } else {
            engine.getLifecycleChannel().appIsResumed();
        }
        flutterChannel = new MethodChannel(engine.getDartExecutor(), OverlayConstants.OVERLAY_TAG);
        overlayMessageChannel = new BasicMessageChannel<>(
                engine.getDartExecutor(), OverlayConstants.MESSENGER_TAG, JSONMessageCodec.INSTANCE);
    
        flutterView = new FlutterView(getApplicationContext(), new FlutterTextureView(getApplicationContext()));
        flutterView.attachToFlutterEngine(engine);
        flutterView.setFitsSystemWindows(true);
        flutterView.setFocusable(true);
        flutterView.setFocusableInTouchMode(true);
        flutterView.setBackgroundColor(Color.TRANSPARENT);
    
        flutterChannel.setMethodCallHandler((call, result) -> {
            if ("updateFlag".equals(call.method)) {
                String flag = String.valueOf(call.argument("flag"));
                updateOverlayFlag(result, flag);
            } else if ("updateOverlayPosition".equals(call.method)) {
                int x = call.<Integer>argument("x");
                int y = call.<Integer>argument("y");
                moveOverlay(x, y, result);
            } else if ("resizeOverlay".equals(call.method)) {
                int width = call.argument("width");
                int height = call.argument("height");
                boolean enableDrag = call.argument("enableDrag");
                resizeOverlay(width, height, enableDrag, result);
            }
        });
    
        overlayMessageChannel.setMessageHandler((message, reply) -> {
            WindowSetup.messenger.send(message);
        });
    
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
            Rect b = metrics.getBounds();
            szWindow.set(b.width(), b.height());
        } else {
            //noinspection deprecation
            windowManager.getDefaultDisplay().getSize(szWindow);
        }
    
        int width = (WindowSetup.width == -1999) ? -1 : WindowSetup.width;
        int height = (WindowSetup.height != -1999) ? WindowSetup.height : screenHeight();
    
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                0,
                -statusBarHeightPx(),
                type,
                WindowSetup.flag
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );
    
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && WindowSetup.flag == clickableFlag) {
            params.alpha = MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER;
        }
    
        params.gravity = WindowSetup.gravity;
        flutterView.setOnTouchListener(this);
    
        try {
            windowManager.addView(flutterView, params);
        } catch (Throwable t) {
            Log.e("OverlayService", "addView failed: " + t);
            stopSelf();
            isRunning = false;
            return START_NOT_STICKY;
        }
    
        int dx = startX == OverlayConstants.DEFAULT_XY ? 0 : startX;
        int dy = startY == OverlayConstants.DEFAULT_XY ? -statusBarHeightPx() : startY;
        moveOverlay(dx, dy, null);
    
        return START_NOT_STICKY;
    }

    private int screenHeight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
            Rect b = metrics.getBounds();
            int baseHeight = b.height();
            return inPortrait()
                    ? baseHeight + statusBarHeightPx() + navigationBarHeightPx()
                    : baseHeight + statusBarHeightPx();
        } else {
            //noinspection deprecation
            Display display = windowManager.getDefaultDisplay();
            //noinspection deprecation
            DisplayMetrics dm = new DisplayMetrics();
            //noinspection deprecation
            display.getRealMetrics(dm);
            return inPortrait()
                    ? dm.heightPixels + statusBarHeightPx() + navigationBarHeightPx()
                    : dm.heightPixels + statusBarHeightPx();
        }
    }

    private int statusBarHeightPx() {
        if (mStatusBarHeight == -1) {
            int statusBarHeightId = mResources.getIdentifier("status_bar_height", "dimen", "android");

            if (statusBarHeightId > 0) {
                mStatusBarHeight = mResources.getDimensionPixelSize(statusBarHeightId);
            } else {
                mStatusBarHeight = dpToPx(DEFAULT_STATUS_BAR_HEIGHT_DP);
            }
        }

        return mStatusBarHeight;
    }

    int navigationBarHeightPx() {
        if (mNavigationBarHeight == -1) {
            int navBarHeightId = mResources.getIdentifier("navigation_bar_height", "dimen", "android");

            if (navBarHeightId > 0) {
                mNavigationBarHeight = mResources.getDimensionPixelSize(navBarHeightId);
            } else {
                mNavigationBarHeight = dpToPx(DEFAULT_NAV_BAR_HEIGHT_DP);
            }
        }

        return mNavigationBarHeight;
    }


    private void updateOverlayFlag(MethodChannel.Result result, String flag) {
        if (windowManager != null) {
            WindowSetup.setFlag(flag);
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) flutterView.getLayoutParams();
            params.flags = WindowSetup.flag | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && WindowSetup.flag == clickableFlag) {
                params.alpha = MAXIMUM_OPACITY_ALLOWED_FOR_S_AND_HIGHER;
            } else {
                params.alpha = 1;
            }
            windowManager.updateViewLayout(flutterView, params);
            result.success(true);
        } else {
            result.success(false);
        }
    }

    private void resizeOverlay(int width, int height, boolean enableDrag, MethodChannel.Result result) {
        if (windowManager != null) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) flutterView.getLayoutParams();
            params.width = (width == -1999 || width == -1) ? -1 : dpToPx(width);
            params.height = (height == -1999 || height == -1) ? -1 : dpToPx(height);
            WindowSetup.enableDrag = enableDrag;
            windowManager.updateViewLayout(flutterView, params);
            result.success(true);
        } else {
            result.success(false);
        }
    }

    private void moveOverlay(int x, int y, MethodChannel.Result result) {
        if (windowManager != null) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) flutterView.getLayoutParams();
            params.x = (x == -1999 || x == -1) ? -1 : dpToPx(x);
            params.y = dpToPx(y);
            windowManager.updateViewLayout(flutterView, params);
            if (result != null)
                result.success(true);
        } else {
            if (result != null)
                result.success(false);
        }
    }


    public static Map<String, Double> getCurrentPosition() {
        if (instance != null && instance.flutterView != null) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) instance.flutterView.getLayoutParams();
            Map<String, Double> position = new HashMap<>();
            position.put("x", instance.pxToDp(params.x));
            position.put("y", instance.pxToDp(params.y));
            return position;
        }
        return null;
    }

    public static boolean moveOverlay(int x, int y) {
        if (instance != null && instance.flutterView != null) {
            if (instance.windowManager != null) {
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) instance.flutterView.getLayoutParams();
                params.x = (x == -1999 || x == -1) ? -1 : instance.dpToPx(x);
                params.y = instance.dpToPx(y);
                instance.windowManager.updateViewLayout(instance.flutterView, params);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }


    @Override
    public void onCreate() {
        // The channels are wired in onStartCommand, which always runs before
        // they are used; here the engine only needs to exist.
        ensureEngine(this);

        currentOrientation = getResources().getConfiguration().orientation;

        // Do NOT promote to foreground: overlay should not create a second FGS.
        // Keep service non-foreground; instance tracking remains for overlay controls.
        instance = this;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Check if orientation has changed
        if (newConfig.orientation != currentOrientation && newConfig.orientation != Configuration.ORIENTATION_UNDEFINED) {
            currentOrientation = newConfig.orientation;
            
            // Emit orientation change event
            String orientationString = (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) ? "portrait" : "landscape";
            
            if (FlutterOverlayWindowPlugin.orientationEventSink != null) {
                Runnable r = () -> {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("event", "orientation_changed");
                    payload.put("orientation", orientationString);
                    FlutterOverlayWindowPlugin.orientationEventSink.success(payload);
                    Log.d("OverlayService", "Orientation changed to: " + orientationString);
                };
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    r.run();
                } else {
                    new Handler(Looper.getMainLooper()).post(r);
                }
            } else {
                Log.w("OverlayService", "orientationEventSink is null, orientation_changed event lost");
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                (float) dp, mResources.getDisplayMetrics());
    }

    private double pxToDp(int px) {
        return (double) px / mResources.getDisplayMetrics().density;
    }

    private boolean inPortrait() {
        return mResources.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (windowManager != null && WindowSetup.enableDrag) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) flutterView.getLayoutParams();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = false;
                    lastX = event.getRawX();
                    lastY = event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - lastX;
                    float dy = event.getRawY() - lastY;
                    if (!dragging && dx * dx + dy * dy < 25) {
                        return false;
                    }
                    lastX = event.getRawX();
                    lastY = event.getRawY();
                    boolean invertX = WindowSetup.gravity == (Gravity.TOP | Gravity.RIGHT)
                            || WindowSetup.gravity == (Gravity.CENTER | Gravity.RIGHT)
                            || WindowSetup.gravity == (Gravity.BOTTOM | Gravity.RIGHT);
                    boolean invertY = WindowSetup.gravity == (Gravity.BOTTOM | Gravity.LEFT)
                            || WindowSetup.gravity == Gravity.BOTTOM
                            || WindowSetup.gravity == (Gravity.BOTTOM | Gravity.RIGHT);
                    int xx = params.x + ((int) dx * (invertX ? -1 : 1));
                    int yy = params.y + ((int) dy * (invertY ? -1 : 1));
                    params.x = xx;
                    params.y = yy;
                    if (windowManager != null) {
                        windowManager.updateViewLayout(flutterView, params);
                    }
                    dragging = true;
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    lastYPosition = params.y;
                    if (!WindowSetup.positionGravity.equals("none")) {
                        if (windowManager == null) return false;
                        windowManager.updateViewLayout(flutterView, params);
                        mTrayTimerTask = new TrayAnimationTimerTask();
                        mTrayAnimationTimer = new Timer();
                        mTrayAnimationTimer.schedule(mTrayTimerTask, 0, 25);
                    }
                    // Emit overlay_moved event with final position (only if drag occurred)
                    if (dragging) {
                        final int finalX = params.x;
                        final int finalY = params.y;
                        if (FlutterOverlayWindowPlugin.dragEventSink != null) {
                            Runnable r = () -> {
                                Map<String, Object> payload = new HashMap<>();
                                payload.put("event", "overlay_moved");
                                payload.put("x", (int) Math.round(pxToDp(finalX)));
                                payload.put("y", (int) Math.round(pxToDp(finalY)));
                                FlutterOverlayWindowPlugin.dragEventSink.success(payload);
                            };
                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                r.run();
                            } else {
                                new Handler(Looper.getMainLooper()).post(r);
                            }
                        } else {
                            Log.w("OverlayService", "dragEventSink is null, overlay_moved event lost");
                        }
                        dragging = false;
                    }
                    return false;
                default:
                    return false;
            }
            return false;
        }
        return false;
    }

    private class TrayAnimationTimerTask extends TimerTask {
        int mDestX;
        int mDestY;
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) flutterView.getLayoutParams();

        public TrayAnimationTimerTask() {
            super();
            mDestY = lastYPosition;
            switch (WindowSetup.positionGravity) {
                case "auto":
                    mDestX = (params.x + (flutterView.getWidth() / 2)) <= szWindow.x / 2 ? 0 : szWindow.x - flutterView.getWidth();
                    return;
                case "left":
                    mDestX = 0;
                    return;
                case "right":
                    mDestX = szWindow.x - flutterView.getWidth();
                    return;
                default:
                    mDestX = params.x;
                    mDestY = params.y;
                    break;
            }
        }

        @Override
        public void run() {
            mAnimationHandler.post(() -> {
                params.x = (2 * (params.x - mDestX)) / 3 + mDestX;
                params.y = (2 * (params.y - mDestY)) / 3 + mDestY;
                if (windowManager != null) {
                    windowManager.updateViewLayout(flutterView, params);
                }
                if (Math.abs(params.x - mDestX) < 2 && Math.abs(params.y - mDestY) < 2) {
                    TrayAnimationTimerTask.this.cancel();
                    mTrayAnimationTimer.cancel();
                }
            });
        }
    }


}