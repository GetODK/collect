package org.odk.collect.android.http;

import org.odk.collect.android.http.okhttp.OkHttpOpenRosaServerClientFactory;
import org.odk.collect.android.http.openrosa.OpenRosaServerClientFactory;

import okhttp3.OkHttpClient;
import okhttp3.tls.internal.TlsUtil;

public class OkHttpOpenRosaServerClientFactoryTest extends OpenRosaServerClientFactoryTest {

    @Override
    protected OpenRosaServerClientFactory buildSubject() {
        OkHttpClient baseClient = new OkHttpClient.Builder()
                .sslSocketFactory(
                        TlsUtil.localhost().sslSocketFactory(),
                        TlsUtil.localhost().trustManager())
                .build();
        
        return new OkHttpOpenRosaServerClientFactory(baseClient);
    }

    @Override
    protected Boolean useRealHttps() {
        return true;
    }
}
