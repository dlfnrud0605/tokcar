package com.example.tokcar.server.chat;

/** 시스템 안내 글의 조사(앱의 Josa 와 같은 규칙): 받침이 있으면 이/을, 없으면 가/를. 한글이 아니면 받침 없는 쪽. */
final class Korean {

    private Korean() {
    }

    static String ga(String word) {
        return word + (batchim(word) ? "이" : "가");
    }

    static String eul(String word) {
        return word + (batchim(word) ? "을" : "를");
    }

    private static boolean batchim(String word) {
        if (word == null || word.isEmpty()) return false;
        char c = word.charAt(word.length() - 1);
        return c >= 0xAC00 && c <= 0xD7A3 && (c - 0xAC00) % 28 != 0;
    }
}
