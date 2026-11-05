package com.landata.shared.crypto;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.landata.shared.config.LandataProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {
  private final Aead aead;
  private final String keyId;

  public SecretCipher(LandataProperties properties) {
    try {
      AeadConfig.register();
      Path path = Path.of(properties.getCrypto().getKeysetPath());
      KeysetHandle handle;
      if (Files.exists(path)) {
        handle = CleartextKeysetHandle.read(JsonKeysetReader.withFile(path.toFile()));
      } else {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
        CleartextKeysetHandle.write(handle, JsonKeysetWriter.withFile(path.toFile()));
      }
      this.aead = handle.getPrimitive(Aead.class);
      this.keyId = String.valueOf(handle.getKeysetInfo().getPrimaryKeyId());
    } catch (Exception ex) {
      throw new IllegalStateException("Could not load the Tink keyset", ex);
    }
  }

  public String keyId() {
    return keyId;
  }

  public byte[] encrypt(String plaintext, byte[] associatedData) {
    try {
      return aead.encrypt(plaintext.getBytes(StandardCharsets.UTF_8), associatedData);
    } catch (Exception ex) {
      throw new IllegalStateException("Could not encrypt secret", ex);
    }
  }

  public String decrypt(byte[] ciphertext, byte[] associatedData) {
    try {
      return new String(aead.decrypt(ciphertext, associatedData), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException("Could not decrypt secret", ex);
    }
  }
}
