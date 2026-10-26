package com.anon.schedule.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * 大白U帮（com.zhuochi.hydream 2.0.7）协议客户端，用于读取自己的账户余额与澡堂空位。
 *
 * 协议链路（逆向 + 实机验证结论见交接文档）：
 *   POST {BASE}{path}  body: ztechMsg=<JSON>
 *   ztechMsg = {"head":{verify,msg_id,length,uuid,enc:"GZ"},"data":"<cipher>"}
 *   cipher   = 自定义 base64( DES-CBC( zlib( 非 ASCII 转义后的 {data,header} ) ) )
 *   verify   = SHA1(msg_id + uuid + length + SALT + cipher)
 *
 * 只做读操作（登录 / 钱包 / 澡堂区域 / 坑位），不涉及支付、开阀等写接口。
 */
object UBangApi {

    private const val BASE = "http://lz.hydream.cn/api/v1/"
    private const val SALT = "q9KswPoz62jQk12W"
    private const val DES_KEY = "5yoOxt9w"
    private const val DES_IV = "11111111"
    /** 设备类型：App 里「当前设备」默认值就是 faucet（水龙头），不是数字 */
    private const val DEVICE_TYPE_KEY = "faucet"
    private const val TIMEOUT_MS = 20_000

    const val CODE_OK = 200

    /** 90 = 未登录 / token 失效 */
    const val CODE_UNAUTHORIZED = 90

    class ApiException(val code: Int, message: String) : Exception(message)

    data class Login(
        val token: String,
        val orgAreaId: Int,
        val areaName: String?,
        val orgName: String?,
        val userName: String
    )

    data class Balance(
        val available: Double,
        val given: Double,
        val deposit: String?,
        val score: String?
    ) {
        val total: Double get() = available + given
    }

    data class Area(
        val id: Int,
        val name: String,
        val floor: Int,
        val gender: String,
        val idle: Int,
        val total: Int,
        val tip: String
    )

    data class Slot(
        val number: String,
        val status: String,
        val endTime: String
    )

    data class Snapshot(
        val balance: Balance,
        val areas: List<Area>,
        val areaName: String?,
        val orgName: String?
    )

    // ------------------------------------------------------------ 组合接口

    /** 读取余额 + 澡堂空位；token 失效时用本机保存的账号自动重登一次 */
    suspend fun load(context: Context): Snapshot {
        val creds = UBangPrefs.credentials(context)
        var token = UBangPrefs.token(context)
        if (token.isBlank()) {
            val (mobile, password) = creds
                ?: throw ApiException(-1, "还没有登录，请先输入手机号和密码")
            token = relogin(context, mobile, password)
        }
        return try {
            fetch(context, token)
        } catch (e: ApiException) {
            if (e.code != CODE_UNAUTHORIZED) throw e
            val (mobile, password) = creds
                ?: throw ApiException(CODE_UNAUTHORIZED, "登录已过期，请重新输入账号密码")
            fetch(context, relogin(context, mobile, password))
        }
    }

    suspend fun login(context: Context, mobile: String, password: String): Login =
        withContext(Dispatchers.IO) {
            val uuid = UBangPrefs.deviceUuid(context)
            val node = call(
                "UserApi/loginWidthPwd", "loginWidthPwd",
                JSONObject().put("mobile", mobile).put("pwd", password),
                token = "", uuid = uuid
            )
            val obj = node.optJSONObject("data") ?: throw ApiException(-1, "登录返回为空")
            val login = Login(
                token = obj.optString("token"),
                orgAreaId = obj.optInt("default_org_area_id"),
                areaName = obj.optString("default_org_area_name").ifBlank { null },
                orgName = obj.optString("default_org_name").ifBlank { null },
                userName = obj.optString("user_nickname")
                    .ifBlank { obj.optString("real_name") }
                    .ifBlank { obj.optString("mobile") }
                    .ifBlank { mobile }
            )
            if (login.token.isBlank()) throw ApiException(-1, "登录失败：没有拿到 token")
            UBangPrefs.saveLogin(context, mobile, password, login)
            login
        }

    /** 坑位明细（ready / running / reserved） */
    suspend fun slots(context: Context, areaId: Int): List<Slot> = withContext(Dispatchers.IO) {
        val creds = UBangPrefs.credentials(context)
        var token = UBangPrefs.token(context)
        if (token.isBlank()) {
            val (mobile, password) = creds ?: throw ApiException(-1, "还没有登录")
            token = relogin(context, mobile, password)
        }
        val uuid = UBangPrefs.deviceUuid(context)
        val data = JSONObject()
            .put("deviceAreaId", areaId)
            .put("deviceTypeKey", DEVICE_TYPE_KEY)
        val node = try {
            call("DeviceAreaApi/selectDevicesByAreaId", "selectDevicesByAreaId", data, token, uuid)
        } catch (e: ApiException) {
            if (e.code != CODE_UNAUTHORIZED) throw e
            val (mobile, password) = creds ?: throw ApiException(CODE_UNAUTHORIZED, "登录已过期，请重新输入账号密码")
            call(
                "DeviceAreaApi/selectDevicesByAreaId", "selectDevicesByAreaId", data,
                relogin(context, mobile, password), uuid
            )
        }
        parseSlots(node.optJSONArray("data"))
    }

    // ------------------------------------------------------------ 内部流程

    private suspend fun relogin(context: Context, mobile: String, password: String): String =
        login(context, mobile, password).token

    private suspend fun fetch(context: Context, token: String): Snapshot =
        withContext(Dispatchers.IO) {
            val uuid = UBangPrefs.deviceUuid(context)
            val walletNode = call("UserApi/getWalletInfo", "getWalletInfo", JSONObject(), token, uuid)
            val w = walletNode.optJSONObject("data")
            val balance = Balance(
                available = w.number("available_balance"),
                given = w.number("given_balance"),
                deposit = w?.optString("deposit")?.ifBlank { null },
                score = w?.optString("score")?.ifBlank { null }
            )
            val orgAreaId = UBangPrefs.orgAreaId(context)
            val areas = if (orgAreaId > 0) {
                val node = call(
                    "DeviceAreaApi/selectDeviceAreasWithDeviceState", "selectDeviceAreasWithDeviceState",
                    JSONObject().put("orgAreaId", orgAreaId).put("deviceTypeKey", DEVICE_TYPE_KEY),
                    token, uuid
                )
                parseAreas(node.optJSONArray("data"))
            } else {
                emptyList()
            }
            Snapshot(balance, areas, UBangPrefs.areaName(context), UBangPrefs.orgName(context))
        }

    private fun parseAreas(arr: JSONArray?): List<Area> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Area(
                id = o.optInt("device_area_id"),
                name = o.optString("device_area_name").trim(),
                floor = o.optInt("floor"),
                gender = genderLabel(o.optString("gender_limit")),
                idle = o.optInt("idleNum"),
                total = o.optInt("totalNum"),
                tip = pickTip(o)
            )
        }
    }

    private fun genderLabel(raw: String): String = when (raw.trim().lowercase(Locale.US)) {
        "male" -> "男"
        "female" -> "女"
        else -> raw.trim()
    }

    /**
     * 维护提示只在维护结束时间还没到的时候显示（服务器会一直返回历史维护文案），
     * 否则用正常的营业提醒 reminder。
     */
    private fun pickTip(o: JSONObject): String {
        val maintenance = o.optString("maintenance_tip").trim()
        val end = o.optString("maintenance_end").toLongOrNull() ?: 0L
        val endMs = if (end in 1 until 1_000_000_000_000L) end * 1000 else end
        val inMaintenance = maintenance.isNotBlank() && endMs > System.currentTimeMillis()
        return if (inMaintenance) maintenance else o.optString("reminder").trim()
    }

    private fun parseSlots(arr: JSONArray?): List<Slot> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Slot(
                number = o.optString("device_number").ifBlank { o.optString("device_name") },
                status = o.optString("device_status"),
                endTime = o.optString("end_time")
            )
        }.sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
    }

    private fun JSONObject?.number(key: String): Double {
        val raw = this?.opt(key) ?: return 0.0
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    // ------------------------------------------------------------ 协议实现

    /** 一次完整调用：加密组包 → 发送 → 解密 → 判 code */
    private fun call(
        path: String,
        act: String,
        data: JSONObject,
        token: String,
        uuid: String
    ): JSONObject {
        val ztechMsg = encode(act, data, token, uuid)
        val body = "ztechMsg=" + percentEncode(ztechMsg)
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "okhttp/3.12.0")
        }
        val text: String
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw ApiException(-1, "HTTP $code：${text.take(120).ifBlank { "服务器无响应" }}")
            }
        } finally {
            conn.disconnect()
        }
        val node = decodeResponse(text)
        val code = node.optInt("code", -1)
        if (code != CODE_OK) {
            throw ApiException(code, node.optString("msg").ifBlank { "请求失败（code $code）" })
        }
        return node
    }

    private fun encode(act: String, data: JSONObject, token: String, uuid: String): String {
        val header = JSONObject()
            .put("device_type", "android")
            .put("source_sys_version", "12")
            .put("source_version", "2.0.7")
            .put("source_model", "PGT-AN10")
            .put("token", token)
            .put("act", act)
            .put("enc", "GZ")
        val payload = JSONObject().put("data", data).put("header", header).toString()

        // 1. 非 ASCII 转 \uXXXX（同 utils.c.a.g()）
        val escaped = escapeNonAscii(payload)
        // 2. zlib 压缩（Java Deflater 默认带 zlib 头）
        val compressed = zlibCompress(escaped.toByteArray(Charsets.UTF_8))
        // 3. DES-CBC 加密 + 每 45 字节块后加空格的自定义 base64
        val cipher = customBase64(desEncrypt(compressed))
        val length = escaped.length
        val msgId = String.format(Locale.US, "%010d", System.currentTimeMillis() / 1000) +
                Random.nextInt(100000, 1000000)
        val verify = sha1Hex(msgId + uuid + length + SALT + cipher)

        val head = JSONObject()
            .put("verify", verify)
            .put("msg_id", msgId)
            .put("length", length)
            .put("uuid", uuid)
            .put("enc", "GZ")
        return JSONObject().put("head", head).put("data", cipher).toString()
    }

    private fun decodeResponse(text: String): JSONObject {
        val outer = runCatching { JSONObject(text) }
            .getOrElse { throw ApiException(-1, "服务返回不是 JSON：${text.take(120)}") }
        val enc = outer.optString("data")
        if (enc.isBlank()) throw ApiException(-1, "服务返回缺少 data：${text.take(120)}")
        val plain = runCatching {
            String(zlibDecompress(desDecrypt(base64Decode(enc))), Charsets.UTF_8)
        }.getOrElse { throw ApiException(-1, "响应解密失败：${it.message}") }
        val root = runCatching { JSONObject(plain) }
            .getOrElse { throw ApiException(-1, "响应内容不是 JSON") }
        return root.optJSONObject("data") ?: throw ApiException(-1, "响应缺少业务节点")
    }

    /** utils.c.a.g()：非 ASCII 字符转 \uXXXX */
    private fun escapeNonAscii(s: String): String {
        val sb = StringBuilder(s.length + 32)
        for (ch in s) {
            if (ch.code > 0x7F) sb.append("\\u").append(Integer.toHexString(ch.code)) else sb.append(ch)
        }
        return sb.toString()
    }

    /** utils.c.d()：标准 base64，每 45 字节块之间插入空格；整除 45 时末尾也有空格 */
    private fun customBase64(data: ByteArray): String {
        val sb = StringBuilder(data.size * 2)
        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + 45, data.size)
            if (offset > 0) sb.append(' ')
            sb.append(Base64.encodeToString(data.copyOfRange(offset, end), Base64.NO_WRAP))
            offset = end
        }
        if (data.isNotEmpty() && data.size % 45 == 0) sb.append(' ')
        return sb.toString()
    }

    /** utils.c.c()：解码前忽略空白字符 */
    private fun base64Decode(s: String): ByteArray =
        Base64.decode(s.filterNot { it.isWhitespace() }, Base64.NO_WRAP)

    private fun desEncrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("DES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(DES_KEY.toByteArray(Charsets.US_ASCII), "DES"),
            IvParameterSpec(DES_IV.toByteArray(Charsets.US_ASCII))
        )
        return cipher.doFinal(data)
    }

    private fun desDecrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("DES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(DES_KEY.toByteArray(Charsets.US_ASCII), "DES"),
            IvParameterSpec(DES_IV.toByteArray(Charsets.US_ASCII))
        )
        return cipher.doFinal(data)
    }

    private fun zlibCompress(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val out = ByteArrayOutputStream(data.size + 64)
        val buf = ByteArray(4096)
        try {
            deflater.setInput(data)
            deflater.finish()
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
        } finally {
            deflater.end()
        }
        return out.toByteArray()
    }

    private fun zlibDecompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        val out = ByteArrayOutputStream(data.size * 3)
        val buf = ByteArray(4096)
        try {
            inflater.setInput(data)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                out.write(buf, 0, n)
            }
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    private fun sha1Hex(s: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.US, "%02x", it) }

    /** 表单百分号编码（空格 → %20，避免 + 号歧义） */
    private fun percentEncode(s: String): String {
        val sb = StringBuilder(s.length * 3)
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c < 0x80 && (c.toChar().isLetterOrDigit() || c.toChar() in "-_.~")) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append(String.format(Locale.US, "%02X", c))
            }
        }
        return sb.toString()
    }
}

/** 账号与 token 的本机存储（只存本机，不上传任何第三方） */
object UBangPrefs {

    private const val NAME = "ubang"
    private const val K_MOBILE = "mobile"
    private const val K_PASSWORD = "password"
    private const val K_TOKEN = "token"
    private const val K_ORG_AREA_ID = "orgAreaId"
    private const val K_AREA_NAME = "areaName"
    private const val K_ORG_NAME = "orgName"
    private const val K_UUID = "uuid"

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun credentials(context: Context): Pair<String, String>? {
        val p = prefs(context)
        val mobile = p.getString(K_MOBILE, "")?.trim().orEmpty()
        val password = p.getString(K_PASSWORD, "").orEmpty()
        return if (mobile.isNotBlank() && password.isNotBlank()) mobile to password else null
    }

    /** 已登录：有 token 即可，密码只是用来在 token 过期后自动重登 */
    fun hasSession(context: Context): Boolean =
        token(context).isNotBlank() || credentials(context) != null

    fun token(context: Context): String = prefs(context).getString(K_TOKEN, "").orEmpty()

    fun orgAreaId(context: Context): Int = prefs(context).getInt(K_ORG_AREA_ID, 0)

    fun areaName(context: Context): String? =
        prefs(context).getString(K_AREA_NAME, null)?.ifBlank { null }

    fun orgName(context: Context): String? =
        prefs(context).getString(K_ORG_NAME, null)?.ifBlank { null }

    fun deviceUuid(context: Context): String {
        val p = prefs(context)
        p.getString(K_UUID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val uuid = UUID.randomUUID().toString()
        p.edit().putString(K_UUID, uuid).apply()
        return uuid
    }

    fun saveLogin(context: Context, mobile: String, password: String, login: UBangApi.Login) {
        prefs(context).edit()
            .putString(K_MOBILE, mobile)
            .putString(K_PASSWORD, password)
            .putString(K_TOKEN, login.token)
            .putInt(K_ORG_AREA_ID, login.orgAreaId)
            .putString(K_AREA_NAME, login.areaName)
            .putString(K_ORG_NAME, login.orgName)
            .apply()
    }

    /** 退出登录：清账号与 token，保留设备 uuid（协议要求恒定） */
    fun clear(context: Context) {
        val uuid = deviceUuid(context)
        prefs(context).edit().clear().putString(K_UUID, uuid).apply()
    }
}
