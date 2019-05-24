package de.adorsys.datasafe.rest.controller;

import de.adorsys.datasafe.business.api.types.UserID;
import de.adorsys.datasafe.business.api.types.UserIDAuth;
import de.adorsys.datasafe.business.api.types.actions.ListRequest;
import de.adorsys.datasafe.business.api.types.actions.ReadRequest;
import de.adorsys.datasafe.business.api.types.actions.RemoveRequest;
import de.adorsys.datasafe.business.api.types.actions.WriteRequest;
import de.adorsys.datasafe.business.api.types.keystore.ReadKeyPassword;
import de.adorsys.datasafe.business.api.types.resource.BasePrivateResource;
import de.adorsys.datasafe.business.api.types.resource.PrivateResource;
import de.adorsys.datasafe.business.impl.service.DefaultDatasafeServices;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

@Slf4j
@RestController
public class DocumentController {

    private final DefaultDatasafeServices dataSafeService;

    public DocumentController(DefaultDatasafeServices dataSafeService) {
        this.dataSafeService = dataSafeService;
    }

    @SneakyThrows
    @GetMapping(value = "/document/{path:.*}", produces = APPLICATION_OCTET_STREAM_VALUE)
    public void readDocument(@RequestHeader String user,
                             @RequestHeader String password,
                             @PathVariable String path,
                             HttpServletResponse response) {
        UserIDAuth userIDAuth = new UserIDAuth(new UserID(user), new ReadKeyPassword(password));
        PrivateResource resource = BasePrivateResource.forPrivate(URI.create("./" + path));
        ReadRequest<UserIDAuth, PrivateResource> request = ReadRequest.forPrivate(userIDAuth, resource);
        InputStream inputStream = dataSafeService.privateService().read(request);
        OutputStream outputStream = response.getOutputStream();
        StreamUtils.copy(inputStream, outputStream);
        inputStream.close();
        outputStream.close();
        log.debug("User: {}, read private file from: {}", user, resource);
    }

    @SneakyThrows
    @PutMapping(value = "/document/{path:.*}", consumes = APPLICATION_OCTET_STREAM_VALUE)
    public void writeDocument(@RequestHeader String user,
                              @RequestHeader String password,
                              InputStream inputStream,
                              @PathVariable String path) {
        UserIDAuth userIDAuth = new UserIDAuth(new UserID(user), new ReadKeyPassword(password));
        OutputStream stream = dataSafeService.privateService().write(WriteRequest.forDefaultPrivate(userIDAuth, path));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StreamUtils.copy(inputStream, outputStream);
        stream.write(outputStream.toByteArray());
        stream.close();
        log.debug("User: {}, write private file to: {}", user, path);
    }

    @GetMapping("/documents/{path:.*}")
    public List<String> listDocuments(@RequestHeader String user,
                                      @RequestHeader String password,
                                      @PathVariable(required = false) String path) {
        UserIDAuth userIDAuth = new UserIDAuth(new UserID(user), new ReadKeyPassword(password));
        path = "./" + Objects.toString(path, "");
        List<String> documetnList = dataSafeService.privateService().list(ListRequest.forDefaultPrivate(userIDAuth, path))
                .map(e -> e.getResource().asPrivate().decryptedPath().getPath())
                .collect(Collectors.toList());
        log.debug("List for path {} returned {} items", path, documetnList.size());
        return documetnList;
    }

    @DeleteMapping("/document/{path:.*}")
    public void deleteDocument(@RequestHeader String user,
                               @RequestHeader String password,
                               @PathVariable String path) {
        UserIDAuth userIDAuth = new UserIDAuth(new UserID(user), new ReadKeyPassword(password));
        PrivateResource resource = BasePrivateResource.forPrivate(URI.create(path));
        RemoveRequest<UserIDAuth, PrivateResource> request = RemoveRequest.forPrivate(userIDAuth, resource);
        dataSafeService.privateService().remove(request);
        log.debug("User: {}, delete private file: {}", user, resource);
    }
}
