enum OverlayOrientation {
  portrait,
  landscape,
  unknown;

  static OverlayOrientation fromString(String? value) {
    switch (value) {
      case 'portrait':
        return OverlayOrientation.portrait;
      case 'landscape':
        return OverlayOrientation.landscape;
      default:
        return OverlayOrientation.unknown;
    }
  }

  @override
  String toString() {
    switch (this) {
      case OverlayOrientation.portrait:
        return 'portrait';
      case OverlayOrientation.landscape:
        return 'landscape';
      case OverlayOrientation.unknown:
        return 'unknown';
    }
  }
}
