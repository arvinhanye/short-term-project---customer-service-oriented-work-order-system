package com.ticket.service;

import com.ticket.dao.mysql.ProfileDAO;
import com.ticket.dao.mysql.UserDAO;
import com.ticket.exception.BusinessException;
import com.ticket.model.Profile;
import com.ticket.model.User;
import com.ticket.model.UserRole;
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

    public record ManagedAccountResult(User user, String temporaryPassword) { }

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
        Long userId = insertUser(user, "注册失败");
        user.setUserId(userId);
        auditLogService.write(String.valueOf(userId), "USER_OPERATION", "INFO", "用户注册成功，角色=USER", "USER_REGISTER");
        return user;
    }

    /** 由 ROOT 创建后台治理账号；密码仅由调用方展示一次。 */
    public ManagedAccountResult createManagedAccount(User actor, String username, String email, String phone,
                                                       String role, String reason) {
        actor = requireFreshActor(actor);
        requireRoot(actor);
        UserRole targetRole = UserRole.from(role);
        if (targetRole == UserRole.USER) {
            throw new BusinessException("普通用户请通过公开注册入口创建");
        }
        String normalizedReason = requireReason(reason);
        if (username == null || username.isBlank() || username.length() > 50) {
            throw new BusinessException("用户名长度不合法");
        }
        validateEmail(email);
        validatePhone(phone);
        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim();
        String normalizedPhone = phone.trim();
        if (userDAO.findByUsername(normalizedUsername) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (userDAO.findByEmail(normalizedEmail) != null) {
            throw new BusinessException("邮箱已存在");
        }
        String temporaryPassword;
        while (true) {
            temporaryPassword = PasswordUtil.generateTemporaryPassword();
            try {
                PasswordUtil.validateStrength(temporaryPassword,
                    normalizedUsername, normalizedEmail, normalizedPhone);
                break;
            } catch (BusinessException ignored) {
                // 极低概率下随机密码包含账号上下文，重新生成即可。
            }
        }
        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(PasswordUtil.hashPassword(temporaryPassword,
            normalizedUsername, normalizedEmail, normalizedPhone));
        user.setEmail(normalizedEmail);
        user.setPhone(normalizedPhone);
        user.setRole(targetRole.name());
        user.setStatus(1);
        user.setFailedLoginAttempts(0);
        user.setMustChangePassword(1);
        user.setPasswordChangedAt(null);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        Long userId = insertUser(user, "后台账号创建失败");
        user.setUserId(userId);
        auditPermissionChange(actor, user, null, targetRole.name(), normalizedReason, "CREATE_MANAGED_ACCOUNT");
        return new ManagedAccountResult(withoutPasswordHash(user), temporaryPassword);
    }

    private Long insertUser(User user, String errorMessage) {
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
            throw new BusinessException(errorMessage, ex);
        }
        return userId;
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
        if ("ROOT".equals(user.getRole())) {
            auditLogService.write(String.valueOf(user.getUserId()), "ROOT_LOGIN", "WARN",
                "高权限 ROOT 账号登录", "ROOT_LOGIN_ALERT");
        }
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

    public String resetPassword(User actor, Long userId, String currentPassword) {
        actor = requireFreshActor(actor);
        requireAdministrator(actor);
        verifyCurrentPassword(actor, currentPassword);
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
        requireCanManageLowerRole(actor, target);
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
        auditLogService.write(String.valueOf(actor.getUserId()), "PERMISSION_CHANGE", "WARN",
            "操作者=" + actor.getUserId() + "，目标账号=" + userId + "，角色=" + target.getRole()
                + "，操作=重置临时密码", "RESET_USER_PASSWORD");
        return temporaryPassword;
    }

    public void updateUser(User actor, User user) {
        requireActiveUser(actor);
        if (!actor.getUserId().equals(user.getUserId())) {
            actor = requireFreshActor(actor);
            requireAdministrator(actor);
            User target = userDAO.findByIdForSecurity(user.getUserId());
            if (target == null) {
                throw new BusinessException("用户不存在");
            }
            requireCanManageLowerRole(actor, target);
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
        if (!actor.getUserId().equals(profile.getUserId())) {
            actor = requireFreshActor(actor);
            requireAdministrator(actor);
            User target = userDAO.findByIdForSecurity(profile.getUserId());
            if (target == null) {
                throw new BusinessException("用户不存在");
            }
            requireCanManageLowerRole(actor, target);
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
        changeUserStatus(actor, userId, status, "账号状态维护", null);
    }

    public void changeUserStatus(User actor, Long userId, int status, String reason, String currentPassword) {
        actor = requireFreshActor(actor);
        requireAdministrator(actor);
        verifyCurrentPassword(actor, currentPassword);
        validateStatusChange(actor, userId, status);
        User target = userDAO.findByIdForSecurity(userId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        authorizeGovernanceTarget(actor, target, currentPassword);
        String normalizedReason = requireReason(reason);
        int result = userDAO.updateStatusWithRootProtection(userId, status, target.getRole());
        if (result == -1) {
            throw new BusinessException("不能禁用最后一个有效 ROOT");
        }
        if (result == -2) {
            throw new BusinessException("目标账号角色刚刚发生变化，请刷新后重试");
        }
        if (result != 1) {
            throw new BusinessException("账号状态更新失败");
        }
        auditPermissionChange(actor, target, target.getRole(), target.getRole(), normalizedReason,
            status == 1 ? "ENABLE_ACCOUNT" : "DISABLE_ACCOUNT");
    }

    public void changeUserRole(User actor, Long userId, String newRole, String reason, String currentPassword) {
        actor = requireFreshActor(actor);
        requireRoot(actor);
        verifyCurrentPassword(actor, currentPassword);
        if (userId == null) {
            throw new BusinessException("用户不存在");
        }
        if (actor.getUserId().equals(userId)) {
            throw new BusinessException("不能修改当前登录账号的角色");
        }
        User target = userDAO.findByIdForSecurity(userId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        UserRole desiredRole = UserRole.from(newRole);
        if (desiredRole == UserRole.USER) {
            throw new BusinessException("后台权限页面不能将账号调整为普通用户");
        }
        authorizeGovernanceTarget(actor, target, currentPassword);
        String normalizedReason = requireReason(reason);
        String beforeRole = target.getRole();
        if (beforeRole.equals(desiredRole.name())) {
            throw new BusinessException("目标账号已是该角色");
        }
        int result = userDAO.updateRoleWithRootProtection(userId, desiredRole.name(), beforeRole);
        if (result == -1) {
            throw new BusinessException("不能降级最后一个有效 ROOT");
        }
        if (result == -2) {
            throw new BusinessException("目标账号角色刚刚发生变化，请刷新后重试");
        }
        if (result != 1) {
            throw new BusinessException("角色更新失败");
        }
        auditPermissionChange(actor, target, beforeRole, desiredRole.name(), normalizedReason, "CHANGE_USER_ROLE");
    }

    public User findById(Long userId) {
        return withoutPasswordHash(userDAO.findById(userId));
    }

    public java.util.List<User> listUsers(User actor) {
        actor = requireFreshActor(actor);
        requireAdministrator(actor);
        return java.util.List.copyOf(userDAO.findAllPublic());
    }

    public java.util.List<User> listAssignableStaff(User actor) {
        actor = requireFreshActor(actor);
        requireTicketStaff(actor);
        return java.util.List.copyOf(userDAO.findActiveAdminsPublic());
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
        requireAdministrator(actor);
    }

    public static void requireBusinessAdmin(User actor) {
        requireActiveUser(actor);
        if (!"ADMIN".equals(actor.getRole())) {
            throw new BusinessException("需要 ADMIN 权限");
        }
    }

    public static void requireAdministrator(User actor) {
        requireActiveUser(actor);
        if (!roleOf(actor).canViewAdministration()) {
            throw new BusinessException("需要 ROOT 或 ADMIN 权限");
        }
    }

    public static void requireRoot(User actor) {
        requireActiveUser(actor);
        if (roleOf(actor) != UserRole.ROOT) {
            throw new BusinessException("需要 ROOT 权限");
        }
    }

    public static void requireTicketStaff(User actor) {
        requireActiveUser(actor);
        if (!roleOf(actor).canProcessTickets()) {
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

    public static boolean isTicketStaff(User actor) {
        return actor != null && actor.getStatus() != null && actor.getStatus() == 1
            && roleOf(actor).canProcessTickets();
    }

    public static boolean canManageAccount(User actor, User target) {
        if (actor == null || target == null || actor.getStatus() == null || actor.getStatus() != 1) {
            return false;
        }
        UserRole actorRole = roleOf(actor);
        UserRole targetRole = roleOf(target);
        return (actorRole == UserRole.ROOT && targetRole == UserRole.ROOT
                && !actor.getUserId().equals(target.getUserId()))
            || targetRole.isLowerThan(actorRole);
    }

    private static UserRole roleOf(User user) {
        return UserRole.from(user.getRole());
    }

    private User requireFreshActor(User actor) {
        requireActiveUser(actor);
        User stored = userDAO.findByIdForSecurity(actor.getUserId());
        if (stored == null || stored.getStatus() == null || stored.getStatus() != 1) {
            throw new BusinessException("当前账号已不可用，请重新登录");
        }
        if (!stored.getRole().equals(actor.getRole())) {
            throw new BusinessException("当前账号角色已变更，请重新登录");
        }
        return stored;
    }

    private void requireCanManageLowerRole(User actor, User target) {
        if (!roleOf(target).isLowerThan(roleOf(actor))) {
            throw new BusinessException("只能管理比自己角色等级低的账号");
        }
    }

    private void authorizeGovernanceTarget(User actor, User target, String currentPassword) {
        UserRole actorRole = roleOf(actor);
        UserRole targetRole = roleOf(target);
        if (actorRole == UserRole.ROOT && targetRole == UserRole.ROOT) {
            if (actor.getUserId().equals(target.getUserId())) {
                throw new BusinessException("不能操作当前登录账号");
            }
            verifyCurrentPassword(actor, currentPassword);
            return;
        }
        requireCanManageLowerRole(actor, target);
    }

    private void verifyCurrentPassword(User actor, String currentPassword) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new BusinessException("ROOT 账号之间的敏感操作需要重新输入当前密码");
        }
        User stored = userDAO.findByIdForSecurity(actor.getUserId());
        if (stored == null || !PasswordUtil.matches(currentPassword, stored.getPasswordHash())) {
            throw new BusinessException("当前密码验证失败");
        }
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("请输入操作原因");
        }
        String normalized = reason.trim();
        if (normalized.length() > 200) {
            throw new BusinessException("操作原因不能超过 200 个字符");
        }
        return normalized;
    }

    private void auditPermissionChange(User actor, User target, String beforeRole, String afterRole,
                                       String reason, String operation) {
        auditLogService.writePermissionChange(String.valueOf(actor.getUserId()), String.valueOf(target.getUserId()),
            beforeRole, afterRole, reason, operation);
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
        if (profile.getNotificationPreference() != null
                && !java.util.Set.of("ALL", "STATUS", "RESULT", "NONE")
                    .contains(profile.getNotificationPreference())) {
            throw new BusinessException("通知偏好无效");
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
