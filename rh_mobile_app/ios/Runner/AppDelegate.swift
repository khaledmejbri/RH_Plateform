import CoreLocation
import Flutter
import UIKit

/// Compatible Flutter 3.29 (Codemagic) — pas de FlutterImplicitEngineDelegate.
@main
@objc class AppDelegate: FlutterAppDelegate, CLLocationManagerDelegate {
  private let locationManager = CLLocationManager()
  private var pendingResult: FlutterResult?
  private var timeoutWork: DispatchWorkItem?
  private var freshRequested = false

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)
    let launched = super.application(application, didFinishLaunchingWithOptions: launchOptions)

    locationManager.delegate = self
    locationManager.desiredAccuracy = kCLLocationAccuracyBest
    setupLocationChannel()
    return launched
  }

  private func setupLocationChannel() {
    guard let controller = window?.rootViewController as? FlutterViewController else {
      return
    }
    let channel = FlutterMethodChannel(
      name: "rh_connect/location",
      binaryMessenger: controller.binaryMessenger
    )
    channel.setMethodCallHandler { [weak self] (call: FlutterMethodCall, result: @escaping FlutterResult) in
      guard let self = self else { return }
      switch call.method {
      case "isLocationEnabled":
        result(CLLocationManager.locationServicesEnabled())
      case "getCurrentPosition":
        // fresh = true : ignore la dernière position en cache (pose du site par moyenne, D-P03)
        let args = call.arguments as? [String: Any]
        self.freshRequested = (args?["fresh"] as? Bool) == true
        self.requestPosition(result)
      default:
        result(FlutterMethodNotImplemented)
      }
    }
  }

  private func authStatus() -> CLAuthorizationStatus {
    if #available(iOS 14.0, *) {
      return locationManager.authorizationStatus
    }
    return CLLocationManager.authorizationStatus()
  }

  private func requestPosition(_ result: @escaping FlutterResult) {
    if !CLLocationManager.locationServicesEnabled() {
      result(FlutterError(code: "DISABLED", message: "GPS désactivé", details: nil))
      return
    }
    let status = authStatus()
    if status == .denied || status == .restricted {
      result(FlutterError(code: "PERMISSION", message: "Localisation non autorisée", details: nil))
      return
    }
    pendingResult = result
    if status == .notDetermined {
      locationManager.requestWhenInUseAuthorization()
      return
    }
    startLocationRequest()
  }

  private func startLocationRequest() {
    timeoutWork?.cancel()
    let work = DispatchWorkItem { [weak self] in
      guard let self = self else { return }
      if !self.freshRequested, let last = self.locationManager.location {
        self.finish(location: last)
      } else {
        self.finish(
          error: FlutterError(
            code: "TIMEOUT",
            message: "Impossible d’obtenir la position GPS. Réessayez à l’extérieur.",
            details: nil
          )
        )
      }
    }
    timeoutWork = work
    DispatchQueue.main.asyncAfter(deadline: .now() + 20, execute: work)
    locationManager.requestLocation()
  }

  func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    handleAuthorizationChange(authStatus())
  }

  func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
    handleAuthorizationChange(status)
  }

  private func handleAuthorizationChange(_ status: CLAuthorizationStatus) {
    guard pendingResult != nil else { return }
    if status == .authorizedWhenInUse || status == .authorizedAlways {
      startLocationRequest()
    } else if status == .denied || status == .restricted {
      finish(error: FlutterError(code: "PERMISSION", message: "Localisation non autorisée", details: nil))
    }
  }

  func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    if let loc = locations.last {
      finish(location: loc)
    }
  }

  func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    if let last = manager.location {
      finish(location: last)
    } else {
      finish(error: FlutterError(code: "ERROR", message: error.localizedDescription, details: nil))
    }
  }

  private func finish(location: CLLocation) {
    timeoutWork?.cancel()
    timeoutWork = nil
    let accuracy: Any = location.horizontalAccuracy >= 0 ? location.horizontalAccuracy : NSNull()
    pendingResult?([
      "latitude": location.coordinate.latitude,
      "longitude": location.coordinate.longitude,
      "accuracyMeters": accuracy,
      "isMock": isSimulated(location),
    ])
    pendingResult = nil
  }

  /// Position fournie par un simulateur ou une appli de faux GPS (iOS 15+, D-P01 / D-P02).
  private func isSimulated(_ location: CLLocation) -> Bool {
    if #available(iOS 15.0, *) {
      return location.sourceInformation?.isSimulatedBySoftware ?? false
    }
    return false
  }

  private func finish(error: FlutterError) {
    timeoutWork?.cancel()
    timeoutWork = nil
    pendingResult?(error)
    pendingResult = nil
  }
}
