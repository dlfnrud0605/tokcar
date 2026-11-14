import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tokcar_mobile/core/store.dart';
import 'package:tokcar_mobile/data/seed.dart';
import 'package:tokcar_mobile/data/server.dart';
import 'package:tokcar_mobile/main.dart';

void main() {
  Future<TokStore> start(WidgetTester tester, {Session? session}) async {
    tester.view.physicalSize = const Size(1080, 2340);
    tester.view.devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    final s = TokStore(server: FakeServer(Seed.fromJson(File('assets/dummy/seed.json').readAsStringSync())), session: session);
    await tester.pumpWidget(TokCarApp(s));
    await tester.pump();
    return s;
  }

  testWidgets('chat list has no guide buttons and room opens without the top route card', (tester) async {
    await start(tester, session: const Session('papa', '아빠', '01012345678', ''));
    expect(find.text('채팅'), findsOneWidget);
    expect(find.text('우리 가족'), findsOneWidget);
    expect(find.text('안내'), findsNothing); // 모바일: 목적지 띠의 [안내] 없음
    await tester.tap(find.text('우리 가족'));
    await tester.pump();
    expect(find.text('저녁 먹고 와?'), findsOneWidget);
    expect(find.text('메시지 입력'), findsOneWidget);
    expect(find.text('집'), findsNothing); // 대화방 위 경로 카드(목적지 이름) 없음
    expect(tester.takeException(), isNull);
  });

  testWidgets('route share card shows map button and map opens', (tester) async {
    final s = await start(tester, session: const Session('papa', '아빠', '01012345678', ''));
    s.openRoom('family');
    s.simulateIncoming('dest');
    await tester.pump();
    await tester.pump();
    expect(find.text('지도 보기'), findsOneWidget);
    expect(find.text('경유지'), findsNothing);
    await tester.tap(find.text('지도 보기'));
    await tester.pump();
    expect(find.bySemanticsLabel('지도 닫기'), findsOneWidget); // 위 칩은 없고 뒤로 가기 버튼만
    expect(find.text('민지의 차'), findsNothing);
    expect(find.text('실시간'), findsNothing);
    await tester.pump(const Duration(seconds: 50));
    expect(find.text('도착'), findsWidgets);
    s.closeMap();
    await tester.pump(const Duration(seconds: 3));
    expect(tester.takeException(), isNull);
  });

  testWidgets('here card also has map button and opens a pin-only map', (tester) async {
    final s = await start(tester, session: const Session('papa', '아빠', '01012345678', ''));
    s.openRoom('family');
    s.simulateIncoming('here');
    await tester.pump();
    await tester.pump();
    expect(find.text('지도 보기'), findsOneWidget);
    expect(find.text('경유지'), findsNothing);
    await tester.tap(find.text('지도 보기'));
    await tester.pump();
    expect(find.bySemanticsLabel('지도 닫기'), findsOneWidget);
    expect(find.text('엄마의 위치'), findsNothing); // 위 칩 없음
    expect(find.text('위치 공유'), findsWidgets); // 아래 카드의 태그
    expect(find.textContaining('에 보낸 위치예요'), findsWidgets);
    await tester.pump(const Duration(seconds: 10));
    expect(s.map!.here, isTrue);
    s.handleBack();
    await tester.pump(const Duration(seconds: 3));
    expect(tester.takeException(), isNull);
  });

  testWidgets('welcome screen when signed out, then each tab renders', (tester) async {
    final s = await start(tester);
    expect(find.text('TokCar'), findsOneWidget);
    await tester.tap(find.text('로그인'));
    await tester.pump();
    expect(find.text('아이디와 비밀번호를 입력해 주세요'), findsOneWidget);
    s.setLoginId('papa');
    s.setPw('papa1234!');
    s.doLogin();
    await tester.pump();
    expect(find.text('채팅'), findsOneWidget);
    s.openTab(HomeTab.friends);
    await tester.pump();
    expect(find.text('친구 10'), findsOneWidget);
    s.openTab(HomeTab.settings);
    await tester.pump();
    expect(find.text('개발용 · 시험'), findsNothing);
    expect(find.text('로그아웃'), findsOneWidget);
    await tester.pump(const Duration(seconds: 3));
    expect(tester.takeException(), isNull);
  });
}
