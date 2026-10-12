package com.smartparking.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartparking.user.po.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
