package tether

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.google.gson.JsonParser
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.lang.Exception
import java.net.URI

val exchanges = mutableMapOf(
    "nobitex" to (0.0 to 0.0),
    "arzpaya" to (0.0 to 0.0),
    "farhadmarket" to (0.0 to 0.0),
    "ramzinex" to (0.0 to 0.0)
)
var isExchangesNew = false

fun main() {
    while (true) {
        getPrices()

        if (isExchangesNew) {
            isExchangesNew = false
            println()
            println()
            exchanges.toList()
                .sortedBy { it.second.second }
                .forEach {
                    println("%-15s price: %-10.1f price+fee: %.1f".format(it.first, it.second.first, it.second.second))
                }
            println()
        } else {
            print(".")
        }

        Thread.sleep(2000)
    }
}

fun getPrices() {
    var wait = true
    object : WebSocketClient(URI("wss://market.arzpaya.com/signalr/connect?transport=webSockets&clientProtocol=1.5&&connectionToken=AQAAANCMnd8BFdERjHoAwE%2FCl%2BsBAAAAHj4j9Alz4ECxVTti7D9fVgAAAAACAAAAAAAQZgAAAAEAACAAAAAnvjFQqIl4ksTHnf2Mw2RImL7JlMrlEA%2FU%2FvXd5fqOmwAAAAAOgAAAAAIAACAAAACcnxGt6G6q3XxYohzTLbMr2CMbRG0C0UKQ6QYJQEtHhTAAAAC6SMTW%2FOlA0n0FWze7j9q0u5EOU5dgO9NL4jZshKfqGJ2%2F9SOPBhg8P%2FSXSV5yo2hAAAAAHCuLBwdxrPIxAl7GlXv5APRs1DMVKCshQuBdrJ5EAYKN2xQlZ4MNSY%2FJ9ljNr4qfxJSrRbgrYvl8xBrr5ItSTg%3D%3D&connectionData=%5B%7B%22name%22%3A%22orderhub%22%7D%5D&tid=5")) {
        override fun onOpen(handshakedata: ServerHandshake?) {
            this.send("""{"H":"orderhub","M":"GetExchangeChangesInit","A":["f6fe6cc5-657e-4463-a3fc-26e514a3ed92"],"I":0}""")
        }

        override fun onMessage(message: String?) {
            if (!message.isNullOrEmpty() && message.contains("""{"H":"OrderHub",""")) {
                val msg = JsonParser.parseString(message).asJsonObject.getAsJsonArray("M").get(0).asJsonObject.getAsJsonArray("A").get(0).asString
                val json = JsonParser.parseString(msg).asJsonArray.find { it.asJsonObject.get("ExchangeType").asString == "7" }
                val res = json!!.asJsonObject.get("CurrentBuyPrice").asDouble
                if (exchanges.get("arzpaya")!!.first != res) {
                    exchanges.set("arzpaya", (res to res + (res * .002)))
                    isExchangesNew = true
                }
                this.close()
                wait = false
            }
        }

        override fun onError(ex: Exception?) {
            print("a")
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
        }
    }.connect()

    while (wait) {
        Thread.sleep(300)
    }

    wait = true
    object : WebSocketClient(URI("wss://wsapi.farhadmarket.com/wsapiv2")) {
        override fun onOpen(handshakedata: ServerHandshake?) {
            this.send("""{ "action":"subscribe", "channel":"market.depth", "params":{ "market":"USDT_IRR", "interval": "0" } }""")
        }

        override fun onMessage(message: String?) {
            val json = JsonParser.parseString(message).asJsonObject
            if (json.get("event").asString == "init" && json.get("channel").asString == "market.depth") {
                val res = json.getAsJsonObject("body").getAsJsonArray("asks").get(0).asJsonArray.get(0).asDouble / 10
                if (exchanges.get("farhadmarket")!!.first != res) {
                    exchanges.set("farhadmarket", (res to res + (res * .004)))
                    isExchangesNew = true
                }
                this.close()
                wait = false
            }
        }

        override fun onError(ex: Exception?) {
            print("f")
        }

        override fun onClose(code: Int, reason: String?, remote: Boolean) {
        }
    }.connect()

    while (wait) {
        Thread.sleep(300)
    }

    Fuel.post("https://api-f.nobitex1.ir/market/stats")
        .body("""{"srcCurrency": "usdt", "dstCurrency": "rls"}""")
        .header(Headers.CONTENT_TYPE, "application/json").responseString { request, response, result ->
            result.fold({ success ->
                val json = JsonParser.parseString(success).asJsonObject
                val res = json.getAsJsonObject("stats").getAsJsonObject("usdt-rls").get("bestSell").asDouble / 10
                if (exchanges.get("nobitex")!!.first != res) {
                    exchanges.set("nobitex", (res to res + (res * .0035)))
                    isExchangesNew = true
                }
            }, {
                print("n")
            })
        }.join()

    Fuel.get("https://publicapi.ramzinex.com/exchange/api/v1.0/exchange/pairs/11")
        .responseString { request, response, result ->
            result.fold({ success ->
                val json = JsonParser.parseString(success).asJsonObject
                val res = json.getAsJsonObject("data").get("sell").asDouble / 10
                if (exchanges.get("ramzinex")!!.first != res) {
                    exchanges.set("ramzinex", (res to res + (res * .0032)))
                    isExchangesNew = true
                }
            }, {
                print("r")
            })
        }.join()
}