package de.adorsys.datasafe.business.impl.e2e;

import de.adorsys.datasafe.business.impl.service.DefaultDatasafeServices;
import de.adorsys.datasafe.encrypiton.api.types.UserIDAuth;
import de.adorsys.datasafe.encrypiton.api.types.keystore.ReadStorePassword;
import de.adorsys.datasafe.encrypiton.impl.keystore.KeyStoreServiceImpl;
import de.adorsys.datasafe.storage.impl.fs.FileSystemStorageService;
import de.adorsys.datasafe.types.api.resource.Uri;
import de.adorsys.datasafe.types.api.shared.BaseMockitoTest;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import static de.adorsys.datasafe.business.impl.e2e.DatasafeServicesProvider.STORE_PAZZWORD;
import static de.adorsys.datasafe.encrypiton.api.types.keystore.KeyStoreCreationConfig.DOCUMENT_KEY_ID_PREFIX;
import static de.adorsys.datasafe.encrypiton.api.types.keystore.KeyStoreCreationConfig.PATH_KEY_ID_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that default-built keystore contains certain keys.
 */
class KeystoreE2ETest extends BaseMockitoTest {

    private DefaultDatasafeServices datasafeServices;

    @BeforeEach
    void init(@TempDir Path rootPath) {
        datasafeServices = DatasafeServicesProvider.defaultDatasafeServices(
                new FileSystemStorageService(rootPath),
                new Uri(rootPath.toUri())
        );
    }

    @Test
    @SneakyThrows
    void testDefaultKeystoreHasProperKeys() {
        UserIDAuth auth = new UserIDAuth("user", "pass");
        datasafeServices.userProfile().registerUsingDefaults(auth);
        URI keystorePath = datasafeServices.userProfile().privateProfile(auth)
                .getKeystore().location().asURI();

        KeyStoreServiceImpl keyStoreService = new KeyStoreServiceImpl();
        KeyStore keyStore = keyStoreService.deserialize(
                Files.readAllBytes(Paths.get(keystorePath)),
                "ID",
                new ReadStorePassword(STORE_PAZZWORD)
        );

        assertThat(aliases(keyStore)).filteredOn(it -> it.matches(PATH_KEY_ID_PREFIX + ".+")).hasSize(1);
        assertThat(aliases(keyStore)).filteredOn(it -> it.matches(DOCUMENT_KEY_ID_PREFIX + ".+")).hasSize(1);
    }

    @SneakyThrows
    private Set<String> aliases(KeyStore keyStore) {
        Enumeration<String> aliases = keyStore.aliases();
        Set<String> result = new HashSet<>();
        while (aliases.hasMoreElements()) {
            result.add(aliases.nextElement());
        }

        return result;
    }
}
