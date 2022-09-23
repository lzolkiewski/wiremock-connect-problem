import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.JvmProxyConfigurer;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProblemExampleTest {

    private static final String ANY_BASE_URL = "http://any.host.com";

    private static final String ANY_PATH = "/doesnt/matter";

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort().enableBrowserProxying(true));

    private final HttpClient reactiveHttpClient = HttpClient.create().compress(true).proxyWithSystemProperties();

    private final org.apache.hc.client5.http.classic.HttpClient otherHttpClient = HttpClientBuilder.create().useSystemProperties().build();

    @BeforeAll
    static void setUp() {
        SERVER.start();
        WireMock.configureFor(SERVER.port());
        JvmProxyConfigurer.configureFor(SERVER);
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
}
