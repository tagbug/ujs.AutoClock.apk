package com.tagbug.ujs.autoclock.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.*

/**
 * Object转换Base64字符串的序列化/反序列化工具
 */
object ObjectToBase64 {

    /**
     * 将Object序列化并转为Base64字符串
     *
     * @param obj 对象
     * @return 序列化后的Base64字符串
     */
    fun encode(obj: Any?): String? {
        if (obj == null) {
            return null
        }
        val byteOutput = ByteArrayOutputStream()
        val objOutput = ObjectOutputStream(byteOutput)
        objOutput.writeObject(obj)
        val encodedBytes = Base64.getEncoder().encode(byteOutput.toByteArray())
        return String(encodedBytes)
    }

    /**
     * 将Base64字符串反序列化为对象
     *
     * @param base64Str Base64字符串
     * @return 反序列化后的对象
     */
    fun decode(base64Str: String?): Any? {
        if (base64Str == null || base64Str.isEmpty()) {
            return null
        }
        val decodedBytes = Base64.getDecoder().decode(base64Str)
        val byteInput = ByteArrayInputStream(decodedBytes)
        val objInput = ObjectInputStream(byteInput)
        return objInput.readObject()
    }
}