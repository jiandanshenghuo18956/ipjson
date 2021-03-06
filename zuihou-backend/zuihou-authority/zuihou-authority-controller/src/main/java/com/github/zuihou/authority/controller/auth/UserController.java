package com.github.zuihou.authority.controller.auth;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.zuihou.authority.dto.auth.*;
import com.github.zuihou.authority.entity.auth.Role;
import com.github.zuihou.authority.entity.auth.User;
import com.github.zuihou.authority.entity.core.Org;
import com.github.zuihou.authority.entity.core.Station;
import com.github.zuihou.authority.service.auth.ResourceService;
import com.github.zuihou.authority.service.auth.RoleService;
import com.github.zuihou.authority.service.auth.UserService;
import com.github.zuihou.authority.service.core.OrgService;
import com.github.zuihou.authority.service.core.StationService;
import com.github.zuihou.base.BaseController;
import com.github.zuihou.base.R;
import com.github.zuihou.base.entity.SuperEntity;
import com.github.zuihou.database.mybatis.conditions.Wraps;
import com.github.zuihou.database.mybatis.conditions.query.LbqWrapper;
import com.github.zuihou.exception.BizException;
import com.github.zuihou.log.annotation.SysLog;
import com.github.zuihou.model.RemoteData;
import com.github.zuihou.msgs.api.SmsApi;
import com.github.zuihou.sms.dto.VerificationCodeDTO;
import com.github.zuihou.sms.enumeration.VerificationCodeType;
import com.github.zuihou.user.feign.UserQuery;
import com.github.zuihou.user.model.SysOrg;
import com.github.zuihou.user.model.SysRole;
import com.github.zuihou.user.model.SysStation;
import com.github.zuihou.user.model.SysUser;
import com.github.zuihou.utils.BeanPlusUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.zuihou.common.constant.BizConstant.DEMO_ORG_ID;
import static com.github.zuihou.common.constant.BizConstant.DEMO_STATION_ID;

/**
 * <p>
 * ???????????????
 * ??????
 * </p>
 *
 * @author zuihou
 * @date 2019-07-22
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/user")
@Api(value = "User", tags = "??????")
public class UserController extends BaseController {

    @Autowired
    private UserService userService;
    @Autowired
    private OrgService orgService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private StationService stationService;
    @Resource
    private SmsApi smsApi;

    @Autowired
    private ResourceService resourceService;

    /**
     * ??????????????????
     *
     * @param userPage ??????????????????
     * @return ????????????
     */
    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "current", value = "??????", dataType = "long", paramType = "query", defaultValue = "1"),
            @ApiImplicitParam(name = "size", value = "????????????", dataType = "long", paramType = "query", defaultValue = "10"),
    })
    @GetMapping("/page")
    @SysLog("??????????????????")
    public R<IPage<User>> page(UserPageDTO userPage) {
        IPage<User> page = getPage();

        User user = BeanUtil.toBean(userPage, User.class);
//        if (userPage.getOrgId() != null && userPage.getOrgId() >= 0) {
//            user.setOrg(null);
//        }
        LbqWrapper<User> wrapper = Wraps.lbQ(user);
        if (userPage.getOrgId() != null && userPage.getOrgId() >= 0) {
            List<Org> children = orgService.findChildren(Arrays.asList(userPage.getOrgId()));
            wrapper.in(User::getOrg, children.stream().map((org) -> new RemoteData(org.getId())).collect(Collectors.toList()));
        }
        wrapper.geHeader(User::getCreateTime, userPage.getStartCreateTime())
                .leFooter(User::getCreateTime, userPage.getEndCreateTime())
                .like(User::getName, userPage.getName())
                .like(User::getAccount, userPage.getAccount())
                .like(User::getEmail, userPage.getEmail())
                .like(User::getMobile, userPage.getMobile())
                .eq(User::getSex, userPage.getSex())
                .eq(User::getStatus, userPage.getStatus())
                .orderByDesc(User::getId);
        userService.findPage(page, wrapper);
        return success(page);
    }

    /**
     * ????????????
     *
     * @param id ??????id
     * @return ????????????
     */
    @ApiOperation(value = "????????????", notes = "????????????")
    @GetMapping("/{id}")
    @SysLog("????????????")
    public R<User> get(@PathVariable Long id) {
        return success(userService.getById(id));
    }


    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    @GetMapping("/find")
    @SysLog("??????????????????")
    public R<List<Long>> findAllUserId() {
        return success(userService.list().stream().mapToLong(User::getId).boxed().collect(Collectors.toList()));
    }


    /**
     * ????????????
     *
     * @param data ????????????
     * @return ????????????
     */
    @ApiOperation(value = "????????????", notes = "??????????????????????????????")
    @PostMapping
    @SysLog("????????????")
    public R<User> save(@RequestBody @Validated UserSaveDTO data) {
        User user = BeanUtil.toBean(data, User.class);
        userService.saveUser(user);
        return success(user);
    }

    /**
     * ????????????
     *
     * @param data ????????????
     * @return ????????????
     */
    @ApiOperation(value = "????????????", notes = "??????????????????????????????")
    @PutMapping
    @SysLog("????????????")
    public R<User> update(@RequestBody @Validated(SuperEntity.Update.class) UserUpdateDTO data) {
        User user = BeanUtil.toBean(data, User.class);
        userService.updateUser(user);
        return success(user);
    }

    @ApiOperation(value = "????????????", notes = "????????????")
    @PutMapping("/avatar")
    @SysLog("????????????")
    public R<User> avatar(@RequestBody @Validated(SuperEntity.Update.class) UserUpdateAvatarDTO data) {
        User user = BeanUtil.toBean(data, User.class);
        userService.updateUser(user);
        return success(user);
    }

    @ApiOperation(value = "????????????", notes = "????????????")
    @PutMapping("/password")
    @SysLog("????????????")
    public R<Boolean> updatePassword(@RequestBody UserUpdatePasswordDTO data) {
        return success(userService.updatePassword(data));
    }

    @ApiOperation(value = "????????????", notes = "????????????")
    @GetMapping("/reset")
    @SysLog("????????????")
    public R<Boolean> resetTx(@RequestParam("ids[]") List<Long> ids) {
        userService.reset(ids);
        return success();
    }

    /**
     * ????????????
     *
     * @param ids ??????id
     * @return ????????????
     */
    @ApiOperation(value = "????????????", notes = "??????id??????????????????")
    @DeleteMapping
    @SysLog("????????????")
    public R<Boolean> delete(@RequestParam("ids[]") List<Long> ids) {
        userService.remove(ids);
        return success(true);
    }


    /**
     * ??????????????????
     *
     * @param id ??????id
     * @return ????????????
     */
    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    @PostMapping(value = "/anno/id/{id}")
    public R<SysUser> getById(@PathVariable Long id, @RequestBody UserQuery query) {
        User user = userService.getById(id);
        if (user == null) {
            return success(null);
        }
        SysUser sysUser = BeanUtil.toBean(user, SysUser.class);

        sysUser.setOrgId(RemoteData.getKey(user.getOrg()));
        sysUser.setStationId(RemoteData.getKey(user.getOrg()));

        if (query.getFull() || query.getOrg()) {
            sysUser.setOrg(BeanUtil.toBean(orgService.getById(user.getOrg()), SysOrg.class));
        }
        if (query.getFull() || query.getStation()) {
            Station station = stationService.getById(user.getStation());
            SysStation sysStation = BeanUtil.toBean(station, SysStation.class);
            sysStation.setOrgId(RemoteData.getKey(station.getOrg()));
            sysUser.setStation(sysStation);
        }

        if (query.getFull() || query.getRoles()) {
            List<Role> list = roleService.findRoleByUserId(id);
            sysUser.setRoles(BeanPlusUtil.toBeanList(list, SysRole.class));
        }

        return success(sysUser);
    }

    /**
     * ????????????id???????????????????????????
     *
     * @param id ??????id
     * @return
     */
    @ApiOperation(value = "????????????????????????", notes = "????????????id???????????????????????????")
    @GetMapping(value = "/ds/{id}")
    public Map<String, Object> getDataScopeById(@PathVariable("id") Long id) {
        return userService.getDataScopeById(id);
    }

    /**
     * ??????????????????????????????
     *
     * @param roleId  ??????id
     * @param keyword ???????????????
     * @return
     */
    @ApiOperation(value = "??????????????????????????????", notes = "??????????????????????????????")
    @GetMapping(value = "/role/{roleId}")
    public R<UserRoleDTO> findUserByRoleId(@PathVariable("roleId") Long roleId, @RequestParam(value = "keyword", required = false) String keyword) {
        List<User> list = userService.findUserByRoleId(roleId, keyword);
        List<Long> idList = list.stream().mapToLong(User::getId).boxed().collect(Collectors.toList());
        return success(UserRoleDTO.builder().idList(idList).userList(list).build());
    }


    /**
     * ????????????
     *
     * @param data ????????????
     * @return ????????????
     */
    @ApiOperation(value = "????????????", notes = "????????????")
    @PostMapping("/anno/register")
    @SysLog("????????????")
    public R<Boolean> register(@RequestBody @Validated UserRegisterDTO data) {
        R<Boolean> result = smsApi.verification(VerificationCodeDTO.builder()
                .code(data.getVerificationCode())
                .mobile(data.getMobile()).type(VerificationCodeType.REGISTER_USER)
                .build());

        //??????????????????????????????
        if (result.getIsError() || !result.getData()) {
            return result;
        }

        User user = User.builder()
                .account(data.getMobile())
                .name(data.getMobile()).orgId(new RemoteData<>(DEMO_ORG_ID)).stationId(new RemoteData<>(DEMO_STATION_ID))
                .mobile(data.getMobile())
                .password(DigestUtils.md5Hex(data.getPassword()))
                .build();
        return success(userService.save(user));
    }


    @SysLog("?????????????????????????????????")
    @ApiOperation(value = "?????????????????????????????????", notes = "?????????????????????????????????")
    @PostMapping(value = "/reload")
    public R<LoginDTO> reload(@RequestParam Long userId) throws BizException {
        User user = userService.getById(userId);
        if (user == null) {
            return R.fail("???????????????");
        }

        List<com.github.zuihou.authority.entity.auth.Resource> resourceList = this.resourceService.findVisibleResource(ResourceQueryDTO.builder().userId(userId).build());
        List<String> permissionsList = resourceList.stream().map(com.github.zuihou.authority.entity.auth.Resource::getCode).collect(Collectors.toList());

        return this.success(LoginDTO.builder().user(BeanUtil.toBean(user, UserDTO.class)).permissionsList(permissionsList).token(null).build());
    }


}
