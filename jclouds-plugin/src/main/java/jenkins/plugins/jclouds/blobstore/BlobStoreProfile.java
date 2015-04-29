package jenkins.plugins.jclouds.blobstore;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

import hudson.util.ListBoxModel;
import org.kohsuke.stapler.AncestorInPath;
import hudson.model.ItemGroup;
import shaded.com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import hudson.FilePath;
import org.jclouds.ContextBuilder;
import org.jclouds.apis.Apis;
import shaded.com.google.common.base.Strings;
import hudson.model.Computer;
import hudson.security.AccessControlled;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.security.ACL;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import hudson.model.Hudson;
import jenkins.model.Jenkins;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;


/**
 * Model class for Blobstore profile. User can configure multiple profiles to upload artifacts to different providers.
 *
 * @author Vijay Kiran
 */
public class BlobStoreProfile {

    private static final Logger LOGGER = Logger.getLogger(BlobStoreProfile.class.getName());

    private String profileName;
    private String providerName;
    private String cloudManagerKeyId;

    @DataBoundConstructor
    public BlobStoreProfile(final String profileName, final String providerName, final String cloudManagerKeyId) {
        System.out.println("[" + profileName + ", " + providerName + ", " + cloudManagerKeyId + "]");
        this.profileName = profileName;
        this.providerName = providerName;
        this.cloudManagerKeyId = cloudManagerKeyId;
    }

    private static StandardUsernameCredentials getCredential(String keyId) {
        if (Strings.isNullOrEmpty(keyId)) {
            return null;
        }

        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, Hudson.getInstance(), ACL.SYSTEM, null),
                CredentialsMatchers.withId(keyId));
    }

    /**
     * Configured profile.
     *
     * @return - name of the profile.
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Provider Name as per the JClouds Blobstore supported providers.
     *
     * @return - providerName String
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Cloud provider identity.
     *
     * @return
     */
    public String getIdentity() {
        final StandardUsernameCredentials supk = getCredential(cloudManagerKeyId);
        if (null != supk) {
            return supk.getUsername();
        }
        return "";
    }

    public String getCloudManagerKeyId() {
        return cloudManagerKeyId;
    }

    /**
     * Cloud provider credential.
     *
     * @return
     */
    public String getCredential() {
        final StandardUsernameCredentials supk = getCredential(cloudManagerKeyId);
        if (null != supk) {
            if(supk instanceof SSHUserPrivateKey) {
                return ((SSHUserPrivateKey)supk).getPrivateKey();
            } else if(supk instanceof StandardUsernamePasswordCredentials) {
                return ((StandardUsernamePasswordCredentials)supk).getPassword().getPlainText();
            }
        }
        return "";
    }

    static final Iterable<Module> MODULES = ImmutableSet.<Module>of(new EnterpriseConfigurationModule());

    static BlobStoreContext ctx(String providerName, String identity, String credential, Properties overrides) {
        return ContextBuilder.newBuilder(providerName).credentials(identity, credential).overrides(overrides).modules(MODULES)
                .buildView(BlobStoreContext.class);
    }

    /**
     * Upload the specified file from the
     *
     * @param filePath  to container
     * @param container - The container where the file needs to be uploaded.
     * @param path      - The path in container where the file needs to be uploaded.
     * @param filePath  - the {@link FilePath} of the file which needs to be uploaded.
     * @throws IOException
     * @throws InterruptedException
     */
    public void upload(String container, String path, FilePath filePath) throws IOException, InterruptedException {
        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }
        // correct the classloader so that extensions can be found
        Thread.currentThread().setContextClassLoader(Apis.class.getClassLoader());
        // TODO: endpoint
        final BlobStoreContext context = ctx(this.providerName, getIdentity(), getCredential(), new Properties());
        try {
            final BlobStore blobStore = context.getBlobStore();
            if (!blobStore.containerExists(container)) {
                blobStore.createContainerInLocation(null, container);
            }
            if (!path.equals("") && !blobStore.directoryExists(container, path)) {
                blobStore.createDirectory(container, path);
            }
            String destPath;
            if (path.equals("")) {
                destPath = filePath.getName();
            } else {
                destPath = path + "/" + filePath.getName();
            }
            LOGGER.info("Publishing now to container: " + container + " path: " + destPath);
            Blob blob = context.getBlobStore().blobBuilder(destPath).payload(filePath.read()).build();
            context.getBlobStore().putBlob(container, blob);
            LOGGER.info("Published " + destPath + " to container " + container + " with profile " + this.profileName);
        } finally {
            context.close();
        }
    }
}
