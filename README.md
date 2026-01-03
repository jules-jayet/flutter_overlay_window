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

// Listen for orientation change event
FlutterOverlayWindow.orientationChangedStream.listen((orientation) {
  // orientation is OverlayOrientation.portrait or OverlayOrientation.landscape
  print('Orientation changed to: $orientation');
  // Example: adjust overlay layout based on orientation
});

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

### Drag end event details
- EventChannel: `flutter_overlay_window_drag`
- Event emitted on drag end (ACTION_UP) when `enableDrag: true`:

```json
{ "event": "overlay_moved", "x": 123, "y": 456 }
```

- `x` and `y` are integers in dp (density-independent pixels).
- No events are emitted during movement (ACTION_MOVE), only once when the drag ends.

### Orientation change event details
- EventChannel: `flutter_overlay_window_orientation`
- Event emitted when device orientation changes (portrait ↔ landscape):

```json
{ "event": "orientation_changed", "orientation": "portrait" }
```

- `orientation` is either `"portrait"` or `"landscape"`.
- The stream `orientationChangedStream` returns typed `OverlayOrientation` enum values.
- Events are only emitted when orientation actually changes, not on every configuration change.

### License
MIT
