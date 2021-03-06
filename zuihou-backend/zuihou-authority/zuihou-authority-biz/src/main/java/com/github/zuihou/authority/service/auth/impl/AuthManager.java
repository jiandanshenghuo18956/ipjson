package com.github.zuihou.authority.service.auth.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.zuihou.auth.server.utils.JwtTokenServerUtils;
import com.github.zuihou.auth.utils.JwtUserInfo;
import com.github.zuihou.auth.utils.Token;
import com.github.zuihou.authority.dto.auth.LoginDTO;
import com.github.zuihou.authority.dto.auth.ResourceQueryDTO;
import com.github.zuihou.authority.dto.auth.UserDTO;
import com.github.zuihou.authority.entity.auth.Resource;
import com.github.zuihou.authority.entity.auth.User;
import com.github.zuihou.authority.entity.defaults.GlobalUser;
import com.github.zuihou.authority.entity.defaults.Tenant;
import com.github.zuihou.authority.enumeration.auth.Sex;
import com.github.zuihou.authority.enumeration.defaults.TenantStatusEnum;
import com.github.zuihou.authority.service.auth.ResourceService;
import com.github.zuihou.authority.service.auth.UserService;
import com.github.zuihou.authority.service.defaults.GlobalUserService;
import com.github.zuihou.authority.service.defaults.TenantService;
import com.github.zuihou.authority.utils.TimeUtils;
import com.github.zuihou.base.R;
import com.github.zuihou.context.BaseContextHandler;
import com.github.zuihou.database.properties.DatabaseProperties;
import com.github.zuihou.dozer.DozerUtils;
import com.github.zuihou.exception.BizException;
import com.github.zuihou.exception.code.ExceptionCode;
import com.github.zuihou.model.RemoteData;
import com.github.zuihou.utils.BizAssert;
import com.github.zuihou.utils.NumberHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.zuihou.utils.BizAssert.*;

/**
 * @author zuihou
 * @createTime 2017-12-15 13:42
 */
@Service
@Slf4j
public class AuthManager {
    @Autowired
    private JwtTokenServerUtils jwtTokenServerUtils;
    @Autowired
    private UserService userService;
    @Autowired
    private GlobalUserService globalUserService;
    @Autowired
    private ResourceService resourceService;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private DozerUtils dozer;
    @Autowired
    private DatabaseProperties databaseProperties;

    private static final String SUPER_TENANT = "admin";
    private static final String[] SUPER_ACCOUNT = new String[]{"admin", "superAdmin"};

    /**
     * ??????????????????
     *
     * @param account
     * @param password
     * @return
     */
    public R<LoginDTO> adminLogin(String account, String password) {
        GlobalUser user = this.globalUserService.getOne(Wrappers.<GlobalUser>lambdaQuery()
                .eq(GlobalUser::getAccount, account).eq(GlobalUser::getTenantCode, SUPER_TENANT));
        // ????????????
        if (user == null) {
            throw new BizException(ExceptionCode.JWT_USER_INVALID.getCode(), ExceptionCode.JWT_USER_INVALID.getMsg());
        }

        String passwordMd5 = DigestUtils.md5Hex(password);
        if (!user.getPassword().equalsIgnoreCase(passwordMd5)) {
            this.userService.updatePasswordErrorNumById(user.getId());
            return R.fail("????????????????????????!");
        }
        JwtUserInfo userInfo = new JwtUserInfo(user.getId(), user.getAccount(), user.getName(), 0L, 0L);

        Token token = this.jwtTokenServerUtils.generateUserToken(userInfo, null);
        log.info("token={}", token.getToken());

        UserDTO dto = this.dozer.map(user, UserDTO.class);
        dto.setStatus(true).setOrg(new RemoteData<>(0L)).setStation(new RemoteData<>(0L)).setAvatar("").setSex(Sex.M).setWorkDescribe("???????????????");
        return R.success(LoginDTO.builder().user(dto).token(token).build());
    }

    /**
     * ?????????????????????
     *
     * @param tenantCode
     * @param account
     * @param password
     * @return
     */
    private R<LoginDTO> tenantLogin(String tenantCode, String account, String password) {
        // 1???????????????????????????
        Tenant tenant = this.tenantService.getByCode(tenantCode);
        notNull(tenant, "???????????????");
        BizAssert.equals(TenantStatusEnum.NORMAL, tenant.getStatus(), "???????????????~");
        if (tenant.getExpirationTime() != null) {
            gt(LocalDateTime.now(), tenant.getExpirationTime(), "?????????????????????~");
        }
        BaseContextHandler.setTenant(tenant.getCode());

        // 2. ????????????
        R<User> result = this.getUser(tenant, account, password);
        if (result.getIsError()) {
            return R.fail(result.getCode(), result.getMsg());
        }
        User user = result.getData();

        // 3, token
        Token token = this.getToken(user);

        List<Resource> resourceList = this.resourceService.findVisibleResource(ResourceQueryDTO.builder().userId(user.getId()).build());
        List<String> permissionsList = resourceList.stream().map(Resource::getCode).collect(Collectors.toList());

        log.info("account={}", account);
        return R.success(LoginDTO.builder().user(this.dozer.map(user, UserDTO.class)).permissionsList(permissionsList).token(token).build());
    }

    /**
     * ????????????????????????
     *
     * @param account
     * @param password
     * @return
     */
    private R<LoginDTO> simpleLogin(String account, String password) {
        // 2. ????????????
        R<User> result = this.getUser(new Tenant(), account, password);
        if (result.getIsError()) {
            return R.fail(result.getCode(), result.getMsg());
        }
        User user = result.getData();

        // 3, token
        Token token = this.getToken(user);

        List<Resource> resourceList = this.resourceService.findVisibleResource(ResourceQueryDTO.builder().userId(user.getId()).build());
        List<String> permissionsList = resourceList.stream().map(Resource::getCode).collect(Collectors.toList());

        log.info("account={}", account);
        return R.success(LoginDTO.builder().user(this.dozer.map(user, UserDTO.class)).permissionsList(permissionsList).token(token).build());
    }

    /**
     * ??????????????????
     *
     * @param tenantCode
     * @param account
     * @param password
     * @return
     */

    public R<LoginDTO> login(String tenantCode, String account, String password) {
        if (this.databaseProperties.getIsMultiTenant()) {
            return this.tenantLogin(tenantCode, account, password);
        } else {
            return this.simpleLogin(account, password);
        }
    }

    private Token getToken(User user) {
        Long orgId = RemoteData.getKey(user.getOrg());
        Long stationId = RemoteData.getKey(user.getStation());

        JwtUserInfo userInfo = new JwtUserInfo(user.getId(), user.getAccount(), user.getName(), orgId, stationId);

        Token token = this.jwtTokenServerUtils.generateUserToken(userInfo, null);
        log.info("token={}", token.getToken());
        return token;
    }

    private R<User> getUser(Tenant tenant, String account, String password) {
        User user = this.userService.getOne(Wrappers.<User>lambdaQuery()
                .eq(User::getAccount, account));
        // ????????????
        String passwordMd5 = DigestUtils.md5Hex(password);
        if (user == null) {
//            throw new BizException(ExceptionCode.JWT_USER_INVALID.getCode(), ExceptionCode.JWT_USER_INVALID.getMsg());
            return R.fail(ExceptionCode.JWT_USER_INVALID);
        }


        if (!user.getPassword().equalsIgnoreCase(passwordMd5)) {
            this.userService.updatePasswordErrorNumById(user.getId());
            return R.fail("????????????????????????!");
        }

        // ????????????
        if (user.getPasswordExpireTime() != null) {
            gt(LocalDateTime.now(), user.getPasswordExpireTime(), "??????????????????????????????????????????????????????????????????!");
        }

        // ????????????
        isTrue(user.getStatus(), "???????????????????????????????????????");

        // ????????????
        Integer maxPasswordErrorNum = NumberHelper.getOrDef(tenant.getPasswordErrorNum(), 0);
        Integer passwordErrorNum = NumberHelper.getOrDef(user.getPasswordErrorNum(), 0);
        if (maxPasswordErrorNum > 0 && passwordErrorNum > maxPasswordErrorNum) {
            log.info("??????????????????{}, ????????????:{}", passwordErrorNum, maxPasswordErrorNum);

            LocalDateTime passwordErrorLockTime = TimeUtils.getPasswordErrorLockTime(tenant.getPasswordErrorLockTime());
            log.info("passwordErrorLockTime={}", passwordErrorLockTime);
            if (passwordErrorLockTime.isAfter(user.getPasswordErrorLastTime())) {
                return R.fail("?????????????????????????????????%s???,??????????????????~", maxPasswordErrorNum);
            }
        }

        // ??????????????????
//        userService.update(Wraps.<User>lbU().set(User::getPasswordErrorNum, 0).eq(User::getId, user.getId()));
        this.userService.resetPassErrorNum(user.getId());
        return R.success(user);
    }

    public JwtUserInfo validateUserToken(String token) throws BizException {
        return this.jwtTokenServerUtils.getUserInfo(token);
    }

    public void invalidUserToken(String token) throws BizException {

    }

}
