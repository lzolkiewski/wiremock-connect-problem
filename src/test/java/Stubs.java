import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;

class Stubs {

    static StubMapping anyStub() {
        return get(urlMatching("/doesnt/matter"))
                .withHost(equalTo("any.host.com"))
                .willReturn(aResponse()
                    .withBody(
                        //language=json
                        """
                        {"anyField": "anyContent"}
                        """.trim()
                    )
                    .withStatus(200))
                .build();
    }
}
