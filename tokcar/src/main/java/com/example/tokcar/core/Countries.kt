package com.example.tokcar.core

/**
 * 가입·로그인에서 고르는 나라. [example] = 입력 예시, [min]~[max] = 국가번호 뒤 전화번호 자릿수(맨 앞 0 제외),
 * [trunk] = 나라 안에서 거는 맨 앞 0을 국제 번호에서 빼는 나라.
 * 자릿수 규칙은 시안용 간단 규칙이고 실제 서비스에는 libphonenumber 같은 번호 검사가 필요함 [추정].
 */
data class Country(
    val code: String,
    val name: String,
    val cc: String,
    val example: String,
    val min: Int,
    val max: Int,
    val trunk: Boolean = false,
)

object Countries {
    val all = listOf(
        Country("KR", "대한민국", "82", "10-1234-5678", 9, 10, trunk = true),
        Country("US", "미국", "1", "(555) 123-4567", 10, 10),
        Country("JP", "일본", "81", "90-1234-5678", 9, 10, trunk = true),
        Country("CN", "중국", "86", "138 0013 8000", 11, 11),
        Country("GB", "영국", "44", "7400 123456", 10, 10, trunk = true),
        Country("DE", "독일", "49", "1512 3456789", 10, 11, trunk = true),
        Country("FR", "프랑스", "33", "6 12 34 56 78", 9, 9, trunk = true),
        Country("CA", "캐나다", "1", "(555) 123-4567", 10, 10),
        Country("VN", "베트남", "84", "912 345 678", 9, 10, trunk = true),
        Country("TH", "태국", "66", "81 234 5678", 9, 9, trunk = true),
        Country("SG", "싱가포르", "65", "8123 4567", 8, 8),
        Country("IN", "인도", "91", "98765 43210", 10, 10),
    )

    fun of(code: String): Country = all.firstOrNull { it.code == code } ?: all[0]
}

/** 국제 전화번호(E.164 기준)를 다루는 도우미. */
object PhoneIntl {
    /** 국가번호 뒤에 오는 번호 숫자만(나라 안 맨 앞 0은 뺌). */
    fun national(c: Country, text: String): String {
        val d = text.filter { it.isDigit() }
        return if (c.trunk && d.startsWith("0")) d.drop(1) else d
    }

    fun isValid(c: Country, text: String): Boolean = national(c, text).length in c.min..c.max

    /** +821012345678 */
    fun e164(c: Country, text: String): String = "+${c.cc}${national(c, text)}"

    /** +82 10-1234-5678 */
    fun format(c: Country, text: String): String {
        val d = national(c, text)
        return "+${c.cc} " + if (c.code == "KR" && d.length == 10) "${d.take(2)}-${d.substring(2, 6)}-${d.substring(6)}" else d
    }

    /** +82 10-****-5678 / +1 ******4567 */
    fun mask(c: Country, text: String): String {
        val d = national(c, text)
        return "+${c.cc} " + if (c.code == "KR" && d.length == 10) "${d.take(2)}-****-${d.substring(6)}"
        else d.mapIndexed { i, ch -> if (i < d.length - 4) '*' else ch }.joinToString("")
    }
}

/**
 * 입력하는 대로 하이픈·띄어쓰기를 넣는다(시안의 fmtTyping). 나라마다 입력 예시([Country.example]) 모양을 따라가고,
 * 한국처럼 맨 앞 0을 쓰는 나라는 0을 붙인 모양(010-1234-5678)으로 맞춘다. 예시보다 길면 뒤에 숫자만 이어 붙인다.
 */
fun formatTyping(c: Country, raw: String): String {
    val d = raw.filter { it.isDigit() }
    val pat = (if (c.trunk && d.startsWith("0")) "0" else "") + c.example
    val out = StringBuilder()
    var k = 0
    for (ch in pat) {
        if (k >= d.length) break
        if (ch.isDigit()) out.append(d[k++]) else out.append(ch)
    }
    return out.toString() + d.substring(k)
}
