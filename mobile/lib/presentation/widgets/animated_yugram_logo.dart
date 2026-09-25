import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Widget logo rasmi Yugram — kapal kertas dalam litar bergradasi.
///
/// Reproduksi 1-ke-1 reka bentuk SVG (lihat docs/logo/yugram-logo-animasi.svg):
///   - badge bulat bergradasi biru + sorotan radial;
///   - litar putih yang "terlukis" semasa intro;
///   - kapal kertas tiga faset terbang masuk dengan overshoot;
///   - percikan kelajuan di belakang ekor (pilihan);
///   - terapung lembut + denyar cahaya selepas intro (gelung tak berhujung).
///
/// Tiada pergantungan luar — semuanya CustomPainter asli.
class AnimatedYugramLogo extends StatefulWidget {
  const AnimatedYugramLogo({
    this.size = 160,
    this.showSparks = true,
    super.key,
  });

  /// Saiz paparan (logik) dalam piksel.
  final double size;

  /// Papar percikan kelajuan semasa kapal terbang masuk.
  final bool showSparks;

  @override
  State<AnimatedYugramLogo> createState() => _AnimatedYugramLogoState();
}

class _AnimatedYugramLogoState extends State<AnimatedYugramLogo>
    with TickerProviderStateMixin {
  /// Intro sekali-jalan: pop badge -> litar -> kapal terbang -> percikan.
  late final AnimationController _intro;

  /// Gelung berterusan: terapung + denyar cahaya.
  late final AnimationController _loop;

  @override
  void initState() {
    super.initState();
    _intro = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2200),
    )..forward();
    _loop = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 3200),
    )..repeat();
  }

  @override
  void dispose() {
    _intro.dispose();
    _loop.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: widget.size,
      height: widget.size,
      child: AnimatedBuilder(
        animation: Listenable.merge(<Listenable>[_intro, _loop]),
        builder: (BuildContext context, Widget? child) {
          return CustomPaint(
            painter: _YugramLogoPainter(
              // .value ialah 0..1 - tukar kepada milisaat untuk garis masa painter.
              intro: _intro.value * 2200,
              loop: _loop.value,
              showSparks: widget.showSparks,
            ),
          );
        },
      ),
    );
  }
}

class _YugramLogoPainter extends CustomPainter {
  _YugramLogoPainter({
    required this.intro,
    required this.loop,
    required this.showSparks,
  });

  /// Progres intro 0..1 (2.2s).
  final double intro;

  /// Fasa gelung 0..1 (3.2s, berulang).
  final double loop;

  final bool showSparks;

  // Palet (sepadan dengan SVG)
  static const Color _bgTop = Color(0xFF41B7F6);
  static const Color _bgMid = Color(0xFF2B8BEA);
  static const Color _bgBottom = Color(0xFF1D63E0);
  static const Color _facetMain = Color(0xFFFFFFFF);
  static const Color _facetLower = Color(0xFFDCEBFF);
  static const Color _facetTail = Color(0xFFB3D9FF);
  static const Color _glow = Color(0xFF9FE0FF);
  static const Color _shadow = Color(0xFF0B2A66);

  // Ruang reka bentuk 512x512, pusat (256,256) - diskala ke saiz kanvas.
  static const double _d = 512;
  static const double _c = _d / 2;
  static const double _ringR = 200;
  static const double _badgeR = 224;
  static const Offset _center = Offset(_c, _c);
  static const Rect _badgeRect = Rect.fromLTWH(_c - _badgeR, _c - _badgeR, _badgeR * 2, _badgeR * 2);

  // Titik kapal kertas (relatif pusat, hidung ke kanan-atas)
  static const Offset _nose = Offset(132, -88);
  static const Offset _t1 = Offset(-134, -14);
  static const Offset _t2 = Offset(-86, 56);
  static const Offset _keel = Offset(-26, 116);
  static const Offset _fold = Offset(-16, 42);

  // Percikan (pendek, di dalam litar)
  static const List<List<Offset>> _sparks = <List<Offset>>[
    <Offset>[Offset(-142, 6), Offset(-172, -4)],
    <Offset>[Offset(-132, 52), Offset(-158, 62)],
  ];

  double _t(double startMs, double endMs) {
    if (intro <= startMs) {
      return 0;
    }
    if (intro >= endMs) {
      return 1;
    }
    return (intro - startMs) / (endMs - startMs);
  }

  static double _easeOutCubic(double p) => 1 - math.pow(1 - p, 3).toDouble();

  static double _easeOutBack(double p, [double s = 1.70158]) {
    final double q = p - 1;
    return 1 + q * q * ((s + 1) * q + s);
  }

  @override
  void paint(Canvas canvas, Size size) {
    final double scale = size.width / _d;
    canvas.save();
    canvas.scale(scale);

    const Offset center = _center;

    // ---- masa (ms, sepadan dengan SVG)
    final double popRaw = _t(0, 500);
    final double ringP = _easeOutCubic(_t(150, 1050));
    final double flyP = intro < 550 ? 0.0 : _easeOutBack(_t(550, 1500), 1.45);
    final double sparkP = showSparks ? _sparkOpacity() : 0.0;
    final double bob = intro >= 1500 ? -6 * math.sin(loop * 2 * math.pi) : 0.0;
    final double glowOp = (intro >= 1200
            ? 0.54 + 0.24 * math.sin(loop * 2 * math.pi)
            : 0.30 + 0.10 * (intro / 1200))
        .clamp(0.0, 1.0);
    final double badgeOpacity = (intro / 300).clamp(0.0, 1.0);

    // ---- pop badge: skala + kelegapan seluruh komposisi
    final double badgeScale = 0.90 + 0.10 * _easeOutBack(popRaw, 1.9);
    final bool needLayer = badgeOpacity < 1 || badgeScale < 0.999;
    if (needLayer) {
      canvas.saveLayer(
        Offset.zero & const Size(_d, _d),
        Paint()..color = Colors.white.withValues(alpha: badgeOpacity),
      );
    }
    if (badgeScale < 0.999) {
      canvas.translate(_c, _c);
      canvas.scale(badgeScale);
      canvas.translate(-_c, -_c);
    }

    // ---- badge + sorotan
    final Paint badgePaint = Paint()
      ..shader = const LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: <Color>[_bgTop, _bgMid, _bgBottom],
        stops: <double>[0, 0.55, 1],
      ).createShader(_badgeRect);
    canvas.drawCircle(center, _badgeR, badgePaint);

    final Paint highlightPaint = Paint()
      ..shader = const RadialGradient(
        center: Alignment(-0.32, -0.56),
        radius: 0.62,
        colors: <Color>[Color(0x4DFFFFFF), Color(0x0FFFFFFF), Color(0x00FFFFFF)],
        stops: <double>[0, 0.55, 1],
      ).createShader(_badgeRect);
    canvas.drawCircle(center, _badgeR, highlightPaint);

    // ---- denyar cahaya di belakang kapal
    final Paint glowPaint = Paint()
      ..color = _glow.withValues(alpha: glowOp)
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 26);
    canvas.drawCircle(center + const Offset(6, -2), 150, glowPaint);

    // ---- bayang kapal
    final Paint shadowPaint = Paint()
      ..color = _shadow.withValues(alpha: 0.20)
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 22);
    canvas.drawOval(
      Rect.fromCenter(
        center: center + const Offset(10, 128),
        width: 224,
        height: 48,
      ),
      shadowPaint,
    );

    // ---- litar "terlukis" (mengikut arah jam bermula atas)
    if (ringP > 0) {
      final Paint ringPaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 9
        ..strokeCap = StrokeCap.round
        ..color = Colors.white.withValues(alpha: 0.88);
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: _ringR),
        -math.pi / 2,
        2 * math.pi * ringP,
        false,
        ringPaint,
      );
    }

    // ---- percikan kelajuan
    if (sparkP > 0) {
      final Paint sparkPaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 11
        ..strokeCap = StrokeCap.round
        ..color = Colors.white.withValues(alpha: 0.95 * sparkP);
      for (final List<Offset> s in _sparks) {
        canvas.drawLine(center + s[0], center + s[1], sparkPaint);
      }
    }

    // ---- kapal kertas: terbang masuk (overshoot) + terapung
    final double dx = -96 * (1 - flyP);
    final double dy = 74 * (1 - flyP) + bob;
    final double angleRad = -9 * (math.pi / 180) * (1 - flyP);

    canvas.save();
    canvas.translate(_c + dx, _c + dy);
    canvas.rotate(angleRad);
    canvas.translate(-_c, -_c);

    final Path planeClip = Path()
      ..addOval(
        Rect.fromCircle(center: center, radius: _badgeR),
      );
    canvas.save();
    canvas.clipPath(planeClip);
    final Path upper = Path()
      ..addPolygon(<Offset>[center + _nose, center + _t1, center + _fold], true);
    final Path lower = Path()
      ..addPolygon(
          <Offset>[center + _nose, center + _fold, center + _keel], true);
    final Path tail = Path()
      ..addPolygon(<Offset>[center + _t1, center + _t2, center + _fold], true);
    canvas.drawPath(upper, Paint()..color = _facetMain);
    canvas.drawPath(lower, Paint()..color = _facetLower);
    canvas.drawPath(tail, Paint()..color = _facetTail);
    canvas.restore();

    canvas.restore();

    if (needLayer) {
      canvas.restore();
    }
    canvas.restore();
  }

  /// Kelegapan percikan: muncul semasa terbang (550-800ms), kekal sehingga
  /// 1500ms, kemudian hilang menjelang 2050ms.
  double _sparkOpacity() {
    if (intro < 550 || intro > 2050) {
      return 0;
    }
    if (intro < 800) {
      return (intro - 550) / 250;
    }
    if (intro < 1500) {
      return 1;
    }
    return 1 - (intro - 1500) / 550;
  }

  @override
  bool shouldRepaint(_YugramLogoPainter oldDelegate) =>
      oldDelegate.intro != intro ||
      oldDelegate.loop != loop ||
      oldDelegate.showSparks != showSparks;
}
