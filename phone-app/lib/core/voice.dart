import 'dart:async';

import 'package:speech_to_text/speech_recognition_error.dart';
import 'package:speech_to_text/speech_to_text.dart';

/// 말하기(음성 인식)가 실패한 이유. [reason] 은 토스트로 그대로 보인다.
class VoiceException implements Exception {
  const VoiceException(this.reason);
  final String reason;
}

/// 말하기. null 이면 알아듣지 못한 것, 인식을 못 하는 사정이 있으면 [VoiceException].
abstract class VoiceInput {
  Future<String?> listen();
  /// 듣기를 멈춘다(그때까지 들은 말은 버림).
  void cancel();
}

/// 폰의 음성 인식(speech_to_text 패키지: 안드로이드 SpeechRecognizer · iOS Speech). 한국어로 한 번 듣는다.
/// 차량 앱은 Pleos SpeechToText SDK를 쓰지만 폰에는 없어서 바꿈.
class PhoneVoiceInput implements VoiceInput {
  final _stt = SpeechToText();
  bool _ready = false;
  Completer<String?>? _done;
  String _heard = '';

  void _finish([Object? error]) {
    final c = _done;
    if (c == null || c.isCompleted) return;
    _done = null;
    if (error != null) {
      c.completeError(error);
    } else {
      c.complete(_heard.trim().isEmpty ? null : _heard);
    }
  }

  void _onError(SpeechRecognitionError e) {
    // 말이 없거나 못 알아들은 것은 "잘 못 들었어요"로(null), 그 밖의 문제는 이유를 보인다
    if (e.errorMsg == 'error_no_match' || e.errorMsg == 'error_speech_timeout') {
      _finish();
    } else if (e.errorMsg == 'error_permission' || e.errorMsg == 'error_insufficient_permissions') {
      _finish(const VoiceException('마이크 권한을 허용해 주세요'));
    } else {
      _finish(VoiceException('말하기를 쓸 수 없어요 (${e.errorMsg})'));
    }
  }

  @override
  Future<String?> listen() async {
    if (!_ready) {
      _ready = await _stt.initialize(onError: _onError, onStatus: (s) {
        if (s == SpeechToText.doneStatus) _finish();
      });
      if (!_ready) throw const VoiceException('이 폰에서는 말하기를 쓸 수 없어요');
    }
    _heard = '';
    final c = Completer<String?>();
    _done = c;
    await _stt.listen(
      onResult: (r) {
        _heard = r.recognizedWords;
        if (r.finalResult) _finish();
      },
      listenOptions: SpeechListenOptions(
        localeId: 'ko_KR', listenFor: const Duration(seconds: 15), pauseFor: const Duration(seconds: 3),
        partialResults: true, cancelOnError: true, listenMode: ListenMode.dictation,
      ),
    );
    return c.future;
  }

  @override
  void cancel() {
    _heard = '';
    _stt.cancel();
    _finish();
  }
}
