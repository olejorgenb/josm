// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.oauth;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.JOptionPane;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.exception.OAuthException;

import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.data.oauth.OAuthParameters;
import org.openstreetmap.josm.data.oauth.OAuthToken;
import org.openstreetmap.josm.data.osm.UserInfo;
import org.openstreetmap.josm.gui.HelpAwareOptionPane;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.io.OsmApiException;
import org.openstreetmap.josm.io.OsmDataParsingException;
import org.openstreetmap.josm.io.OsmServerUserInfoReader;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.io.auth.DefaultAuthenticator;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Checks whether an OSM API server can be accessed with a specific Access Token.
 *
 * It retrieves the user details for the user which is authorized to access the server with
 * this token.
 *
 */
public class TestAccessTokenTask extends PleaseWaitRunnable {
    private OAuthToken token;
    private OAuthParameters oauthParameters;
    private boolean canceled;
    private Component parent;
    private String apiUrl;
    private HttpURLConnection connection;

    /**
     * Create the task
     *
     * @param parent the parent component relative to which the  {@see PleaseWaitRunnable}-Dialog is displayed
     * @param apiUrl the API URL. Must not be null.
     * @param parameters the OAuth parameters. Must not be null.
     * @param accessToken the Access Token. Must not be null.
     */
    public TestAccessTokenTask(Component parent, String apiUrl, OAuthParameters parameters, OAuthToken accessToken) {
        super(parent, tr("Testing OAuth Access Token"), false /* don't ignore exceptions */);
        CheckParameterUtil.ensureParameterNotNull(apiUrl, "apiUrl");
        CheckParameterUtil.ensureParameterNotNull(parameters, "parameters");
        CheckParameterUtil.ensureParameterNotNull(accessToken, "accessToken");
        this.token = accessToken;
        this.oauthParameters = parameters;
        this.parent = parent;
        this.apiUrl = apiUrl;
    }

    @Override
    protected void cancel() {
        canceled = true;
        synchronized(this) {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    protected void finish() {}

    protected void sign(HttpURLConnection con) throws OAuthException{
        OAuthConsumer consumer = oauthParameters.buildConsumer();
        consumer.setTokenWithSecret(token.getKey(), token.getSecret());
        consumer.sign(con);
    }

    protected String normalizeApiUrl(String url) {
        // remove leading and trailing white space
        url = url.trim();

        // remove trailing slashes
        while(url.endsWith("/")) {
            url = url.substring(0, url.lastIndexOf("/"));
        }
        return url;
    }

    protected UserInfo getUserDetails() throws OsmOAuthAuthorizationException, OsmDataParsingException,OsmTransferException {
        boolean authenticatorEnabled = true;
        try {
            URL url = new URL(normalizeApiUrl(apiUrl) + "/0.6/user/details");
            authenticatorEnabled = DefaultAuthenticator.getInstance().isEnabled();
            DefaultAuthenticator.getInstance().setEnabled(false);
            synchronized(this) {
                connection = (HttpURLConnection)url.openConnection();
            }

            connection.setDoOutput(true);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", Version.getInstance().getAgentString());
            connection.setRequestProperty("Host", connection.getURL().getHost());
            sign(connection);
            connection.connect();

            if (connection.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED)
                throw new OsmApiException(HttpURLConnection.HTTP_UNAUTHORIZED, tr("Retrieving user details with Access Token Key ''{0}'' was rejected.", token.getKey()), null);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN)
                throw new OsmApiException(HttpURLConnection.HTTP_FORBIDDEN, tr("Retrieving user details with Access Token Key ''{0}'' was forbidden.", token.getKey()), null);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new OsmApiException(connection.getResponseCode(),connection.getHeaderField("Error"), null);
            Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(connection.getInputStream());
            return OsmServerUserInfoReader.buildFromXML(d);
        } catch(SAXException e) {
            throw new OsmDataParsingException(e);
        } catch(ParserConfigurationException e){
            throw new OsmDataParsingException(e);
        } catch(MalformedURLException e) {
            throw new OsmTransferException(e);
        } catch(IOException e){
            throw new OsmTransferException(e);
        } catch(OAuthException e) {
            throw new OsmOAuthAuthorizationException(e);
        } finally {
            DefaultAuthenticator.getInstance().setEnabled(authenticatorEnabled);
        }
    }

    protected void notifySuccess(UserInfo userInfo) {
        HelpAwareOptionPane.showOptionDialog(
                parent,
                tr("<html>"
                        + "Successfully used the Access Token ''{0}'' to<br>"
                        + "access the OSM server at ''{1}''.<br>"
                        + "You are accessing the OSM server as user ''{2}'' with id ''{3}''."
                        + "</html>",
                        token.getKey(),
                        apiUrl,
                        userInfo.getDisplayName(),
                        userInfo.getId()
                ),
                tr("Success"),
                JOptionPane.INFORMATION_MESSAGE,
                HelpUtil.ht("/Dialog/OAuthAuthorisationWizard#AccessTokenOK")
        );
    }

    protected void alertFailedAuthentication() {
        HelpAwareOptionPane.showOptionDialog(
                parent,
                tr("<html>"
                        + "Failed to access the OSM server ''{0}''<br>"
                        + "with the Access Token ''{0}''.<br>"
                        + "The server rejected the Access Token as unauthorized. You will not<br>"
                        + "be able to access any protected resource on this server using this token."
                        +"</html>",
                        apiUrl,
                        token.getKey()
                ),
                tr("Test failed"),
                JOptionPane.ERROR_MESSAGE,
                HelpUtil.ht("/Dialog/OAuthAuthorisationWizard#AccessTokenFailed")
        );
    }

    protected void alertFailedAuthorisation() {
        HelpAwareOptionPane.showOptionDialog(
                parent,
                tr("<html>"
                        + "The Access Token ''{1}'' is known to the OSM server ''{0}''.<br>"
                        + "The test to retrieve the user details for this token failed, though.<br>"
                        + "Depending on what rights are granted to this token you may nevertheless use it<br>"
                        + "to upload data, upload GPS traces, and/or access other protected resources."
                        +"</html>",
                        apiUrl,
                        token.getKey()
                ),
                tr("Token allows restricted access"),
                JOptionPane.WARNING_MESSAGE,
                HelpUtil.ht("/Dialog/OAuthAuthorisationWizard#AccessTokenFailed")
        );
    }

    protected void alertFailedConnection() {
        HelpAwareOptionPane.showOptionDialog(
                parent,
                tr("<html>"
                        + "Failed to retrieve information about the current user"
                        + " from the OSM server ''{0}''.<br>"
                        + "This is probably not a problem caused by the tested Access Token, but<br>"
                        + "rather a problem with the server configuration. Carefully check the server<br>"
                        + "URL and your Internet connection."
                        +"</html>",
                        apiUrl,
                        token.getKey()
                ),
                tr("Test failed"),
                JOptionPane.ERROR_MESSAGE,
                HelpUtil.ht("/Dialog/OAuthAuthorisationWizard#AccessTokenFailed")
        );
    }

    protected void alertFailedSigning() {
        HelpAwareOptionPane.showOptionDialog(
                parent,
                tr("<html>"
                        + "Failed to sign the request for the OSM server ''{0}'' with the "
                        + "token ''{1}''.<br>"
                        + "The token ist probably invalid."
                        +"</html>",
                        apiUrl,
                        token.getKey()
                ),
                tr("Test failed"),
                JOptionPane.ERROR_MESSAGE,
                HelpUtil.ht("/Dialog/OAuthAuthorisationWizard#AccessTokenFailed")
        );
    }

    protected void alertInternalError() {
        HelpAwareOptionPane.showOptionDialog(
                parent,
                tr("<html>"
                        + "The test failed because the server responded with an internal error.<br>"
                        + "JOSM could not decide whether the token is valid. Please try again later."
                        + "</html>",
                        apiUrl,
                        token.getKey()
                ),
                tr("Test failed"),
                JOptionPane.WARNING_MESSAGE,
                HelpUtil.ht("/Dialog/OAuthAuthorisationWizard#AccessTokenFailed")
        );
    }

    @Override
    protected void realRun() throws SAXException, IOException, OsmTransferException {
        try {
            getProgressMonitor().indeterminateSubTask(tr("Retrieving user info..."));
            UserInfo userInfo = getUserDetails();
            if (canceled) return;
            notifySuccess(userInfo);
        }catch(OsmOAuthAuthorizationException e) {
            if (canceled) return;
            e.printStackTrace();
            alertFailedSigning();
        } catch(OsmApiException e) {
            if (canceled) return;
            e.printStackTrace();
            if (e.getResponseCode() == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                alertInternalError();
                return;
            } if (e.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
                alertFailedAuthentication();
                return;
            } else if (e.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN) {
                alertFailedAuthorisation();
                return;
            }
            alertFailedConnection();
        } catch(OsmTransferException e) {
            if (canceled) return;
            e.printStackTrace();
            alertFailedConnection();
        }
    }
}
