package org.odk.collect.android.http;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.odk.collect.android.http.openrosa.HttpGetResult;
import org.odk.collect.android.http.openrosa.OpenRosaConstants;
import org.odk.collect.android.http.openrosa.OpenRosaHttpInterface;
import org.odk.collect.android.utilities.DocumentFetchResult;
import org.odk.collect.android.utilities.WebCredentialsUtils;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayInputStream;
import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class CollectServerClientTest {

    private OpenRosaHttpInterface httpInterface;
    private CollectServerClient collectServerClient;

    @Before
    public void setup() {
        httpInterface = mock(OpenRosaHttpInterface.class);
        collectServerClient = new CollectServerClient(httpInterface, new WebCredentialsUtils());
    }

    @Test
    public void getXMLDocument_returnsResultWith0Status() throws Exception {
        HashMap<String, String> headers = new HashMap<>();
        headers.put(OpenRosaConstants.VERSION_HEADER, "1.0");
        when(httpInterface.executeGetRequest(any(), any(), any())).thenReturn(new HttpGetResult(
                new ByteArrayInputStream("".getBytes()),
                headers,
                "hash",
                200
        ));

        DocumentFetchResult result = collectServerClient.getXmlDocument("http://testurl");
        assertThat(result.responseCode, equalTo(0));
        assertThat(result.isOpenRosaResponse, equalTo(true));
        assertThat(result.errorMessage, nullValue());
    }

    @Test
    public void getXMLDocument_whenUnsuccessful_returnsResultWithStatusAndErrorMessage() throws Exception {
        when(httpInterface.executeGetRequest(any(), any(), any())).thenReturn(new HttpGetResult(null, new HashMap<>(), "", 500));

        DocumentFetchResult result = collectServerClient.getXmlDocument("http://testurl");
        assertThat(result.responseCode, equalTo(500));
        assertThat(result.errorMessage, equalTo("getXmlDocument failed while accessing http://testurl with status code: 500"));
    }

    @Test
    public void getXMLDocument_whenHttpInterfaceThrowsAnException_returnsResultWith0StatusAndErrorMessage() throws Exception {
        when(httpInterface.executeGetRequest(any(), any(), any())).thenThrow(new Exception());

        DocumentFetchResult result = collectServerClient.getXmlDocument("http://testurl");
        assertThat(result.responseCode, equalTo(0));
        assertThat(result.errorMessage, equalTo("Parsing failed with null while accessing http://testurl"));
    }
}


