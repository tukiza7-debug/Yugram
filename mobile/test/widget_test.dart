import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yugram_chat/domain/entities/message_entity.dart';
import 'package:yugram_chat/presentation/widgets/animated_yugram_logo.dart';
import 'package:yugram_chat/presentation/widgets/message_bubble.dart';

/// Ujian widget asas untuk komponen status penghantaran (tick tunggal /
/// berganda - Fitur 11) dan logo beranimasi Yugram. Memastikan pakej
/// aplikasi dapat dikompil dan komponen UI teras dipaparkan dengan betul.
void main() {
  Widget tickApp(DeliveryStatus status) => MaterialApp(
        home: Scaffold(
          body: Center(child: TickIndicator(status: status)),
        ),
      );

  testWidgets('TickIndicator memaparkan tick tunggal untuk status sent',
      (WidgetTester tester) async {
    await tester.pumpWidget(tickApp(DeliveryStatus.sent));
    expect(find.byIcon(Icons.done), findsOneWidget);
  });

  testWidgets('TickIndicator memaparkan tick berganda untuk status read',
      (WidgetTester tester) async {
    await tester.pumpWidget(tickApp(DeliveryStatus.read));
    expect(find.byIcon(Icons.done_all), findsOneWidget);
  });

  testWidgets('TickIndicator memaparkan jam untuk status sending',
      (WidgetTester tester) async {
    await tester.pumpWidget(tickApp(DeliveryStatus.sending));
    expect(find.byIcon(Icons.schedule), findsOneWidget);
  });

  testWidgets('TickIndicator memaparkan ikon ralat untuk status failed',
      (WidgetTester tester) async {
    await tester.pumpWidget(tickApp(DeliveryStatus.failed));
    expect(find.byIcon(Icons.error_outline), findsOneWidget);
  });

  test('MediaEntity mengira saiz boleh dibaca manusia', () {
    const MediaEntity media = MediaEntity(
      url: '/uploads/ujian.png',
      name: 'ujian.png',
      mimeType: 'image/png',
      size: 2048,
    );
    expect(media.isImage, isTrue);
    expect(media.readableSize, '2 KB');
  });

  testWidgets('AnimatedYugramLogo memaparkan badge pada bingkai pertama',
      (WidgetTester tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: Center(child: AnimatedYugramLogo(size: 120))),
      ),
    );
    await tester.pump();
    expect(find.byType(AnimatedYugramLogo), findsOneWidget);
    final Size rendered = tester.getSize(find.byType(AnimatedYugramLogo));
    expect(rendered.width, 120);
    expect(rendered.height, 120);
  });

  testWidgets('AnimatedYugramLogo berjalan melalui keseluruhan intro tanpa ralat',
      (WidgetTester tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: Center(child: AnimatedYugramLogo(size: 96, showSparks: false)),
        ),
      ),
    );
    // Lonjak masa melangkaui intro (2.2s) + beberapa gelung terapung.
    await tester.pump(const Duration(seconds: 3));
    await tester.pump(const Duration(seconds: 2));
    await tester.pump(const Duration(seconds: 2));
    expect(tester.takeException(), isNull);
  });
}
