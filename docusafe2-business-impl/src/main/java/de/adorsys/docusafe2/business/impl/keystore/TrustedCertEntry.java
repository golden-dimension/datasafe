package de.adorsys.docusafe2.business.impl.keystore;

import de.adorsys.docusafe2.business.api.keystore.types.KeyEntry;
import org.bouncycastle.cert.X509CertificateHolder;

public interface TrustedCertEntry extends KeyEntry {
    X509CertificateHolder getCertificate();
}