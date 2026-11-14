package com.example.tokcar

import com.example.tokcar.data.ServerReverseGeocoder
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 좌표 → 주소가 네이버가 아니라 가족 서버의 중계 주소(/api/naver/…)로 가고, 로그인 토큰만 붙으며(네이버 키는 앱에 없음), 응답은 예전과 같게 해석된다. */
class ServerReverseGeocoderTest {
    private val seen = mutableListOf<Triple<String, String?, String?>>()    // 경로+쿼리, Authorization, 네이버 키 헤더
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            val h = ex.requestHeaders
            seen += Triple(ex.requestURI.rawPath + "?" + ex.requestURI.rawQuery, h.getFirst("Authorization"), h.getFirst("x-ncp-apigw-api-key") ?: h.getFirst("X-NCP-APIGW-API-KEY"))
            val body = """{"results":[{"name":"addr","region":{"area0":{"name":"kr"},"area1":{"name":"경기도"},"area2":{"name":"성남시 수정구"},"area3":{"name":"시흥동"}},"land":{"number1":"335","number2":""}}]}"""
                .toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.write(body)
            ex.close()
        }
        start()
    }
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @After
    fun stop() = server.stop(0)

    @Test
    fun asksTheFamilyServerWithTheLoginTokenAndNoNaverKey() = runBlocking {
        val geo = ServerReverseGeocoder(base, { "tok-123" })
        assertEquals("경기도 성남시 수정구 시흥동 335", geo.addressOf(37.4127, 127.0947))
        val (path, auth, naverKey) = seen.single()
        assertEquals("/api/naver/reverse-geocode?coords=127.0947,37.4127&orders=addr&output=json", path)
        assertEquals("Bearer tok-123", auth)
        assertNull("앱은 네이버 키를 보내지 않는다", naverKey)
    }

    @Test
    fun withoutALoginTokenItDoesNotCallAnything() = runBlocking {
        val geo = ServerReverseGeocoder(base, { null })
        assertNull(geo.addressOf(37.4127, 127.0947))
        assertTrue(seen.isEmpty())
    }

    @Test
    fun aServerErrorMeansNoAddress() = runBlocking {
        server.removeContext("/")
        server.createContext("/") { ex -> ex.sendResponseHeaders(503, -1); ex.close() }
        assertNull(ServerReverseGeocoder(base, { "t" }).addressOf(37.4, 127.1))
    }
}
