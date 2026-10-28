package com.smartparking.user.controller;

import com.smartparking.common.core.domain.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * 小区档案查询。
 *
 * <p>注意：这里用 JdbcTemplate 直接查询，返回的是数据库列名。
 * 前端统一按驼峰命名消费（spacesTotal / createTime），因此 SQL 中显式加别名，
 * 避免前端拿到 spaces_total 造成字段读空。
 */
@RestController
@RequestMapping("/api/user/community")
@RequiredArgsConstructor
public class CommunityController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(jdbcTemplate.queryForList(
                "SELECT id, name, address, city, "
                        + "spaces_total AS spacesTotal, "
                        + "status, "
                        + "create_time AS createTime, "
                        + "update_time AS updateTime "
                        + "FROM community ORDER BY id"));
    }
}
