package jenkins.plugins.jclouds.blobstore;

import junit.framework.TestCase;

public class BlobStoreProfileLiveTest extends TestCase {

    private BlobStoreTestFixture fixture;
    private BlobStoreProfile profile;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        fixture = new BlobStoreTestFixture();
        fixture.setUp();

        // TODO: this may need to vary per test
        profile = new BlobStoreProfile(fixture.getProvider() + "-profile", fixture.getProvider(), null);
    }

    public static final String CONTAINER_PREFIX = System.getProperty("user.name") + "-blobstore-profile";

    public void testUpload() {
        String container = CONTAINER_PREFIX + "-upload";
        try {
            fixture.getBlobStore().createContainerInLocation(null, container);
            // filePath == ??
            // profile.upload(container, filePath)
            // assertTrue(fixture.getBlobStore().blobExists(container, filename));
        } finally {
            fixture.getBlobStore().deleteContainer(container);
        }
    }

    @Override
    public void tearDown() {
        if (fixture != null)
            fixture.tearDown();
    }
}
