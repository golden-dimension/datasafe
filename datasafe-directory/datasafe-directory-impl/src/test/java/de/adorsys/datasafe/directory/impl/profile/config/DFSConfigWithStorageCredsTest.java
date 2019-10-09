package de.adorsys.datasafe.directory.impl.profile.config;

import de.adorsys.datasafe.directory.api.types.CreateUserPrivateProfile;
import de.adorsys.datasafe.encrypiton.api.types.UserIDAuth;
import de.adorsys.datasafe.encrypiton.api.types.keystore.ReadKeyPassword;
import de.adorsys.datasafe.encrypiton.api.types.keystore.ReadStorePassword;
import de.adorsys.datasafe.types.api.global.Version;
import de.adorsys.datasafe.types.api.shared.BaseMockitoTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DFSConfigWithStorageCredsTest extends BaseMockitoTest {

    private DFSConfigWithStorageCreds tested = new DFSConfigWithStorageCreds("file://path", new ReadStorePassword("secret"));

    @Test
    void defaultPrivateTemplate() {
        UserIDAuth user = new UserIDAuth("", new ReadKeyPassword(""));

        CreateUserPrivateProfile profile = tested.defaultPrivateTemplate(user);

        assertThat(profile.getId()).isEqualTo(user);
        assertThat(profile.getAppVersion()).isEqualTo(Version.current());
        assertThat(profile.getPrivateStorage().location().asString()).isEqualTo("file://path/private/files/");
        assertThat(profile.getKeystore().location().asString()).isEqualTo("file://path/private/keystore");
        assertThat(profile.getStorageCredentialsKeystore().location().asString())
                .isEqualTo("file://path/private/storagekeystore");
        assertThat(profile.getPublishPubKeysTo().location().asString()).isEqualTo("file://path/public/pubkeys");
        assertThat(profile.getInboxWithWriteAccess().location().asString()).isEqualTo("file://path/public/inbox/");
    }
}
