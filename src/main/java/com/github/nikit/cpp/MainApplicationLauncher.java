package com.github.nikit.cpp;


import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.cookie.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.cookie.BrowserCompatSpec;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by nik on 16.04.16.
 */
public class MainApplicationLauncher {
    public static void main(String... args) throws URISyntaxException, IOException, InterruptedException {
        String email = args[0];
        String password = args[1];
        String applicationId = args[2]; // aka clientId

        CookieSpecProvider easySpecProvider = new CookieSpecProvider() {

            public CookieSpec create(HttpContext context) {
                return new BrowserCompatSpec() {
                    @Override
                    public void validate(Cookie cookie, CookieOrigin origin) throws MalformedCookieException {
                        // Oh, I am easy
                        System.out.println("ignoring cookie validation...");
                    }
                };
            }

        };

        Lookup<CookieSpecProvider> cookieSpecRegistry = RegistryBuilder.<CookieSpecProvider>create()
                .register(CookieSpecs.DEFAULT, easySpecProvider)
                //.register(CookieSpecs.STANDARD, easySpecProvider)
                //.register(CookieSpecs.STANDARD_STRICT, easySpecProvider)
                .build();

        // TODO set timeouts
        try(CloseableHttpClient httpClient = HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy())
                .setUserAgent("Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:45.0) Gecko/20100101 Firefox/45.0")
                .setDefaultCookieSpecRegistry(cookieSpecRegistry)
                .build();) {


            URIBuilder builder = new URIBuilder();
            builder.setScheme("https").setHost("oauth.vk.com").setPath("/authorize")
                    .setParameter("client_id", applicationId)
                    .setParameter("display", "page")
                    .setParameter("redirect_uri", "https://oauth.vk.com/blank.html")
                    .setParameter("scope", "email")
                    .setParameter("response_type", "token")
                    .setParameter("v", "5.50");


            URI url = builder.build();
            HttpGet httpGet = new HttpGet(url);
            try(CloseableHttpResponse httpResponse = httpClient.execute(httpGet);) {
                String response = EntityUtils.toString(httpResponse.getEntity(), StandardCharsets.UTF_8);
                System.out.println(response);

                System.out.println("---------------------------------------------");

                Document doc = Jsoup.parse(response);
                // http://jsoup.org/cookbook/extracting-data/selector-syntax
                Elements inputs = doc.select("input");
                Iterator<Element> iterator = inputs.iterator();

                List<NameValuePair> pairs = new ArrayList<>();
                while (iterator.hasNext()){
                    Element el = iterator.next();
                    System.out.println(el);
                    String name = el.attr("name");
                    String value = el.attr("value");

                    // исключаем на всякий случай
                    if("email".equals(name)){
                        continue;
                    }
                    if("pass".equals(name)){
                        continue;
                    }
                    if("submit_input".equals(name)){
                        continue;
                    }
                    pairs.add(new BasicNameValuePair(name, value));
                }
                System.out.println("==============================================");

                HttpPost httpPost = new HttpPost("https://login.vk.com/?act=login&soft=1&utf8=1");


                pairs.add(new BasicNameValuePair("email", email));
                pairs.add(new BasicNameValuePair("pass", password));
                HttpEntity httpEntity = EntityBuilder.create().setParameters(pairs).build();
                httpPost.setEntity(httpEntity);
                // Эти 3 хедера не являются обязательными, но я их передаю чтобы не вызывать подозрений =)
                httpPost.setHeader("Referer", url.toString());
                httpPost.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                httpPost.setHeader("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3");

                HttpContext context = new BasicHttpContext();
                try(CloseableHttpResponse httpResponse2 = httpClient.execute(httpPost, context);) {
                    String response2 = EntityUtils.toString(httpResponse2.getEntity(), "windows-1251");
                    System.out.println(response2);

                    // http://stackoverflow.com/questions/1456987/httpclient-4-how-to-capture-last-redirect-url/1457173#1457173
                    HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
                    HttpHost currentHost = (HttpHost)  context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
                    String currentUrl = (currentReq.getURI().isAbsolute()) ? currentReq.getURI().toString() : (currentHost.toURI() + currentReq.getURI());
                    System.out.println(currentUrl);

                    String originalUriWithFragment = ((HttpRequestWrapper) currentReq).getOriginal().getRequestLine().getUri();
                    System.out.println(originalUriWithFragment);

                    URL urlWithAccessToken = new URL(originalUriWithFragment);
                    String fragment = urlWithAccessToken.toURI().getFragment();
                    System.out.println("fragment=" + fragment);
                    String accessToken = getFromFragment(fragment, "access_token");
                    System.out.println(accessToken);

                    String userId = getFromFragment(fragment, "user_id");
                    System.out.println(userId);
                }
            }

        }
    }

    public static String getFromFragment(String fragment, String name) {
        int lastAmpersand = fragment.indexOf("&");
        int startPos = fragment.indexOf(name+"=");
        return fragment.substring(startPos, (lastAmpersand != -1 && lastAmpersand>startPos) ? lastAmpersand : fragment.length());
    }
}
