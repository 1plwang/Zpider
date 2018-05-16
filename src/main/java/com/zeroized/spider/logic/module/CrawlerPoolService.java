package com.zeroized.spider.logic.module;

import com.zeroized.spider.crawler.CrawlControllerFactory;
import com.zeroized.spider.crawler.CrawlControllerOptions;
import com.zeroized.spider.crawler.CrawlerFactory;
import com.zeroized.spider.crawler.CrawlerOptions;
import com.zeroized.spider.domain.*;
import com.zeroized.spider.logic.pool.CrawlerPool;
import com.zeroized.spider.logic.rx.CrawlerObservable;
import com.zeroized.spider.repo.mongo.CrawlerInfoRepo;
import edu.uci.ics.crawler4j.crawler.CrawlController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by Zero on 2018/5/7.
 */
@Service
public class CrawlerPoolService {
    public static final int SUCCESS = 1;
    public static final int ERROR = 0;

    private final CrawlerPool crawlerPool;

    private final CrawlerObservable crawlerObservable;

    private final CrawlControllerFactory crawlControllerFactory;

    private final CrawlerInfoRepo crawlerInfoRepo;

    @Autowired
    public CrawlerPoolService(CrawlerPool crawlerPool, CrawlerObservable crawlerObservable, CrawlControllerFactory crawlControllerFactory, CrawlerInfoRepo crawlerInfoRepo) {
        this.crawlerPool = crawlerPool;
        this.crawlerObservable = crawlerObservable;
        this.crawlControllerFactory = crawlControllerFactory;
        this.crawlerInfoRepo = crawlerInfoRepo;
    }

    @PostConstruct
    public void init() {
        initPool();
    }

    public String register(CrawlConfig crawlConfig){
        CrawlController controller = null;
        try {
            controller = configController(crawlConfig.getName(),
                    crawlConfig.getAdvancedOpt(), crawlConfig.getSeeds());
            CrawlerFactory crawlerFactory = configCrawler(crawlConfig.getAllowDomains(),
                    crawlConfig.getCrawlUrlPrefixes(), crawlConfig.getColumns(), crawlConfig.getName());
            CrawlerInfo info = crawlerPool.register(controller, crawlerFactory, crawlConfig);
            if (info != null) {
                crawlerInfoRepo.save(new CrawlerInfoEntity(info.getId(), info.getStatus(), info.getCrawlConfig()));
                return info.getId();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<CrawlerStatusInfo> getAllCrawler() {
        List<CrawlerInfoEntity> crawlers = crawlerInfoRepo.findAll();
        return crawlers.stream()
                .map(x -> {
                    String uuid = x.getId();
                    int status = x.getStatus();
                    return statusToStatusInfo(uuid, status);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public int startCrawler(String uuid) {
        int status = crawlerPool.changeStatus(uuid, CrawlerPool.START_CRAWLER);
        return updateToDatabase(status, uuid);
    }

    public int stopCrawler(String uuid) {
        int status = crawlerPool.changeStatus(uuid, CrawlerPool.STOP_CRAWLER);
        return updateToDatabase(status, uuid);
    }

    public void getCrawlerStatus(String uuid) {
        CrawlerInfo crawlerInfo = crawlerPool.get(uuid);
    }

    public CrawlConfig getConfig(String uuid) {
        return crawlerInfoRepo.findById(uuid).get().getCrawlConfig();
    }

    private void initPool() {
        List<CrawlerInfoEntity> crawlerInfoList = crawlerInfoRepo.findByStatus(CrawlerInfo.READY);
        crawlerInfoList.forEach(this::register);
    }

    private String register(CrawlerInfoEntity crawlerInfoEntity) {
        CrawlConfig crawlConfig = crawlerInfoEntity.getCrawlConfig();
        CrawlController controller = null;
        try {
            controller = configController(crawlConfig.getName(),
                    crawlConfig.getAdvancedOpt(), crawlConfig.getSeeds());
            CrawlerFactory crawlerFactory = configCrawler(crawlConfig.getAllowDomains(),
                    crawlConfig.getCrawlUrlPrefixes(), crawlConfig.getColumns(), crawlConfig.getName());
            CrawlerInfo info = crawlerPool.register(crawlerInfoEntity.getId(), crawlerInfoEntity.getStatus(),
                    controller, crawlerFactory, crawlConfig);
            if (info != null) {
                crawlerInfoRepo.save(new CrawlerInfoEntity(info.getId(), info.getStatus(), info.getCrawlConfig()));
                return info.getId();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private CrawlController configController(String name, CrawlAdvConfig advConfig, List<String> seeds) throws Exception {
        CrawlControllerOptions options = crawlControllerFactory.createOption();
        options.setWorkers(advConfig.getWorkers());
        options.setDelay(advConfig.getPoliteWait());
        options.setDepth(advConfig.getMaxDepth());
        options.setPage(advConfig.getMaxPage());
        options.setDir(name + "\\");

        CrawlController crawlController = crawlControllerFactory.newController(options);
        for (String seed : seeds) {
            crawlController.addSeed(seed);
        }
        return crawlController;
    }

    private CrawlerFactory configCrawler(List<String> allowDomains, List<String> crawlUrlPrefixes, List<Column> columns, String name) {
        CrawlerOptions crawlerOptions = new CrawlerOptions(allowDomains, crawlUrlPrefixes, columns, name);
        return new CrawlerFactory(crawlerOptions, crawlerObservable);
    }

    private static CrawlerStatusInfo statusToStatusInfo(String uuid, int status) {
        switch (status) {
            case CrawlerInfo.READY:
                return new CrawlerStatusInfo(uuid, "就绪", "启动,start;修改配置,revise;删除,delete");
            case CrawlerInfo.STARTED:
                return new CrawlerStatusInfo(uuid, "运行中", "停止,stop;查看已有结果,show");
            case CrawlerInfo.ERROR:
                return new CrawlerStatusInfo(uuid, "错误", "查看错误,show");
            case CrawlerInfo.FINISHED:
                return new CrawlerStatusInfo(uuid, "完成", "查看结果,show");
            case CrawlerInfo.PENDING_PROCESS:
                return new CrawlerStatusInfo(uuid, "待处理", "查看待处理的事件,show");
            case CrawlerInfo.STOPPED:
                return new CrawlerStatusInfo(uuid, "停止", "查看结果,show;继续,resume;重启,restart");
        }
        return null;
    }

    private int updateToDatabase(int status, String uuid) {
        if (status == CrawlerPool.SUCCESS) {
            CrawlerInfo crawlerInfo = crawlerPool.get(uuid);
            crawlerInfoRepo.save(new CrawlerInfoEntity(crawlerInfo.getId(),
                    crawlerInfo.getStatus(),
                    crawlerInfo.getCrawlConfig()));
        }
        return status;
    }
}
