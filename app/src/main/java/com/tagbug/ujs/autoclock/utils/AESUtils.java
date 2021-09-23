package com.tagbug.ujs.autoclock.utils;

import java.security.*;
import java.security.spec.*;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Copied From JavaScript (https://pass.ujs.edu.cn/cas/login)
 */
public class AESUtils {
    public static byte[] AESEncode(String message, String password, String iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            Key key = new SecretKeySpec(password.getBytes(), "AES");
            AlgorithmParameterSpec paramSpec = new IvParameterSpec(iv.getBytes());
            cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
            return cipher.doFinal(message.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String _rds(int len) {
        String $_chars = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
        int _chars_len = $_chars.length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append($_chars.charAt((int) Math.floor(Math.random() * _chars_len)));
        }
        return sb.toString();
    }

    public static String encryptAES(String data, String key) {
        return Base64.getEncoder().encodeToString(AESEncode(_rds(64) + data, key, _rds(16)));
    }
}