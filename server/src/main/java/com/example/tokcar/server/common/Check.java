package com.example.tokcar.server.common;

/** 앱이 보낸 값 확인. 틀리면 BAD_REQUEST. */
public final class Check {

    private Check() {
    }

    public static <T> T present(T v, String field) {
        if (v == null) throw ApiException.badRequest(field + " 값이 없어요");
        return v;
    }

    /** 빈 글이면 안 되는 글. 앞뒤 공백은 지운다. */
    public static String text(String v, String field, int max) {
        String t = present(v, field).strip();
        if (t.isEmpty()) throw ApiException.badRequest(field + " 값이 비어 있어요");
        if (t.length() > max) throw ApiException.badRequest(field + "은(는) " + max + "자까지예요");
        return t;
    }

    /** 비어도 되는 글(null 이면 빈 글). */
    public static String optText(String v, String field, int max) {
        String t = v == null ? "" : v.strip();
        if (t.length() > max) throw ApiException.badRequest(field + "은(는) " + max + "자까지예요");
        return t;
    }

    public static double lat(Double v) {
        double d = present(v, "lat");
        if (!(d >= -90 && d <= 90)) throw ApiException.badRequest("위도가 범위 밖이에요");
        return d;
    }

    public static double lon(Double v) {
        double d = present(v, "lon");
        if (!(d >= -180 && d <= 180)) throw ApiException.badRequest("경도가 범위 밖이에요");
        return d;
    }

    public static int range(Integer v, String field, int min, int max) {
        int i = present(v, field);
        if (i < min || i > max) throw ApiException.badRequest(field + "은(는) " + min + "~" + max + " 사이여야 해요");
        return i;
    }
}
