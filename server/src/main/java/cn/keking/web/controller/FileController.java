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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
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

    /**
     * 文件上传接口
     * @author ZHANGCHAO
     * @param file 上传的文件
     * @return 响应结果
     */
    @PostMapping("/fileUpload")
    public ReturnResponse<Object> fileUpload(@RequestParam("file") MultipartFile file) {
        logger.info("上传文件：{}", file.getOriginalFilename());
        
        // 检查文件是否为空
        if (file.isEmpty()) {
            return ReturnResponse.failure("文件不能为空");
        }
        
        // 获取文件名
        String fileName = WebUtils.getFileNameFromMultipartFile(file);
        
        // 检查文件名是否非法
        if (KkFileUtils.isIllegalFileName(fileName)) {
            return ReturnResponse.failure("不允许上传的文件名");
        }
        
        // 检查文件扩展名是否在禁止列表中
        String fileType = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        String[] prohibitArr = ConfigConstants.getProhibit();
        for (String prohibit : prohibitArr) {
            if (prohibit.equalsIgnoreCase(fileType)) {
                return ReturnResponse.failure("不允许上传 " + fileType + " 类型的文件");
            }
        }
        
        try {
            // 创建demo目录，获取规范化的绝对路径
            File demoFolder = new File(fileDir + demoPath).getAbsoluteFile().getCanonicalFile();
            if (!demoFolder.exists()) {
                boolean created = demoFolder.mkdirs();
                if (!created) {
                    logger.error("创建目录失败：{}", demoFolder.getAbsolutePath());
                    return ReturnResponse.failure("创建目录失败");
                }
            }
            
            // 生成唯一文件名（添加时间戳避免重复）
            String originalFileName = fileName;
            String fileNameWithoutExt = originalFileName.substring(0, originalFileName.lastIndexOf("."));
            String ext = originalFileName.substring(originalFileName.lastIndexOf("."));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
            String uniqueFileName = fileNameWithoutExt + "_" + sdf.format(new Date()) + ext;
            
            // 保存文件 - 使用规范化的绝对路径
            File destFile = new File(demoFolder, uniqueFileName).getCanonicalFile();
            logger.info("准备保存文件到：{}", destFile.getAbsolutePath());
            
            // 使用字节流直接写入文件
            try (InputStream inputStream = file.getInputStream();
                 OutputStream outputStream = Files.newOutputStream(destFile.toPath())) {
                StreamUtils.copy(inputStream, outputStream);
            }
            
            logger.info("文件上传成功：{}", destFile.getAbsolutePath());
            return ReturnResponse.success("文件上传成功：" + uniqueFileName);
        } catch (IOException e) {
            logger.error("文件上传失败", e);
            return ReturnResponse.failure("文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 删除文件接口
     * @author ZHANGCHAO
     * @param fileName 文件名（Base64编码）
     * @param password 删除密码
     * @param request HTTP请求
     * @return 响应结果
     */
    @GetMapping("/deleteFile")
    public ReturnResponse<Object> deleteFile(String fileName, String password, HttpServletRequest request) {
        logger.info("删除文件请求：{}", fileName);
        
        // 检查是否需要验证码
        if (ConfigConstants.getDeleteCaptcha()) {
            // 从session获取验证码和生成时间
            String captchaCode = (String) request.getSession().getAttribute(CAPTCHA_CODE);
            Long captchaTime = (Long) request.getSession().getAttribute(CAPTCHA_GENERATE_TIME);
            
            // 验证验证码
            if (ObjectUtils.isEmpty(captchaCode) || ObjectUtils.isEmpty(password)) {
                return ReturnResponse.failure("删除文件失败，验证码错误！");
            }
            
            // 检查验证码是否过期（5分钟）
            if (System.currentTimeMillis() - captchaTime > 5 * 60 * 1000) {
                return ReturnResponse.failure("删除文件失败，验证码已过期！");
            }
            
            // 验证码不区分大小写
            if (!captchaCode.equalsIgnoreCase(password)) {
                return ReturnResponse.failure("删除文件失败，验证码错误！");
            }
            
            // 清除session中的验证码
            request.getSession().removeAttribute(CAPTCHA_CODE);
            request.getSession().removeAttribute(CAPTCHA_GENERATE_TIME);
        } else {
            // 使用密码验证
            if (!ConfigConstants.getPassword().equals(password)) {
                return ReturnResponse.failure("删除文件失败，密码错误！");
            }
        }
        
        try {
            // 解码文件名
            String decodedFileName = WebUtils.decodeUrl(fileName);
            
            // 去除baseUrl前缀
            String baseUrl = ConfigConstants.getBaseUrl();
            if (decodedFileName.startsWith(baseUrl)) {
                decodedFileName = decodedFileName.substring(baseUrl.length());
            } else if (decodedFileName.startsWith("http://") || decodedFileName.startsWith("https://")) {
                // 处理完整URL的情况
                int index = decodedFileName.indexOf(demoDir);
                if (index != -1) {
                    decodedFileName = decodedFileName.substring(index);
                }
            }
            
            // 检查文件名是否非法
            if (KkFileUtils.isIllegalFileName(decodedFileName)) {
                return ReturnResponse.failure("不允许删除的文件路径");
            }
            
            // 构建文件路径
            String filePath = fileDir + decodedFileName;
            File file = new File(filePath);
            
            // 检查文件是否存在
            if (!file.exists()) {
                return ReturnResponse.failure("文件不存在");
            }
            
            // 检查文件是否在demo目录下
            if (!file.getAbsolutePath().contains(demoPath)) {
                return ReturnResponse.failure("只能删除demo目录下的文件");
            }
            
            // 删除文件
            if (file.delete()) {
                logger.info("文件删除成功：{}", filePath);
                return ReturnResponse.success("文件删除成功");
            } else {
                return ReturnResponse.failure("文件删除失败");
            }
        } catch (Exception e) {
            logger.error("删除文件失败", e);
            return ReturnResponse.failure("删除文件失败：" + e.getMessage());
        }
    }

    /**
     * 生成删除文件验证码
     * @author ZHANGCHAO
     * @param request HTTP请求
     * @param response HTTP响应
     */
    @GetMapping("/deleteFile/captcha")
    public void captcha(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 生成验证码
            String captchaCode = CaptchaUtil.generateCaptchaCode();
            
            // 将验证码保存到session
            request.getSession().setAttribute(CAPTCHA_CODE, captchaCode);
            request.getSession().setAttribute(CAPTCHA_GENERATE_TIME, System.currentTimeMillis());
            
            // 生成验证码图片
            BufferedImage image = CaptchaUtil.generateCaptchaPic(captchaCode);
            
            // 设置响应类型
            response.setContentType("image/jpeg");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Cache-Control", "no-cache");
            response.setDateHeader("Expires", 0);
            
            // 输出图片
            ServletOutputStream out = response.getOutputStream();
            ImageIO.write(image, "JPEG", out);
            out.flush();
            out.close();
        } catch (IOException e) {
            logger.error("生成验证码失败", e);
        }
    }
}
