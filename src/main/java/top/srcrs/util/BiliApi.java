package top.srcrs.util;

/**
 * B 站接口地址集中登记处。
 * <p>
 * 这个项目"失效"的主要原因就是 B 站陆续下线了一批老接口，而地址散落在各个任务类里，
 * 每次坏掉都要翻遍全项目。所有接口统一放在这里，以后哪个接口再变只需要改一行。
 * <p>
 * 接口含义与字段可参考社区维护的文档：
 * <a href="https://github.com/SocialSisterYi/bilibili-API-collect">bilibili-API-collect</a>
 *
 * @author srcrs
 * @Time 2026-07-26
 */
public final class BiliApi {

    private BiliApi() {
    }

    /* ------------------------------ 主站 / 账号 ------------------------------ */

    /** 导航栏信息，同时也是 WBI 密钥的来源 */
    public static final String NAV = "https://api.bilibili.com/x/web-interface/nav";
    /** 获取 buvid3 / buvid4，缺少这两个 Cookie 容易触发风控 */
    public static final String FINGER_SPI = "https://api.bilibili.com/x/frontend/finger/spi";
    /** 上报浏览器指纹，激活 buvid，不激活的话投币等敏感接口会回 -401 */
    public static final String EX_CLIMB_WUZHI =
            "https://api.bilibili.com/x/internal/gaia-gateway/ExClimbWuzhi";
    /** 生成 bili_ticket */
    public static final String GEN_WEB_TICKET =
            "https://api.bilibili.com/bapis/bilibili.api.ticket.v1.Ticket/GenWebTicket";
    /** 每日经验任务完成情况 */
    public static final String EXP_REWARD = "https://api.bilibili.com/x/member/web/exp/reward";
    /** 每日经验任务完成情况（旧接口，作为兜底） */
    public static final String LEGACY_HOME_REWARD = "https://account.bilibili.com/home/reward";
    /** 今日投币已获得的经验，专用接口，比从 exp/reward 里挑字段可靠 */
    public static final String COIN_TODAY_EXP = "https://api.bilibili.com/x/web-interface/coin/today/exp";

    /* ------------------------------ 视频 ------------------------------ */

    /** 综合热门视频 */
    public static final String POPULAR = "https://api.bilibili.com/x/web-interface/popular";
    /** 首页推荐视频（需要 WBI 签名） */
    public static final String RCMD_WBI = "https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd";
    /** 全站排行榜，不需要登录也不需要签名，是最不容易坏的一路视频来源 */
    public static final String RANKING_V2 = "https://api.bilibili.com/x/web-interface/ranking/v2";
    /** 视频详细信息 */
    public static final String VIEW = "https://api.bilibili.com/x/web-interface/view";
    /** 视频分 P 列表，用来拿 cid 和时长 */
    public static final String PAGELIST = "https://api.bilibili.com/x/player/pagelist";
    /** 上报播放进度（老接口，仍然有效） */
    public static final String HISTORY_REPORT = "https://api.bilibili.com/x/v2/history/report";
    /** 视频心跳，现在的"观看视频"经验主要由它发放 */
    public static final String CLICK_HEARTBEAT = "https://api.bilibili.com/x/click-interface/web/heartbeat";
    /** 分享视频 */
    public static final String SHARE_ADD = "https://api.bilibili.com/x/web-interface/share/add";
    /** 投币 */
    public static final String COIN_ADD = "https://api.bilibili.com/x/web-interface/coin/add";
    /** 查询自己给某视频投了几个币 */
    public static final String ARCHIVE_COINS = "https://api.bilibili.com/x/web-interface/archive/coins";

    /* ------------------------------ 用户空间 / 动态 ------------------------------ */

    /** UP 主投稿列表（需要 WBI 签名，老的 x/space/arc/search 已不可用） */
    public static final String SPACE_ARC_SEARCH_WBI = "https://api.bilibili.com/x/space/wbi/arc/search";
    /** 最近投币的视频 */
    public static final String SPACE_COIN_VIDEO = "https://api.bilibili.com/x/space/coin/video";
    /** 关注动态（替代已下线的 api.vc.bilibili.com/dynamic_svr） */
    public static final String DYNAMIC_FEED_ALL = "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all";

    /* ------------------------------ 大会员 ------------------------------ */

    /** 我的大会员权益列表 */
    public static final String VIP_PRIVILEGE_MY = "https://api.bilibili.com/x/vip/privilege/my";
    /** 领取大会员权益 */
    public static final String VIP_PRIVILEGE_RECEIVE = "https://api.bilibili.com/x/vip/privilege/receive";
    /** B 币券快速充电 */
    public static final String ELEC_PAY_QUICK = "https://api.bilibili.com/x/ugcpay/web/v2/trade/elec/pay/quick";
    /** 充电留言 */
    public static final String ELEC_MESSAGE = "https://api.bilibili.com/x/ugcpay/trade/elec/message";

    /* ------------------------------ 直播 ------------------------------ */

    /** 直播签到 */
    public static final String LIVE_SIGN = "https://api.live.bilibili.com/xlive/web-ucenter/v1/sign/DoSign";
    /** 直播个人信息（银瓜子余额） */
    public static final String LIVE_USER_INFO = "https://api.live.bilibili.com/xlive/web-ucenter/user/get_user_info";
    /** 银瓜子兑换硬币 */
    public static final String LIVE_SILVER2COIN = "https://api.live.bilibili.com/xlive/revenue/v1/wallet/silver2coin";
    /** 银瓜子兑换硬币（旧接口，作为兜底） */
    public static final String LIVE_SILVER2COIN_LEGACY = "https://api.live.bilibili.com/pay/v1/Exchange/silver2coin";
    /** 直播背包礼物列表 */
    public static final String LIVE_BAG_LIST = "https://api.live.bilibili.com/xlive/web-room/v1/gift/bag_list";
    /** 赠送背包礼物（替代已下线的 gift/v2/live/bag_send） */
    public static final String LIVE_BAG_SEND = "https://api.live.bilibili.com/xlive/revenue/v1/gift/sendBag";
    /** 分区直播间列表（替代已下线的 relation/v1/AppWeb/getRecommendList） */
    public static final String LIVE_ROOM_LIST = "https://api.live.bilibili.com/xlive/web-interface/v1/second/getList";
    /** 根据 uid 查直播间 */
    public static final String LIVE_ROOM_INFO_OLD = "https://api.live.bilibili.com/room/v1/Room/getRoomInfoOld";
    /** 根据房间号查直播间信息 */
    public static final String LIVE_ROOM_INFO = "https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom";
    /** 直播下单（B 币券兑换金瓜子） */
    public static final String LIVE_CREATE_ORDER = "https://api.live.bilibili.com/xlive/revenue/v1/order/createOrder";

    /* ------------------------------ 漫画 ------------------------------ */

    /** 漫画签到 */
    public static final String MANGA_CLOCK_IN = "https://manga.bilibili.com/twirp/activity.v1.Activity/ClockIn";

    /* ------------------------------ Referer ------------------------------ */

    public static final String REFERER_MAIN = "https://www.bilibili.com/";
    public static final String REFERER_LIVE = "https://live.bilibili.com/";
    public static final String REFERER_MANGA = "https://manga.bilibili.com/";
    public static final String REFERER_VIP = "https://account.bilibili.com/";
    /** 经验任务相关接口，web 端是从账号中心页发起的 */
    public static final String REFERER_ACCOUNT_HOME = "https://account.bilibili.com/account/home";
    /** 直播签到、银瓜子相关接口，web 端是从直播中心发起的 */
    public static final String REFERER_LIVE_LINK = "https://link.bilibili.com/";

    /**
     * 拼出视频播放页地址，投币/分享时作为 Referer 使用。
     *
     * @param bvid 视频 bvid，可为空
     * @param aid  视频 aid
     * @return 播放页地址
     */
    public static String videoPage(String bvid, String aid) {
        if (StringUtil.isNotBlank(bvid)) {
            return "https://www.bilibili.com/video/" + bvid;
        }
        return "https://www.bilibili.com/video/av" + aid;
    }
}
