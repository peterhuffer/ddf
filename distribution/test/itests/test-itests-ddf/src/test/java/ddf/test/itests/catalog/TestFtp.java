/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.test.itests.catalog;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.fail;
import static com.jayway.restassured.RestAssured.delete;
import static com.jayway.restassured.RestAssured.when;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import com.jayway.restassured.path.xml.XmlPath;
import com.jayway.restassured.response.Response;

import ddf.common.test.AfterExam;
import ddf.common.test.BeforeExam;
import ddf.test.itests.AbstractIntegrationTest;

/**
 * Integration Tests for the FTP Endpoint supporting ingest.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class TestFtp extends AbstractIntegrationTest {

    /*
     * TODO Remove in favor of new auth method
     */
    private static final String USERNAME = "admin";

    /*
     * TODO Remove in favor of new auth method
     */
    private static final String PASSWORD = "admin";


    private static final String FTP_SERVER = "localhost";

    private static final int FTP_DEFAULT_PORT = 8021;

    private static final String FTP_ENDPOINT_FEATURE = "catalog-core-ftp";

    private static final String SAMPLE_DATA = "sample test data";

    private static final String SAMPLE_IMAGE = "/9466484_b06f26d579_o.jpg";

    private FTPClient client;


    @BeforeExam
    public void beforeExam() throws Exception {
        try {
            basePort = getBasePort();
            getAdminConfig().setLogLevels();
            getServiceManager().waitForRequiredApps(getDefaultRequiredApps());
            getServiceManager().waitForAllBundles();
            getCatalogBundle().waitForCatalogProvider();

            getServiceManager().startFeature(true, FTP_ENDPOINT_FEATURE);
        } catch (Exception e) {
            LOGGER.error("Failed in @BeforeExam: ", e);
            fail("Failed in @BeforeExam: " + e.getMessage());
        }
    }

    @AfterExam
    public void afterExam() throws Exception {
        try {
            // Turn off feature to not interfere with other tests
            getServiceManager().stopFeature(true, FTP_ENDPOINT_FEATURE);
            disconnect();

        } catch(Exception e) {
            LOGGER.error("Failed in @AfterExam: ", e);
            fail("Failed in @AfterExam: " + e.getMessage());
        }
    }

    @Before
    public void setup() {
    }

    @After
    public void tearDown() throws Exception {
        disconnect();
        client = null;
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testFtp() throws Exception {
        client = createInsecureClient();
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testFtpsExplicit() throws Exception {
        client = createSecureClient(false);

        fail("Not implemented.");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testFtpsImplicit() throws Exception {
        client = createSecureClient(true);

        fail("Not implemented.");
    }

    /**
     *
     * @throws Exception
     */
    @Test(expected=SSLException.class)
    public void testFtpsImplicitFailure() throws Exception {
        boolean isImplicit = true;
        client = new FTPSClient(isImplicit);
        client.connect(FTP_SERVER, FTP_DEFAULT_PORT);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testInsecureFtpPut() throws Exception {
        client = createInsecureClient();

        client.enterLocalPassiveMode();
        client.setControlKeepAliveTimeout(60);
        client.setFileType(FTP.BINARY_FILE_TYPE);

        InputStream ios = new ByteArrayInputStream(SAMPLE_DATA.getBytes());

        // file will not actually be written to disk on ftp server
        boolean done = client.storeFile("TxfrFile", ios);
        ios.close();
        showServerReply(client);

        if(done) {
            LOGGER.debug("File uploaded successfully.");
        } else {
            LOGGER.error("Failed to upload file.");
        }

        // verify FTP PUT resulted in ingested data
        Response response = executeOpenSearch("xml", "q=*", "count=100");
        response.then().log().all().body("metacards.metacard.size()", equalTo(1));

        // clean up test data
        deleteMetacard(getMetacardIdFromResponse(response));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testInsecureFtpPutStream() throws Exception {
        client = createInsecureClient();
        client.enterLocalPassiveMode();
        client.setControlKeepAliveTimeout(60);
        client.setFileType(FTP.BINARY_FILE_TYPE);

        // transfer the data by FTP
        String metacard1Xml = IOUtils.toString(getClass().getClassLoader()
                .getResourceAsStream("metacard1.xml"), UTF_8.name());
        ftpPut(metacard1Xml, true);

        // verify FTP PUT resulted in ingested data
        Response response = executeOpenSearch("xml", "q=*", "count=100");
        response.then().log().all().body("metacards.metacard.size()", equalTo(1));

        // clean up test data
        deleteMetacard(getMetacardIdFromResponse(response));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testSecureFtpPut() throws Exception {
        fail("Not implemented.");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testSecureFtpPutMultiple() throws Exception {
        fail("Not implemented.");
    }


    /**
     *
     * @throws Exception
     */
    @Test
    public void testSecureFtpIngestFailure() throws Exception {
        fail("Not implemented.");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testSecureFtpBadCert() throws Exception {
        fail("Not implemented.");
    }




    // ---------------------------------------------------------------------------- //

    private FTPClient createInsecureClient() throws Exception {
        FTPClient ftp = new FTPClient();

        ftp.connect(FTP_SERVER, FTP_DEFAULT_PORT);
        showServerReply(ftp);
        int connectionReply = ftp.getReplyCode();
        if (!FTPReply.isPositiveCompletion(connectionReply)) {
            fail("FTP server refused connection: " + connectionReply);
        }

        boolean success = ftp.login(USERNAME, PASSWORD);
        showServerReply(ftp);
        if (!success) {
            fail("Could not log in to the FTP server.");
        }

        return ftp;
    }

    private FTPSClient createSecureClient(boolean implicit) throws KeyStoreException, IOException,
            NoSuchAlgorithmException {
        FTPSClient ftps = new FTPSClient(implicit);
        ftps.setNeedClientAuth(true);

 /*       KeyStore ks = KeyStore.getInstance("JKS");
        FileInputStream fis = new FileInputStream(FTPCLIENT_KEYSTORE);
        ks.load(fis, KEYSTORE_PASSWORD.toCharArray());
        fis.close();

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());

        ftps.setKeyManager(kmf.getKeyManagers()[0]);*/

        return ftps;
    }

    private void showServerReply(FTPClient ftpClient) {
        String[] replies = ftpClient.getReplyStrings();
        if (replies != null && replies.length > 0) {
            for (String aReply : replies) {
                LOGGER.info("Server response: " + aReply);
            }
        }
    }

    private Response executeOpenSearch(String format, String... query) {
        StringBuilder buffer = new StringBuilder(OPENSEARCH_PATH.getUrl()).append("?")
                .append("format=")
                .append(format);

        for (String term : query) {
            buffer.append("&").append(term);
        }

        String url = buffer.toString();
        LOGGER.info("Getting response to {}", url);

        return when().get(url);
    }

    private void ftpPut(String data, boolean streaming) throws IOException {
        if(streaming) {
            InputStream is = new ByteArrayInputStream(data.getBytes());

            LOGGER.info("Start uploading data via FTP PUT...");
            OutputStream os = client.storeFileStream("test");
            byte[] bytesIn = new byte[4096];
            int read = 0;

            while ((read = is.read(bytesIn)) != -1) {
                os.write(bytesIn, 0, read);
            }

            is.close();
            os.close();
        }

        boolean done = client.completePendingCommand();
        if(done) {
            LOGGER.debug("File uploaded successfully.");
        } else {
            LOGGER.error("Failed to upload file.");
        }
    }

    private String getMetacardIdFromResponse(Response response)
            throws IOException, XPathExpressionException {
        return XmlPath
                .given(response.asString())
                .get("metacards.metacard[0].@gml:id");
    }

    public void deleteMetacard(String id) {
        LOGGER.info("Deleting metacard {}", id);
        delete(REST_PATH.getUrl() + id).then()
                .assertThat()
                .statusCode(200)
                .log()
                .all();
    }

    private void disconnect() {
        if(client != null && client.isConnected()) {
            try {
                client.logout();
            } catch(IOException ioe) {
                // ignore
            }
            try {
                client.disconnect();
            } catch(IOException ioe) {
                // ignore
            }
        }
    }
}
