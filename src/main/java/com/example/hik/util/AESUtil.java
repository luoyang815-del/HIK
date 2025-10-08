package com.example.hik.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;

public class AESUtil {
    public static String encrypt(String secretKey, String plainText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes("UTF-8"), "AES");
            IvParameterSpec iv = new IvParameterSpec(secretKey.getBytes("UTF-8"));
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            byte[] out = cipher.doFinal(plainText.getBytes("UTF-8"));
            return Base64.encodeBase64String(out);
        } catch (Exception e) {
            throw new RuntimeException("AESUtil encrypt error", e);
        }
    }

    public static String decrypt(String secretKey, String cipherTextBase64) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec key = new SecretKeySpec(secretKey.getBytes("UTF-8"), "AES");
            IvParameterSpec iv = new IvParameterSpec(secretKey.getBytes("UTF-8"));
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            byte[] bytes = org.apache.commons.codec.binary.Base64.decodeBase64(cipherTextBase64);
            byte[] out = cipher.doFinal(bytes);
            return new String(out, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("AESUtil decrypt error", e);
        }
    }
}
