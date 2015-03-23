package com.hindsighttesting.maven.behave;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.ws.rs.core.MediaType;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;

/**
 * Processes and Downloads Cucumber Feature files from Behave for JIRA
 * 
 * @goal features
 * @phase generate-test-resources
 */
public class FeaturesMojo extends AbstractMojo {
    /**
     * Location of the file.
     * 
     * @parameter 
     *            expression="${project.build.directory}/generated-test-sources/cucumber"
     * @required
     */
    private File outputDirectory;

    /**
     * URL of the JIRA instance.
     * 
     * @parameter
     * @required
     */
    private URL server;

    /**
     * JIRA Project Key.
     * 
     * @parameter
     * @required
     */
    private String projectKey;

    /**
     * The User Name for JIRA.
     * 
     * @parameter
     */
    private String username;

    /**
     * The password for the JIRA User.
     * 
     * @parameter
     */
    private String password;

    /**
     * Download Scenarios that are marked as manual
     * 
     * @parameter
     */
    private boolean includeManual = false;

    /**
     * The url of the HTTP proxy server
     * 
     * @parameter expression="${http.proxyHost}"
     */
    private String httpProxyURL;

    /**
     * The username required to access the HTTP proxy server
     * 
     * @parameter expression="${http.proxyUsername}"
     */
    private String httpProxyUsername;

    /**
     * The password required to access the HTTP proxy server
     * 
     * @parameter expression="${http.proxyPassword}"
     */
    private String httpProxyPassword;

    /**
     * HTTP timeout in milliseconds.
     * 
     * @parameter expression="${http.proxyPassword}"
     */
    private Integer httpTimeout;

    public void execute() throws MojoExecutionException {
        File targetFolder = outputDirectory;

        if (!targetFolder.exists()) {
            targetFolder.mkdirs();
        }

        if (projectKey == null || projectKey.length() < 2) {
            throw new IllegalArgumentException(
                    "A JIRA Project is required and it must be at least 2 characters long");
        }

        DefaultApacheHttpClient4Config config = new DefaultApacheHttpClient4Config();
        if (httpProxyURL != null) {
            config.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_URI, httpProxyURL);
            if (httpProxyUsername != null) {
                config.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_USERNAME,
                        httpProxyUsername);
                config.getProperties().put(ApacheHttpClient4Config.PROPERTY_PROXY_PASSWORD,
                        httpProxyPassword);
            }
        }

        if (httpTimeout != null) {
            config.getProperties().put(ApacheHttpClient4Config.PROPERTY_READ_TIMEOUT, httpTimeout);
        }

        Client restClient = Client.create(config);

        if (username != null || !username.isEmpty()) {
            restClient.addFilter(new HTTPBasicAuthFilter(username, password));
        }

        Builder featureListRequest = buildRestResource(
                restClient,
                server + "/rest/cucumber/1.0/project/" + projectKey + "/features?manual="
                        + Boolean.toString(includeManual));

        ClientResponse response = featureListRequest.get(ClientResponse.class);

        switch (response.getStatus()) {
        case 401:
            throw new MojoExecutionException("Username or Password are invalid");
        case 403:
            throw new MojoExecutionException("Too many login failures. Please try again later");
        case 404:
            throw new MojoExecutionException("Project could not be found");
        case 405:
        case 406:
            throw new MojoExecutionException(
                    "The version of Behave is not compatiable with this version of the plugin");
        }

        InputStream featuresZip = response.getEntity(InputStream.class);
        extractFeatureFiles(targetFolder, featuresZip);

    }

    private void extractFeatureFiles(File targetFolder, InputStream featuresZip)
            throws MojoExecutionException {
        try {

            if (featuresZip == null) {
                throw new MojoExecutionException("The server didn't return any feature files");
            }

            int fileCount = 0;
            ZipInputStream zis = null;
            try {
                zis = new ZipInputStream(featuresZip);
                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {
                    FileOutputStream fileOutput = new FileOutputStream(new File(targetFolder,
                            entry.getName()));
                    org.apache.commons.io.IOUtils.copy(zis, fileOutput);
                    fileOutput.close();
                    fileCount++;
                }
            } finally {
                if (zis != null) {
                    zis.close();
                }
            }
            getLog().info("Succesfully downloaded " + fileCount + " feature files from Behave");

        } catch (Exception e) {
            throw new MojoExecutionException("Failed to extract feature files", e);
        }
    }

    private Builder buildRestResource(Client restClient, String url) {
        Builder requestBuilder = restClient.resource(url).type(MediaType.APPLICATION_JSON);
        requestBuilder.accept("application/zip");
        return requestBuilder;
    }

}