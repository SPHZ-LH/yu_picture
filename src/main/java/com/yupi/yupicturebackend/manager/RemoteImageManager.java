package com.yupi.yupicturebackend.manager;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.yupi.yupicturebackend.constant.PictureSearchConstant;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 远程图片抓取：把用户给的图片地址安全地落到本地临时文件。
 * <p>
 * 以图搜图接口对匿名开放，服务端去抓一个由请求方指定的地址是典型的 SSRF 面——
 * 不设防的话，请求方可以拿它探内网端口、读云厂商元数据（169.254.169.254）。
 * 因此这里做三件事：<b>每一跳</b>都校验协议与解析结果必须落在公网、限制跳转次数、
 * 边下边数、超限即断（不能只信 Content-Length，它可以缺失或撒谎）。
 * <p>
 * 跳转必须自己接管：HttpUtil 的自动跳转不会再校验目标地址，
 * 攻击者只要用一个能 302 到内网的公网域名就能绕过入口校验。
 */
@Slf4j
@Service
public class RemoteImageManager {

    /**
     * 单张查询图大小上限：与本地文件上传路径共用同一口径（见 {@link PictureSearchConstant}）。
     * Content-Length 可能缺失或撒谎，实际以流式读到的字节数为准。
     */
    private static final long MAX_IMAGE_BYTES = PictureSearchConstant.MAX_QUERY_IMAGE_BYTES;

    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private static final int READ_TIMEOUT_MS = 15_000;

    /** 最大跳转次数：正常图床很少超过 2 跳（http → https → CDN） */
    private static final int MAX_REDIRECTS = 3;

    /**
     * 下载远程图片到指定临时文件。
     *
     * @param imageUrl 用户提供的图片地址
     * @param dest     目标临时文件（调用方负责删除）
     * @return 由文件头判定出的图片后缀（jpg/png/webp），可直接用于对象存储命名
     */
    public String downloadToTempFile(String imageUrl, File dest) {
        String currentUrl = StrUtil.trim(imageUrl);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            validatePublicHttpUrl(currentUrl);
            HttpResponse response = null;
            try {
                // 必须走 executeAsync 自己读流：execute() 会先把整个响应体读进内存（Hutool 的
                // forceSync），那样下面那个大小上限就形同虚设——恶意响应可以先把内存打满再被拒绝。
                response = HttpUtil.createRequest(Method.GET, currentUrl)
                        .setFollowRedirects(false)
                        .setConnectionTimeout(CONNECT_TIMEOUT_MS)
                        .timeout(READ_TIMEOUT_MS)
                        .executeAsync();
                int status = response.getStatus();
                if (isRedirect(status)) {
                    String location = response.header("Location");
                    ThrowUtils.throwIf(StrUtil.isBlank(location), ErrorCode.PARAMS_ERROR, "图片地址跳转异常");
                    currentUrl = resolveLocation(currentUrl, location);
                    continue;
                }
                ThrowUtils.throwIf(status != HttpStatus.HTTP_OK,
                        ErrorCode.PARAMS_ERROR, "图片地址无法访问（HTTP " + status + "）");
                checkContentType(response);
                writeBodyWithLimit(response, dest);
                return resolveSuffixFromSignature(dest);
            } catch (BusinessException e) {
                // 自己抛的业务错误（格式不符、超过大小上限等）原样冒出去，别被下面的兜底改写成"抓取失败"
                throw e;
            } catch (IOException | RuntimeException e) {
                // Hutool 把网络异常包成 IORuntimeException / HttpException（都不是 IOException），
                // 漏掉的话会冒到全局处理器变成没有上下文的「系统错误」，日志里也看不到是哪个地址出的问题。
                // 兜底捕获 RuntimeException 是为了不依赖 Hutool 具体包了哪一层——真正的业务错误已在上面拦下。
                log.warn("远程图片抓取失败, url={}", currentUrl, e);
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "图片抓取失败或超时，请确认地址可直接打开，或改用本地上传");
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址跳转次数过多");
    }

    /**
     * 校验地址可安全访问：仅 http/https，且域名解析出的<b>每一个</b>地址都必须是公网地址。
     * <p>
     * 以图搜图抓图与 URL 上传图片共用这条校验；抓图链路在<b>每一跳</b>都会再调一次
     * （域名可能在不同跳解析出不同地址，DNS rebinding 就是要靠逐跳重查来挡）。
     * 域名可能同时解析出公网与内网地址（DNS 轮询/多记录），只抽查一个等于没查。
     * 也顺带覆盖了直接写内网 IP 的情况——IP 字面量同样会走解析。
     */
    public void validatePublicHttpUrl(String url) {
        ThrowUtils.throwIf(StrUtil.isBlank(url), ErrorCode.PARAMS_ERROR, "图片地址不能为空");
        URL parsed;
        try {
            parsed = new URL(url);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址格式不正确");
        }
        String protocol = StrUtil.nullToEmpty(parsed.getProtocol()).toLowerCase(Locale.ROOT);
        ThrowUtils.throwIf(!"http".equals(protocol) && !"https".equals(protocol),
                ErrorCode.PARAMS_ERROR, "仅支持 http/https 图片地址");
        String host = parsed.getHost();
        ThrowUtils.throwIf(StrUtil.isBlank(host), ErrorCode.PARAMS_ERROR, "图片地址缺少域名");

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址的域名无法解析");
        }
        ThrowUtils.throwIf(addresses.length == 0, ErrorCode.PARAMS_ERROR, "图片地址的域名无法解析");
        for (InetAddress address : addresses) {
            ThrowUtils.throwIf(!isPublicAddress(address),
                    ErrorCode.PARAMS_ERROR, "图片地址指向内网，已拒绝访问");
        }
    }

    /**
     * 是否公网地址。
     * <p>
     * {@link InetAddress} 自带的几类判断不覆盖 IPv6 唯一本地地址（fc00::/7）
     * 与 IPv4 运营商级 NAT（100.64.0.0/10），这两段同样不该被访问，故手工补齐。
     * <p>
     * 包内可见 + 静态是为了单测可以直接喂 IP 字面量（不触发 DNS / 网络）。
     */
    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            // IPv6 唯一本地地址 fc00::/7
            return (bytes[0] & 0xFE) != 0xFC;
        }
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 100.64.0.0/10 运营商级 NAT
            return !(first == 100 && second >= 64 && second <= 127);
        }
        return false;
    }

    private boolean isRedirect(int status) {
        return status == HttpStatus.HTTP_MOVED_PERM
                || status == HttpStatus.HTTP_MOVED_TEMP
                || status == 303
                || status == 307
                || status == 308;
    }

    /** 相对跳转（Location 只给路径）要基于当前地址补全，再交给下一跳校验 */
    private String resolveLocation(String currentUrl, String location) {
        try {
            return new URL(new URL(currentUrl), location.trim()).toString();
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片地址跳转目标不合法");
        }
    }

    /** Content-Type 只用来提前止损：省得把明显不是图片的响应整个下下来 */
    private void checkContentType(HttpResponse response) {
        String contentType = response.header("Content-Type");
        if (StrUtil.isBlank(contentType)) {
            return;
        }
        String mimeType = StrUtil.subBefore(contentType, ";", false).trim().toLowerCase(Locale.ROOT);
        ThrowUtils.throwIf(!mimeType.startsWith("image/"),
                ErrorCode.PARAMS_ERROR, "该地址不是图片（Content-Type: " + mimeType + "）");
    }

    /** 边下边数：Content-Length 可能缺失或是假的，只能以实际读到的字节为准 */
    private void writeBodyWithLimit(HttpResponse response, File dest) throws IOException {
        long total = 0;
        try (InputStream in = response.bodyStream();
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                ThrowUtils.throwIf(total > MAX_IMAGE_BYTES,
                        ErrorCode.PARAMS_ERROR, PictureSearchConstant.QUERY_IMAGE_OVER_SIZE_MESSAGE);
                out.write(buffer, 0, read);
            }
        }
        ThrowUtils.throwIf(total == 0, ErrorCode.PARAMS_ERROR, "图片内容为空");
    }

    /**
     * 按文件头判定真实格式，而不是信 Content-Type：后者可以随便填，
     * 而判出来的后缀要参与对象存储命名与检索格式白名单，判错会把脏数据带进下游。
     */
    private String resolveSuffixFromSignature(File file) {
        byte[] header = new byte[12];
        int length;
        try (InputStream in = new FileInputStream(file)) {
            length = in.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询图片读取失败");
        }
        ThrowUtils.throwIf(length < 12, ErrorCode.PARAMS_ERROR, "无法识别的图片格式");
        if (isJpeg(header)) {
            return "jpg";
        }
        if (isPng(header)) {
            return "png";
        }
        if (isWebp(header)) {
            return "webp";
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 jpg/png/webp 图片");
    }

    private boolean isJpeg(byte[] header) {
        return (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] header) {
        return (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G';
    }

    /** WebP：RIFF....WEBP，中间 4 字节是文件长度 */
    private boolean isWebp(byte[] header) {
        return header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }
}
