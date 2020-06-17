package com.avispl.symphony.dal.communicator.crestron;

import com.avispl.symphony.api.dal.dto.control.ConnectionState;
import com.avispl.symphony.dal.BaseDevice;
import com.avispl.symphony.dal.communicator.Communicator;
import com.avispl.symphony.dal.communicator.ConnectionStatus;
import org.apache.http.Header;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
/**************************************************************************
 * A bare bones http communicator for communication with specific devices.
 * Not for general use!
 **************************************************************************/
public class CustomHttpCommunicator extends BaseDevice implements Communicator {
    private CloseableHttpClient httpClient;
    private HttpClientContext context;
    private String login;
    private String password;
    private int timeout = 30000;
    private long timestamp;
    private ConnectionState connectionState;



    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public CustomHttpCommunicator() {
        connectionState = ConnectionState.Disconnected;
        try {
            this.connect();
        } catch (Exception e){

        }
    }

    @Override
    public void connect() throws Exception {
        connectionState = ConnectionState.Connecting;
        RequestConfig defaultRequestConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.DEFAULT)
                .setConnectionRequestTimeout(timeout)
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout).build();

        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        httpClientBuilder.setDefaultRequestConfig(defaultRequestConfig);

        //Trust All Certificates
        SSLContextBuilder sslContextBuilder = SSLContexts.custom();
        sslContextBuilder.loadTrustMaterial((KeyStore)null, new TrustStrategy() {
            public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                return true;
            }
        });
        SSLContext sslContext = sslContextBuilder.build();
        httpClientBuilder.setSSLContext(sslContext);
        HostnameVerifier hostNameVerifier = new HostnameVerifier() {
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        };
        httpClientBuilder.setSSLHostnameVerifier(hostNameVerifier);
        CookieStore cookieStore = new BasicCookieStore();
        context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        httpClient = httpClientBuilder.build();
        timestamp = System.currentTimeMillis();
        connectionState = ConnectionState.Connected;
    }

    @Override
    public void disconnect() throws Exception {
        try {
            httpClient.close();
        } finally {
            connectionState = ConnectionState.Disconnected;
            httpClient = null;
        }
    }

    @Override
    public ConnectionStatus getConnectionStatus() {
        return new ConnectionStatus(){{setConnectionState(connectionState);}};
    }

    HttpResponse doGet(String path) throws Exception{
        HttpGet httpGet = new HttpGet("https://" + (this.getHost().charAt(this.host.length()-1) == '/' ? this.getHost() : this.getHost() + "/") + path);
        int responseCode = 0;
        String responseString = "";
        List<Header> responseHeaders = new ArrayList<Header>();
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet, context);
            responseString = readResponseBody(new InputStreamReader(response.getEntity().getContent()));
            responseCode = response.getStatusLine().getStatusCode();
            responseHeaders.addAll(Arrays.asList(response.getAllHeaders()));
        } catch (Exception e){
            return new HttpResponse(0, null, "");
        }
        return new HttpResponse(responseCode, responseHeaders, responseString);
    }

    HttpResponse doPost(String path, String body) throws Exception{
        HttpPost httpPost = new HttpPost("https://" + this.getHost() + path);
        StringEntity entity = new StringEntity(body);
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        int responseCode;
        String responseString;
        List<Header> responseHeaders = new ArrayList<Header>();
        CloseableHttpResponse response = httpClient.execute(httpPost, context);
        responseString = readResponseBody(new InputStreamReader(response.getEntity().getContent()));
        responseCode = response.getStatusLine().getStatusCode();
        responseHeaders.addAll(Arrays.asList(response.getAllHeaders()));

        return new HttpResponse(responseCode, responseHeaders, responseString);
    }


    private String readResponseBody(InputStreamReader inputStream) throws Exception {
        BufferedReader rd = new BufferedReader(inputStream);
        StringBuilder response = new StringBuilder();
        String temp;
        while((temp = rd.readLine()) != null) {
            response.append(temp);
        }
        return response.toString();
    }

    public static class HttpResponse{
        private int responseCode;
        private List<Header> responseHeaders;
        private String responseBody;

        HttpResponse(int responseCode, List<Header> responseHeaders,String responseBody){
            this.responseCode = responseCode;
            this.responseHeaders = responseHeaders;
            this.responseBody = responseBody;
        }
        int getResponseCode() {
            return responseCode;
        }

        public List<Header> getResponseHeaders() {
            return responseHeaders;
        }

        public String getResponseBody() {
            return responseBody;
        }

        String getFullResponse(){
            StringBuilder output = new StringBuilder("Response Code: " + responseCode + "\n");
            output.append("--------Response Headers--------\r\n");
            if (!responseHeaders.isEmpty()) {
                for (Header h : responseHeaders) {
                    output.append(h.getName() + ":" + h.getValue() + "\n");
                }
            }
            output.append("--------Body--------\r\n");
            output.append(responseBody);
            return output.toString();
        }
    }

}
