package de.adorsys.datasafe.simple.adapter.impl.legacy;

import de.adorsys.datasafe.encrypiton.api.keystore.KeyStoreService;
import de.adorsys.datasafe.encrypiton.api.types.encryption.KeyCreationConfig;
import de.adorsys.datasafe.encrypiton.api.types.encryption.KeyStoreConfig;
import de.adorsys.datasafe.encrypiton.api.types.keystore.*;
import de.adorsys.datasafe.encrypiton.impl.keystore.KeyStoreServiceImpl;
import de.adorsys.datasafe.simple.adapter.api.legacy.pathencryption.LegacySymmetricPathEncryptionService;
import de.adorsys.datasafe.simple.adapter.impl.WithBouncyCastle;
import de.adorsys.datasafe.simple.adapter.impl.legacy.pathencryption.LegacyPathDigestConfig;
import de.adorsys.datasafe.simple.adapter.impl.legacy.pathencryption.LegacyPathEncryptor;
import de.adorsys.datasafe.simple.adapter.impl.legacy.pathencryption.LegacyIntegrityPreservingUriEncryption;
import de.adorsys.datasafe.types.api.resource.Uri;
import de.adorsys.datasafe.types.api.types.ReadKeyPassword;
import de.adorsys.datasafe.types.api.types.ReadStorePassword;
import de.adorsys.datasafe.types.api.utils.ReadKeyPasswordTestFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;

import static de.adorsys.datasafe.encrypiton.api.types.encryption.KeyCreationConfig.PATH_KEY_ID_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
class LegacyIntegrityPreservingUriEncryptionTest extends WithBouncyCastle {

    private LegacySymmetricPathEncryptionService legacySymmetricPathEncryptionService = new LegacyIntegrityPreservingUriEncryption(
            new LegacyPathEncryptor(new LegacyPathDigestConfig())
    );

    private KeyStoreService keyStoreService = new KeyStoreServiceImpl(KeyStoreConfig.builder().build());
    private ReadKeyPassword readKeyPassword = ReadKeyPasswordTestFactory.getForString("readkeypassword");
    private ReadStorePassword readStorePassword = new ReadStorePassword("readstorepassword");
    private KeyStoreAuth keyStoreAuth = new KeyStoreAuth(readStorePassword, readKeyPassword);
    private KeyCreationConfig config = KeyCreationConfig.builder().encKeyNumber(1).signKeyNumber(1).build();
    private KeyStore keyStore = keyStoreService.createKeyStore(keyStoreAuth, config);
    private KeyStoreAccess keyStoreAccess = new KeyStoreAccess(keyStore, keyStoreAuth);

    @Test
    void testSuccessEncryptDecryptPath() {
        String testPath = "path/to/file";

        log.info("Test path: {}", testPath);

        Uri testURI = new Uri(testPath);

        SecretKeySpec secretKey = keyStoreService.getSecretKey(
                keyStoreAccess,
                KeystoreUtil.keyIdByPrefix(keyStore, PATH_KEY_ID_PREFIX)
        );
        Uri encrypted = legacySymmetricPathEncryptionService.encrypt(secretKey, testURI);
        Uri decrypted = legacySymmetricPathEncryptionService.decrypt(secretKey, encrypted);

        log.debug("Encrypted path: {}", encrypted);

        assertEquals(testPath, decrypted.toASCIIString());
    }

    @Test
    void testFailEncryptPathWithWrongKeyID() throws URISyntaxException {
        String testPath = "path/to/file/";

        log.info("Test path: {}", testPath);

        Uri testURI = new Uri(testPath);

        SecretKeySpec secretKey = keyStoreService.getSecretKey(keyStoreAccess, new KeyID("Invalid key"));
        // secret keys is null, because during key obtain was used incorrect KeyID,
        // so symmetricPathEncryptionService#encrypt throw BaseException(was handled NullPointerException)
        assertThrows(IllegalArgumentException.class, () -> legacySymmetricPathEncryptionService.encrypt(secretKey, testURI));
    }

    @Test
    void testFailEncryptPathWithBrokenEncryptedPath() {
        SecretKeySpec secretKey = keyStoreService.getSecretKey(
                keyStoreAccess,
                KeystoreUtil.keyIdByPrefix(keyStore, PATH_KEY_ID_PREFIX)
        );

        assertThrows(BadPaddingException.class,
                () -> legacySymmetricPathEncryptionService.decrypt(secretKey,
                        new Uri(URI.create("bRQiW8qLNPEy5tO7shfV0w==/k0HooCVlmhHkQFw8mc=="))));
    }

    @Test
    void testFailEncryptPathWithTextPath() {
        SecretKeySpec secretKey = keyStoreService.getSecretKey(
                keyStoreAccess,
                KeystoreUtil.keyIdByPrefix(keyStore, PATH_KEY_ID_PREFIX)
        );

        assertThrows(IllegalBlockSizeException.class,
                () -> legacySymmetricPathEncryptionService.decrypt(secretKey,
                        new Uri("/simple/text/path/")));
    }
}