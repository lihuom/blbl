package blbl.cat3399.core.phoneinput

import android.graphics.Bitmap
import android.graphics.Color
import blbl.cat3399.core.log.AppLog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Enumeration
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 手机扫码输入：TV 端起一个局域网 HTTP 服务，手机扫二维码打开网页，
 * 输入文本后 POST 回来，直接注入到搜索栏或立即搜索。
 *
 * 端口固定 9529（与社区常见实现一致，便于记忆）。
 * 路径：
 *   GET  /        → 手机端网页（输入框 + 投送到搜索框 / 直接搜索）
 *   POST /input   → JSON { "text": "...", "action": "fill" | "search" }
 *   GET  /status  → JSON { "running": true }
 *
 * 不引入新依赖：HTTP 用 JDK ServerSocket 手写，二维码用已有的 zxing:core。
 */
object PhoneInputServer : Closeable {
    const val PORT = 9529

    /** 手机端发来的动作：fill=仅填入搜索框；search=填入并立即搜索。 */
    enum class Action { FILL, SEARCH }

    /** 收到手机端输入时的回调（在主线程调用）。 */
    fun interface Listener {
        fun onInput(text: String, action: Action)
    }

    @Volatile
    private var listener: Listener? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    private val started = AtomicBoolean(false)

    val isRunning: Boolean
        get() = serverSocket != null && started.get()

    fun setListener(l: Listener?) {
        listener = l
    }

    /** 启动服务（幂等）。返回 true 表示已在运行或启动成功。 */
    fun start(): Boolean {
        if (isRunning) return true
        synchronized(this) {
            if (isRunning) return true
            return try {
                val socket = ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))
                serverSocket = socket
                started.set(true)
                acceptThread =
                    thread(name = "blbl-phone-input-accept", isDaemon = true) {
                        AppLog.i("PhoneInput", "started port=$PORT")
                        while (!socket.isClosed) {
                            val client = runCatching { socket.accept() }.getOrNull() ?: break
                            thread(name = "blbl-phone-input-worker", isDaemon = true) {
                                runCatching { handleClient(client) }
                                    .onFailure { t -> AppLog.w("PhoneInput", "handle client failed", t) }
                                    .also { runCatching { client.close() } }
                            }
                        }
                        AppLog.i("PhoneInput", "accept loop ended")
                    }
                true
            } catch (e: Exception) {
                AppLog.w("PhoneInput", "start failed", e)
                started.set(false)
                serverSocket = null
                false
            }
        }
    }

    fun stop() {
        val socket = serverSocket ?: return
        serverSocket = null
        started.set(false)
        runCatching { socket.close() }
        acceptThread = null
        AppLog.i("PhoneInput", "stopped")
    }

    override fun close() = stop()

    /** 获取第一个非回环、非虚拟网卡的局域网 IPv4 地址，失败返回 null。 */
    fun getLanIp(): String? {
        val interfaces: Enumeration<NetworkInterface> =
            runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            // 跳过 down 掉的、回环、虚拟网卡
            val up = runCatching { intf.isUp }.getOrDefault(false)
            val loopback = runCatching { intf.isLoopback }.getOrDefault(false)
            val virtual = runCatching { intf.isVirtual }.getOrDefault(false)
            if (!up || loopback || virtual) continue
            val addrs = intf.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                val host = addr.hostAddress ?: continue
                // 只要 IPv4（点分十进制），不要 IPv6
                if (addr is InetAddress && host.contains('.') && !addr.isLoopbackAddress) {
                    return host
                }
            }
        }
        return null
    }

    /** 二维码内容：http://<lan-ip>:9529/ */
    fun buildUrl(): String? {
        val ip = getLanIp() ?: return null
        return "http://$ip:$PORT/"
    }

    /** 生成二维码 Bitmap（白底黑码，size x size）。失败返回 null。 */
    fun generateQrBitmap(url: String, size: Int = 512): Bitmap? {
        return runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1,
            )
            val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        }.onFailure { AppLog.w("PhoneInput", "generate qr failed", it) }.getOrNull()
    }

    private fun handleClient(socket: Socket) {
        socket.soTimeout = 15_000
        socket.use { s ->
            val input = s.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
            val requestLine = input.readLine()?.trim().orEmpty()
            if (requestLine.isBlank()) return
            val parts = requestLine.split(' ', limit = 3)
            if (parts.size < 2) return
            val method = parts[0].trim().uppercase(Locale.US)
            val target = parts[1].trim()

            // 读 headers
            val headers = LinkedHashMap<String, String>()
            var contentLength = 0L
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                val name = line.substring(0, idx).trim().lowercase(Locale.US)
                val value = line.substring(idx + 1).trim()
                headers[name] = value
                if (name == "content-length") contentLength = value.toLongOrNull() ?: 0L
            }

            val path = target.substringBefore('?')

            when {
                method == "GET" && (path == "/" || path == "/index.html") -> {
                    respondHtml(s, buildInputPage())
                }
                method == "POST" && path == "/input" -> {
                    val body = readBody(input, contentLength)
                    handleInput(body)
                    respondJson(s, """{"ok":true}""")
                }
                method == "GET" && path == "/status" -> {
                    respondJson(s, """{"running":true,"port":$PORT}""")
                }
                else -> {
                    respondPlain(s, 404, "Not Found", "path=$path")
                }
            }
        }
    }

    private fun readBody(input: java.io.BufferedReader, contentLength: Long): String {
        if (contentLength <= 0) return ""
        val buf = CharArray(contentLength.toInt().coerceAtMost(64 * 1024))
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n <= 0) break
            total += n
        }
        // headers 用 ISO-8859-1 读取（HTTP 头规范），body 的 UTF-8 字节会被当成 Latin-1 字符。
        // ISO-8859-1 的 char 与 byte 一一映射（char.code & 0xFF），转回原始字节后再用 UTF-8 解码，
        // 否则中文会变成乱码（如 "你好" → "ä½ å¥½"）。
        val bytes = ByteArray(total) { i -> (buf[i].code and 0xFF).toByte() }
        return String(bytes, StandardCharsets.UTF_8)
    }

    /** 解析 JSON（极简，只取 text 和 action 字段，不引 Gson）。 */
    private fun handleInput(body: String) {
        val text = extractJsonString(body, "text") ?: return
        val actionRaw = extractJsonString(body, "action")?.lowercase(Locale.US)
        val action = if (actionRaw == "search") Action.SEARCH else Action.FILL
        if (text.isBlank()) return
        // 切到主线程回调
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            listener?.onInput(text, action)
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val keyToken = "\"$key\""
        val idx = json.indexOf(keyToken)
        if (idx < 0) return null
        val colon = json.indexOf(':', idx + keyToken.length)
        if (colon < 0) return null
        var i = colon + 1
        while (i < json.length && (json[i] == ' ' || json[i] == '\t' || json[i] == '\n' || json[i] == '\r')) i++
        if (i >= json.length) return null
        if (json[i] != '"') return null // 只处理字符串值
        i++
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                when (val next = json[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    else -> sb.append(next)
                }
                i += 2
            } else if (c == '"') {
                break
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun respondHtml(socket: Socket, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val out = BufferedOutputStream(socket.getOutputStream())
        out.write("HTTP/1.1 200 OK\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("Content-Type: text/html; charset=utf-8\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("Content-Length: ${bytes.size}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("Connection: close\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    private fun respondJson(socket: Socket, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val out = BufferedOutputStream(socket.getOutputStream())
        out.write("HTTP/1.1 200 OK\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("Content-Type: application/json; charset=utf-8\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("Content-Length: ${bytes.size}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("Connection: close\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    private fun respondPlain(socket: Socket, code: Int, message: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val out = BufferedOutputStream(socket.getOutputStream())
        out.write("HTTP/1.1 $code $message\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("Content-Type: text/plain; charset=utf-8\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("Content-Length: ${bytes.size}\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("Connection: close\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        out.write(bytes)
        out.flush()
    }

    private fun buildInputPage(): String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>搜索 · 远程输入</title>
<style>
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  body {
    margin: 0; min-height: 100vh; background: #1a1a1a; color: #fff;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
    display: flex; flex-direction: column; align-items: center; padding: 24px 16px 32px;
  }
  h1 { font-size: 28px; margin: 40px 0 24px; font-weight: 700; letter-spacing: 2px; }
  textarea {
    width: 100%; max-width: 520px; min-height: 120px;
    background: #2a2a2a; border: 2px solid #4caf50; border-radius: 14px;
    color: #fff; font-size: 18px; padding: 16px; resize: vertical; outline: none;
    line-height: 1.5;
  }
  textarea::placeholder { color: #888; }
  .btns {
    display: flex; gap: 16px; width: 100%; max-width: 520px; margin-top: 20px;
  }
  button {
    flex: 1; padding: 18px 0; border: none; border-radius: 14px;
    font-size: 20px; font-weight: 700; color: #000; cursor: pointer;
    transition: opacity .15s;
  }
  button:active { opacity: .7; }
  .btn-fill { background: #81c784; }
  .btn-search { background: #ff8a65; }
  .status {
    margin-top: 16px; min-height: 28px; font-size: 18px; color: #4caf50; font-weight: 600;
  }
  .tip { margin-top: 12px; font-size: 14px; color: #888; text-align: center; max-width: 520px; line-height: 1.6; }
</style>
</head>
<body>
  <h1>搜索</h1>
  <textarea id="t" placeholder="关键词 / BV号 / uid12345 / 视频链接..."></textarea>
  <div class="btns">
    <button class="btn-fill" onclick="send('fill')">投送到搜索框</button>
    <button class="btn-search" onclick="send('search')">直接搜索</button>
  </div>
  <div class="status" id="s"></div>
  <div class="tip">粘贴分享链接（含 t=秒）可精准空降播放</div>
<script>
  var t = document.getElementById('t');
  var s = document.getElementById('s');
  function send(action) {
    var text = t.value;
    if (!text || !text.trim()) { t.focus(); return; }
    s.textContent = '发送中...';
    s.style.color = '#888';
    fetch('/input', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: text, action: action })
    }).then(function(r) { return r.json(); }).then(function(d) {
      if (d && d.ok) {
        s.textContent = '已发送';
        s.style.color = '#4caf50';
      } else {
        s.textContent = '发送失败';
        s.style.color = '#ff5252';
      }
    }).catch(function() {
      s.textContent = '网络错误，请确认手机与电视在同一局域网';
      s.style.color = '#ff5252';
    });
  }
  t.focus();
</script>
</body>
</html>
""".trimIndent()
}
