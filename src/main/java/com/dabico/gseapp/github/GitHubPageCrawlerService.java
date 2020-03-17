package com.dabico.gseapp.github;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;

import static java.lang.Long.parseLong;
import static com.dabico.gseapp.util.DateUtils.fromGitDateString;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class GitHubPageCrawlerService {
    static final Logger logger = LoggerFactory.getLogger(GitHubPageCrawlerService.class);

    final String repoURL;
    long commits = 0;
    long branches = 0;
    long releases = 0;
    long contributors = 0;
    long watchers = 0;
    long stars = 0;
    long totalIssues = 0;
    long openIssues = 0;
    long totalPullRequests = 0;
    long openPullRequests = 0;
    Date lastCommit = null;
    String lastCommitSHA = null;

    public void mine() throws IOException {
        logger.info("Mining data for: " + repoURL);
        mineProjectPage();
        mineIssuesPage();
        minePullsPage();
        mineCommitsPage();
    }

    private void mineProjectPage() throws IOException {
        Document document   = Jsoup.connect(repoURL).get();
        Elements summary_ul = document.getElementsByClass("numbers-summary");
        commits  = parseLong(normalizeNumberString(summary_ul.get(0).childNode(1).childNode(1).childNode(3).childNode(0).toString()));
        branches = parseLong(normalizeNumberString(summary_ul.get(0).childNode(3).childNode(1).childNode(3).childNode(0).toString()));
        releases = parseLong(normalizeNumberString(summary_ul.get(0).childNode(7).childNode(1).childNode(3).childNode(0).toString()));
        Node contributors_a = summary_ul.get(0).childNode(9).childNode(1);
        if (contributors_a.childNodeSize() < 4){
            contributors = getContributorsWithSelenium(repoURL);
        } else {
            contributors = parseLong(normalizeNumberString(contributors_a.childNode(3).childNode(0).toString()));
        }
        Elements pagehead_ul = document.getElementsByClass("pagehead-actions flex-shrink-0 ");
        watchers = parseLong(normalizeNumberString(pagehead_ul.get(0).childNode(3).childNode(3).attr("aria-label").split(" ")[0]));
        stars    = parseLong(normalizeNumberString(pagehead_ul.get(0).childNode(5).childNode(3).attr("aria-label").split(" ")[0]));
    }

    private void mineIssuesPage() throws IOException {
        Document document  = Jsoup.connect(repoURL + "/issues").get();
        Elements table_div = document.getElementsByClass("table-list-header-toggle states flex-auto pl-0");
        long open   = parseLong(normalizeNumberString(table_div.get(0).childNode(1).childNode(2).toString().trim().split(" ")[0]));
        long closed = parseLong(normalizeNumberString(table_div.get(0).childNode(3).childNode(2).toString().trim().split(" ")[0]));
        openIssues  = open;
        totalIssues = open + closed;
    }

    private void minePullsPage() throws IOException {
        Document document  = Jsoup.connect(repoURL + "/pulls").get();
        Elements table_div = document.getElementsByClass("table-list-header-toggle states flex-auto pl-0");
        long open   = parseLong(normalizeNumberString(table_div.get(0).childNode(1).childNode(2).toString().trim().split(" ")[0]));
        long closed = parseLong(normalizeNumberString(table_div.get(0).childNode(3).childNode(2).toString().trim().split(" ")[0]));
        openPullRequests  = open;
        totalPullRequests = open + closed;
    }

    private void mineCommitsPage() throws IOException {
        Document document = Jsoup.connect(repoURL + "/commits").get();
        Elements button_a = document.getElementsByClass("sha btn btn-outline BtnGroup-item");
        String link = button_a.get(0).attr("href");
        document = Jsoup.connect(Endpoints.DEFAULT.getUrl()+"/"+link).get();
        String date_time = document.getElementsByTag("relative-time").attr("datetime");
        lastCommit = fromGitDateString(date_time);
        lastCommitSHA = document.getElementsByClass("sha user-select-contain").get(0).childNode(0).toString();
    }

    private long getContributorsWithSelenium(String repoURL) {
        ClassLoader classLoader = getClass().getClassLoader();
        URL url = classLoader.getResource("chromedriver");
        String driverFile = url.getFile();
        System.setProperty("webdriver.chrome.driver", driverFile);
        System.setProperty("webdriver.chrome.silentOutput", "true");
        DesiredCapabilities capabilities = new DesiredCapabilities();
        ChromeDriverService service = new ChromeDriverService.Builder()
                .usingDriverExecutable(new File(driverFile))
                .build();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--no-sandbox");
        options.addArguments("--headless");
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("start-maximized");
        options.addArguments("disable-infobars");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-dev-shm-usage");
        capabilities.setCapability(ChromeOptions.CAPABILITY,options);
        ChromeDriver driver = new ChromeDriver(service,options);

        try {
            driver.get(repoURL);
        } catch (UnreachableBrowserException ube){
            ube.printStackTrace();
            driver.close();
            driver.quit();
            return -1L; //RETURN -1 TO INDICATE COMPLETE FAILURE
        }

        WebDriverWait wait = new WebDriverWait(driver,5);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#js-repo-pjax-container > div.container-lg.clearfix.new-discussion-timeline.px-3 > div > div.overall-summary.border-bottom-0.mb-0.rounded-bottom-0 > ul > li:nth-child(5) > a > span")));
        String contributors_str = driver.findElementByCssSelector("#js-repo-pjax-container > div.container-lg.clearfix.new-discussion-timeline.px-3 > div > div.overall-summary.border-bottom-0.mb-0.rounded-bottom-0 > ul > li:nth-child(5) > a > span").getText();
        driver.close();
        driver.quit();
        return Long.parseLong(normalizeNumberString(contributors_str));
    }

    private String normalizeNumberString(String input){ return input.trim().replaceAll(",",""); }
}
