package flutter.overlay.window.flutter_overlay_window;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.plugin.common.BasicMessageChannel;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.JSONMessageCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.PluginRegistry;

public class FlutterOverlayWindowPlugin implements
        FlutterPlugin, ActivityAware, BasicMessageChannel.MessageHandler<Object>, MethodCallHandler,
        PluginRegistry.ActivityResultListener {

    private static final String TAG = "OverlayPlugin";
    private static final int REQUEST_CODE_FOR_OVERLAY_PERMISSION = 1248;

    private MethodChannel channel;
    private Context context;
    private Activity mActivity;
    private ActivityPluginBinding activityBinding;
    private BasicMessageChannel<Object> messenger;
    private Application.ActivityLifecycleCallbacks lifecycleCallbacks;

    // The deferred reply of the single in-flight `requestPermission` call.
    // Only `requestPermission` may park a Result here — every other method
    // answers synchronously — and it is consumed exactly once, because
    // answering a MethodChannel Result twice is a fatal IllegalStateException
    // ("Reply already submitted").
    private Result pendingPermissionResult;

    public static EventChannel.EventSink dragEventSink;
    public static EventChannel.EventSink orientationEventSink;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.context = flutterPluginBinding.getApplicationContext();
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), OverlayConstants.CHANNEL_TAG);
        channel.setMethodCallHandler(this);

        messenger = new BasicMessageChannel<>(flutterPluginBinding.getBinaryMessenger(), OverlayConstants.MESSENGER_TAG,
                JSONMessageCodec.INSTANCE);
        messenger.setMessageHandler(this);
        WindowSetup.messenger = messenger;

        EventChannel dragChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_overlay_window_drag");
        dragChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object args, EventChannel.EventSink events) {
                dragEventSink = events;
            }

            @Override
            public void onCancel(Object args) {
                // Deliberately kept: the sink must survive engine restarts so
                // drag events keep flowing after the app restarts.
            }
        });

        EventChannel orientationChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_overlay_window_orientation");
        orientationChannel.setStreamHandler(new EventChannel.StreamHandler() {
            @Override
            public void onListen(Object args, EventChannel.EventSink events) {
                orientationEventSink = events;
            }

            @Override
            public void onCancel(Object args) {
                // Deliberately kept, same as the drag sink above.
            }
        });
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "checkPermission":
                result.success(checkOverlayPermission());
                break;
            case "requestPermission":
                requestOverlayPermission(result);
                break;
            case "showOverlay":
                showOverlay(call, result);
                break;
            case "isOverlayActive":
                result.success(OverlayService.isRunning);
                break;
            case "moveOverlay": {
                int x = call.argument("x");
                int y = call.argument("y");
                result.success(OverlayService.moveOverlay(x, y));
                break;
            }
            case "getOverlayPosition":
                result.success(OverlayService.getCurrentPosition());
                break;
            case "closeOverlay":
                if (OverlayService.isRunning) {
                    context.stopService(new Intent(context, OverlayService.class));
                    result.success(true);
                } else {
                    // Answer even with nothing to close: an unanswered Result
                    // leaves the Dart future pending forever.
                    result.success(false);
                }
                break;
            default:
                result.notImplemented();
        }
    }

    private void requestOverlayPermission(Result result) {
        // Below M there is no runtime overlay permission, and when it is
        // already held the settings round trip would be a pointless detour.
        if (checkOverlayPermission()) {
            result.success(true);
            return;
        }
        if (mActivity == null) {
            result.error("NO_ACTIVITY",
                    "requestPermission needs a foreground activity to open the overlay settings", null);
            return;
        }
        // A second request while one is in flight would strand the first
        // reply forever; settle it with the current state before parking the
        // new one.
        if (pendingPermissionResult != null) {
            pendingPermissionResult.success(checkOverlayPermission());
        }
        pendingPermissionResult = result;
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + mActivity.getPackageName()));
        mActivity.startActivityForResult(intent, REQUEST_CODE_FOR_OVERLAY_PERMISSION);
    }

    private void showOverlay(MethodCall call, Result result) {
        if (!checkOverlayPermission()) {
            result.error("PERMISSION", "overlay permission is not enabled", null);
            return;
        }
        Integer height = call.argument("height");
        Integer width = call.argument("width");
        String alignment = call.argument("alignment");
        String flag = call.argument("flag");
        Boolean enableDrag = call.argument("enableDrag");
        String positionGravity = call.argument("positionGravity");
        Map<String, Integer> startPosition = call.argument("startPosition");

        WindowSetup.width = width != null ? width : -1;
        WindowSetup.height = height != null ? height : -1;
        WindowSetup.enableDrag = enableDrag != null && enableDrag;
        WindowSetup.setGravityFromAlignment(alignment != null ? alignment : "center");
        WindowSetup.setFlag(flag != null ? flag : "flagNotFocusable");
        WindowSetup.positionGravity = positionGravity;

        final Intent intent = new Intent(context, OverlayService.class);
        intent.putExtra("startX", coordinate(startPosition, "x"));
        intent.putExtra("startY", coordinate(startPosition, "y"));
        context.startService(intent);
        result.success(null);
    }

    private static int coordinate(@Nullable Map<String, Integer> position, String key) {
        Integer value = position != null ? position.get(key) : null;
        return value != null ? value : OverlayConstants.DEFAULT_XY;
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        messenger.setMessageHandler(null);
        // dragEventSink and orientationEventSink are deliberately kept, see
        // the stream handlers in onAttachedToEngine.
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        mActivity = binding.getActivity();
        activityBinding = binding;
        binding.addActivityResultListener(this);
        registerActivityLifecycleMonitoring();
        OverlayService.ensureEngine(context);
    }

    // Auto-closes the overlay when the host activity is destroyed for good.
    private void registerActivityLifecycleMonitoring() {
        if (mActivity == null) {
            return;
        }
        lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(@NonNull Activity activity) {}

            @Override
            public void onActivityResumed(@NonNull Activity activity) {}

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                if (activity == mActivity && OverlayService.isRunning) {
                    context.stopService(new Intent(context, OverlayService.class));
                    Log.d(TAG, "Auto-closed overlay on app destroy");
                }
            }
        };
        mActivity.getApplication().registerActivityLifecycleCallbacks(lifecycleCallbacks);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        // pendingPermissionResult survives on purpose: a rotation while the
        // user sits in the settings screen must not lose the reply — the
        // activity result is delivered after the reattach.
        detachFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        // The settings round trip can never complete once the activity is
        // gone for good; settle a pending request rather than stranding it.
        if (pendingPermissionResult != null) {
            pendingPermissionResult.success(checkOverlayPermission());
            pendingPermissionResult = null;
        }
        detachFromActivity();
    }

    // Mirror of onAttachedToActivity: config-change detaches used to skip
    // this teardown, stacking one more Application lifecycle callback on
    // every rotation for the lifetime of the process.
    private void detachFromActivity() {
        if (activityBinding != null) {
            activityBinding.removeActivityResultListener(this);
            activityBinding = null;
        }
        if (mActivity != null && lifecycleCallbacks != null) {
            mActivity.getApplication().unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
            lifecycleCallbacks = null;
        }
        mActivity = null;
    }

    @Override
    public void onMessage(@Nullable Object message, @NonNull BasicMessageChannel.Reply<Object> reply) {
        FlutterEngine engine = FlutterEngineCache.getInstance().get(OverlayConstants.CACHED_TAG);
        if (engine == null) {
            // No overlay engine to forward to; still answer so the sender's
            // future completes.
            reply.reply(null);
            return;
        }
        new BasicMessageChannel<>(engine.getDartExecutor(), OverlayConstants.MESSENGER_TAG,
                JSONMessageCodec.INSTANCE).send(message, reply);
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_FOR_OVERLAY_PERMISSION) {
            return false;
        }
        // Consume exactly once: the null check absorbs redeliveries and
        // results arriving after the request was already settled elsewhere.
        if (pendingPermissionResult != null) {
            pendingPermissionResult.success(checkOverlayPermission());
            pendingPermissionResult = null;
        }
        return true;
    }
}
