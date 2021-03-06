package com.cn.web.wx;

import com.alibaba.fastjson.JSON;
import com.cn.beans.common.ResultBean;
import com.cn.beans.wx.WXConfig;
import com.cn.beans.wx.WXTicket;
import com.cn.service.token.AccessTokenService;
import com.cn.service.utils.HttpUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 微信配置信息获取
 */
@RestController
@RequestMapping("/config")
public class WXConfigController {

    private static final Logger LOGGER = Logger.getLogger(WXConfigController.class);
    private static final String TICKET_REDIS_KEY = "CACHE:TICKET";
    private static final long TICKET_REDIS_EXPIRE = 7200;

    @Value("${wx.app.id}")
    private String appId;

    @Value("${wx.url.ticket}")
    private String ticketUrl;

    final RedisTemplate<String, Object> redisTemplate;

    final AccessTokenService accessTokenService;

    public WXConfigController(RedisTemplate<String, Object> redisTemplate, AccessTokenService accessTokenService) {
        this.redisTemplate = redisTemplate;
        this.accessTokenService = accessTokenService;
    }

    @GetMapping("/getWXConfig")
    public ResultBean getWXConfig(String url) {
        ResultBean resultBean = new ResultBean();
        String wxTicket = getWXTicket();
        if (StringUtils.isBlank(wxTicket)) {
            resultBean.setRtnCode(ResultBean.FAIL_CODE);
            resultBean.setRtnMsg(ResultBean.FAIL_MSG);
            return resultBean;
        }
        long timestamp = System.currentTimeMillis();
        String nonceStr = UUID.randomUUID().toString();
        String sha = "jsapi_ticket=" + wxTicket + "&noncestr=" + nonceStr + "&timestamp=" + timestamp + "&url=" + url;
        String signature = DigestUtils.sha1Hex(sha);
        WXConfig wxConfig = new WXConfig(appId, String.valueOf(timestamp), nonceStr, signature);
        resultBean.setResult(wxConfig);
        return resultBean;
    }

    @GetMapping("/getAppId")
    public ResultBean getAppId() {
        ResultBean resultBean = new ResultBean();
        resultBean.setResult(appId);
        return resultBean;
    }

    /**
     * 获取签名校验临时票据
     *
     * @return 临时票据
     */
    private String getWXTicket() {
        Object ticketCache = redisTemplate.opsForValue().get(TICKET_REDIS_KEY);
        if (ticketCache != null) {
            return (String) ticketCache;
        }
        return initWXTicket();
    }

    /**
     * 初始化临时票据
     *
     * @return 临时票据
     */
    private String initWXTicket() {
        String token = accessTokenService.getAccessToken();
        if (StringUtils.isEmpty(token)) {
            return "";
        }
        List<NameValuePair> list = new ArrayList<>();
        list.add(new BasicNameValuePair("type", "jsapi"));
        list.add(new BasicNameValuePair("access_token", token));
        String ticketStr = HttpUtils.httpGet(ticketUrl, list);
        if (StringUtils.isBlank(ticketStr)) {
            LOGGER.error("initWXTicket=>获取临时票据失败:" + ticketStr);
            return "";
        }
        WXTicket wxTicket = JSON.parseObject(ticketStr, WXTicket.class);
        String ticket = wxTicket.getTicket();
        if (StringUtils.isBlank(ticket)) {
            LOGGER.error("initWXTicket=>获取临时票据失败:" + ticketStr);
            return "";
        }
        redisTemplate.opsForValue().set(TICKET_REDIS_KEY, ticket, TICKET_REDIS_EXPIRE);
        return ticket;
    }
}
