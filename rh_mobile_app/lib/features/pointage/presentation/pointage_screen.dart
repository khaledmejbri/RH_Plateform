import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:camera/camera.dart';
import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/notification_action.dart';

class PointageScreen extends StatefulWidget {
  const PointageScreen({super.key});

  @override
  State<PointageScreen> createState() => _PointageScreenState();
}

class _PointageScreenState extends State<PointageScreen> {
  bool _isQrScanned    = false;
  bool _isFaceVerified = false;
  String? _scannedData;
  late CameraController _cameraController;
  late FaceDetector _faceDetector;
  bool _isCameraInitialized = false;

  @override
  void initState() {
    super.initState();
    _faceDetector = FaceDetector(
      options: FaceDetectorOptions(
        enableContours: true,
        enableLandmarks: true,
        performanceMode: FaceDetectorMode.accurate,
      ),
    );
  }

  Future<void> _initializeFaceCamera() async {
    final cameras = await availableCameras();
    final front = cameras.firstWhere(
        (c) => c.lensDirection == CameraLensDirection.front);
    _cameraController = CameraController(front, ResolutionPreset.high,
        enableAudio: false);
    await _cameraController.initialize();
    if (mounted) setState(() => _isCameraInitialized = true);
  }

  @override
  void dispose() {
    _faceDetector.close();
    if (_isCameraInitialized) _cameraController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.surface,
        title: const Text('Pointage'),
        actions: const [NotificationActionBadge()],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            children: [
              // Progress steps
              _StepIndicator(step: _isQrScanned ? (_isFaceVerified ? 3 : 2) : 1),
              const SizedBox(height: 40),
              Expanded(child: _buildContent()),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildContent() {
    if (!_isQrScanned) {
      return _StepCard(
        icon: Icons.qr_code_scanner_rounded,
        iconBg: AppTheme.primarySurface,
        iconColor: AppTheme.primary,
        title: 'Scanner le QR Code',
        subtitle: 'Scannez le code QR affiché à l\'entrée de votre bâtiment.',
        action: FilledButton.icon(
          onPressed: () => _startQrScan(context),
          icon: const Icon(Icons.camera_alt_rounded),
          label: const Text('Lancer le scanner'),
        ),
      );
    }

    if (!_isFaceVerified) {
      return Column(
        children: [
          if (_isCameraInitialized)
            Expanded(
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 20),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: AppTheme.primary, width: 3),
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(21),
                  child: CameraPreview(_cameraController),
                ),
              ),
            )
          else
            const Expanded(
                child: Center(child: CircularProgressIndicator())),
          const SizedBox(height: 24),
          Text('Reconnaissance faciale',
              style: Theme.of(context)
                  .textTheme
                  .titleMedium
                  ?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: 8),
          Text('Placez votre visage dans le cadre.',
              style: Theme.of(context).textTheme.bodySmall,
              textAlign: TextAlign.center),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: _verifyFace,
              icon: const Icon(Icons.verified_user_rounded),
              label: const Text('Vérifier mon identité'),
            ),
          ),
        ],
      );
    }

    // Success
    return _StepCard(
      icon: Icons.check_circle_rounded,
      iconBg: const Color(0xFFF0FDF4),
      iconColor: const Color(0xFF16A34A),
      title: 'Pointage réussi !',
      subtitle:
          'Heure enregistrée : ${TimeOfDay.now().format(context)}',
      action: OutlinedButton(
        onPressed: () => setState(() {
          _isQrScanned = false;
          _isFaceVerified = false;
        }),
        child: const Text('Terminer'),
      ),
    );
  }

  void _startQrScan(BuildContext context) async {
    final result = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.black,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (_) => SizedBox(
        height: MediaQuery.of(context).size.height * 0.7,
        child: MobileScanner(
          onDetect: (capture) {
            final barcodes = capture.barcodes;
            if (barcodes.isNotEmpty) {
              Navigator.pop(context, barcodes.first.rawValue);
            }
          },
        ),
      ),
    );

    if (result != null) {
      setState(() {
        _isQrScanned = true;
        _scannedData = result;
      });
      _initializeFaceCamera();
    }
  }

  void _verifyFace() async {
    await Future.delayed(const Duration(seconds: 2));
    if (mounted) setState(() => _isFaceVerified = true);
  }
}

class _StepIndicator extends StatelessWidget {
  final int step;
  const _StepIndicator({required this.step});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        _StepDot(label: 'QR Code', active: step >= 1, done: step > 1),
        Expanded(child: Container(height: 1, color: step > 1 ? AppTheme.primary : AppTheme.border)),
        _StepDot(label: 'Visage', active: step >= 2, done: step > 2),
        Expanded(child: Container(height: 1, color: step > 2 ? AppTheme.primary : AppTheme.border)),
        _StepDot(label: 'Validé', active: step >= 3, done: false),
      ],
    );
  }
}

class _StepDot extends StatelessWidget {
  final String label;
  final bool active;
  final bool done;
  const _StepDot({required this.label, required this.active, required this.done});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 32,
          height: 32,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: active ? AppTheme.primary : AppTheme.border,
          ),
          child: Icon(
            done ? Icons.check_rounded : Icons.circle,
            size: done ? 18 : 10,
            color: Colors.white,
          ),
        ),
        const SizedBox(height: 4),
        Text(label,
            style: TextStyle(
                fontSize: 11,
                color: active ? AppTheme.primary : AppTheme.textSecondary,
                fontWeight: active ? FontWeight.w600 : FontWeight.w400)),
      ],
    );
  }
}

class _StepCard extends StatelessWidget {
  final IconData icon;
  final Color iconBg;
  final Color iconColor;
  final String title;
  final String subtitle;
  final Widget action;

  const _StepCard({
    required this.icon, required this.iconBg, required this.iconColor,
    required this.title, required this.subtitle, required this.action,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 96,
            height: 96,
            decoration: BoxDecoration(color: iconBg, shape: BoxShape.circle),
            child: Icon(icon, size: 48, color: iconColor),
          ),
          const SizedBox(height: 24),
          Text(title,
              style: Theme.of(context)
                  .textTheme
                  .titleMedium
                  ?.copyWith(fontWeight: FontWeight.w700),
              textAlign: TextAlign.center),
          const SizedBox(height: 8),
          Text(subtitle,
              style: Theme.of(context).textTheme.bodySmall,
              textAlign: TextAlign.center),
          const SizedBox(height: 36),
          SizedBox(width: double.infinity, child: action),
        ],
      ),
    );
  }
}
