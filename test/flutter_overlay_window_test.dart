import 'package:flutter/services.dart';
import 'package:flutter_overlay_window/flutter_overlay_window.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('x-slayer/overlay_channel');
  final calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          switch (call.method) {
            case 'checkPermission':
            case 'requestPermission':
              return true;
            case 'closeOverlay':
            case 'isOverlayActive':
              return false;
            default:
              return null;
          }
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });

  group('FlutterOverlayWindow', () {
    test('closeOverlay answers even when there is nothing to close', () async {
      expect(await FlutterOverlayWindow.closeOverlay(), isFalse);
      expect(await FlutterOverlayWindow.isActive(), isFalse);
    });

    test('isPermissionGranted relays the platform answer', () async {
      expect(await FlutterOverlayWindow.isPermissionGranted(), isTrue);
      expect(calls.single.method, 'checkPermission');
    });

    test('requestPermission relays the platform answer', () async {
      expect(await FlutterOverlayWindow.requestPermission(), isTrue);
      expect(calls.single.method, 'requestPermission');
    });
  });
}
