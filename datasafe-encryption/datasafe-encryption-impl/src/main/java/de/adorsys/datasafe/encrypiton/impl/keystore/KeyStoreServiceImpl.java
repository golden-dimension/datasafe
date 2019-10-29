package de.adorsys.datasafe.encrypiton.impl.keystore;

import com.google.common.collect.ImmutableMap;
import de.adorsys.datasafe.encrypiton.api.keystore.KeyStoreService;
import de.adorsys.datasafe.encrypiton.api.types.encryption.KeyCreationConfig;
import de.adorsys.datasafe.encrypiton.api.types.keystore.*;
import de.adorsys.datasafe.types.api.context.annotations.RuntimeDelegate;
import de.adorsys.datasafe.types.api.types.ReadKeyPassword;
import de.adorsys.datasafe.types.api.types.ReadStorePassword;
import de.adorsys.keymanagement.api.types.KeySetTemplate;
import de.adorsys.keymanagement.config.keystore.KeyStoreConfig;
import de.adorsys.keymanagement.api.types.source.KeySet;
import de.adorsys.keymanagement.api.types.template.generated.Encrypting;
import de.adorsys.keymanagement.api.types.template.generated.Secret;
import de.adorsys.keymanagement.juggler.services.BCJuggler;
import de.adorsys.keymanagement.juggler.services.DaggerBCJuggler;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static de.adorsys.datasafe.encrypiton.api.types.encryption.KeyCreationConfig.*;

@Slf4j
@RuntimeDelegate
public class KeyStoreServiceImpl implements KeyStoreService {

    private final KeyStoreConfig config;
    private final String passwordStoreEncAlgo;

    @Inject
    public KeyStoreServiceImpl(KeyStoreConfig config) {
        this.config = config;
        this.passwordStoreEncAlgo = this.config.getPasswordKeysAlgo();
    }

    @Override
    public KeyStore createKeyStore(KeyStoreAuth keyStoreAuth,
                                   KeyCreationConfig config) {

        return createKeyStore(
                keyStoreAuth,
                config,
                ImmutableMap.of(
                        new KeyID(PATH_KEY_ID_PREFIX + UUID.randomUUID().toString()), Optional.empty(),
                        new KeyID(PATH_KEY_ID_PREFIX_CTR + UUID.randomUUID().toString()), Optional.empty(),
                        new KeyID(DOCUMENT_KEY_ID_PREFIX + UUID.randomUUID().toString()), Optional.empty()
                )
        );
    }

    @Override
    public KeyStore createKeyStore(KeyStoreAuth keyStoreAuth,
                                   KeyCreationConfig keyConfig,
                                   Map<KeyID, Optional<SecretKeyEntry>> secretKeys) {

        log.debug("start create keystore ");
        // TODO, hier also statt der StoreID nun das
        String serverKeyPairAliasPrefix = UUID.randomUUID().toString();
        log.debug("keystoreid = {}", serverKeyPairAliasPrefix);

        BCJuggler juggler = DaggerBCJuggler.builder().build();
        EncryptingKeyCreationCfg encConf = keyConfig.getEncrypting();
        Supplier<char[]> passSupplier = () -> keyStoreAuth.getReadKeyPassword().getValue();
        KeySetTemplate template = KeySetTemplate.builder()
                .generatedEncryptionKeys(Encrypting.with()
                        .algo(encConf.getAlgo())
                        .sigAlgo(encConf.getSigAlgo())
                        .keySize(encConf.getSize())
                        .prefix("ENC").password(passSupplier)
                        .build()
                        .repeat(keyConfig.getEncKeyNumber()))
                .generatedSecretKeys(secretKeys.keySet().stream()
                        .map(it -> Secret.with().prefix(it.getValue()).build())
                        .collect(Collectors.toList()))
                .build();
        KeySet keySet = juggler.generateKeys().fromTemplate(template);

        KeyStore ks = juggler.toKeystore().withConfig(config)
                .generate(keySet, passSupplier);
        log.debug("finished create keystore ");
        return ks;
    }

    @Override
    @SneakyThrows
    public KeyStore updateKeyStoreReadKeyPassword(KeyStore current,
                                                  KeyStoreAuth currentCredentials,
                                                  KeyStoreAuth newCredentials) {
        KeyStore newKeystore = KeyStoreServiceImplBaseFunctions.newKeyStore(config);
        Enumeration<String> aliases = current.aliases();

        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Key currentKey = current.getKey(alias, currentCredentials.getReadKeyPassword().getValue());
            newKeystore.setKeyEntry(
                    alias,
                    currentKey,
                    newCredentials.getReadKeyPassword().getValue(),
                    current.getCertificateChain(alias)
            );
        }

        return newKeystore;
    }

    @Override
    @SneakyThrows
    public List<PublicKeyIDWithPublicKey> getPublicKeys(KeyStoreAccess keyStoreAccess) {
        log.debug("get public keys");
        List<PublicKeyIDWithPublicKey> result = new ArrayList<>();
        KeyStore keyStore = keyStoreAccess.getKeyStore();
        for (Enumeration<String> keyAliases = keyStore.aliases(); keyAliases.hasMoreElements(); ) {
            final String keyAlias = keyAliases.nextElement();
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(keyAlias);
            if (cert == null) continue; // skip
            boolean[] keyUsage = cert.getKeyUsage();
            // digitalSignature (0), nonRepudiation (1), keyEncipherment (2), dataEncipherment (3),
            // keyAgreement (4), keyCertSign (5), cRLSign (6), encipherOnly (7), decipherOnly (8)
            if (keyUsage[2] || keyUsage[3] || keyUsage[4]) {
                result.add(new PublicKeyIDWithPublicKey(new KeyID(keyAlias), cert.getPublicKey()));
            }
        }
        return result;
    }

    @Override
    @SneakyThrows
    public PrivateKey getPrivateKey(KeyStoreAccess keyStoreAccess, KeyID keyID) {
        ReadKeyPassword readKeyPassword = keyStoreAccess.getKeyStoreAuth().getReadKeyPassword();
        KeyStore keyStore = keyStoreAccess.getKeyStore();
        PrivateKey privateKey;
        privateKey = (PrivateKey) keyStore.getKey(keyID.getValue(), readKeyPassword.getValue());
        return privateKey;
    }

    @Override
    @SneakyThrows
    public SecretKeySpec getSecretKey(KeyStoreAccess keyStoreAccess, KeyID keyID) {
        KeyStore keyStore = keyStoreAccess.getKeyStore();
        char[] password = keyStoreAccess.getKeyStoreAuth().getReadKeyPassword().getValue();
        return (SecretKeySpec) keyStore.getKey(keyID.getValue(), password);
    }

    @Override
    @SneakyThrows
    public void addPasswordBasedSecretKey(KeyStoreAccess keyStoreAccess, String alias, char[] secret) {
        PBEKeySpec pbeKeySpec = new PBEKeySpec(secret);
        SecretKeyFactory keyFac = SecretKeyFactory.getInstance(passwordStoreEncAlgo);
        SecretKey key = keyFac.generateSecret(pbeKeySpec);
        keyStoreAccess.getKeyStore()
                .setKeyEntry(
                        alias,
                        key,
                        keyStoreAccess.getKeyStoreAuth().getReadKeyPassword().getValue(),
                        null
                );
    }

    @Override
    @SneakyThrows
    public void removeKey(KeyStoreAccess keyStoreAccess, String alias) {
        keyStoreAccess.getKeyStore().deleteEntry(alias);
    }

    @Override
    @SneakyThrows
    public byte[] serialize(KeyStore store, ReadStorePassword readStorePassword) {
        BCJuggler juggler = DaggerBCJuggler.builder().build();
        return juggler.serializeDeserialize().serialize(store, readStorePassword::getValue);
    }

    @Override
    @SneakyThrows
    public KeyStore deserialize(byte[] payload, ReadStorePassword readStorePassword) {
        BCJuggler juggler = DaggerBCJuggler.builder().build();
        return juggler.serializeDeserialize().withConfig(config).deserialize(payload, readStorePassword::getValue);
    }
}
