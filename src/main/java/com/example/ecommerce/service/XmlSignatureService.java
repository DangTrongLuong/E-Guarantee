package com.example.ecommerce.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.*;
import javax.xml.crypto.dsig.spec.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class XmlSignatureService {
    private final SigningKeyProvider keys;

    public byte[] sign(byte[] bytes) throws Exception {
        var doc = SecureXml.parse(bytes);
        if (doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").getLength() != 0)
            throw new IllegalArgumentException("XML already contains a signature");
        var material = keys.get();
        var factory = XMLSignatureFactory.getInstance("DOM");
        var transforms = List.of(factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                factory.newTransform(CanonicalizationMethod.EXCLUSIVE, (TransformParameterSpec) null));
        var ref = factory.newReference("", factory.newDigestMethod(DigestMethod.SHA256, null), transforms, null, null);
        var info = factory.newSignedInfo(
                factory.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE, (C14NMethodParameterSpec) null),
                factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null), List.of(ref));
        var keyFactory = factory.getKeyInfoFactory();
        var keyInfo = keyFactory.newKeyInfo(List.of(keyFactory.newX509Data(material.chain())));
        var context = new DOMSignContext(material.privateKey(), doc.getDocumentElement());
        context.setDefaultNamespacePrefix("ds");
        factory.newXMLSignature(info, keyInfo).sign(context);
        return SecureXml.serialize(doc);
    }

    public void verify(byte[] bytes) throws Exception {
        var doc = SecureXml.parse(bytes);
        var nodes = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (nodes.getLength() != 1 || nodes.item(0).getParentNode() != doc.getDocumentElement())
            throw new java.security.SignatureException("Expected one signature directly under XML root");
        var factory = XMLSignatureFactory.getInstance("DOM");
        var context = new DOMValidateContext(keys.get().certificate().getPublicKey(), nodes.item(0));
        context.setProperty("org.jcp.xml.dsig.secureValidation", true);
        var defaultDereferencer = factory.getURIDereferencer();
        context.setURIDereferencer((reference, ctx) -> {
            if (!"".equals(reference.getURI())) throw new javax.xml.crypto.URIReferenceException("External references forbidden");
            return defaultDereferencer.dereference(reference, ctx);
        });
        var signature = factory.unmarshalXMLSignature(context);
        var signed = signature.getSignedInfo();
        if (!SignatureMethod.RSA_SHA256.equals(signed.getSignatureMethod().getAlgorithm())
                || !CanonicalizationMethod.EXCLUSIVE.equals(signed.getCanonicalizationMethod().getAlgorithm())
                || signed.getReferences().size() != 1 || !signature.getObjects().isEmpty())
            throw new java.security.SignatureException("Unexpected XML signature structure");
        var ref = (Reference) signed.getReferences().getFirst();
        var transforms = ref.getTransforms();
        if (!"".equals(ref.getURI()) || !DigestMethod.SHA256.equals(ref.getDigestMethod().getAlgorithm())
                || transforms.size() != 2
                || !Transform.ENVELOPED.equals(((Transform) transforms.get(0)).getAlgorithm())
                || !CanonicalizationMethod.EXCLUSIVE.equals(((Transform) transforms.get(1)).getAlgorithm())
                || !signature.validate(context))
            throw new java.security.SignatureException("XML signature verification failed");
    }
}
