package cn.keking.web.filter;

import cn.keking.config.ConfigConstants;
import cn.keking.utils.WebUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

/**
 * @author chenjh
 * @since 2020/2/18 19:13
 */
public class TrustHostFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(TrustHostFilter.class);
    private String notTrustHostHtmlView;

    @Override
    public void init(FilterConfig filterConfig) {
        ClassPathResource classPathResource = new ClassPathResource("web/notTrustHost.html");
        try {
            byte[] bytes = FileCopyUtils.copyToByteArray(classPathResource.getInputStream());
            this.notTrustHostHtmlView = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            // 如果读取失败，提供默认的 HTML 内容
            this.notTrustHostHtmlView = "<html><body><h1>Access Denied</h1><p>Host ${current_host} is not trusted.</p></body></html>";
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String url = WebUtils.getSourceUrl(request);
        
        // 如果 URL 仍然是 URL 编码的，需要先解码
        if (url != null && url.contains("%")) {
            try {
                url = URLDecoder.decode(url, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                logger.error("TrustHostFilter - URL解码失败", e);
            }
        }
        
        String host = WebUtils.getHost(url);
        
        if (host == null) {
            host = "unknown";
        }
        
        if (isNotTrustHost(host)) {
            // 防御性检查，确保 notTrustHostHtmlView 不为 null
            if (this.notTrustHostHtmlView == null) {
                this.notTrustHostHtmlView = "<html><body><h1>Access Denied</h1><p>Host ${current_host} is not trusted.</p></body></html>";
            }
            String html = this.notTrustHostHtmlView.replace("${current_host}", host);
            response.getWriter().write(html);
            response.getWriter().close();
        } else {
            chain.doFilter(request, response);
        }
    }

    public boolean isNotTrustHost(String host) {
        if (CollectionUtils.isNotEmpty(ConfigConstants.getNotTrustHostSet())) {
            return ConfigConstants.getNotTrustHostSet().contains(host);
        }
        if (CollectionUtils.isNotEmpty(ConfigConstants.getTrustHostSet())) {
            return !ConfigConstants.getTrustHostSet().contains(host);
        }
        return false;
    }

    @Override
    public void destroy() {

    }

}
