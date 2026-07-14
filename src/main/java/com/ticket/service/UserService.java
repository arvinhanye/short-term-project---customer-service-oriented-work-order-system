package com.ticket.service;

import com.ticket.dao.mysql.ProfileDAO;
import com.ticket.dao.mysql.UserDAO;
import com.ticket.exception.BusinessException;
import com.ticket.model.Profile;
import com.ticket.model.User;
import com.ticket.util.PasswordUtil;
import com.ticket.util.MySQLDBUtil;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

public class UserService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    static final int LOGIN_LOCK_MINUTES = 10;
    private final UserDAO userDAO = new UserDAO();
    private final ProfileDAO profileDAO = new ProfileDAO();
    private final ActionLogService actionLogService = new ActionLogService();
    private final AuditLogService auditLogService = new AuditLogService();

    public User register(String username, String password, String email, String phone) {
        validateRegistration(username, password, email, phone);
        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim();
        String normalizedPhone = phone.trim();
        if (userDAO.findByUsername(normalizedUsername) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (userDAO.findByEmail(normalizedEmail) != null) {
            throw new BusinessException("邮箱已存在");
        }
        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(PasswordUtil.hashPassword(password, normalizedUsername, normalizedEmail, normalizedPhone));
        user.setEmail(normalizedEmail);
        user.setPhone(normalizedPhone);
        user.setRole("USER");
        user.setStatus(1);
        user.setFailedLoginAttempts(0);
        user.setMustChangePassword(0);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        Long userId;
        try (Connection connection = MySQLDBUtil.getWriteConnection()) {
            connection.setAutoCommit(false);
            try {
                userId = userDAO.insert(connection, user);
                connection.commit();
                user.setUserId(userId);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception ex) {
            throw new BusinessException("注册失败", ex);
        }
        auditLogService.write(String.valueOf(userId), "ADMIN_OPERATION", "INFO", "用户注册成功", "USER_REGISTER");
        return user;
    }

    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException("请输入用户名和密码");
        }
        User user = userDAO.findByUsernameForAuthentication(username.trim());
        if (user == null) {
            PasswordUtil.consumeDummyHash(password);
            auditLogService.write(null, "LOGIN_FAIL", "WARN", "登录失败", "USER_LOGIN");
            throw new BusinessException("用户名或密码错误");
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            auditLogService.write(String.valueOf(user.getUserId()), "LOGIN_FAIL", "WARN",
                "账号处于登录保护期", "USER_LOGIN_THROTTLED");
            throw new BusinessException("登录失败次数过多，请稍后再试");
        }
        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(now)) {
            userDAO.updateLoginSecurity(user.getUserId(), 0, null);
        }
        if (!PasswordUtil.matches(password, user.getPasswordHash())) {
            User failedState = userDAO.recordFailedLogin(
                user.getUserId(), MAX_FAILED_LOGIN_ATTEMPTS, LOGIN_LOCK_MINUTES);
            auditLogService.write(String.valueOf(user.getUserId()), "LOGIN_FAIL", "WARN", "登录失败", "USER_LOGIN");
            if (failedState != null && failedState.getLockedUntil() != null
                    && failedState.getLockedUntil().isAfter(now)) {
                throw new BusinessException("登录失败次数过多，账号已临时保护 10 分钟");
            }
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            auditLogService.write(String.valueOf(user.getUserId()), "USER_DISABLED", "WARN", "用户已被禁用", "USER_LOGIN");
            throw new BusinessException("用户已被禁用");
        }
        userDAO.updateLoginSecurity(user.getUserId(), 0, null);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (PasswordUtil.needsRehash(user.getPasswordHash())) {
            userDAO.updatePasswordHash(user.getUserId(), PasswordUtil.rehashPassword(password));
        }
        actionLogService.write(String.valueOf(user.getUserId()), null, "LOGIN");
        auditLogService.write(String.valueOf(user.getUserId()), "LOGIN", "INFO", "登录成功", "USER_LOGIN");
        return withoutPasswordHash(user);
    }

    public void changePassword(User actor, String currentPassword, String newPassword) {
        requireActiveUser(actor);
        User stored = userDAO.findByIdForSecurity(actor.getUserId());
        if (stored == null || !PasswordUtil.matches(currentPassword, stored.getPasswordHash())) {
            auditLogService.write(String.valueOf(actor.getUserId()), "PASSWORD_CHANGE_FAIL", "WARN",
                "修改密码时当前密码校验失败", "CHANGE_PASSWORD");
            throw new BusinessException("当前密码不正确");
        }
        if (PasswordUtil.matches(newPassword, stored.getPasswordHash())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }
        String newHash = PasswordUtil.hashPassword(newPassword,
            stored.getUsername(), stored.getEmail(), stored.getPhone());
        if (userDAO.updatePasswordSecurity(stored.getUserId(), newHash, false, LocalDateTime.now()) != 1) {
            throw new BusinessException("密码修改失败");
        }
        actor.setMustChangePassword(0);
        actor.setPasswordChangedAt(LocalDateTime.now());
        auditLogService.write(String.valueOf(actor.getUserId()), "PASSWORD_CHANGE", "INFO",
            "用户已修改密码", "CHANGE_PASSWORD");
    }

    public String resetPassword(User actor, Long userId) {
        requireAdmin(actor);
        if (userId == null) {
            throw new BusinessException("用户不存在");
        }
        if (actor.getUserId().equals(userId)) {
            throw new BusinessException("当前账号请使用修改密码功能");
        }
        User target = userDAO.findByIdForSecurity(userId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        String temporaryPassword;
        String hash;
        do {
            temporaryPassword = PasswordUtil.generateTemporaryPassword();
            try {
                hash = PasswordUtil.hashPassword(temporaryPassword,
                    target.getUsername(), target.getEmail(), target.getPhone());
            } catch (BusinessException ex) {
                hash = null;
            }
        } while (hash == null);
        if (userDAO.updatePasswordSecurity(userId, hash, true, LocalDateTime.now()) != 1) {
            throw new BusinessException("密码重置失败");
        }
        auditLogService.write(String.valueOf(actor.getUserId()), "ADMIN_OPERATION", "WARN",
            "管理员重置用户密码，目标用户ID=" + userId, "RESET_USER_PASSWORD");
        return temporaryPassword;
    }

    public void updateUser(User actor, User user) {
        requireActiveUser(actor);
        if (!actor.getUserId().equals(user.getUserId()) && !isAdmin(actor)) {
            throw new BusinessException("无权修改该用户信息");
        }
        validateEmail(user.getEmail());
        validatePhone(user.getPhone());
        userDAO.updateBasicInfo(user);
        actionLogService.write(String.valueOf(actor.getUserId()), null, "UPDATE_PROFILE");
        auditLogService.write(String.valueOf(actor.getUserId()), "USER_OPERATION", "INFO",
            "更新用户基础资料", "UPDATE_USER_BASIC_INFO");
    }

    public void saveProfile(User actor, Profile profile) {
        requireActiveUser(actor);
        if (!actor.getUserId().equals(profile.getUserId()) && !isAdmin(actor)) {
            throw new BusinessException("无权修改该用户档案");
        }
        validateProfile(profile);
        profileDAO.upsert(profile);
        actionLogService.write(String.valueOf(actor.getUserId()), null, "UPDATE_PROFILE");
        auditLogService.write(String.valueOf(actor.getUserId()), "USER_OPERATION", "INFO",
            "更新用户档案", "SAVE_PROFILE");
    }

    public Profile getProfile(Long userId) {
        return profileDAO.findByUserId(userId);
    }

    public void changeUserStatus(User actor, Long userId, int status) {
        requireAdmin(actor);
        validateStatusChange(actor, userId, status);
        if (userDAO.findById(userId) == null) {
            throw new BusinessException("用户不存在");
        }
        userDAO.updateStatus(userId, status);
        auditLogService.write(String.valueOf(actor.getUserId()), "ADMIN_OPERATION", "INFO", "更新用户状态", "CHANGE_USER_STATUS");
    }

    public User findById(Long userId) {
        return withoutPasswordHash(userDAO.findById(userId));
    }

    public java.util.List<User> listUsers(User actor) {
        requireAdmin(actor);
        return userDAO.findAll().stream().map(UserService::withoutPasswordHash).toList();
    }

    static void validateStatusChange(User actor, Long userId, int status) {
        if (userId == null) {
            throw new BusinessException("用户不存在");
        }
        if (status != 0 && status != 1) {
            throw new BusinessException("用户状态非法");
        }
        if (status == 0 && actor != null && userId.equals(actor.getUserId())) {
            throw new BusinessException("不能禁用当前登录账号");
        }
    }

    public static void requireAdmin(User actor) {
        requireActiveUser(actor);
        if (!"ADMIN".equals(actor.getRole())) {
            throw new BusinessException("需要 ADMIN 权限");
        }
    }

    public static void requireActiveUser(User actor) {
        if (actor == null || actor.getStatus() == null || actor.getStatus() != 1) {
            throw new BusinessException("当前用户不可用");
        }
    }

    public static boolean isAdmin(User actor) {
        return actor != null && "ADMIN".equals(actor.getRole());
    }

    private void validateRegistration(String username, String password, String email, String phone) {
        if (username == null || username.isBlank() || username.length() > 50) {
            throw new BusinessException("用户名长度不合法");
        }
        PasswordUtil.validateStrength(password, username, email, phone);
        validateEmail(email);
        validatePhone(phone);
    }

    private void validateEmail(String email) {
        if (email == null || email.length() > 100 || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new BusinessException("邮箱格式不正确");
        }
    }

    private void validatePhone(String phone) {
        if (phone == null || phone.length() > 20 || !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new BusinessException("手机号格式不正确");
        }
    }

    private void validateProfile(Profile profile) {
        if (profile == null || profile.getUserId() == null) {
            throw new BusinessException("用户档案不完整");
        }
        if (tooLong(profile.getRealName(), 50)) {
            throw new BusinessException("真实姓名长度不合法");
        }
        if (tooLong(profile.getIdCard(), 20)) {
            throw new BusinessException("身份证号长度不合法");
        }
        if (tooLong(profile.getAddress(), 500)) {
            throw new BusinessException("地址长度不合法");
        }
        if (tooLong(profile.getNotes(), 1000)) {
            throw new BusinessException("备注长度不合法");
        }
    }

    private boolean tooLong(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    private static User withoutPasswordHash(User user) {
        if (user != null) {
            user.setPasswordHash(null);
        }
        return user;
    }

}
