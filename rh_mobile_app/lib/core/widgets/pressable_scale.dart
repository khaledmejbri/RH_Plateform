import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Retour haptique + légère mise à l’échelle au clic.
class PressableScale extends StatefulWidget {
  const PressableScale({
    super.key,
    required this.child,
    required this.onPressed,
    this.minScale = 0.97,
  });

  final Widget child;
  final VoidCallback? onPressed;
  final double minScale;

  @override
  State<PressableScale> createState() => _PressableScaleState();
}

class _PressableScaleState extends State<PressableScale> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 100),
  );
  late final Animation<double> _scale = Tween(begin: 1.0, end: widget.minScale).animate(
    CurvedAnimation(parent: _c, curve: Curves.easeInOut),
  );

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (_) {
        if (widget.onPressed != null) _c.forward();
      },
      onTapCancel: () => _c.reverse(),
      onTapUp: (_) {
        _c.reverse();
        if (widget.onPressed != null) {
          HapticFeedback.lightImpact();
          widget.onPressed!();
        }
      },
      child: ScaleTransition(scale: _scale, child: widget.child),
    );
  }
}
