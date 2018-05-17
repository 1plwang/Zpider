package com.zeroized.spider.controller;

import com.zeroized.spider.business.service.CrawlerPoolService;
import com.zeroized.spider.business.service.ElasticService;
import com.zeroized.spider.domain.CrawlerStatusInfo;
import com.zeroized.spider.domain.crawler.CrawlConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Zero on 2018/5/9.
 */
@RestController
@RequestMapping("/monitor")
@SessionAttributes("crawlerConfig")
public class MonitorController {

    private final CrawlerPoolService crawlerPoolService;

    private final ElasticService elasticService;

    @Autowired
    public MonitorController(CrawlerPoolService crawlerPoolService, ElasticService elasticService) {
        this.crawlerPoolService = crawlerPoolService;
        this.elasticService = elasticService;
    }

    @RequestMapping("/all")
    public MessageBean allCrawlers() {
        List<CrawlerStatusInfo> allInfo = crawlerPoolService.getAllCrawler();
        MessageBean messageBean=MessageBean.successBean();
        messageBean.getMessage().put("data",allInfo!=null?allInfo:new ArrayList<>());
        return messageBean;
    }

    @RequestMapping("/show/config")
    public MessageBean showConfig(@RequestParam String uuid) {
        CrawlConfig crawlConfig= crawlerPoolService.getConfig(uuid);
        MessageBean messageBean=MessageBean.successBean();
        messageBean.getMessage().put("data",crawlConfig);
        return messageBean;
    }

    @RequestMapping("/opt/start")
    public MessageBean start(@RequestParam String uuid){
        MessageBean messageBean;
        int statusCode=crawlerPoolService.startCrawler(uuid);
        if (statusCode==CrawlerPoolService.SUCCESS){
            messageBean= MessageBean.successBean();
        }else{
            messageBean= MessageBean.errorBean();
        }
        return messageBean;
    }

    @RequestMapping("/opt/revise")
    public ModelAndView revise(@RequestParam String uuid, ModelMap modelMap){
        if (modelMap.containsAttribute("crawlerConfig")){
            modelMap.remove("crawlerConfig");
        }
        modelMap.put("crawlerConfig",crawlerPoolService.getConfig(uuid));
        return new ModelAndView("redirect:/create");
    }

    @RequestMapping("/opt/stop")
    public MessageBean stop(@RequestParam String uuid){
        MessageBean messageBean;
        int statusCode=crawlerPoolService.stopCrawler(uuid);
        if (statusCode==CrawlerPoolService.SUCCESS){
            messageBean= MessageBean.successBean();
        }else{
            messageBean= MessageBean.errorBean();
        }
        return messageBean;
    }

    @RequestMapping("/opt/show")
    public MessageBean result(@RequestParam String uuid){
        MessageBean messageBean;
        try {
            List<Map<String,Object>> result= elasticService.search(uuid);
            messageBean=MessageBean.successBean();
            messageBean.getMessage().put("data",result);
        } catch (IOException e) {
            e.printStackTrace();
            messageBean=MessageBean.errorBean();
        }
        return messageBean;
    }
}
