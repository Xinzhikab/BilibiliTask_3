package top.srcrs.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 生成 gaia 风控指纹上报数据（ExClimbWuzhi）。
 * <p>
 * B 站 2024 年起，通过 {@code /x/frontend/finger/spi} 拿到的 buvid3/buvid4 是"未激活"状态，
 * 必须再带着这些 Cookie 调一次 {@code /x/internal/gaia-gateway/ExClimbWuzhi} 上报一份浏览器
 * 指纹，投币/点赞这类敏感写接口才会放行，否则一律回 {@code -401 非法访问}。
 * <p>
 * 字段名是混淆后的短 key，值参考自社区项目（Bili23-Downloader 等）抓包整理的固定模板，
 * 其中 UA、uuid 与本次运行使用的 Cookie 保持一致。
 *
 * @author srcrs
 * @Time 2026-08-16
 */
public final class FingerprintPayload {

    private FingerprintPayload() {
    }

    /**
     * 生成上报用的 JSON 请求体。
     *
     * @param userAgent 本次运行使用的 UserAgent
     * @param uuid      本地生成的 _uuid Cookie
     * @return 形如 {@code {"payload":"{...}"}} 的 JSON 字符串
     */
    public static String build(String userAgent, String uuid) {
        JSONObject payload = new JSONObject(new LinkedHashMap<>());
        payload.put("3064", 1);
        payload.put("5062", String.valueOf(System.currentTimeMillis()));
        payload.put("03bf", "https%3A%2F%2Fwww.bilibili.com%2F");
        payload.put("39c8", "333.1007.fp.risk");
        payload.put("34f1", "");
        payload.put("d402", "");
        payload.put("654a", "");
        payload.put("6e7c", "1699x794");
        payload.put("3c43", navigator(userAgent));
        payload.put("54ef", "{\"b_ut\":\"5\",\"home_version\":\"V8\",\"in_new_ab\":true,"
                + "\"ab_version\":{\"for_ai_home_version\":\"V8\",\"in_theme_version\":\"OPEN\","
                + "\"enable_web_push\":\"DISABLE\",\"enable_ai_floor_api\":\"ENABLE\","
                + "\"enable_shortcut_key\":\"DISABLE\",\"rcmd_timeout_config\":\"550\","
                + "\"home_performance_opt\":\"ssr_fetch_opt\",\"infra_projection\":\"OFF\"},"
                + "\"ab_split_num\":{\"for_ai_home_version\":54,\"in_theme_version\":30,"
                + "\"enable_web_push\":10,\"enable_ai_floor_api\":137,\"enable_shortcut_key\":54,"
                + "\"rcmd_timeout_config\":49,\"home_performance_opt\":49,\"infra_projection\":49},"
                + "\"uniq_page_id\":\"1479525693152\",\"is_modern\":true}");
        payload.put("8b94", "");
        payload.put("df35", uuid);
        payload.put("07a4", "zh-CN");
        payload.put("5f45", null);
        payload.put("db46", 0);

        JSONObject body = new JSONObject(new LinkedHashMap<>());
        body.put("payload", payload.toJSONString());
        return body.toJSONString();
    }

    /**
     * navigator 相关的指纹片段。
     *
     * @param userAgent UserAgent
     * @return 3c43 字段内容
     */
    private static JSONObject navigator(String userAgent) {
        JSONObject nav = new JSONObject(new LinkedHashMap<>());
        nav.put("2673", 0);
        nav.put("5766", 32);
        nav.put("6527", 0);
        nav.put("7003", 1);
        nav.put("807e", 1);
        nav.put("b8ce", userAgent);
        nav.put("641c", 0);
        nav.put("07a4", "zh-CN");
        nav.put("1c57", 32);
        nav.put("0bd0", 20);
        JSONArray screen = new JSONArray();
        screen.add(960);
        screen.add(1707);
        nav.put("748e", screen);
        JSONArray avail = new JSONArray();
        avail.add(912);
        avail.add(1707);
        nav.put("d61f", avail);
        nav.put("fc9d", -480);
        nav.put("6aa9", "Asia/Shanghai");
        nav.put("75b8", 1);
        nav.put("3b21", 1);
        nav.put("8a1c", 0);
        nav.put("d52f", "not available");
        nav.put("adca", "Win32");
        nav.put("80c9", pdfPlugins());
        nav.put("13ab", "EPQAAAAASUVORK5CYII=");
        nav.put("bfe9", "//TgNIfAAAAAZJREFUAwBde+3wgcxEHQAAAABJRU5ErkJggg==");
        nav.put("a3c1", webglInfos());
        nav.put("6bc5", "Google Inc. (NVIDIA)~ANGLE (NVIDIA, NVIDIA GeForce RTX 4060 Laptop GPU "
                + "(0x000028E0) Direct3D11 vs_5_0 ps_5_0, D3D11)");
        nav.put("ed31", 0);
        nav.put("72bd", 0);
        nav.put("097b", 0);
        JSONArray touch = new JSONArray();
        touch.add(0);
        touch.add(0);
        touch.add(0);
        nav.put("52cd", touch);
        nav.put("a658", fonts());
        nav.put("d02f", "124.04347527516074");
        return nav;
    }

    /**
     * 浏览器 PDF 插件列表。
     *
     * @return 80c9 字段内容
     */
    private static JSONArray pdfPlugins() {
        String[] names = {
                "PDF Viewer", "Chrome PDF Viewer", "Chromium PDF Viewer",
                "Microsoft Edge PDF Viewer", "WebKit built-in PDF"
        };
        JSONArray plugins = new JSONArray();
        for (String name : names) {
            JSONArray plugin = new JSONArray();
            plugin.add(name);
            plugin.add("Portable Document Format");
            JSONArray types = new JSONArray();
            JSONArray pdf = new JSONArray();
            pdf.add("application/pdf");
            pdf.add("pdf");
            types.add(pdf);
            JSONArray textPdf = new JSONArray();
            textPdf.add("text/pdf");
            textPdf.add("pdf");
            types.add(textPdf);
            plugin.add(types);
            plugins.add(plugin);
        }
        return plugins;
    }

    /**
     * WebGL 指纹信息，取一组常见显卡的稳定值。
     *
     * @return a3c1 字段内容
     */
    private static JSONArray webglInfos() {
        JSONArray infos = new JSONArray();
        infos.add("extensions:ANGLE_instanced_arrays;EXT_blend_minmax;EXT_clip_control;"
                + "EXT_color_buffer_half_float;EXT_depth_clamp;EXT_disjoint_timer_query;EXT_float_blend;"
                + "EXT_frag_depth;EXT_polygon_offset_clamp;EXT_shader_texture_lod;"
                + "EXT_texture_compression_bptc;EXT_texture_compression_rgtc;"
                + "EXT_texture_filter_anisotropic;EXT_texture_mirror_clamp_to_edge;EXT_sRGB;"
                + "KHR_parallel_shader_compile;OES_element_index_uint;OES_fbo_render_mipmap;"
                + "OES_standard_derivatives;OES_texture_float;OES_texture_float_linear;"
                + "OES_texture_half_float;OES_texture_half_float_linear;OES_vertex_array_object;"
                + "WEBGL_blend_func_extended;WEBGL_color_buffer_float;"
                + "WEBGL_compressed_texture_s3tc;WEBGL_compressed_texture_s3tc_srgb;"
                + "WEBGL_debug_renderer_info;WEBGL_debug_shaders;WEBGL_depth_texture;"
                + "WEBGL_draw_buffers;WEBGL_lose_context;WEBGL_multi_draw;WEBGL_polygon_mode");
        infos.add("webgl aliased line width range:[1, 1]");
        infos.add("webgl aliased point size range:[1, 1024]");
        infos.add("webgl alpha bits:8");
        infos.add("webgl antialiasing:yes");
        infos.add("webgl blue bits:8");
        infos.add("webgl depth bits:24");
        infos.add("webgl green bits:8");
        infos.add("webgl max anisotropy:16");
        infos.add("webgl max combined texture image units:32");
        infos.add("webgl max cube map texture size:16384");
        infos.add("webgl max fragment uniform vectors:1024");
        infos.add("webgl max render buffer size:16384");
        infos.add("webgl max texture image units:16");
        infos.add("webgl max texture size:16384");
        infos.add("webgl max varying vectors:30");
        infos.add("webgl max vertex attribs:16");
        infos.add("webgl max vertex texture image units:16");
        infos.add("webgl max vertex uniform vectors:4095");
        infos.add("webgl max viewport dims:[32767, 32767]");
        infos.add("webgl red bits:8");
        infos.add("webgl renderer:WebKit WebGL");
        infos.add("webgl shading language version:WebGL GLSL ES 1.0 "
                + "(OpenGL ES GLSL ES 1.0 Chromium)");
        infos.add("webgl stencil bits:0");
        infos.add("webgl vendor:WebKit");
        infos.add("webgl version:WebGL 1.0 (OpenGL ES 2.0 Chromium)");
        infos.add("webgl unmasked vendor:Google Inc. (NVIDIA)");
        infos.add("webgl unmasked renderer:ANGLE (NVIDIA, NVIDIA GeForce RTX 4060 Laptop GPU "
                + "(0x000028E0) Direct3D11 vs_5_0 ps_5_0, D3D11)");
        String[][] precisions = {
                {"vertex shader high float", "23"},
                {"vertex shader high float rangeMin", "127"},
                {"vertex shader high float rangeMax", "127"},
                {"vertex shader medium float", "23"},
                {"vertex shader medium float rangeMin", "127"},
                {"vertex shader medium float rangeMax", "127"},
                {"vertex shader low float", "23"},
                {"vertex shader low float rangeMin", "127"},
                {"vertex shader low float rangeMax", "127"},
                {"fragment shader high float", "23"},
                {"fragment shader high float rangeMin", "127"},
                {"fragment shader high float rangeMax", "127"},
                {"fragment shader medium float", "23"},
                {"fragment shader medium float rangeMin", "127"},
                {"fragment shader medium float rangeMax", "127"},
                {"fragment shader low float", "23"},
                {"fragment shader low float rangeMin", "127"},
                {"fragment shader low float rangeMax", "127"},
                {"vertex shader high int", "0"},
                {"vertex shader high int rangeMin", "31"},
                {"vertex shader high int rangeMax", "30"},
                {"vertex shader medium int", "0"},
                {"vertex shader medium int rangeMin", "31"},
                {"vertex shader medium int rangeMax", "30"},
                {"vertex shader low int", "0"},
                {"vertex shader low int rangeMin", "31"},
                {"vertex shader low int rangeMax", "30"},
                {"fragment shader high int", "0"},
                {"fragment shader high int rangeMin", "31"},
                {"fragment shader high int rangeMax", "30"},
                {"fragment shader medium int", "0"},
                {"fragment shader medium int rangeMin", "31"},
                {"fragment shader medium int rangeMax", "30"},
                {"fragment shader low int", "0"},
                {"fragment shader low int rangeMin", "31"},
                {"fragment shader low int rangeMax", "30"}
        };
        for (String[] p : precisions) {
            infos.add("webgl " + p[0] + ":" + p[1]);
        }
        return infos;
    }

    /**
     * 系统字体列表。
     *
     * @return a658 字段内容
     */
    private static JSONArray fonts() {
        String[] fontNames = {
                "Arial", "Arial Black", "Arial Narrow", "Book Antiqua", "Bookman Old Style",
                "Calibri", "Cambria", "Cambria Math", "Century", "Century Gothic",
                "Century Schoolbook", "Comic Sans MS", "Consolas", "Courier", "Courier New",
                "Georgia", "Helvetica", "Impact", "Lucida Bright", "Lucida Calligraphy",
                "Lucida Console", "Lucida Fax", "Lucida Handwriting", "Lucida Sans",
                "Lucida Sans Typewriter", "Lucida Sans Unicode", "Microsoft Sans Serif",
                "Monotype Corsiva", "MS Gothic", "MS PGothic", "MS Reference Sans Serif",
                "MS Sans Serif", "MS Serif", "Palatino Linotype", "Segoe Print", "Segoe Script",
                "Segoe UI", "Segoe UI Light", "Segoe UI Semibold", "Segoe UI Symbol", "Tahoma",
                "Times", "Times New Roman", "Trebuchet MS", "Verdana", "Wingdings",
                "Wingdings 2", "Wingdings 3"
        };
        JSONArray list = new JSONArray();
        for (String name : fontNames) {
            list.add(name);
        }
        return list;
    }

    /**
     * 生成本地配套 Cookie：形如 {@code 5B381058-15A4-BD2D-28CB-7E1FDA12EABB94513infoc} 的 _uuid。
     *
     * @return uuid
     */
    public static String randomUuid() {
        String chars = "123456789ABCDEF10";
        int[] lengths = {8, 4, 4, 4, 12};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lengths.length; i++) {
            if (i > 0) {
                sb.append('-');
            }
            for (int j = 0; j < lengths[i]; j++) {
                sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
            }
        }
        sb.append(String.valueOf(System.currentTimeMillis() % 100000));
        sb.append("infoc");
        return sb.toString();
    }

    /**
     * 生成本地配套 Cookie：b_lsid。
     *
     * @return 形如 {@code E79E0DE5_1A00A0C320E} 的标识
     */
    public static String randomBLsid() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(Character.toUpperCase(Character.forDigit(
                    ThreadLocalRandom.current().nextInt(16), 16)));
        }
        return sb.toString() + "_"
                + Long.toHexString(System.currentTimeMillis() / 1000).toUpperCase();
    }

    /**
     * 生成本地配套 Cookie：buvid_fp，32 位十六进制。
     *
     * @return 设备指纹
     */
    public static String randomBuvidFp() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
