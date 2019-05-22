package de.adorsys.datasafe.business.impl.e2e;

import de.adorsys.datasafe.business.api.storage.StorageService;
import de.adorsys.datasafe.business.api.types.UserID;
import de.adorsys.datasafe.business.api.types.UserIDAuth;
import de.adorsys.datasafe.business.api.types.keystore.ReadKeyPassword;
import de.adorsys.datasafe.business.api.types.resource.AbsoluteLocation;
import de.adorsys.datasafe.business.api.types.resource.BasePrivateResource;
import de.adorsys.datasafe.business.api.types.resource.ResolvedResource;
import de.adorsys.datasafe.business.impl.service.DefaultDatasafeServices;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BasicFunctionalityTest extends WithStorageProvider {

    private static final String MESSAGE_ONE = "Hello here 1";
    private static final String FOLDER = "folder1";
    private static final String PRIVATE_FILE = "secret.txt";
    private static final String PRIVATE_FILE_PATH = FOLDER + "/" + PRIVATE_FILE;
    private static final String SHARED_FILE = "hello.txt";
    private static final String SHARED_FILE_PATH = SHARED_FILE;

    private StorageService storage;
    private URI location;

    @ParameterizedTest
    @MethodSource("storages")
    public void testDFSBasedProfileStorage(WithStorageProvider.StorageDescriptor descriptor) {
        init(descriptor);
        UserID userJohn = new UserID("john");
        assertThat(profileRetrievalService.userExists(userJohn)).isFalse();
        john = registerUser(userJohn.getValue(), descriptor.getLocation());
        assertThat(profileRetrievalService.userExists(userJohn)).isTrue();
        profileRemovalService.deregister(john);
        assertThat(profileRetrievalService.userExists(userJohn)).isFalse();
    }

    @ParameterizedTest
    @MethodSource("storages")
    void testWriteToPrivateListPrivateReadPrivateAndSendToAndReadFromInbox(WithStorageProvider.StorageDescriptor descriptor) {
        init(descriptor);

        registerJohnAndJane(descriptor.getLocation());

        writeDataToPrivate(jane, PRIVATE_FILE_PATH, message_one);

        AbsoluteLocation<ResolvedResource> privateJane = getFirstFileInPrivate(jane);

        String privateContentJane = readPrivateUsingPrivateKey(jane, privateJane.getResource().asPrivate());

        sendToInbox(john.getUserID(), SHARED_FILE_PATH, privateContentJane);

        AbsoluteLocation<ResolvedResource> inboxJohn = getFirstFileInInbox(john);

        String result = readInboxUsingPrivateKey(john, inboxJohn.getResource().asPrivate());

        assertThat(result).isEqualTo(MESSAGE_ONE);
        assertThat(privateJane.getResource().asPrivate().decryptedPath()).asString().isEqualTo(PRIVATE_FILE_PATH);
        assertThat(privateJane.getResource().asPrivate().encryptedPath()).asString().isNotEqualTo(PRIVATE_FILE_PATH);
        validateInboxStructAndEncryption(inboxJohn);
        validatePrivateStructAndEncryption(privateJane);

        removeFromPrivate(jane, privateJane.getResource().asPrivate());
        removeFromInbox(john, inboxJohn.getResource().asPrivate());
    }

    @SneakyThrows
    private void validateInboxStructAndEncryption(AbsoluteLocation<ResolvedResource> expectedInboxResource) {
        List<AbsoluteLocation<ResolvedResource>> inbox = listFiles(it -> it.contains(INBOX_COMPONENT));

        assertThat(inbox).hasSize(1);
        AbsoluteLocation<ResolvedResource> foundResource = inbox.get(0);
        assertThat(foundResource.location()).isEqualTo(expectedInboxResource.location());
        // no path encryption for inbox:
        assertThat(foundResource.location().getPath()).asString().contains(SHARED_FILE);
        // validate encryption on high-level:
        assertThat(storage.read(foundResource)).asString().doesNotContain(MESSAGE_ONE);
    }

    @SneakyThrows
    private void validatePrivateStructAndEncryption(AbsoluteLocation<ResolvedResource> expectedPrivateResource) {
        List<AbsoluteLocation<ResolvedResource>> privateFiles = listFiles(
                it -> it.contains(PRIVATE_FILES_COMPONENT));

        assertThat(privateFiles).hasSize(1);
        AbsoluteLocation<ResolvedResource> foundResource = privateFiles.get(0);
        assertThat(foundResource.location()).isEqualTo(expectedPrivateResource.location());

        // validate encryption on high-level:
        assertThat(foundResource.toString()).doesNotContain(PRIVATE_FILE);
        assertThat(foundResource.toString()).doesNotContain(FOLDER);
        assertThat(storage.read(foundResource)).asString().doesNotContain(MESSAGE_ONE);
    }

    @SneakyThrows
    private List<AbsoluteLocation<ResolvedResource>> listFiles(Predicate<String> pattern) {
        return storage.list(new AbsoluteLocation<>(BasePrivateResource.forPrivate(location)))
                .filter(it -> !it.location().toString().startsWith("."))
                .filter(it -> pattern.test(it.location().toString()))
                .collect(Collectors.toList());
    }

    private void init(WithStorageProvider.StorageDescriptor descriptor) {
        DefaultDatasafeServices datasafeServices = DatasafeServicesProvider
                .defaultDatasafeServices(descriptor.getStorageService(), descriptor.getLocation());
        initialize(datasafeServices);

        this.location = descriptor.getLocation();
        this.storage = descriptor.getStorageService();
    }
}
