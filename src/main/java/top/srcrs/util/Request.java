package top.srcrs.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import top.srcrs.domain.UserData;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网络请求工具类。
 * <p>
 * 相比早期版本主要有三点变化：
 * <ol>
 *   <li>Cookie 里补上了 buvid3 / buvid4 / bili_ticket，缺了这些现在很容易被判成机器人；</li>
 *   <li>限速改成真正的滑动窗口，老实现的计数器不会归零，跑到后面每个请求都要干等一分钟；</li>
 *   <li>请求失败不再抛异常，而是返回 {@code code = -1} 的结果，任务侧统一按"这项没做成"处理。</li>
 * </ol>
 *
 * @author srcrs
 * @Time 2020-10-13
 */
@Slf4j
public final class Request {

    private Request() {
    }

    /** 网络层自身的失败码，与 B 站返回的业务码区分开 */
    public static final int TRANSPORT_ERROR = -1;

    private static final UserData USER_DATA = UserData.getInstance();

    /** 两次请求之间的最小间隔 */
    private static final long MIN_INTERVAL_MS = 600L;
    /** 在最小间隔基础上叠加的随机抖动上限 */
    private static final long JITTER_MS = 500L;
    /** 每分钟最多发出的请求数 */
    private static final int MAX_PER_MINUTE = 40;
    private static final long ONE_MINUTE_MS = 60_000L;

    /** 单次请求的重试次数（含首次） */
    private static final int MAX_ATTEMPTS = 2;

    private static final Deque<Long> RECENT_REQUESTS = new ArrayDeque<>();
    private static long lastRequestAt = 0L;

    /** 网络层失败次数，运行结束时汇总用 */
    private static final AtomicInteger TRANSPORT_ERRORS = new AtomicInteger();

    private static volatile String userAgent = InitUserAgent.getOne();
    private static volatile boolean bootstrapped = false;
    private static volatile String buvid3 = "";
    private static volatile String buvid4 = "";
    private static volatile String biliTicket = "";
    private static volatile String uuidCookie = "";
    private static volatile String bLsid = "";
    private static volatile String bNut = "";
    private static volatile String buvidFp = "";

    private static final CloseableHttpClient CLIENT = buildClient();

    private static CloseableHttpClient buildClient() {
        PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
        manager.setMaxTotal(16);
        manager.setDefaultMaxPerRoute(8);
        RequestConfig config = RequestConfig.custom()
                                            .setConnectTimeout(10_000)
                                            .setSocketTimeout(20_000)
                                            .setConnectionRequestTimeout(5_000)
                                            // 自己拼 Cookie 头，关掉 httpclient 的 Cookie 管理避免它把请求头改写掉
                                            .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                                            .build();
        return HttpClients.custom()
                          .setConnectionManager(manager)
                          .setDefaultRequestConfig(config)
                          .disableAutomaticRetries()
                          .build();
    }

    /**
     * 设置本次运行使用的 UserAgent。
     *
     * @param ua UserAgent
     */
    public static void setUserAgent(String ua) {
        if (StringUtil.isNotBlank(ua)) {
            userAgent = ua;
        }
    }

    /**
     * 网络层失败的累计次数。
     *
     * @return 失败次数
     */
    public static int transportErrors() {
        return TRANSPORT_ERRORS.get();
    }

    /* ------------------------------ 对外的请求方法 ------------------------------ */

    /**
     * 发送 GET 请求。
     *
     * @param url 请求地址
     * @return 响应内容
     */
    public static JSONObject get(String url) {
        return get(url, new JSONObject());
    }

    /**
     * 发送 GET 请求。
     *
     * @param url    请求地址
     * @param params 查询参数
     * @return 响应内容
     */
    public static JSONObject get(String url, JSONObject params) {
        return get(url, params, BiliApi.REFERER_MAIN);
    }

    /**
     * 发送 GET 请求。
     *
     * @param url     请求地址
     * @param params  查询参数
     * @param referer Referer 头，B 站部分接口会校验
     * @return 响应内容
     */
    public static JSONObject get(String url, JSONObject params, String referer) {
        URI uri = withQuery(url, params);
        if (uri == null) {
            return error("请求地址不合法: " + url);
        }
        return execute(builder(HttpGet.METHOD_NAME, referer).setUri(uri).build());
    }

    /**
     * 发送带 WBI 签名的 GET 请求。
     *
     * @param url    请求地址
     * @param params 查询参数，签名会在内部补齐
     * @return 响应内容
     */
    public static JSONObject getWbi(String url, JSONObject params) {
        return getWbi(url, params, BiliApi.REFERER_MAIN);
    }

    /**
     * 发送带 WBI 签名的 GET 请求。
     *
     * @param url     请求地址
     * @param params  查询参数，签名会在内部补齐
     * @param referer Referer 头
     * @return 响应内容
     */
    public static JSONObject getWbi(String url, JSONObject params, String referer) {
        String query = WbiSignature.signedQuery(params);
        if (query.isEmpty()) {
            // 签名拿不到时退回普通请求，让接口自己决定要不要放行
            return get(url, params, referer);
        }
        return getRaw(url, query, referer);
    }

    /**
     * 用已经编码好的查询串发送 GET 请求，不做二次编码。
     *
     * @param url      请求地址
     * @param rawQuery 已编码的查询串
     * @param referer  Referer 头
     * @return 响应内容
     */
    private static JSONObject getRaw(String url, String rawQuery, String referer) {
        String full = url + (url.indexOf('?') >= 0 ? "&" : "?") + rawQuery;
        try {
            return execute(builder(HttpGet.METHOD_NAME, referer).setUri(URI.create(full)).build());
        } catch (IllegalArgumentException e) {
            log.error("💔地址解析失败: {}", url, e);
            return error("请求地址不合法: " + url);
        }
    }

    /**
     * 发送 POST 请求，参数放在表单体里。
     *
     * @param url  请求地址
     * @param form 表单参数
     * @return 响应内容
     */
    public static JSONObject post(String url, JSONObject form) {
        return post(url, form, BiliApi.REFERER_MAIN);
    }

    /**
     * 发送 POST 请求，参数放在表单体里。
     *
     * @param url     请求地址
     * @param form    表单参数
     * @param referer Referer 头
     * @return 响应内容
     */
    public static JSONObject post(String url, JSONObject form, String referer) {
        HttpUriRequest request = builder(HttpPost.METHOD_NAME, referer)
                .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .setUri(url)
                .addParameters(pairs(form))
                .build();
        return execute(request);
    }

    /**
     * 发送 POST 请求，但参数放在查询串里。
     * <p>
     * bili_ticket 接口就只认查询串，之前把参数塞进表单体，服务端一直回 {@code empty ts field}。
     *
     * @param url    请求地址
     * @param params 查询参数
     * @return 响应内容
     */
    public static JSONObject postQuery(String url, JSONObject params) {
        return postQuery(url, params, BiliApi.REFERER_MAIN);
    }

    /**
     * 发送 POST 请求，但参数放在查询串里。
     *
     * @param url     请求地址
     * @param params  查询参数
     * @param referer Referer 头
     * @return 响应内容
     */
    public static JSONObject postQuery(String url, JSONObject params, String referer) {
        URI uri = withQuery(url, params);
        if (uri == null) {
            return error("请求地址不合法: " + url);
        }
        return execute(builder(HttpPost.METHOD_NAME, referer).setUri(uri).build());
    }

    /**
     * 发送 JSON 请求体的 POST 请求。
     * <p>
     * gaia 风控的指纹上报接口（ExClimbWuzhi）要求 {@code application/json} 请求体。
     *
     * @param url      请求地址
     * @param jsonBody JSON 字符串
     * @param referer  Referer 头
     * @return 响应内容
     */
    public static JSONObject postJson(String url, String jsonBody, String referer) {
        HttpUriRequest request = builder(HttpPost.METHOD_NAME, referer)
                .addHeader("Content-Type", "application/json")
                .setUri(url)
                .setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8))
                .build();
        return execute(request);
    }

    /**
     * 把 JSON 参数转成 httpclient 的键值对数组。
     * <p>
     * 推送相关的工具类还在用，保留为公开方法。
     *
     * @param params 参数
     * @return 键值对数组
     */
    public static NameValuePair[] pairs(JSONObject params) {
        List<NameValuePair> list = new ArrayList<>();
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                list.add(new BasicNameValuePair(entry.getKey(), StringUtil.get(entry.getValue())));
            }
        }
        return list.toArray(new NameValuePair[0]);
    }

    /**
     * 构造一个失败结果，字段与 B 站返回保持一致，任务侧不用区分来源。
     *
     * @param message 失败原因
     * @return 失败结果
     */
    public static JSONObject error(String message) {
        JSONObject json = new JSONObject();
        json.put("code", TRANSPORT_ERROR);
        json.put("message", message);
        return json;
    }

    /**
     * 安全地取出业务返回码。
     * <p>
     * 漫画那套 twirp 接口出错时会把 code 写成 {@code "invalid_argument"} 这类字符串，
     * 直接按 int 读会抛 {@link NumberFormatException}。
     *
     * @param json 响应内容
     * @return 返回码；缺失或者不是数字时返回 {@link #TRANSPORT_ERROR}
     */
    public static int code(JSONObject json) {
        if (json == null) {
            return TRANSPORT_ERROR;
        }
        Object raw = json.get("code");
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw == null) {
            return TRANSPORT_ERROR;
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return TRANSPORT_ERROR;
        }
    }

    /**
     * 取出响应里的提示文案，兼容 {@code message} 与 {@code msg} 两种字段名。
     *
     * @param json 响应内容
     * @return 提示文案，没有时返回空串
     */
    public static String message(JSONObject json) {
        if (json == null) {
            return "";
        }
        String message = json.getString("message");
        if (StringUtil.isBlank(message)) {
            message = json.getString("msg");
        }
        return StringUtil.trimToEmpty(message);
    }

    /* ------------------------------ 内部实现 ------------------------------ */

    private static RequestBuilder builder(String method, String referer) {
        bootstrap();
        return RequestBuilder.create(method)
                             .addHeader("Accept", "application/json, text/plain, */*")
                             .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                             .addHeader("Connection", "keep-alive")
                             .addHeader("Origin", "https://www.bilibili.com")
                             .addHeader("Referer", referer == null ? BiliApi.REFERER_MAIN : referer)
                             .addHeader("User-Agent", userAgent)
                             .addHeader("Cookie", cookie());
    }

    /**
     * 拼出请求使用的 Cookie。
     *
     * @return Cookie 头的值
     */
    private static String cookie() {
        StringBuilder sb = new StringBuilder(USER_DATA.getCookie());
        appendCookie(sb, "buvid3", buvid3);
        appendCookie(sb, "buvid4", buvid4);
        appendCookie(sb, "bili_ticket", biliTicket);
        appendCookie(sb, "_uuid", uuidCookie);
        appendCookie(sb, "b_lsid", bLsid);
        appendCookie(sb, "b_nut", bNut);
        appendCookie(sb, "buvid_fp", buvidFp);
        return sb.toString();
    }

    private static void appendCookie(StringBuilder sb, String name, String value) {
        if (StringUtil.isNotBlank(value)) {
            sb.append(name).append('=').append(value).append(';');
        }
    }

    /**
     * 首次请求前补齐风控相关的 Cookie，并激活 buvid。
     * <p>
     * 先把标记置位再去请求，避免这两个请求自己又触发一次初始化。
     * <p>
     * 2024 年起 B 站要求 buvid3 必须带上配套设备 Cookie（_uuid/b_lsid/b_nut/buvid_fp），
     * 并调 ExClimbWuzhi 上报一次浏览器指纹才算"激活"；未激活的 buvid 在投币等
     * 敏感写接口上会被直接判成 {@code -401 非法访问}。
     */
    private static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        try {
            JSONObject spi = get(BiliApi.FINGER_SPI);
            JSONObject data = spi.getJSONObject("data");
            if (data != null) {
                buvid3 = StringUtil.trimToEmpty(data.getString("b_3"));
                buvid4 = StringUtil.trimToEmpty(data.getString("b_4"));
            }
            if (StringUtil.isBlank(buvid3)) {
                buvid3 = InitUserAgent.randomBuvid();
                log.debug("buvid 接口不可用，改用本地生成的 buvid3");
            }
            uuidCookie = FingerprintPayload.randomUuid();
            bLsid = FingerprintPayload.randomBLsid();
            bNut = String.valueOf(System.currentTimeMillis() / 1000);
            buvidFp = FingerprintPayload.randomBuvidFp();
            biliTicket = BiliTicket.fetch();
            activateBuvid();
        } catch (Exception e) {
            log.warn("⚠️初始化风控 Cookie 失败，继续以基础 Cookie 运行: {}", e.getMessage());
        }
    }

    /**
     * 上报浏览器指纹，激活 buvid。
     * <p>
     * 失败只记日志不中断：激活失败时敏感接口可能被拦，但观看/签到等多数任务仍可完成。
     */
    private static void activateBuvid() {
        JSONObject response = postJson(BiliApi.EX_CLIMB_WUZHI,
                FingerprintPayload.build(userAgent, uuidCookie), BiliApi.REFERER_MAIN);
        if (Request.code(response) == 0) {
            log.debug("buvid 激活成功");
        } else {
            log.warn("⚠️buvid 激活失败（投币可能被拦）: {} - {}",
                    response.getString("code"), response.getString("message"));
        }
    }

    /**
     * 把参数拼到地址的查询串上。
     *
     * @param url    原始地址，允许自带查询串
     * @param params 追加的参数
     * @return 拼好的地址，地址非法时返回 null
     */
    private static URI withQuery(String url, JSONObject params) {
        try {
            URIBuilder uriBuilder = new URIBuilder(url);
            if (params != null) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    uriBuilder.addParameter(entry.getKey(), StringUtil.get(entry.getValue()));
                }
            }
            return uriBuilder.build();
        } catch (URISyntaxException e) {
            log.error("💔地址解析失败: {}", url, e);
            return null;
        }
    }

    private static JSONObject execute(HttpUriRequest request) {
        String url = request.getURI().getPath();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            throttle();
            try (CloseableHttpResponse response = CLIENT.execute(request)) {
                int status = response.getStatusLine().getStatusCode();
                String body = response.getEntity() == null
                        ? ""
                        : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                JSONObject result = parse(status, body, url);
                if (result != null) {
                    logResult(request.getMethod(), url, result);
                    return result;
                }
            } catch (IOException e) {
                log.warn("⚠️{} {} 网络异常 (第 {}/{} 次): {}",
                        request.getMethod(), url, attempt, MAX_ATTEMPTS, e.getMessage());
            }
            if (attempt < MAX_ATTEMPTS) {
                sleep(1000L * attempt);
            }
        }
        TRANSPORT_ERRORS.incrementAndGet();
        log.error("💔{} {} 请求失败，已重试 {} 次", request.getMethod(), url, MAX_ATTEMPTS);
        return error("请求失败: " + url);
    }

    /**
     * 解析响应体。
     *
     * @param status HTTP 状态码
     * @param body   响应体
     * @param url    请求路径，仅用于日志
     * @return 解析结果；返回 null 表示这次响应不可用，可以重试
     */
    private static JSONObject parse(int status, String body, String url) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) {
            log.warn("⚠️{} 返回空响应 (HTTP {})", url, status);
            return null;
        }
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            // 触发风控或者被跳到登录页时会返回 HTML
            log.warn("⚠️{} 返回了非 JSON 内容 (HTTP {})，可能已触发风控", url, status);
            return null;
        }
        try {
            JSONObject json = JSON.parseObject(trimmed);
            return json == null ? null : json;
        } catch (JSONException e) {
            log.warn("⚠️{} 响应解析失败 (HTTP {}): {}", url, status, e.getMessage());
            return null;
        }
    }

    private static void logResult(String method, String url, JSONObject result) {
        int code = code(result);
        if (code == 0) {
            log.debug("✅{} {}", method, url);
            return;
        }
        String message = message(result);
        if (code == -412 || code == -352 || code == -509) {
            log.warn("⚠️{} {} 触发风控: {} - {}", method, url, code, message);
        } else {
            log.debug("ℹ️{} {} 返回: {} - {}", method, url, code, message);
        }
    }

    /**
     * 限速：保证最小间隔，并把每分钟的请求数压在阈值内。
     */
    private static void throttle() {
        long waitMs;
        synchronized (RECENT_REQUESTS) {
            long now = System.currentTimeMillis();
            while (!RECENT_REQUESTS.isEmpty() && now - RECENT_REQUESTS.peekFirst() > ONE_MINUTE_MS) {
                RECENT_REQUESTS.pollFirst();
            }
            long readyAt = lastRequestAt + MIN_INTERVAL_MS
                    + ThreadLocalRandom.current().nextLong(JITTER_MS);
            if (RECENT_REQUESTS.size() >= MAX_PER_MINUTE) {
                long windowReadyAt = RECENT_REQUESTS.peekFirst() + ONE_MINUTE_MS;
                readyAt = Math.max(readyAt, windowReadyAt);
                log.debug("⏰每分钟请求数已达上限，等待窗口滑动");
            }
            waitMs = Math.max(0L, readyAt - now);
            long scheduledAt = now + waitMs;
            lastRequestAt = scheduledAt;
            RECENT_REQUESTS.addLast(scheduledAt);
        }
        sleep(waitMs);
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
