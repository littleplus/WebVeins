package com.xiongbeer.webveins.example.webmagic;

import com.xiongbeer.webveins.service.local.Action;
import com.xiongbeer.webveins.service.local.Bootstrap;
import com.xiongbeer.webveins.utils.InitLogger;
import com.xiongbeer.webveins.utils.UrlFileLoader;

import io.netty.util.internal.ConcurrentSet;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 爬虫客户端
 * 在启动了worker服务的情况下运行此爬虫就会自动的开始任务了
 * 这里就简单的爬取wiki百科里的链接
 *
 * Created by shaoxiong on 17-5-9.
 */
public class Crawler extends Action implements PageProcessor {
    private Site site = Site.me().setRetryTimes(3)
            .setSleepTime(1000).setUseGzip(true)
            .setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.87 Safari/537.36");
    private static Set<String> newUrls = new ConcurrentSet<String>();
    private static Spider spider = Spider.create(new Crawler()).thread(3);


    /* 每当worker领取到任务以后就会自动的运行这个函数，可以视为一个异步的callback */
    @Override
    public boolean run(String urlFilePath) {
    	try {
    	    /* worker领取到的url存放在一个本地文件夹中，这里提供了一个UrlFileLoader来把其中的url读到内存中 */
			List<String> urlsList = new UrlFileLoader().readFileByLine(urlFilePath);
			/* 读取的url存在list中，读取出来放入爬虫的爬取队列中 */
			for(String url:urlsList){
				spider.addUrl(url);
			}
			spider.run();
            /* 任务执行完毕，上传新的url，为了节省内存你可以选择清空newurls，但也可以选择不清空以此来减轻manager的去重负担，这里选择了保留 */
            Bootstrap.upLoadNewUrls(newUrls);
            return true;
    	} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		/* 如果返回false就会被视为放弃了任务，该任务就会被manager重置，然后等待被重新领取 */
		return false;
    }

    @Override
    public Site getSite() {
        return site;
    }

    @Override
    public void process(Page page) {
        String html = page.getHtml().toString();
        String selfUrl = page.getUrl().toString();
        Pattern pattern = Pattern.compile("(?<=<a href=\")(?!" + selfUrl
                + ")https://en.wikipedia.org/wiki/.*?(?=\")");
        Matcher matcher = pattern.matcher(html);
        while(matcher.find()){
            /* 因为只是做一个简单的测试，太多了任务数量太多要爬太久，所以这里获取到的url超过1000就不继续爬了 */
            if(newUrls.size() <= 1000) {
                newUrls.add(matcher.group());
            }
            else{
              return;
            }
        }
    }

    public static void main(String[] args){
        InitLogger.init();
        Crawler crawler = new Crawler();
        /* 将爬虫实例传给引导类 */
        Bootstrap bootstrap = new Bootstrap(crawler);
        bootstrap.runClient();
        try {
        	/* 短暂的等待，等待Client与Server建立长连接 */
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            /* 连接建立，告诉Server爬虫已经准备好啦！ */
			bootstrap.ready();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
}
