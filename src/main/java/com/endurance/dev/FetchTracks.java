package com.endurance.dev;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 下载器：把赛道 SVG 之类的外部文件抓下来。
 *
 * <p>为什么用 Java 而不用 PowerShell/curl：这台机器的 Windows SChannel 在沙箱里
 * 拿不到凭据（SEC_E_NO_CREDENTIALS），所有走 SChannel 的 HTTPS 都会握手失败。
 * Java 用自带的 TLS 实现，完全绕开这个问题。</p>
 *
 * <p>用法：{@code java ... com.endurance.dev.FetchTracks <url> <输出文件> [代理端口]}</p>
 */
public final class FetchTracks {

    private static final String DEFAULT_PROXY = "127.0.0.1:7890";

    private FetchTracks() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("用法: FetchTracks <url> <输出文件> [代理 host:port]");
            System.out.println("      代理传 none 表示直连");
            return;
        }
        String url = args[0];
        Path target = Path.of(args[1]);
        String proxySpec = args.length > 2 ? args[2] : DEFAULT_PROXY;

        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        byte[] body = fetch(url, proxySpec);
        Files.write(target, body);
        System.out.println("OK " + target + "  " + body.length + " bytes");
        String head = new String(body, 0, Math.min(160, body.length),
                java.nio.charset.StandardCharsets.UTF_8);
        System.out.println(head.replace('\n', ' ').replace('\r', ' '));
    }

    /** 抓一个 URL 的内容。 */
    public static byte[] fetch(String url, String proxySpec) throws IOException, InterruptedException {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.ALWAYS);
        if (proxySpec != null && !"none".equalsIgnoreCase(proxySpec)) {
            String[] parts = proxySpec.split(":");
            builder.proxy(ProxySelector.of(new InetSocketAddress(
                    parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 7890)));
        }
        HttpClient client = builder.build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(40))
                .header("User-Agent", "EnduranceStrategy/1.0 (course project)")
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + " from " + url);
        }
        return response.body();
    }
}
