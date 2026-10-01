package com.example.ecommerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

@Component
public class SigningKeyProvider {
    private final Resource resource;
    private final String password;
    private final String alias;
    private Material cached;

    public SigningKeyProvider(@Value("${signing.keystore.location}") Resource resource,
                              @Value("${signing.keystore.password}") String password,
                              @Value("${signing.keystore.alias}") String alias) {
        this.resource = resource;
        this.password = password;
        this.alias = alias;
    }

    public synchronized Material get() throws Exception {
        if (cached == null) {
            KeyStore store = KeyStore.getInstance("PKCS12");
            char[] chars = password.toCharArray();
            try {
                try (var in = resource.getInputStream()) { store.load(in, chars); }
                var key = store.getKey(alias, chars);
                var chain = store.getCertificateChain(alias);
                if (!(key instanceof PrivateKey privateKey) || !"RSA".equals(privateKey.getAlgorithm())
                        || chain == null || chain.length == 0)
                    throw new GeneralSecurityException("Keystore must contain an RSA key and certificate chain");
                List<X509Certificate> certificates = new ArrayList<>();
                for (var certificate : chain) certificates.add((X509Certificate) certificate);
                boolean[] usage = certificates.getFirst().getKeyUsage();
                if (usage != null && !usage[0]) throw new GeneralSecurityException("Certificate cannot sign documents");
                Signature probe = Signature.getInstance("SHA256withRSA");
                byte[] challenge = new byte[32];
                new SecureRandom().nextBytes(challenge);
                probe.initSign(privateKey); probe.update(challenge);
                byte[] signature = probe.sign();
                probe.initVerify(certificates.getFirst()); probe.update(challenge);
                if (!probe.verify(signature)) throw new GeneralSecurityException("Key does not match certificate");
                cached = new Material(privateKey, List.copyOf(certificates));
            } finally { Arrays.fill(chars, '\0'); }
        }
        for (var certificate : cached.chain()) certificate.checkValidity();
        return cached;
    }

    public record Material(PrivateKey privateKey, List<X509Certificate> chain) {
        public X509Certificate certificate() { return chain.getFirst(); }
    }
}
