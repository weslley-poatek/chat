@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.core.preferences.encrypted

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFBooleanRef
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytes
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRangeMake
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

internal object KeychainKey {
    // This identifier must remain stable so a module rename does not orphan saved keys.
    private const val KEYCHAIN_SERVICE = "br.com.weslleycampos.chat.core.preferences"
    private const val MIN_KEY_SIZE = 16
    private const val MAX_KEY_SIZE = 128

    fun loadOrCreate(tag: String, byteSize: Int): ByteArray {
        require(byteSize in MIN_KEY_SIZE..MAX_KEY_SIZE) { "Key size out of range ($byteSize bytes)" }
        return loadExisting(tag)?.takeIf { it.size == byteSize } ?: generateAndStore(tag, byteSize)
    }

    private fun loadExisting(tag: String): ByteArray? = memScoped {
        val service = KEYCHAIN_SERVICE.toCFString()
        val account = tag.toCFString()
        val query = cfMutableDictionary().apply {
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, service)
            set(kSecAttrAccount, account)
            set(kSecMatchLimit, kSecMatchLimitOne)
            set(kSecReturnData, kCFBooleanTrue as CFBooleanRef)
        }
        try {
            val result = alloc<CFTypeRefVar>()
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val data = result.value as? CFDataRef ?: return@memScoped null
                    val bytes = data.toByteArray()
                    CFRelease(data)
                    bytes
                }
                errSecItemNotFound -> null
                else -> error("Keychain lookup failed for '$tag' (OSStatus=$status)")
            }
        } finally {
            CFRelease(service)
            CFRelease(account)
            CFRelease(query)
        }
    }

    private fun generateAndStore(tag: String, byteSize: Int): ByteArray {
        val keyBytes = secureRandomBytes(byteSize)

        val service = KEYCHAIN_SERVICE.toCFString()
        val account = tag.toCFString()
        val cfData = keyBytes.toCFData()

        val deleteQuery = cfMutableDictionary().apply {
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, service)
            set(kSecAttrAccount, account)
        }
        SecItemDelete(deleteQuery)
        CFRelease(deleteQuery)

        val addQuery = cfMutableDictionary().apply {
            set(kSecClass, kSecClassGenericPassword)
            set(kSecAttrService, service)
            set(kSecAttrAccount, account)
            set(kSecValueData, cfData)
            set(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        val status = SecItemAdd(addQuery, null)
        CFRelease(addQuery)
        CFRelease(service)
        CFRelease(account)
        CFRelease(cfData)
        check(status == errSecSuccess) { "SecItemAdd failed for '$tag' (OSStatus=$status)" }
        return keyBytes
    }

    private fun cfMutableDictionary(): CFMutableDictionaryRef = CFDictionaryCreateMutable(
        allocator = kCFAllocatorDefault,
        capacity = 0,
        keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
        valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
    )!!

    private fun CFMutableDictionaryRef.set(key: CValuesRef<*>?, value: CValuesRef<*>?) {
        CFDictionarySetValue(this, key, value)
    }

    private fun String.toCFString(): CFStringRef =
        CFStringCreateWithCString(kCFAllocatorDefault, this, kCFStringEncodingUTF8)!!

    private fun ByteArray.toCFData(): CFDataRef = usePinned { pinned ->
        CFDataCreate(
            allocator = kCFAllocatorDefault,
            bytes = pinned.addressOf(0).reinterpret(),
            length = size.convert(),
        )!!
    }

    private fun CFDataRef.toByteArray(): ByteArray {
        val len = CFDataGetLength(this).toInt()
        if (len <= 0) return ByteArray(0)
        val out = ByteArray(len)
        out.usePinned { pinned ->
            CFDataGetBytes(this, CFRangeMake(0, len.convert()), pinned.addressOf(0).reinterpret())
        }
        return out
    }
}
