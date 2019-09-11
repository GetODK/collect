package org.odk.collect.android.http;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.odk.collect.android.http.openrosa.OpenRosaServerClient;
import org.odk.collect.android.http.openrosa.OpenRosaServerClientProvider;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.internal.TlsUtil;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

public abstract class OpenRosaServerClientProviderTest {

    protected abstract OpenRosaServerClientProvider buildSubject();

    private final List<MockWebServer> mockWebServers = new ArrayList<>();

    private OpenRosaServerClientProvider subject;

    @Before
    public void setup() {

        subject = buildSubject();
    }

    @After
    public void teardown() throws Exception {
        for (MockWebServer mockWebServer : mockWebServers) {
            mockWebServer.shutdown();
        }
    }

    @Test
    public void sendsOpenRosaHeaders() throws Exception {
        MockWebServer mockWebServer = startMockWebServer();
        enqueueSuccess(mockWebServer);

        OpenRosaServerClient client = subject.get("http", "Android", null);
        client.makeRequest(buildRequest(mockWebServer, ""), new Date());

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("X-OpenRosa-Version"), equalTo("1.0"));
    }

    @Test
    public void sendsDateHeader() throws Exception {
        MockWebServer mockWebServer = startMockWebServer();
        enqueueSuccess(mockWebServer);

        Date currentTime = new Date();

        OpenRosaServerClient client = subject.get("http", "Android", null);
        client.makeRequest(buildRequest(mockWebServer, ""), currentTime);

        RecordedRequest request = mockWebServer.takeRequest();

        SimpleDateFormat dateFormatGmt = new SimpleDateFormat("E, dd MMM yyyy hh:mm:ss zz", Locale.US);
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        assertThat(request.getHeader("Date"), equalTo(dateFormatGmt.format(currentTime)));
    }

    @Test
    public void sendsAcceptsGzipHeader() throws Exception {
        MockWebServer mockWebServer = startMockWebServer();
        enqueueSuccess(mockWebServer);

        OpenRosaServerClient client = subject.get("http", "Android", null);
        client.makeRequest(buildRequest(mockWebServer, ""), new Date());

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("Accept-Encoding"), equalTo("gzip"));
    }
    
    @Test
    public void withCredentials_whenBasicChallengeReceived_doesNotRetryWithCredentials() throws Exception {
        MockWebServer mockWebServer = startMockWebServer();
        enqueueBasicChallenge(mockWebServer);
        enqueueSuccess(mockWebServer);

        OpenRosaServerClient client = subject.get("http", "Android", new HttpCredentials("user", "pass"));
        client.makeRequest(buildRequest(mockWebServer, ""), new Date());

        assertThat(mockWebServer.getRequestCount(), equalTo(1));
    }

    @Test
    public void withCredentials_whenBasicChallengeReceived_whenHttps_retriesWithCredentials() throws Exception {
        MockWebServer httpsMockWebServer = startHttpsMockWebServer();

        enqueueBasicChallenge(httpsMockWebServer);
        enqueueSuccess(httpsMockWebServer);

        OpenRosaServerClient client = subject.get("https", "Android", new HttpCredentials("user", "pass"));
        client.makeRequest(buildRequest(httpsMockWebServer, ""), new Date());

        assertThat(httpsMockWebServer.getRequestCount(), equalTo(2));
        httpsMockWebServer.takeRequest();
        RecordedRequest request = httpsMockWebServer.takeRequest();
        assertThat(request.getHeader("Authorization"), equalTo("Basic dXNlcjpwYXNz"));
    }

    @Test
    public void withCredentials_whenDigestChallengeReceived_retriesWithCredentials() throws Exception {
        MockWebServer mockWebServer = startMockWebServer();
        enqueueDigestChallenge(mockWebServer);
        enqueueSuccess(mockWebServer);

        OpenRosaServerClient client = subject.get("http", "Android", new HttpCredentials("user", "pass"));
        client.makeRequest(buildRequest(mockWebServer, ""), new Date());

        assertThat(mockWebServer.getRequestCount(), equalTo(2));
        mockWebServer.takeRequest();
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("Authorization"), startsWith("Digest"));
    }

    @Test
    public void withCredentials_whenDigestChallengeReceived_whenHttps_retriesWithCredentials() throws Exception {
        MockWebServer httpsMockWebServer = startHttpsMockWebServer();

        enqueueDigestChallenge(httpsMockWebServer);
        enqueueSuccess(httpsMockWebServer);

        OpenRosaServerClient client = subject.get("https", "Android", new HttpCredentials("user", "pass"));
        client.makeRequest(buildRequest(httpsMockWebServer, ""), new Date());

        assertThat(httpsMockWebServer.getRequestCount(), equalTo(2));
        httpsMockWebServer.takeRequest();
        RecordedRequest request = httpsMockWebServer.takeRequest();
        assertThat(request.getHeader("Authorization"), startsWith("Digest"));
    }

    @Test
    public void withCredentials_onceBasicChallenged_whenHttps_proactivelySendsCredentials() throws Exception {
        MockWebServer httpsMockWebServer = startHttpsMockWebServer();

        enqueueBasicChallenge(httpsMockWebServer);
        enqueueSuccess(httpsMockWebServer);
        enqueueSuccess(httpsMockWebServer);

        OpenRosaServerClient client = subject.get("https", "Android", new HttpCredentials("user", "pass"));
        client.makeRequest(buildRequest(httpsMockWebServer, ""), new Date());
        client.makeRequest(buildRequest(httpsMockWebServer, "/different"), new Date());

        assertThat(httpsMockWebServer.getRequestCount(), equalTo(3));
        httpsMockWebServer.takeRequest();
        httpsMockWebServer.takeRequest();
        RecordedRequest request = httpsMockWebServer.takeRequest();
        assertThat(request.getHeader("Authorization"), equalTo("Basic dXNlcjpwYXNz"));
    }

    @Test
    public void withCredentials_onceDigestChallenged_proactivelySendsCredentials() throws Exception {
        MockWebServer mockWebServer = startMockWebServer();

        enqueueDigestChallenge(mockWebServer);
        enqueueSuccess(mockWebServer);
        enqueueSuccess(mockWebServer);

        OpenRosaServerClient client = subject.get("http", "Android", new HttpCredentials("user", "pass"));
        client.makeRequest(buildRequest(mockWebServer, ""), new Date());
        client.makeRequest(buildRequest(mockWebServer, "/different"), new Date());

        assertThat(mockWebServer.getRequestCount(), equalTo(3));
        mockWebServer.takeRequest();
        mockWebServer.takeRequest();
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("Authorization"), startsWith("Digest"));
    }

    @Test
    public void withCredentials_onceDigestChallenged_whenHttps_proactivelySendsCredentials() throws Exception {
        MockWebServer httpsMockWebServer = startHttpsMockWebServer();

        enqueueDigestChallenge(httpsMockWebServer);
        enqueueSuccess(httpsMockWebServer);
        enqueueSuccess(httpsMockWebServer);

        OpenRosaServerClient client = subject.get("https", "Android", new HttpCredentials("user", "pass"));
        client.makeRequest(buildRequest(httpsMockWebServer, ""), new Date());
        client.makeRequest(buildRequest(httpsMockWebServer, "/different"), new Date());

        assertThat(httpsMockWebServer.getRequestCount(), equalTo(3));
        httpsMockWebServer.takeRequest();
        httpsMockWebServer.takeRequest();
        RecordedRequest request = httpsMockWebServer.takeRequest();
        assertThat(request.getHeader("Authorization"), startsWith("Digest"));
    }

    @Test
    public void authenticationIsCachedBetweenInstances() throws Exception {
        MockWebServer mockWebServer = startMockWebServer();

        enqueueDigestChallenge(mockWebServer);
        enqueueSuccess(mockWebServer);
        enqueueSuccess(mockWebServer);

        subject.get("http", "Android", new HttpCredentials("user", "pass")).makeRequest(buildRequest(mockWebServer, ""), new Date());
        subject.get("http", "Android", new HttpCredentials("user", "pass")).makeRequest(buildRequest(mockWebServer, "/different"), new Date());

        assertThat(mockWebServer.getRequestCount(), equalTo(3));
        mockWebServer.takeRequest();
        mockWebServer.takeRequest();
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("Authorization"), startsWith("Digest"));
    }

    @Test
    public void whenUsingDifferentCredentials_authenticationIsNotCachedBetweenInstances() throws Exception {
        MockWebServer mockWebServer = startMockWebServer();

        enqueueDigestChallenge(mockWebServer);
        enqueueSuccess(mockWebServer);
        enqueueDigestChallenge(mockWebServer);
        enqueueSuccess(mockWebServer);

        subject.get("http", "Android", new HttpCredentials("user", "pass")).makeRequest(buildRequest(mockWebServer, ""), new Date());
        subject.get("http", "Android", new HttpCredentials("new-user", "pass")).makeRequest(buildRequest(mockWebServer, "/different"), new Date());

        assertThat(mockWebServer.getRequestCount(), equalTo(4));
        mockWebServer.takeRequest();
        mockWebServer.takeRequest();
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("Authorization"), equalTo(null));
    }

    @Test
    public void whenConnectingToDifferentHosts_authenticationIsNotCachedBetweenInstances() throws Exception {
        MockWebServer host1 = startMockWebServer();
        MockWebServer host2 = startMockWebServer();

        enqueueDigestChallenge(host1);
        enqueueSuccess(host1);

        enqueueDigestChallenge(host2);
        enqueueSuccess(host2);

        subject.get("http", "Android", new HttpCredentials("user", "pass")).makeRequest(buildRequest(host1, ""), new Date());
        subject.get("http", "Android", new HttpCredentials("user", "pass")).makeRequest(buildRequest(host2, ""), new Date());

        assertThat(host2.getRequestCount(), equalTo(2));

        RecordedRequest request = host2.takeRequest();
        assertThat(request.getHeader("Authorization"), equalTo(null));

        host2.shutdown();
    }

    @Test
    public void whenLastRequestSetCookies_nextRequestDoesNotSendThem() throws Exception {
        MockWebServer mockWebServer = startMockWebServer();

        mockWebServer.enqueue(new MockResponse()
                .addHeader("Set-Cookie", "blah=blah"));
        enqueueSuccess(mockWebServer);

        subject.get("http", "Android", new HttpCredentials("user", "pass")).makeRequest(buildRequest(mockWebServer, ""), new Date());
        subject.get("http", "Android", new HttpCredentials("user", "pass")).makeRequest(buildRequest(mockWebServer, ""), new Date());

        mockWebServer.takeRequest();
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("Cookie"), isEmptyOrNullString());
    }

    private MockWebServer startHttpsMockWebServer() throws IOException {
        MockWebServer httpsMockWebServer = new MockWebServer();
        mockWebServers.add(httpsMockWebServer);

        httpsMockWebServer.useHttps(TlsUtil.localhost().sslSocketFactory(), false);
        httpsMockWebServer.start(8443);
        return httpsMockWebServer;
    }

    private MockWebServer startMockWebServer() throws Exception {
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServers.add(mockWebServer);

        mockWebServer.start();
        return mockWebServer;
    }

    private void enqueueSuccess(MockWebServer mockWebServer) {
        mockWebServer.enqueue(new MockResponse());
    }

    private void enqueueBasicChallenge(MockWebServer mockWebServer) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .addHeader("WWW-Authenticate: Basic realm=\"protected area\"")
                .setBody("Please authenticate."));
    }

    private void enqueueDigestChallenge(MockWebServer mockWebServer) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .addHeader("WWW-Authenticate: Digest realm=\"ODK Aggregate\", qop=\"auth\", nonce=\"MTU2NTA4MjEzODI4OTpmMjc4MDM5N2YxZTJiNDRiNjNiYTBiMThiOWQ4ZTlkMg==\"")
                .setBody("Please authenticate."));
    }

    @NotNull
    private Request buildRequest(MockWebServer mockWebServer, String path) {
        return new Request.Builder().url(mockWebServer.url(path)).build();
    }
}
