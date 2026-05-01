package com.security.filemanager.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.security.filemanager.dto.ForgotPasswordRequest;
import com.security.filemanager.dto.ResetPasswordRequest;
import com.security.filemanager.dto.VerifyResetCodeRequest;
import com.security.filemanager.entity.User;
import com.security.filemanager.mapper.UserMapper;
import com.security.filemanager.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PasswordResetService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private JavaMailSender mailSender;

    @Value("${secure-file.reset-code.ttl-minutes:5}")
    private long ttlMinutes;

    @Value("${secure-file.reset-code.resend-seconds:60}")
    private long resendSeconds;

    @Value("${secure-file.reset-mail.from:}")
    private String mailFrom;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Map<String, ResetCodeEntry> codeStore = new ConcurrentHashMap<>();

    private static class ResetCodeEntry {
        private String email;
        private String code;
        private long expireAt;
        private long lastSentAt;
        private long verifiedUntil;
    }

    public void sendResetCode(ForgotPasswordRequest request) {
        String username = normalize(request.getUsername());
        String email = normalize(request.getEmail());

        User user = userMapper.selectByUsernameIncludeDisabled(username);
        if (user == null) {
            throw new RuntimeException("账号不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new RuntimeException("该账号未绑定邮箱");
        }
        if (!user.getEmail().trim().equalsIgnoreCase(email)) {
            throw new RuntimeException("邮箱与账号预留邮箱不一致");
        }

        String key = buildKey(username);
        long now = System.currentTimeMillis();
        ResetCodeEntry existing = codeStore.get(key);
        if (existing != null && existing.expireAt > now) {
            long remain = existing.lastSentAt + resendSeconds * 1000 - now;
            if (remain > 0) {
                throw new RuntimeException("请求过于频繁，请稍后再试");
            }
        }

        String code = generateCode();
        ResetCodeEntry entry = new ResetCodeEntry();
        entry.email = email;
        entry.code = code;
        entry.lastSentAt = now;
        entry.expireAt = now + ttlMinutes * 60 * 1000;
        entry.verifiedUntil = 0;
        codeStore.put(key, entry);

        sendMail(email, code);
    }

    public void verifyCode(VerifyResetCodeRequest request) {
        String username = normalize(request.getUsername());
        String email = normalize(request.getEmail());
        String code = normalize(request.getCode());

        ResetCodeEntry entry = getValidEntry(username, email, code);
        long now = System.currentTimeMillis();
        entry.verifiedUntil = now + ttlMinutes * 60 * 1000;
    }

    public void resetPassword(ResetPasswordRequest request) {
        String username = normalize(request.getUsername());
        String email = normalize(request.getEmail());
        String code = normalize(request.getCode());

        ResetCodeEntry entry = getValidEntry(username, email, code);
        long now = System.currentTimeMillis();
        if (entry.verifiedUntil <= now) {
            throw new RuntimeException("请先验证验证码");
        }

        User user = userMapper.selectByUsernameIncludeDisabled(username);
        if (user == null) {
            throw new RuntimeException("账号不存在");
        }
        if (user.getEmail() == null || !user.getEmail().trim().equalsIgnoreCase(email)) {
            throw new RuntimeException("邮箱与账号预留邮箱不一致");
        }

        String newPassword = request.getNewPassword();
        if (PasswordUtil.verifyPassword(newPassword, user.getPasswordSalt(), user.getPasswordHash())) {
            throw new RuntimeException("新密码不能与旧密码相同");
        }

        String newSalt = PasswordUtil.generateSalt();
        String newHash = PasswordUtil.hashPassword(newPassword, newSalt);

        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", user.getId());
        wrapper.set("password_salt", newSalt);
        wrapper.set("password_hash", newHash);
        wrapper.set("updated_at", LocalDateTime.now());

        int updated = userMapper.update(null, wrapper);
        if (updated == 0) {
            throw new RuntimeException("密码更新失败，请重试");
        }

        codeStore.remove(buildKey(username));
    }

    private ResetCodeEntry getValidEntry(String username, String email, String code) {
        ResetCodeEntry entry = codeStore.get(buildKey(username));
        if (entry == null) {
            throw new RuntimeException("验证码不存在或已过期");
        }
        long now = System.currentTimeMillis();
        if (entry.expireAt <= now) {
            codeStore.remove(buildKey(username));
            throw new RuntimeException("验证码已过期，请重新获取");
        }
        if (!entry.email.equalsIgnoreCase(email)) {
            throw new RuntimeException("邮箱与账号预留邮箱不一致");
        }
        if (!entry.code.equals(code)) {
            throw new RuntimeException("验证码错误");
        }
        return entry;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildKey(String username) {
        return username.toLowerCase();
    }

    private String generateCode() {
        int code = SECURE_RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    private void sendMail(String to, String code) {
        String subject = "重置密码验证码";
        String text = "您正在进行密码重置操作。验证码: " + code + "\n" +
                "有效期 " + ttlMinutes + " 分钟，请勿泄露给他人。";

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            applyFrom(helper);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("发送邮件失败", e);
            throw new RuntimeException("验证码发送失败，请稍后重试");
        }
    }

    private void applyFrom(MimeMessageHelper helper) throws MessagingException {
        if (mailFrom == null || mailFrom.trim().isEmpty()) {
            return;
        }
        String from = mailFrom.trim();
        int start = from.indexOf('<');
        int end = from.indexOf('>');
        if (start > 0 && end > start) {
            String name = from.substring(0, start).trim();
            String address = from.substring(start + 1, end).trim();
            try {
                helper.setFrom(address, name);
            } catch (Exception e) {
                helper.setFrom(address);
            }
        } else {
            helper.setFrom(from);
        }
    }
}
