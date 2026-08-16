package top.srcrs.task.daily;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import top.srcrs.Task;
import top.srcrs.domain.Config;
import top.srcrs.domain.UserData;
import top.srcrs.domain.VideoInfo;
import top.srcrs.util.Account;
import top.srcrs.util.BiliApi;
import top.srcrs.util.DailyReward;
import top.srcrs.util.Request;
import top.srcrs.util.StringUtil;
import top.srcrs.util.VideoSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 每日投币。
 * <p>
 * 视频来源的优先级：自定义 UP 主的最新投稿 &gt; 关注动态里的视频 &gt; 首页推荐 / 综合热门。
 *
 * @author srcrs
 * @Time 2020-10-13
 */
@Slf4j
public class ThrowCoinTask implements Task {

    private static final UserData USER_DATA = UserData.getInstance();
    private final Config config = Config.getInstance();

    /** 每天最多 5 个币能换经验，再多投也不涨经验 */
    private static final int MAX_DAILY_COIN = 5;
    /** 每投一个币获得的经验 */
    private static final int EXP_PER_COIN = 10;

    @Override
    public void run() {
        int gainedExp = DailyReward.coinExp();
        if (gainedExp < 0) {
            log.warn("【投币】: 今日已投币数量未知，本次跳过❌");
            return;
        }
        int alreadyThrown = gainedExp / EXP_PER_COIN;

        updateMoney();
        int wallet = USER_DATA.getMoney() == null ? 0 : USER_DATA.getMoney().intValue();
        int configured = configuredCoin();
        int remaining = Math.max(configured - alreadyThrown, 0);
        int todo = Math.min(remaining, wallet);

        log.info("【投币计算】: 自定义投币数: {} ,今日已投币: {} ,还需投币: {} ,实际投币: {}",
                configured, alreadyThrown, remaining, todo);
        if (todo == 0) {
            log.info("【投币】: 当前无需执行投币操作❌");
            return;
        }

        List<VideoInfo> videos = collectVideos(todo);
        if (videos.isEmpty()) {
            log.warn("【投币】: 没有找到可投币的视频❌");
            return;
        }

        int success = 0;
        for (VideoInfo video : videos) {
            if (success >= todo) {
                break;
            }
            if (throwCoin(video)) {
                success++;
            }
            sleepBetweenCoins();
        }
        if (success < todo) {
            log.warn("【投币】: 计划投 {} 个币，实际成功 {} 个", todo, success);
        }
        if (success > 0) {
            // 投过币之后缓存里的完成情况就不准了
            DailyReward.invalidate();
        }
    }

    /**
     * 读取配置里的投币数并夹到合法区间。
     *
     * @return 每日计划投币数
     */
    private int configuredCoin() {
        Integer coin = config.getCoin();
        int value = coin == null ? 0 : coin;
        if (value < 0 || value > MAX_DAILY_COIN) {
            int clamped = Math.max(0, Math.min(value, MAX_DAILY_COIN));
            log.warn("⚠️配置的投币数 {} 超出 [0,{}]，按 {} 处理", value, MAX_DAILY_COIN, clamped);
            value = clamped;
        }
        return value;
    }

    /**
     * 刷新硬币余额，每日登录奖励的硬币是程序启动之后才到账的。
     */
    private void updateMoney() {
        Account.refresh();
    }

    /**
     * 按优先级收集候选视频，并逐个确认还没投过币。
     *
     * @param need 需要的数量
     * @return 可以投币的视频列表
     */
    private List<VideoInfo> collectVideos(int need) {
        Map<String, VideoInfo> picked = new LinkedHashMap<>();

        for (VideoInfo video : customUpVideos(need)) {
            collect(picked, video, need);
        }
        if (picked.size() < need) {
            for (VideoInfo video : VideoSource.following(need * 2)) {
                collect(picked, video, need);
            }
            log.info("【关注动态】: 累计取到 {} 个可投币视频", picked.size());
        }
        if (picked.size() < need) {
            for (VideoInfo video : VideoSource.candidates(need * 2)) {
                collect(picked, video, need);
            }
            log.info("【推荐/热门】: 累计取到 {} 个可投币视频", picked.size());
        }
        return new ArrayList<>(picked.values());
    }

    private void collect(Map<String, VideoInfo> picked, VideoInfo video, int need) {
        if (picked.size() >= need || video == null || picked.containsKey(video.getAid())) {
            return;
        }
        if (VideoSource.coinable(video)) {
            picked.put(video.getAid(), video);
        }
    }

    /**
     * 自定义 UP 主的投稿，优先照顾这个月还没投过币的 UP。
     *
     * @param need 需要的数量
     * @return 视频列表
     */
    private List<VideoInfo> customUpVideos(int need) {
        List<String> upList = config.getUpList();
        if (upList == null || upList.isEmpty()) {
            log.info("【优先投币up】: 未配置优先投币up主");
            return Collections.emptyList();
        }
        List<VideoInfo> videos = new ArrayList<>();
        for (String up : sortUpList(upList)) {
            if (videos.size() >= need) {
                break;
            }
            List<VideoInfo> found = VideoSource.space(up, need - videos.size());
            videos.addAll(found);
            log.info("【优先投币up {} 】: 取到 {} 个视频", up, found.size());
        }
        return videos;
    }

    /**
     * 把近期没投过币的 UP 主排到前面。
     *
     * @param upList 配置里的 UP 主列表
     * @return 排序后的列表
     */
    private List<String> sortUpList(List<String> upList) {
        List<String> recentlyCoined = recentlyCoinedUps();
        List<String> sorted = new ArrayList<>();
        for (String up : upList) {
            if (!recentlyCoined.contains(up)) {
                sorted.add(up);
            }
        }
        for (String up : upList) {
            if (recentlyCoined.contains(up)) {
                sorted.add(up);
            }
        }
        return sorted;
    }

    /**
     * 最近投过币的 UP 主 uid。
     *
     * @return uid 列表，接口不可用时返回空列表
     */
    private List<String> recentlyCoinedUps() {
        JSONObject params = new JSONObject();
        params.put("vmid", USER_DATA.getMid());
        JSONObject response = Request.get(BiliApi.SPACE_COIN_VIDEO, params);
        List<String> ups = new ArrayList<>();
        if (Request.code(response) != 0) {
            return ups;
        }
        JSONArray list = response.getJSONArray("data");
        if (list == null) {
            return ups;
        }
        for (int i = 0; i < list.size(); i++) {
            JSONObject item = list.getJSONObject(i);
            JSONObject owner = item == null ? null : item.getJSONObject("owner");
            String mid = owner == null ? null : owner.getString("mid");
            if (StringUtil.isNotBlank(mid) && !ups.contains(mid)) {
                ups.add(mid);
            }
        }
        return ups;
    }

    /**
     * 给一条视频投一个币。
     * <p>
     * 之前这里塞了 {@code eab_x}、{@code ramval}、{@code ga} 等接口并不认识的参数，还套了三层重试，
     * 每次重试前又拉一遍视频详情，请求量翻好几倍反而更容易触发风控。现在只发官方要求的参数，
     * 失败就如实报出来。
     * <p>
     * {@code csrf_token} 与 {@code csrf} 同值，网页端表单两个都带。
     *
     * @param video 视频信息
     * @return 是否投币成功
     */
    private boolean throwCoin(VideoInfo video) {
        JSONObject params = new JSONObject();
        params.put("aid", video.getAid());
        params.put("multiply", "1");
        params.put("select_like", selectLike());
        params.put("cross_domain", "true");
        params.put("csrf", USER_DATA.getBiliJct());
        params.put("csrf_token", USER_DATA.getBiliJct());

        JSONObject response = Request.post(BiliApi.COIN_ADD, params,
                BiliApi.videoPage(video.getBvid(), video.getAid()));
        int code = Request.code(response);
        if (code == 0) {
            log.info("【投币】: 给视频 - av{} - 硬币-1✔", video.getAid());
            return true;
        }
        log.warn("【投币】: 给视频 - av{} - {}({})❌", video.getAid(), response.getString("message"), code);
        return false;
    }

    /**
     * 投币时是否顺带点赞。
     *
     * @return "1" 点赞，"0" 不点赞
     */
    private String selectLike() {
        return "1".equals(StringUtil.trimToEmpty(config.getSelectLike())) ? "1" : "0";
    }

    private void sleepBetweenCoins() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1000, 2500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
