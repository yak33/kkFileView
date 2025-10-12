package cn.keking.web.controller;

import cn.keking.config.ConfigConstants;
import cn.keking.model.ReturnResponse;
import cn.keking.utils.CaptchaUtil;
import cn.keking.utils.DateUtils;
import cn.keking.utils.KkFileUtils;
import cn.keking.utils.RarUtils;
import cn.keking.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.keking.utils.CaptchaUtil.CAPTCHA_CODE;
import static cn.keking.utils.CaptchaUtil.CAPTCHA_GENERATE_TIME;

/**
 * @author yudian-it
 * 2017/12/1
 */
@RestController
public class FileController {

    private final Logger logger = LoggerFactory.getLogger(FileController.class);

    private final String fileDir = ConfigConstants.getFileDir();
    private final String demoDir = "demo";

    private final String demoPath = demoDir + File.separator;
    public static final String BASE64_DECODE_ERROR_MSG = "Base64解码失败，请检查你的 %s 是否采用 Base64 + urlEncode 双重编码了！";

    @GetMapping("/listFiles")
    public List<Map<String, String>> getFiles() {
        List<Map<String, String>> list = new ArrayList<>();
        File file = new File(fileDir + demoPath);
        if (file.exists()) {
            File[] files = Objects.requireNonNull(file.listFiles());
            Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            Arrays.stream(files).forEach(file1 -> {
                Map<String, String> fileName = new HashMap<>();
                fileName.put("fileName", demoDir + "/" + file1.getName());
                list.add(fileName);
            });
        }
        return list;
    }

    @GetMapping("/directory")
    public Object directory(String urls) {
        String fileUrl;
        try {
            fileUrl = WebUtils.decodeUrl(urls);
        } catch (Exception ex) {
            String errorMsg = String.format(BASE64_DECODE_ERROR_MSG, "url");
            return ReturnResponse.failure(errorMsg);
        }
        fileUrl = fileUrl.replaceAll("http://", "");
        if (KkFileUtils.isIllegalFileName(fileUrl)) {
            return ReturnResponse.failure("不允许访问的路径:");
        }
        return RarUtils.getTree(fileUrl);
    }
}
