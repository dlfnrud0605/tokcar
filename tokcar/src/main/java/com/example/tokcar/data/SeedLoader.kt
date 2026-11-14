package com.example.tokcar.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** assets/dummy/seed.json 을 읽어 [Seed]로 바꾼다. (서버가 없을 때 쓰는 더미 데이터) */
object SeedLoader {
    const val ASSET_PATH = "dummy/seed.json"

    fun load(context: Context): Seed =
        fromJson(context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() })

    fun fromJson(text: String): Seed {
        val root = JSONObject(text)
        val me = root.getJSONObject("me")

        val places = root.getJSONArray("places").objects().associate { o ->
            o.getString("id") to Place(
                id = o.getString("id"),
                name = o.getString("name"),
                addr = o.getString("addr"),
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                card = o.optString("card").ifEmpty { null },
            )
        }
        fun friendOf(o: JSONObject) = Friend(
            id = o.getString("id"),
            tel = o.getString("tel"),
            name = o.getString("name"),
            ini = o.getString("ini"),
            color = parseColor(o.getString("color")),
            status = o.optString("status"),
            placeId = o.optString("place").ifEmpty { null },
        )

        val roomObjs = root.getJSONArray("rooms").objects()
        val rooms = roomObjs.map { o ->
            Room(
                id = o.getString("id"),
                name = o.getString("name"),
                placeId = o.optString("place").ifEmpty { null },
                color = parseColor(o.getString("color")),
                ini = o.getString("ini"),
                members = o.getJSONArray("members").strings(),
                unread = o.optInt("unread"),
                time = o.optString("time"),
            )
        }
        val roomMsgs = roomObjs.associate { o ->
            o.getString("id") to o.getJSONArray("msgs").let { arr ->
                List(arr.length()) { i -> arr.getJSONArray(i).let { it.getString(0) to it.getString(1) } }
            }
        }
        val roomLast = roomObjs.associate { it.getString("id") to it.optString("last") }

        val histories = root.getJSONObject("histories").let { h ->
            h.keys().asSequence().associateWith { k -> h.getJSONArray(k).strings() }
        }
        val incoming = root.getJSONObject("incoming").let { inc ->
            inc.keys().asSequence().associateWith { k ->
                val o = inc.getJSONObject(k)
                IncomingSpec(
                    roomId = o.getString("roomId"),
                    who = o.getString("who"),
                    kind = if (o.getString("kind") == "here") LocKind.HERE else LocKind.DEST,
                    placeId = o.getString("place"),
                    from = o.optString("from"),
                    min = o.optInt("min"),
                    pct = o.optInt("pct"),
                    eta = o.optString("eta"),
                )
            }
        }
        val incomingText = root.optJSONObject("incomingText")?.let { inc ->
            inc.keys().asSequence().associateWith { k ->
                val o = inc.getJSONObject(k)
                IncomingText(o.getString("roomId"), o.getString("who"), o.getString("text"))
            }
        }.orEmpty()
        val delays = root.getJSONObject("readDelaysMs").let { d ->
            d.keys().asSequence().associateWith { k -> if (d.isNull(k)) null else d.getLong(k) }
        }

        return Seed(
            myName = me.getString("name"),
            myPhone = me.getString("phone"),
            myAddr = me.optString("addr", "성남시 분당구 삼평동"),
            places = places,
            favs = root.getJSONArray("favs").strings(),
            navRecent = root.getJSONArray("navRecent").strings(),
            navResults = root.getJSONArray("navResults").strings(),
            friends = root.getJSONArray("friends").objects().map(::friendOf),
            phonebook = root.getJSONArray("phonebook").objects().map(::friendOf),
            rooms = rooms,
            roomMsgs = roomMsgs,
            roomLast = roomLast,
            histories = histories,
            incoming = incoming,
            incomingText = incomingText,
            readDelaysMs = delays,
        )
    }

    /** "#c2410c" -> 0xFFC2410C */
    fun parseColor(hex: String): Long = 0xFF000000L or hex.removePrefix("#").toLong(16)

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
}
