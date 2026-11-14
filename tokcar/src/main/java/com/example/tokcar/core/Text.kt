package com.example.tokcar.core

/** 한글 받침에 맞춰 조사를 붙이는 도우미. 한글이 아닌 글자로 끝나면 받침이 없는 것으로 본다. */
object Josa {
    /** 마지막 글자의 받침 번호(0 = 받침 없음, 8 = ㄹ). */
    private fun jong(s: String): Int {
        val c = s.lastOrNull() ?: return 0
        return if (c in '가'..'힣') (c - '가') % 28 else 0
    }

    fun eul(s: String) = s + if (jong(s) == 0) "를" else "을"
    fun ga(s: String) = s + if (jong(s) == 0) "가" else "이"
    fun ro(s: String) = s + if (jong(s).let { it == 0 || it == 8 }) "로" else "으로"
}

object Phone {
    private val ok = Regex("^010\\d{8}$")

    fun digits(t: String): String = t.filter { it.isDigit() }
    fun isValid(t: String): Boolean = ok.matches(digits(t))

    /** 010-1234-5678 */
    fun format(t: String): String =
        digits(t).let { d -> if (d.length == 11) "${d.take(3)}-${d.substring(3, 7)}-${d.substring(7)}" else d }

    /** 010-****-5678 */
    fun mask(t: String): String =
        digits(t).let { d -> if (d.length == 11) "${d.take(3)}-****-${d.substring(7)}" else d }
}

/** 친구 이름으로 방 이름을 자동으로 짓는다. */
fun autoName(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    2 -> names.joinToString(", ")
    else -> names.take(2).joinToString(", ") + " 외 ${names.size - 2}명"
}

/** "성남시 분당구 정자동" -> "분당구 정자동" (카드에는 시 이름 없이 구부터 적는다). */
fun guDong(addr: String): String = addr.replace(Regex("^\\S+시\\s+(?=\\S+구\\s)"), "")

/** 오전/오후 시각 표기. 예: 18:05 -> "오후 6:05" */
fun clockText(hour: Int, minute: Int): String {
    val h = ((hour % 24) + 24) % 24
    val hh = (h % 12).let { if (it == 0) 12 else it }
    return (if (h >= 12) "오후" else "오전") + " $hh:" + minute.toString().padStart(2, '0')
}

/** 시각(ms)을 "오후 6:05" 로. */
fun clockText(ms: Long): String =
    java.util.Calendar.getInstance().apply { timeInMillis = ms }.let { clockText(it.get(java.util.Calendar.HOUR_OF_DAY), it.get(java.util.Calendar.MINUTE)) }

private val EMOJI = Regex("[\\p{So}\\u200D\\uFE0F\\x{1F3FB}-\\x{1F3FF}]")

/** 소리 내어 읽기 좋게 이모지를 빼고 공백을 정리한다. 읽을 글이 없으면 null. 예: "치킨 사와 🍗" -> "치킨 사와" */
fun speakable(text: String): String? = text.replace(EMOJI, "").replace(Regex("\\s+"), " ").trim().ifEmpty { null }

/** 두 좌표 사이 거리(미터). 내비가 알려 준 목적지가 아는 장소인지 가릴 때 쓴다. */
fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = p2 - p1
    val dl = Math.toRadians(lon2 - lon1)
    val h = Math.sin(dp / 2).let { it * it } + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(h))
}

/**
 * 남은 시간을 일·시간·분으로 나눈다(웹 시안의 leftParts와 같음): 203분 = 3시간 23분, 1500분 = 1일 1시간.
 * 0인 칸은 빼고, 모두 0이면 "0분".
 */
fun leftParts(minutes: Int): List<Pair<Int, String>> {
    val m = minutes.coerceAtLeast(0)
    val d = m / 1440
    val h = m % 1440 / 60
    val mm = m % 60
    return buildList {
        if (d > 0) add(d to "일")
        if (h > 0) add(h to "시간")
        if (mm > 0 || isEmpty()) add(mm to "분")
    }
}

/** "3시간 23분" 같은 글(목록 카드의 최근 대화). */
fun leftText(minutes: Int): String = leftParts(minutes).joinToString(" ") { "${it.first}${it.second}" }

/**
 * 주소 도우미. 화면의 큰 글씨 이름이 없거나 주소처럼 생긴 경우(내비가 주소를 이름으로 준 경우)에는 [area] 로 "구(시) 동"까지만 줄여 쓰고,
 * 전체 주소(도로명·번호까지)는 작은 글씨로 보인다.
 */
object Address {
    private val provinceRe = Regex("(특별시|광역시|특별자치시|특별자치도|도)$")
    private val dongRe = Regex("[동읍면리가]$")
    private val districtRe = Regex("(시|군|구)$")
    private val roadRe = Regex("(로|길)$|[로길]\\d")

    /** 전체 주소에서 맨 앞의 도·특별시·광역시 이름은 뺀다: "경상남도 진주시 진주대로500번길 20 (가좌동)" -> "진주시 진주대로500번길 20 (가좌동)". */
    fun withoutProvince(addr: String): String {
        val t = addr.trim().split(Regex("\\s+"))
        return if (t.size > 1 && provinceRe.containsMatchIn(t[0])) t.drop(1).joinToString(" ") else addr.trim()
    }

    /** 이름이 장소 이름이 아니라 주소로 보이는가: "경상남도 진주시 진주대로500번길 20 (가좌동)", "서울특별시 강남구 테헤란로 152" 등. */
    fun looksLikeAddress(s: String): Boolean =
        Regex("[로길]\\s*\\d").containsMatchIn(s) || (s.contains('(') && s.contains(')')) ||
            Regex("^\\S+(시|도|군)\\s+\\S+(시|군|구)\\s").containsMatchIn(s)

    /**
     * 주소를 "구(시) 동"까지만: "경상남도 진주시 진주대로500번길 20 (가좌동)" -> "진주시 가좌동",
     * "성남시 분당구 삼평동 123-4" -> "분당구 삼평동", "서울특별시 강남구 테헤란로 152" -> "강남구 테헤란로"(동이 없으면 도로 이름까지).
     */
    fun area(addr: String): String {
        val a = addr.trim()
        if (a.isEmpty()) return a
        val paren = Regex("\\(([^)]*[동읍면리가])\\)").find(a)?.groupValues?.get(1)
        val tokens = a.replace(Regex("\\([^)]*\\)"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        if (tokens.size > 1 && provinceRe.containsMatchIn(tokens[0])) tokens.removeAt(0)
        val dongIdx = tokens.indexOfFirst { it.length >= 2 && dongRe.containsMatchIn(it) }
        val district = tokens.take(if (dongIdx >= 0) dongIdx else tokens.size).lastOrNull { districtRe.containsMatchIn(it) }
        val dong = paren ?: tokens.getOrNull(dongIdx)
        val road = tokens.firstOrNull { roadRe.containsMatchIn(it) }
        return listOfNotNull(district, dong ?: road).joinToString(" ").ifEmpty { a }
    }
}
