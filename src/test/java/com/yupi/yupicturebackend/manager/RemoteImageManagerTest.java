package com.yupi.yupicturebackend.manager;

import com.yupi.yupicturebackend.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 远程图片抓取的 SSRF 防线：
 * isPublicAddress 直接喂 IP 字面量（不触发 DNS），validatePublicHttpUrl 走完整校验链。
 * 所有用例都不依赖外网——字面量解析与 hosts 文件本机即可完成。
 */
class RemoteImageManagerTest {

    private final RemoteImageManager remoteImageManager = new RemoteImageManager();

    private InetAddress addr(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    @Test
    void loopbackAndPrivateRangesAreRejected() throws Exception {
        assertFalse(RemoteImageManager.isPublicAddress(addr("127.0.0.1")));
        assertFalse(RemoteImageManager.isPublicAddress(addr("10.0.0.5")));
        assertFalse(RemoteImageManager.isPublicAddress(addr("192.168.1.10")));
        assertFalse(RemoteImageManager.isPublicAddress(addr("172.16.0.9")));
        assertFalse(RemoteImageManager.isPublicAddress(addr("0.0.0.0")));
        assertFalse(RemoteImageManager.isPublicAddress(addr("224.0.0.5")));
    }

    @Test
    void cloudMetadataAndCarrierNatAreRejected() throws Exception {
        // 169.254.169.254：云厂商元数据服务，SSRF 的头号目标
        assertFalse(RemoteImageManager.isPublicAddress(addr("169.254.169.254")));
        // 100.64.0.0/10：运营商级 NAT，JDK 自带判断不覆盖，靠手工补的分支拦下
        assertFalse(RemoteImageManager.isPublicAddress(addr("100.64.0.1")));
        assertFalse(RemoteImageManager.isPublicAddress(addr("100.127.255.254")));
        // 100.128.x 已出 /10 段，属于公网
        assertTrue(RemoteImageManager.isPublicAddress(addr("100.128.0.1")));
    }

    @Test
    void ipv6UniqueLocalAndLinkLocalAreRejected() throws Exception {
        assertFalse(RemoteImageManager.isPublicAddress(addr("::1")));
        assertFalse(RemoteImageManager.isPublicAddress(addr("fe80::1")));
        // fc00::/7 唯一本地地址（fc00 与 fd00 两段）
        assertFalse(RemoteImageManager.isPublicAddress(addr("fc00::1")));
        assertFalse(RemoteImageManager.isPublicAddress(addr("fd12::1")));
    }

    @Test
    public void publicAddressesPass() throws Exception {
        assertTrue(RemoteImageManager.isPublicAddress(addr("8.8.8.8")));
        assertTrue(RemoteImageManager.isPublicAddress(addr("1.1.1.1")));
        assertTrue(RemoteImageManager.isPublicAddress(addr("172.32.0.1")));
        assertTrue(RemoteImageManager.isPublicAddress(addr("2606:4700::1111")));
    }

    @Test
    void validateRejectsBlankAndMalformed() {
        assertThrows(BusinessException.class, () -> remoteImageManager.validatePublicHttpUrl(null));
        assertThrows(BusinessException.class, () -> remoteImageManager.validatePublicHttpUrl("   "));
        assertThrows(BusinessException.class, () -> remoteImageManager.validatePublicHttpUrl("not-a-url"));
    }

    @Test
    void validateRejectsNonHttpProtocolBeforeAnyLookup() {
        assertThrows(BusinessException.class, () -> remoteImageManager.validatePublicHttpUrl("ftp://example.com/a.jpg"));
        assertThrows(BusinessException.class, () -> remoteImageManager.validatePublicHttpUrl("file:///etc/passwd"));
    }

    @Test
    void validateRejectsIntranetLiteralAndLocalhost() {
        assertThrows(BusinessException.class, () -> remoteImageManager.validatePublicHttpUrl("http://127.0.0.1:8080/api"));
        assertThrows(BusinessException.class, () -> remoteImageManager.validatePublicHttpUrl("http://localhost/a.jpg"));
        assertThrows(BusinessException.class, () -> remoteImageManager.validatePublicHttpUrl("http://192.168.1.1/x.jpg"));
        assertThrows(BusinessException.class, () -> remoteImageManager.validatePublicHttpUrl("http://169.254.169.254/latest/meta-data/"));
        assertThrows(BusinessException.class, () -> remoteImageManager.validatePublicHttpUrl("http://100.64.0.1/x.jpg"));
    }

    @Test
    void validateAcceptsPublicIpLiteral() {
        // IP 字面量不走 DNS，公网地址离线即可通过校验
        assertDoesNotThrow(() -> remoteImageManager.validatePublicHttpUrl("http://8.8.8.8/x.jpg"));
    }
}
