import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.http.JvmProxyConfigurer;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.*;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;
import reactor.netty.transport.ProxyProvider;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProblemExampleTest {

    private static final String ANY_BASE_URL = "https://any.host.com";

    private static final String ANY_PATH = "/doesnt/matter";

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig()
            .dynamicPort()
            .notifier(new ConsoleNotifier(true))
            .enableBrowserProxying(true));

    private HttpClient reactiveHttpClient;

    private final org.apache.hc.client5.http.classic.HttpClient otherHttpClient = HttpClientBuilder.create().useSystemProperties().build();

    @BeforeAll
    static void setUp() {
        SERVER.start();
        WireMock.configureFor(SERVER.port());
        JvmProxyConfigurer.configureFor(SERVER);
    }

    @BeforeEach
    void setupEach() throws Exception {
        final SslContext sslContext = buildTrustAllSslContext();
        reactiveHttpClient = HttpClient.create()
                .secure(ssl ->
                        ssl.sslContext(sslContext)
                )
                .compress(true)
                .proxy(typeSpec -> typeSpec.type(ProxyProvider.Proxy.HTTP)
                        .address(InetSocketAddress.createUnresolved("localhost", SERVER.port())));
    }

    @AfterAll
    static void tearDown() {
        SERVER.stop();
        SERVER.shutdown();
        JvmProxyConfigurer.restorePrevious();
    }

    @AfterEach
    void afterEach() {
        SERVER.resetRequests();
        SERVER.resetMappings();
    }

    @Test
    @DisplayName("does not fail when using reactive client")
    void doesNotFailWhenUsingReactiveClient() {
        //given
        SERVER.addStubMapping(Stubs.anyStub());

        //when
        var throwable = catchThrowable(() -> reactiveHttpClient.baseUrl(ANY_BASE_URL)
                .get()
                .uri(ANY_PATH)
                .response()
                .block()
        );

        //then
        assertNull(throwable);
    }

    private <T> Throwable catchThrowable(Supplier<T> supplier) {
        try {
            supplier.get();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    @Test
    @DisplayName("goes through proxy while using other http client")
    void goesThroughProxyWhileUsingOtherHttpClient() throws IOException {
        //given
        SERVER.addStubMapping(Stubs.anyStub());
        var get = new HttpGet(ANY_BASE_URL + ANY_PATH);

        //when
        var response = otherHttpClient.execute(get, responseHandler());

        //then

        assertEquals(
                //language=json
                """
                {"anyField": "anyContent"}
                """.trim(),
                response
        );
    }

    private static HttpClientResponseHandler<String> responseHandler() {
        return response -> {
            final HttpEntity entity = response.getEntity();
            try {
                return entity != null ? EntityUtils.toString(entity) : null;
            } catch (final ParseException ex) {
                throw new ClientProtocolException(ex);
            }
        };
    }

    private static SslContext buildTrustAllSslContext() throws Exception {
        TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType)  {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType)  {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };
        return SslContextBuilder.forClient().trustManager(tm).build();
    }
}
