## Flutter Overlay Window (fork)

Flutter plugin to display a Flutter overlay on top of other apps, without using a Foreground Service (FGS).

### Why this fork
- Better compatibility with aggressive OEM ROMs (e.g., MIUI) by avoiding an overlay-specific Foreground Service.
- The overlay runs as a normal (non-FGS) Android service using the `SYSTEM_ALERT_WINDOW` permission.

### Installation
Add to your `pubspec.yaml`:

```yaml
dependencies:
  flutter_overlay_window:
    path: ../flutter_overlay_window
```

### Android configuration
In `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<application>
  ...
  <service
      android:name="flutter.overlay.window.flutter_overlay_window.OverlayService"
      android:exported="false" />
</application>
```

Do not declare a `foregroundServiceType` for the overlay and do not add any `FOREGROUND_SERVICE*` permissions for it.

### Overlay entry point (Dart)

```dart
@pragma('vm:entry-point')
void overlayMain() {
  runApp(const MaterialApp(
    debugShowCheckedModeBanner: false,
    home: Material(child: Text('My overlay')),
  ));
}
```

### Usage

```dart
// Check/request overlay permission
final bool hasPermission = await FlutterOverlayWindow.isPermissionGranted();
final bool requested = await FlutterOverlayWindow.requestPermission();

// Show overlay (no notification params needed)
await FlutterOverlayWindow.showOverlay(
  height: 160,
  width: 330,
  enableDrag: true,
  flag: OverlayFlag.defaultFlag,
  alignment: OverlayAlignment.center,
);

// Close overlay
await FlutterOverlayWindow.closeOverlay();

// Listen for overlay <-> app messages
FlutterOverlayWindow.overlayListener.listen((event) {
  // handle event
});

// Listen for drag end event
FlutterOverlayWindow.overlayMovedStream.listen((e) async {
  final x = e['x'] as int; // dp
  final y = e['y'] as int; // dp
  // Example: persist position, then restore it with startPosition on next showOverlay
  // final prefs = await SharedPreferences.getInstance();
  // await prefs.setInt('overlay_x', x);
  // await prefs.setInt('overlay_y', y);
});

// Optional: readiness signal emitted right after overlay is attached
FlutterOverlayWindow.overlayReadyStream.first.then((e) {
  // e = { "event": "overlay_ready", "x": int, "y": int }
});

// Optional: ping to verify EventChannel health
final ok = await FlutterOverlayWindow.pingDragChannel();
if (!ok) {
  // take action (e.g., restart overlay in your app logic)
}

// Update flags/size/position
await FlutterOverlayWindow.updateFlag(OverlayFlag.defaultFlag);
await FlutterOverlayWindow.resizeOverlay(80, 120);
await FlutterOverlayWindow.moveOverlay(OverlayPosition(0, 156));
await FlutterOverlayWindow.getOverlayPosition();
```

#### Available flags
```dart
enum OverlayFlag {
  clickThrough,
  defaultFlag,
  focusPointer,
}
```

#### Position gravity after drag
```dart
enum PositionGravity {
  none,
  right,
  left,
  auto,
}
```

### Notes
- Avoid multiple Foreground Services running at the same time. This fork removes FGS for the overlay.
- If your app needs a Foreground Service (e.g., for location), keep it in your main/background service.
- After app restarts while the overlay remains on screen, the plugin now ensures the drag EventChannel is robust:
  - `overlay_ready` is emitted when the Dart side subscribes, posted on the main thread.
  - A lightweight watchdog in the Android service re-emits `overlay_ready` once the EventChannel sink binds, if needed.
  - Keep your listeners (`overlayReadyStream`, `overlayMovedStream`) attached early during app startup for best reliability.

### Drag end event details
- EventChannel: `flutter_overlay_window_drag`
- Event emitted on drag end (ACTION_UP) when `enableDrag: true`:

```json
{ "event": "overlay_moved", "x": 123, "y": 456 }
```

- `x` and `y` are integers in dp (density-independent pixels).
- No events are emitted during movement (ACTION_MOVE), only once when the drag ends.

### License
MIT
