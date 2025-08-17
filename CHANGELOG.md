## 6.2.2
- Android: Emit `overlay_ready` event from `OverlayService` after the view is attached and positioned (via `flutter_overlay_window_drag` EventChannel).
- Android: Also emit `overlay_ready` immediately when Dart subscribes to the EventChannel (onListen), if the overlay is already running. This fixes lost events after app restarts.
- Android: Add `pingDragChannel` MethodChannel method to verify EventChannel binding health.
- Dart: Expose `FlutterOverlayWindow.overlayReadyStream` and `FlutterOverlayWindow.pingDragChannel()`.
- Fix: remove duplicated `isOverlayActive` branch in plugin.

## 6.2.0
- Android: Add drag end event via EventChannel (stream name `flutter_overlay_window_drag`). Emits `{ "event": "overlay_moved", "x": int, "y": int }` on ACTION_UP when `enableDrag` is true.
- Dart: Expose `FlutterOverlayWindow.overlayMovedStream` to listen to drag-end events.
- Does not modify `enableDrag` behavior. No foreground service introduced.

## 6.1.0
- Remove all notification-related parameters and code (overlay no longer runs as a foreground service)
- Clean Dart API: drop overlayTitle/overlayContent/notificationIcon/notificationVisibility from `showOverlay`
- Clean Android side: remove notification/channel usage from plugin and service
- Docs updated

## 6.0.1
- Set compileSdk 35 and minSdk 21
- Keep AGP 7.x-compatible build configuration (buildscript/apply plugin) for better compatibility with Flutter plugin resolution

## 6.0.0
- Remove foreground service usage in `OverlayService` to avoid MIUI/AutoPowerKill when combined with an app's location FGS
- Drop `FOREGROUND_SERVICE` permission from the plugin's manifest (overlay now runs as a normal service)
- Keep `START_NOT_STICKY` to prevent auto-restarts after system kills
- No Dart API changes; behavior change: overlay no longer posts its own persistent notification

## 0.5.3
- Make OverlayService.onStartCommand null-safe and resilient to system restarts (no NPE on null Intent, no unnecessary stopSelf), preventing crash loops on MIUI/AutoPowerKill.
- Improve engine/view lifecycle (reuse cached FlutterEngine, cleanly recreate overlay) and fix resizeOverlay() height condition.
- Set START_NOT_STICKY to prevent auto-restart on MIUI/AutoPowerKill.

## 0.5.2
- Fix overlay persisting after app is completely closed (force close/swipe kill)
- Add automatic overlay cleanup on app destruction via activity lifecycle monitoring
- Remove need for manual MainActivity.onDestroy() override in user apps

## 0.5.1
- Add `notificationIcon` parameter to customize notification icon
- Support for custom drawable and mipmap notification icons
- Improved notification icon resolution with fallback system

## 0.5.0
- Update gradle version
- Fix `NullPointerException` in OverlayService


## 0.4.5
- Added instructions for android 14 compatibility

## 0.4.4
- Fix overlay close crash  
- Add `startPosition`: start overlay in default position  
- Add `moveOverlay`: Update the overlay position in the screen  
- Add `getOverlayPosition`: Get the current overlay position

## 0.4.3
- Fix overlay height bug

## 0.4.2
- Fix touch freeze

## 0.4.1
- Remove secure flag  
- Detach view from engine after closing  
- Fix Example to show (Sending data between Main & overlay)

## 0.3.3
- Fix bugs related to android 12+  
- Some code optimizations  
- Fix overlay popup on top of status bar  
- Fix overlay closing

## 0.3.2
- Add the position gravity feature

## 0.3.1
- Fix the overlay permission on Android versions <= 6  
- Add the possibility to resize overlay while it's in action

## 0.2.9
- Fix closing overlay  
- Possibility to check if the overlay is active or not

## 0.2.8
- Change overlay flags names

## 0.2.7
- Fix overlay issue to target all SDK versions  
- Add `overlayTitle` and `overlayContent` arguments  
- Fix typo

## 0.2.2
- Add custom notification content text  
- Improve the `flagNotFocusable`  
- Update example

## 0.2.1
- Add flag update on runtime

## 0.0.2
- Fix keyboard not showing on TextFields

## 0.0.1
- Initial release
